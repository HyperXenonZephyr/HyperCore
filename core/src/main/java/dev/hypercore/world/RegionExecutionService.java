package dev.hypercore.world;

import dev.hypercore.bukkit.HyperCorePlayer;
import dev.hypercore.bukkit.HyperCoreWorld;
import dev.hypercore.plugin.PluginEventBus;
import dev.hypercore.region.RegionKey;
import dev.hypercore.region.RegionTaskCoordinator;
import dev.hypercore.world.region.RegionLock;
import dev.hypercore.world.event.BlockBreakEvent;
import dev.hypercore.world.event.BlockPlaceEvent;
import dev.hypercore.world.event.EntitySpawnEvent;
import dev.hypercore.world.event.PlayerMoveEvent;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Schedules and serializes world mutations by region.
 *
 * <p>The service maps every block position or entity to a {@link RegionKey} and
 * acquires the corresponding {@link RegionLock} before executing the operation.
 * This guarantees that no two threads mutate the same region at the same time,
 * while reads can proceed concurrently with other reads.
 *
 * <p>Operations triggered by Bukkit plugins are executed synchronously under the
 * region lock so that plugins observe immediate results. Tick-driven batch work
 * (for example entity AI) is submitted to {@link RegionTaskCoordinator} and runs
 * on the HyperCore worker pool at the tick boundary.
 *
 * <p>World mutations fire HyperCore internal events on the shared
 * {@link PluginEventBus}. The Bukkit event bridge converts these into Bukkit
 * events and propagates cancellations back to the internal event, so cancelling
 * a Bukkit listener aborts the mutation.
 */
public final class RegionExecutionService {
    private final WorldAccessFactory worldAccessFactory;
    private final RegionTaskCoordinator coordinator;
    private final PluginEventBus eventBus;
    private final ConcurrentHashMap<String, HyperCoreWorld> worldViews = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> entityWorlds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<RegionKey, RegionLock> locks = new ConcurrentHashMap<>();
    private final Set<RegionKey> activeRegions = ConcurrentHashMap.newKeySet();

    public RegionExecutionService(WorldAccessFactory worldAccessFactory, RegionTaskCoordinator coordinator) {
        this(worldAccessFactory, coordinator, new PluginEventBus());
    }

    public RegionExecutionService(
        WorldAccessFactory worldAccessFactory,
        RegionTaskCoordinator coordinator,
        PluginEventBus eventBus
    ) {
        this.worldAccessFactory = Objects.requireNonNull(worldAccessFactory, "worldAccessFactory");
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
    }

    /**
     * Returns the event bus used for internal world events.
     */
    public PluginEventBus eventBus() {
        return eventBus;
    }

    /**
     * Returns the names of all loaded worlds.
     */
    public Collection<String> worldNames() {
        return worldAccessFactory.worldNames();
    }

    /**
     * Returns the Bukkit world view for the given name.
     */
    public World world(String worldName) {
        return worldViews.computeIfAbsent(worldName, name -> new HyperCoreWorld(this, name));
    }

    /**
     * Returns a handle for the world with the given name, or {@code null}.
     */
    public WorldAccess access(String worldName) {
        return worldAccessFactory.access(worldName);
    }

    /**
     * Returns the region lock for the given region, creating it if necessary.
     */
    public RegionLock lockFor(RegionKey key) {
        return locks.computeIfAbsent(key, ignored -> new RegionLock());
    }

    /**
     * Computes the region key that owns the given block position.
     */
    public RegionKey regionKeyFor(String worldName, int blockX, int blockZ) {
        int chunkX = Math.floorDiv(blockX, 16);
        int chunkZ = Math.floorDiv(blockZ, 16);
        return RegionKey.fromChunk(worldName, chunkX, chunkZ, RegionTaskCoordinator.DEFAULT_REGION_SIZE_CHUNKS);
    }

    /**
     * Reads the material of the block at the given coordinates.
     */
    public Material getBlockType(String worldName, int x, int y, int z) {
        WorldAccess world = requireWorld(worldName);
        RegionKey key = regionKeyFor(worldName, x, z);
        try {
            return lockFor(key).read(() -> world.getBlockType(x, y, z));
        } catch (Exception error) {
            throw new RuntimeException("Failed to read block type at " + x + "," + y + "," + z, error);
        }
    }

    /**
     * Writes the material of the block at the given coordinates.
     *
     * <p>Fires a {@link BlockPlaceEvent} when the new type is not air and a
     * {@link BlockBreakEvent} when the new type is air. If either event is
     * cancelled, the mutation is skipped.
     */
    public void setBlockType(String worldName, int x, int y, int z, Material type) {
        WorldAccess world = requireWorld(worldName);
        RegionKey key = regionKeyFor(worldName, x, z);
        try {
            lockFor(key).write(() -> {
                Block block = world(worldName).getBlockAt(x, y, z);
                if (type == Material.AIR) {
                    Material oldType = world.getBlockType(x, y, z);
                    if (oldType != Material.AIR && postCancellable(new BlockBreakEvent(block, null, oldType))) {
                        return;
                    }
                } else {
                    if (postCancellable(new BlockPlaceEvent(block, null, type))) {
                        return;
                    }
                }
                world.setBlockType(x, y, z, type);
                activeRegions.add(key);
            });
        } catch (Exception error) {
            throw new RuntimeException("Failed to write block type at " + x + "," + y + "," + z, error);
        }
    }

    /**
     * Returns the inventory of the block entity at the given coordinates.
     */
    public Inventory getBlockInventory(String worldName, int x, int y, int z) {
        WorldAccess world = requireWorld(worldName);
        RegionKey key = regionKeyFor(worldName, x, z);
        try {
            return lockFor(key).read(() -> world.getBlockInventory(x, y, z));
        } catch (Exception error) {
            throw new RuntimeException("Failed to read block inventory at " + x + "," + y + "," + z, error);
        }
    }

    /**
     * Spawns an entity of the given type at the given location.
     *
     * <p>Fires an {@link EntitySpawnEvent} before the entity is created. If the
     * event is cancelled, {@code null} is returned.
     */
    public UUID spawnEntity(String worldName, EntityType type, Location location) {
        WorldAccess world = requireWorld(worldName);
        RegionKey key = regionKeyFor(worldName, location.getBlockX(), location.getBlockZ());
        WorldAccess.Position position = new WorldAccess.Position(location.getX(), location.getY(), location.getZ());
        try {
            return lockFor(key).write(() -> {
                EntitySpawnEvent event = new EntitySpawnEvent(
                    null,
                    new Location(world(worldName), position.x(), position.y(), position.z()),
                    type
                );
                eventBus.post(event);
                if (event.cancelled()) {
                    return null;
                }
                UUID entityId = world.spawnEntity(type, position);
                if (entityId != null) {
                    entityWorlds.put(entityId, worldName);
                    activeRegions.add(key);
                }
                return entityId;
            });
        } catch (Exception error) {
            throw new RuntimeException("Failed to spawn entity at " + location, error);
        }
    }

    /**
     * Reads the location of the entity with the given unique id.
     *
     * <p>If the entity was not spawned through this service, all loaded worlds
     * are searched and the discovered mapping is cached for subsequent calls.
     */
    public Location getEntityLocation(UUID entityId) {
        String worldName = resolveEntityWorld(entityId);
        if (worldName == null) {
            return null;
        }
        WorldAccess world = worldAccessFactory.access(worldName);
        if (world == null) {
            return null;
        }
        WorldAccess.Position position = world.getEntityPosition(entityId);
        if (position == null) {
            return null;
        }
        return new Location(world(worldName), position.x(), position.y(), position.z());
    }

    /**
     * Returns the unique ids of all entities in the given world.
     */
    public Collection<UUID> entityIds(String worldName) {
        WorldAccess world = access(worldName);
        return world == null ? List.of() : world.entityIds();
    }

    private String resolveEntityWorld(UUID entityId) {
        String worldName = entityWorlds.get(entityId);
        if (worldName != null) {
            return worldName;
        }
        for (String candidate : worldAccessFactory.worldNames()) {
            WorldAccess world = worldAccessFactory.access(candidate);
            if (world == null) {
                continue;
            }
            if (world.getEntityPosition(entityId) != null) {
                entityWorlds.put(entityId, candidate);
                return candidate;
            }
        }
        return null;
    }

    /**
     * Teleports the entity to the given location.
     *
     * <p>Same-region teleports run synchronously under the region write lock.
     * Cross-region teleports are queued as a message to the target region and
     * this method returns {@code false} because the operation is not immediate.
     *
     * <p>For players, a same-region teleport fires a {@link PlayerMoveEvent}.
     */
    public boolean teleportEntity(UUID entityId, Location location) {
        String sourceWorld = resolveEntityWorld(entityId);
        if (sourceWorld == null) {
            return false;
        }
        String targetWorld = location.getWorld() == null ? sourceWorld : location.getWorld().getName();
        RegionKey sourceKey = regionKeyFor(sourceWorld, getEntityX(entityId), getEntityZ(entityId));
        RegionKey targetKey = regionKeyFor(targetWorld, location.getBlockX(), location.getBlockZ());

        if (sourceWorld.equals(targetWorld) && sourceKey.equals(targetKey)) {
            WorldAccess world = requireWorld(targetWorld);
            WorldAccess.Position position = new WorldAccess.Position(location.getX(), location.getY(), location.getZ());
            try {
                return lockFor(targetKey).write(() -> {
                    if (isPlayer(world, entityId)) {
                        WorldAccess.Position current = world.getEntityPosition(entityId);
                        if (current != null) {
                            Location from = new Location(world(targetWorld), current.x(), current.y(), current.z());
                            Player player = resolvePlayer(entityId);
                            if (player != null && postCancellable(new PlayerMoveEvent(player, from, location))) {
                                return false;
                            }
                        }
                    }
                    boolean success = world.teleportEntity(entityId, position);
                    if (success) {
                        activeRegions.add(targetKey);
                    }
                    return success;
                });
            } catch (Exception error) {
                throw new RuntimeException("Failed to teleport entity to " + location, error);
            }
        }

        // Cross-region teleport: schedule the move as a region message so the
        // source and target locks are never held together.
        WorldAccess sourceWorldAccess = access(sourceWorld);
        WorldAccess targetWorldAccess = access(targetWorld);
        if (sourceWorldAccess == null || targetWorldAccess == null) {
            return false;
        }
        WorldAccess.Position position = new WorldAccess.Position(location.getX(), location.getY(), location.getZ());
        coordinator.send(sourceKey, targetKey, () -> {
            if (targetWorldAccess.teleportEntity(entityId, position)) {
                entityWorlds.put(entityId, targetWorld);
                activeRegions.add(targetKey);
            }
        });
        return false;
    }

    /**
     * Returns the inventory of the player with the given unique id.
     */
    public Inventory getPlayerInventory(UUID playerId) {
        for (String worldName : worldAccessFactory.worldNames()) {
            WorldAccess world = worldAccessFactory.access(worldName);
            if (world == null) {
                continue;
            }
            Inventory inventory = world.getPlayerInventory(playerId);
            if (inventory != null) {
                return inventory;
            }
        }
        return null;
    }

    /**
     * Returns the inventory of the player in the given world, or {@code null}.
     */
    public Inventory getPlayerInventory(String worldName, UUID playerId) {
        WorldAccess world = access(worldName);
        return world == null ? null : world.getPlayerInventory(playerId);
    }

    /**
     * Runs one parallel region tick over all active regions and any regions with
     * pending cross-region messages.
     *
     * @param task the per-region work to execute
     * @return a future that completes when the tick finishes
     */
    public CompletableFuture<RegionTaskCoordinator.TickResult> tickRegions(RegionTickTask task) {
        return coordinator.advanceTick(activeRegions(), task, this);
    }

    /**
     * Returns the number of regions that are currently marked active without
     * clearing the tracking set.
     */
    public int pendingActiveRegions() {
        return activeRegions.size();
    }

    /**
     * Returns a snapshot of the currently active regions and clears the internal
     * tracking set so the next tick starts fresh.
     */
    public Set<RegionKey> activeRegions() {
        Set<RegionKey> snapshot = new HashSet<>(activeRegions);
        activeRegions.removeAll(snapshot);
        snapshot.addAll(worldAccessFactory.loadedRegions(RegionTaskCoordinator.DEFAULT_REGION_SIZE_CHUNKS));
        for (String worldName : worldAccessFactory.worldNames()) {
            WorldAccess world = worldAccessFactory.access(worldName);
            if (world == null) {
                continue;
            }
            for (UUID entityId : world.entityIds()) {
                WorldAccess.Position position = world.getEntityPosition(entityId);
                if (position != null) {
                    snapshot.add(regionKeyFor(worldName, (int) Math.floor(position.x()), (int) Math.floor(position.z())));
                }
            }
        }
        return snapshot;
    }

    private boolean postCancellable(PluginEventBus.CancellableEvent event) {
        eventBus.post(event);
        return event.cancelled();
    }

    private boolean isPlayer(WorldAccess world, UUID entityId) {
        return world.getPlayerInventory(entityId) != null;
    }

    private Player resolvePlayer(UUID playerId) {
        for (String worldName : worldAccessFactory.worldNames()) {
            WorldAccess world = worldAccessFactory.access(worldName);
            if (world != null && world.getPlayerInventory(playerId) != null) {
                return new HyperCorePlayer(this, playerId, "Player");
            }
        }
        return null;
    }

    private int getEntityX(UUID entityId) {
        Location location = getEntityLocation(entityId);
        return location == null ? 0 : location.getBlockX();
    }

    private int getEntityZ(UUID entityId) {
        Location location = getEntityLocation(entityId);
        return location == null ? 0 : location.getBlockZ();
    }

    private WorldAccess requireWorld(String worldName) {
        WorldAccess world = worldAccessFactory.access(worldName);
        if (world == null) {
            throw new IllegalArgumentException("World is not loaded: " + worldName);
        }
        return world;
    }
}
