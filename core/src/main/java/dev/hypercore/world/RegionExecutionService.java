package dev.hypercore.world;

import dev.hypercore.bridge.world.BlockDelta;
import dev.hypercore.bridge.world.EntityMoveDelta;
import dev.hypercore.bridge.world.EntityRemoveDelta;
import dev.hypercore.bridge.world.EntitySpawnDelta;
import dev.hypercore.bukkit.HyperCorePlayer;
import dev.hypercore.bukkit.HyperCoreWorld;
import dev.hypercore.plugin.PluginEventBus;
import dev.hypercore.region.RegionKey;
import dev.hypercore.region.RegionTaskCoordinator;
import dev.hypercore.world.region.RegionLock;
import dev.hypercore.bukkit.HyperCoreEntity;
import dev.hypercore.bukkit.HyperCoreLivingEntity;
import dev.hypercore.world.event.BlockBreakEvent;
import dev.hypercore.world.event.BlockPlaceEvent;
import dev.hypercore.world.event.EntityDamageEvent;
import dev.hypercore.world.event.EntitySpawnEvent;
import dev.hypercore.world.event.PlayerMoveEvent;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
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
    private volatile DeltaSink deltaSink = DeltaSink.NOOP;

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
     * Installs the delta sink that mirrors successful mutations to the remote
     * host in bridge mode. A null argument resets to the no-op sink.
     */
    public void setDeltaSink(DeltaSink deltaSink) {
        this.deltaSink = deltaSink == null ? DeltaSink.NOOP : deltaSink;
    }

    /**
     * Returns the currently installed delta sink.
     */
    public DeltaSink deltaSink() {
        return deltaSink;
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
     * Creates or loads a world from the given creator configuration.
     *
     * <p>The actual terrain generation is delegated to the Minecraft server
     * through the loader-specific {@link WorldAccessFactory}. HyperCore only
     * exposes the Bukkit API and returns the corresponding world view.
     *
     * @return the created or loaded world, or {@code null} on failure
     */
    public World createWorld(org.bukkit.WorldCreator creator) {
        String worldName = worldAccessFactory.createWorld(creator);
        if (worldName == null) {
            return null;
        }
        return world(worldName);
    }

    /**
     * Returns a handle for the world with the given name, or {@code null}.
     */
    public WorldAccess access(String worldName) {
        return worldAccessFactory.access(worldName);
    }

    /**
     * Returns the current time of day in the given world.
     */
    public long getTime(String worldName) {
        WorldAccess world = requireWorld(worldName);
        return world.getTime();
    }

    /**
     * Sets the current time of day in the given world.
     */
    public void setTime(String worldName, long time) {
        WorldAccess world = requireWorld(worldName);
        world.setTime(time);
    }

    /**
     * Returns the absolute age of the given world in ticks.
     */
    public long getFullTime(String worldName) {
        WorldAccess world = requireWorld(worldName);
        return world.getTime();
    }

    /**
     * Sets the absolute age of the given world in ticks.
     */
    public void setFullTime(String worldName, long time) {
        WorldAccess world = requireWorld(worldName);
        world.setTime(time);
    }

    /**
     * Returns whether it is raining in the given world.
     */
    public boolean hasStorm(String worldName) {
        WorldAccess world = requireWorld(worldName);
        return world.hasStorm();
    }

    /**
     * Sets whether it is raining in the given world.
     */
    public void setStorm(String worldName, boolean storm) {
        WorldAccess world = requireWorld(worldName);
        world.setStorm(storm);
    }

    /**
     * Returns whether it is thundering in the given world.
     */
    public boolean isThundering(String worldName) {
        WorldAccess world = requireWorld(worldName);
        return world.isThundering();
    }

    /**
     * Sets whether it is thundering in the given world.
     */
    public void setThundering(String worldName, boolean thundering) {
        WorldAccess world = requireWorld(worldName);
        world.setThundering(thundering);
    }

    /**
     * Returns the spawn location of the given world.
     */
    public Location getSpawnLocation(String worldName) {
        WorldAccess world = requireWorld(worldName);
        WorldAccess.Position position = world.getSpawnLocation();
        if (position == null) {
            return null;
        }
        return new Location(world(worldName), position.x(), position.y(), position.z());
    }

    /**
     * Sets the spawn location of the given world.
     */
    public void setSpawnLocation(String worldName, Location location) {
        WorldAccess world = requireWorld(worldName);
        world.setSpawnLocation(new WorldAccess.Position(location.getX(), location.getY(), location.getZ()));
    }

    /**
     * Returns the biome at the given block coordinates.
     */
    public org.bukkit.block.Biome getBiome(String worldName, int x, int y, int z) {
        WorldAccess world = requireWorld(worldName);
        return toBukkitBiome(world.getBiome(x, y, z));
    }

    /**
     * Sets the biome at the given block coordinates.
     */
    public void setBiome(String worldName, int x, int y, int z, org.bukkit.block.Biome biome) {
        WorldAccess world = requireWorld(worldName);
        world.setBiome(x, y, z, toBiomeKey(biome));
    }

    /**
     * Returns the block data string at the given block coordinates.
     */
    public String getBlockDataAsString(String worldName, int x, int y, int z) {
        WorldAccess world = requireWorld(worldName);
        RegionKey key = regionKeyFor(worldName, x, z);
        try {
            return lockFor(key).read(() -> world.getBlockDataAsString(x, y, z));
        } catch (Exception error) {
            throw new RuntimeException("Failed to read block data at " + x + "," + y + "," + z, error);
        }
    }

    /**
     * Sets the block data at the given block coordinates.
     */
    public void setBlockData(String worldName, int x, int y, int z, String blockData) {
        WorldAccess world = requireWorld(worldName);
        RegionKey key = regionKeyFor(worldName, x, z);
        try {
            lockFor(key).write(() -> {
                world.setBlockData(x, y, z, blockData);
                activeRegions.add(key);
                deltaSink.publish(new BlockDelta(worldName, x, y, z, blockData));
            });
        } catch (Exception error) {
            throw new RuntimeException("Failed to write block data at " + x + "," + y + "," + z, error);
        }
    }

    /**
     * Returns the block light level at the given block coordinates.
     */
    public int getBlockLight(String worldName, int x, int y, int z) {
        WorldAccess world = requireWorld(worldName);
        RegionKey key = regionKeyFor(worldName, x, z);
        try {
            return lockFor(key).read(() -> world.getBlockLight(x, y, z));
        } catch (Exception error) {
            throw new RuntimeException("Failed to read block light at " + x + "," + y + "," + z, error);
        }
    }

    /**
     * Returns the sky light level at the given block coordinates.
     */
    public int getSkyLight(String worldName, int x, int y, int z) {
        WorldAccess world = requireWorld(worldName);
        RegionKey key = regionKeyFor(worldName, x, z);
        try {
            return lockFor(key).read(() -> world.getSkyLight(x, y, z));
        } catch (Exception error) {
            throw new RuntimeException("Failed to read sky light at " + x + "," + y + "," + z, error);
        }
    }

    /**
     * Returns whether the block at the given coordinates is directly powered.
     */
    public boolean isBlockPowered(String worldName, int x, int y, int z) {
        WorldAccess world = requireWorld(worldName);
        RegionKey key = regionKeyFor(worldName, x, z);
        try {
            return lockFor(key).read(() -> world.isBlockPowered(x, y, z));
        } catch (Exception error) {
            throw new RuntimeException("Failed to read block powered state at " + x + "," + y + "," + z, error);
        }
    }

    /**
     * Returns whether the block at the given coordinates is indirectly powered.
     */
    public boolean isBlockIndirectlyPowered(String worldName, int x, int y, int z) {
        WorldAccess world = requireWorld(worldName);
        RegionKey key = regionKeyFor(worldName, x, z);
        try {
            return lockFor(key).read(() -> world.isBlockIndirectlyPowered(x, y, z));
        } catch (Exception error) {
            throw new RuntimeException("Failed to read indirect power at " + x + "," + y + "," + z, error);
        }
    }

    /**
     * Returns the redstone signal strength from the given face at the given
     * block coordinates.
     */
    public int getBlockPower(String worldName, int x, int y, int z, org.bukkit.block.BlockFace face) {
        WorldAccess world = requireWorld(worldName);
        RegionKey key = regionKeyFor(worldName, x, z);
        try {
            return lockFor(key).read(() -> world.getBlockPower(x, y, z, face.name().toLowerCase(java.util.Locale.ROOT)));
        } catch (Exception error) {
            throw new RuntimeException("Failed to read block power at " + x + "," + y + "," + z, error);
        }
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
     * Marks the region containing the given block position as active so it will
     * be included in the next region tick. This is a low-overhead way to drive
     * the coordinator in tests and diagnostics without performing world I/O.
     */
    public void activateRegion(String worldName, int blockX, int blockZ) {
        activeRegions.add(regionKeyFor(worldName, blockX, blockZ));
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
                deltaSink.publish(new BlockDelta(worldName, x, y, z, type.name()));
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
                    deltaSink.publish(new EntitySpawnDelta(worldName, entityId, type.name(), position.x(), position.y(), position.z()));
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
                        deltaSink.publish(new EntityMoveDelta(targetWorld, entityId, position.x(), position.y(), position.z()));
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
                deltaSink.publish(new EntityMoveDelta(targetWorld, entityId, position.x(), position.y(), position.z()));
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
     * Returns the Bukkit entity type of the entity with the given unique id,
     * or {@code null} if the entity is not tracked.
     */
    public org.bukkit.entity.EntityType getEntityType(UUID entityId) {
        String worldName = resolveEntityWorld(entityId);
        if (worldName == null) {
            return null;
        }
        WorldAccess world = worldAccessFactory.access(worldName);
        return world == null ? null : world.getEntityType(entityId);
    }

    /**
     * Returns the custom name of the entity with the given unique id.
     */
    public String getEntityCustomName(UUID entityId) {
        String worldName = resolveEntityWorld(entityId);
        if (worldName == null) {
            return null;
        }
        WorldAccess world = worldAccessFactory.access(worldName);
        return world == null ? null : world.getEntityCustomName(entityId);
    }

    /**
     * Sets the custom name of the entity with the given unique id.
     *
     * @return {@code true} if the entity was found and updated
     */
    public boolean setEntityCustomName(UUID entityId, String name) {
        String worldName = resolveEntityWorld(entityId);
        if (worldName == null) {
            return false;
        }
        WorldAccess world = requireWorld(worldName);
        Location location = getEntityLocation(entityId);
        if (location == null) {
            return false;
        }
        RegionKey key = regionKeyFor(worldName, location.getBlockX(), location.getBlockZ());
        try {
            return lockFor(key).write(() -> world.setEntityCustomName(entityId, name));
        } catch (Exception error) {
            throw new RuntimeException("Failed to set custom name for entity " + entityId, error);
        }
    }

    /**
     * Returns whether the entity with the given unique id is alive and loaded.
     */
    public boolean isEntityAlive(UUID entityId) {
        String worldName = resolveEntityWorld(entityId);
        if (worldName == null) {
            return false;
        }
        WorldAccess world = worldAccessFactory.access(worldName);
        return world != null && world.isEntityAlive(entityId);
    }

    /**
     * Removes the entity with the given unique id from the world.
     *
     * @return {@code true} if the entity was found and removed
     */
    public boolean removeEntity(UUID entityId) {
        String worldName = resolveEntityWorld(entityId);
        if (worldName == null) {
            return false;
        }
        WorldAccess world = requireWorld(worldName);
        Location location = getEntityLocation(entityId);
        if (location == null) {
            return false;
        }
        RegionKey key = regionKeyFor(worldName, location.getBlockX(), location.getBlockZ());
        try {
            return lockFor(key).write(() -> {
                boolean removed = world.removeEntity(entityId);
                if (removed) {
                    entityWorlds.remove(entityId);
                    deltaSink.publish(new EntityRemoveDelta(worldName, entityId));
                }
                return removed;
            });
        } catch (Exception error) {
            throw new RuntimeException("Failed to remove entity " + entityId, error);
        }
    }

    /**
     * Returns the velocity of the entity with the given unique id.
     */
    public org.bukkit.util.Vector getEntityVelocity(UUID entityId) {
        String worldName = resolveEntityWorld(entityId);
        if (worldName == null) {
            return new org.bukkit.util.Vector();
        }
        WorldAccess world = requireWorld(worldName);
        Location location = getEntityLocation(entityId);
        if (location == null) {
            return new org.bukkit.util.Vector();
        }
        RegionKey key = regionKeyFor(worldName, location.getBlockX(), location.getBlockZ());
        try {
            return lockFor(key).read(() -> {
                WorldAccess.Vector3 velocity = world.getEntityVelocity(entityId);
                return velocity == null
                    ? new org.bukkit.util.Vector()
                    : new org.bukkit.util.Vector(velocity.x(), velocity.y(), velocity.z());
            });
        } catch (Exception error) {
            throw new RuntimeException("Failed to read velocity for entity " + entityId, error);
        }
    }

    /**
     * Sets the velocity of the entity with the given unique id.
     *
     * @return {@code true} if the entity was found and updated
     */
    public boolean setEntityVelocity(UUID entityId, org.bukkit.util.Vector velocity) {
        String worldName = resolveEntityWorld(entityId);
        if (worldName == null) {
            return false;
        }
        WorldAccess world = requireWorld(worldName);
        Location location = getEntityLocation(entityId);
        if (location == null) {
            return false;
        }
        RegionKey key = regionKeyFor(worldName, location.getBlockX(), location.getBlockZ());
        WorldAccess.Vector3 vector = new WorldAccess.Vector3(velocity.getX(), velocity.getY(), velocity.getZ());
        try {
            return lockFor(key).write(() -> {
                boolean success = world.setEntityVelocity(entityId, vector);
                if (success) {
                    activeRegions.add(key);
                }
                return success;
            });
        } catch (Exception error) {
            throw new RuntimeException("Failed to set velocity for entity " + entityId, error);
        }
    }

    /**
     * Returns the distance the entity with the given unique id has fallen.
     */
    public float getEntityFallDistance(UUID entityId) {
        String worldName = resolveEntityWorld(entityId);
        if (worldName == null) {
            return 0.0f;
        }
        WorldAccess world = requireWorld(worldName);
        Location location = getEntityLocation(entityId);
        if (location == null) {
            return 0.0f;
        }
        RegionKey key = regionKeyFor(worldName, location.getBlockX(), location.getBlockZ());
        try {
            return lockFor(key).read(() -> world.getEntityFallDistance(entityId));
        } catch (Exception error) {
            throw new RuntimeException("Failed to read fall distance for entity " + entityId, error);
        }
    }

    /**
     * Sets the distance the entity with the given unique id has fallen.
     *
     * @return {@code true} if the entity was found and updated
     */
    public boolean setEntityFallDistance(UUID entityId, float distance) {
        String worldName = resolveEntityWorld(entityId);
        if (worldName == null) {
            return false;
        }
        WorldAccess world = requireWorld(worldName);
        Location location = getEntityLocation(entityId);
        if (location == null) {
            return false;
        }
        RegionKey key = regionKeyFor(worldName, location.getBlockX(), location.getBlockZ());
        try {
            return lockFor(key).write(() -> {
                boolean success = world.setEntityFallDistance(entityId, distance);
                if (success) {
                    activeRegions.add(key);
                }
                return success;
            });
        } catch (Exception error) {
            throw new RuntimeException("Failed to set fall distance for entity " + entityId, error);
        }
    }

    /**
     * Returns the number of ticks the entity with the given unique id is on fire.
     */
    public int getEntityFireTicks(UUID entityId) {
        String worldName = resolveEntityWorld(entityId);
        if (worldName == null) {
            return 0;
        }
        WorldAccess world = requireWorld(worldName);
        Location location = getEntityLocation(entityId);
        if (location == null) {
            return 0;
        }
        RegionKey key = regionKeyFor(worldName, location.getBlockX(), location.getBlockZ());
        try {
            return lockFor(key).read(() -> world.getEntityFireTicks(entityId));
        } catch (Exception error) {
            throw new RuntimeException("Failed to read fire ticks for entity " + entityId, error);
        }
    }

    /**
     * Sets the number of ticks the entity with the given unique id is on fire.
     *
     * @return {@code true} if the entity was found and updated
     */
    public boolean setEntityFireTicks(UUID entityId, int ticks) {
        String worldName = resolveEntityWorld(entityId);
        if (worldName == null) {
            return false;
        }
        WorldAccess world = requireWorld(worldName);
        Location location = getEntityLocation(entityId);
        if (location == null) {
            return false;
        }
        RegionKey key = regionKeyFor(worldName, location.getBlockX(), location.getBlockZ());
        try {
            return lockFor(key).write(() -> {
                boolean success = world.setEntityFireTicks(entityId, ticks);
                if (success) {
                    activeRegions.add(key);
                }
                return success;
            });
        } catch (Exception error) {
            throw new RuntimeException("Failed to set fire ticks for entity " + entityId, error);
        }
    }

    /**
     * Returns the passengers of the entity with the given unique id.
     */
    public List<org.bukkit.entity.Entity> getEntityPassengers(UUID entityId) {
        String worldName = resolveEntityWorld(entityId);
        if (worldName == null) {
            return List.of();
        }
        WorldAccess world = requireWorld(worldName);
        Location location = getEntityLocation(entityId);
        if (location == null) {
            return List.of();
        }
        RegionKey key = regionKeyFor(worldName, location.getBlockX(), location.getBlockZ());
        try {
            return lockFor(key).read(() -> {
                Collection<UUID> passengerIds = world.getEntityPassengers(entityId);
                List<org.bukkit.entity.Entity> passengers = new ArrayList<>(passengerIds.size());
                for (UUID passengerId : passengerIds) {
                    org.bukkit.entity.Entity passenger = resolveEntity(passengerId);
                    if (passenger != null) {
                        passengers.add(passenger);
                    }
                }
                return List.copyOf(passengers);
            });
        } catch (Exception error) {
            throw new RuntimeException("Failed to read passengers for entity " + entityId, error);
        }
    }

    /**
     * Adds a passenger to the entity with the given unique id.
     *
     * @return {@code true} if the passenger was added
     */
    public boolean addEntityPassenger(UUID entityId, org.bukkit.entity.Entity passenger) {
        if (passenger == null) {
            return false;
        }
        String worldName = resolveEntityWorld(entityId);
        if (worldName == null) {
            return false;
        }
        WorldAccess world = requireWorld(worldName);
        Location location = getEntityLocation(entityId);
        if (location == null) {
            return false;
        }
        RegionKey key = regionKeyFor(worldName, location.getBlockX(), location.getBlockZ());
        try {
            return lockFor(key).write(() -> {
                boolean success = world.addEntityPassenger(entityId, passenger.getUniqueId());
                if (success) {
                    activeRegions.add(key);
                }
                return success;
            });
        } catch (Exception error) {
            throw new RuntimeException("Failed to add passenger to entity " + entityId, error);
        }
    }

    /**
     * Removes a passenger from the entity with the given unique id.
     */
    public boolean removeEntityPassenger(UUID entityId, org.bukkit.entity.Entity passenger) {
        if (passenger == null) {
            return false;
        }
        String worldName = resolveEntityWorld(entityId);
        if (worldName == null) {
            return false;
        }
        WorldAccess world = requireWorld(worldName);
        Location location = getEntityLocation(entityId);
        if (location == null) {
            return false;
        }
        RegionKey key = regionKeyFor(worldName, location.getBlockX(), location.getBlockZ());
        try {
            return lockFor(key).write(() -> world.removeEntityPassenger(entityId, passenger.getUniqueId()));
        } catch (Exception error) {
            throw new RuntimeException("Failed to remove passenger from entity " + entityId, error);
        }
    }

    /**
     * Returns whether the entity with the given unique id is inside a vehicle.
     */
    public boolean isEntityInsideVehicle(UUID entityId) {
        String worldName = resolveEntityWorld(entityId);
        if (worldName == null) {
            return false;
        }
        WorldAccess world = requireWorld(worldName);
        Location location = getEntityLocation(entityId);
        if (location == null) {
            return false;
        }
        RegionKey key = regionKeyFor(worldName, location.getBlockX(), location.getBlockZ());
        try {
            return lockFor(key).read(() -> world.isEntityInsideVehicle(entityId));
        } catch (Exception error) {
            throw new RuntimeException("Failed to read vehicle state for entity " + entityId, error);
        }
    }

    /**
     * Returns the vehicle of the entity with the given unique id.
     */
    public org.bukkit.entity.Entity getEntityVehicle(UUID entityId) {
        String worldName = resolveEntityWorld(entityId);
        if (worldName == null) {
            return null;
        }
        WorldAccess world = requireWorld(worldName);
        Location location = getEntityLocation(entityId);
        if (location == null) {
            return null;
        }
        RegionKey key = regionKeyFor(worldName, location.getBlockX(), location.getBlockZ());
        try {
            return lockFor(key).read(() -> {
                UUID vehicleId = world.getEntityVehicle(entityId);
                return vehicleId == null ? null : resolveEntity(vehicleId);
            });
        } catch (Exception error) {
            throw new RuntimeException("Failed to read vehicle for entity " + entityId, error);
        }
    }

    /**
     * Makes the entity with the given unique id leave its vehicle.
     *
     * @return {@code true} if the entity was inside a vehicle
     */
    public boolean leaveVehicle(UUID entityId) {
        String worldName = resolveEntityWorld(entityId);
        if (worldName == null) {
            return false;
        }
        WorldAccess world = requireWorld(worldName);
        Location location = getEntityLocation(entityId);
        if (location == null) {
            return false;
        }
        RegionKey key = regionKeyFor(worldName, location.getBlockX(), location.getBlockZ());
        try {
            return lockFor(key).write(() -> world.leaveVehicle(entityId));
        } catch (Exception error) {
            throw new RuntimeException("Failed to make entity " + entityId + " leave vehicle", error);
        }
    }

    /**
     * Returns the health of the living entity with the given unique id.
     */
    public double getEntityHealth(UUID entityId) {
        String worldName = resolveEntityWorld(entityId);
        if (worldName == null) {
            return 0.0;
        }
        WorldAccess world = requireWorld(worldName);
        Location location = getEntityLocation(entityId);
        if (location == null) {
            return 0.0;
        }
        RegionKey key = regionKeyFor(worldName, location.getBlockX(), location.getBlockZ());
        try {
            return lockFor(key).read(() -> world.getEntityHealth(entityId));
        } catch (Exception error) {
            throw new RuntimeException("Failed to read health for entity " + entityId, error);
        }
    }

    /**
     * Sets the health of the living entity with the given unique id.
     *
     * @return {@code true} if the entity was found and updated
     */
    public boolean setEntityHealth(UUID entityId, double health) {
        String worldName = resolveEntityWorld(entityId);
        if (worldName == null) {
            return false;
        }
        WorldAccess world = requireWorld(worldName);
        Location location = getEntityLocation(entityId);
        if (location == null) {
            return false;
        }
        RegionKey key = regionKeyFor(worldName, location.getBlockX(), location.getBlockZ());
        try {
            return lockFor(key).write(() -> {
                boolean success = world.setEntityHealth(entityId, health);
                if (success) {
                    activeRegions.add(key);
                }
                return success;
            });
        } catch (Exception error) {
            throw new RuntimeException("Failed to set health for entity " + entityId, error);
        }
    }

    /**
     * Returns the maximum health of the living entity with the given unique id.
     */
    public double getEntityMaxHealth(UUID entityId) {
        String worldName = resolveEntityWorld(entityId);
        if (worldName == null) {
            return 0.0;
        }
        WorldAccess world = requireWorld(worldName);
        Location location = getEntityLocation(entityId);
        if (location == null) {
            return 0.0;
        }
        RegionKey key = regionKeyFor(worldName, location.getBlockX(), location.getBlockZ());
        try {
            return lockFor(key).read(() -> world.getEntityMaxHealth(entityId));
        } catch (Exception error) {
            throw new RuntimeException("Failed to read max health for entity " + entityId, error);
        }
    }

    /**
     * Sets the maximum health of the living entity with the given unique id.
     *
     * @return {@code true} if the entity was found and updated
     */
    public boolean setEntityMaxHealth(UUID entityId, double maxHealth) {
        String worldName = resolveEntityWorld(entityId);
        if (worldName == null) {
            return false;
        }
        WorldAccess world = requireWorld(worldName);
        Location location = getEntityLocation(entityId);
        if (location == null) {
            return false;
        }
        RegionKey key = regionKeyFor(worldName, location.getBlockX(), location.getBlockZ());
        try {
            return lockFor(key).write(() -> {
                boolean success = world.setEntityMaxHealth(entityId, maxHealth);
                if (success) {
                    activeRegions.add(key);
                }
                return success;
            });
        } catch (Exception error) {
            throw new RuntimeException("Failed to set max health for entity " + entityId, error);
        }
    }

    /**
     * Returns whether the living entity with the given unique id has AI enabled.
     */
    public boolean isEntityAiEnabled(UUID entityId) {
        String worldName = resolveEntityWorld(entityId);
        if (worldName == null) {
            return true;
        }
        WorldAccess world = requireWorld(worldName);
        Location location = getEntityLocation(entityId);
        if (location == null) {
            return true;
        }
        RegionKey key = regionKeyFor(worldName, location.getBlockX(), location.getBlockZ());
        try {
            return lockFor(key).read(() -> world.isEntityAiEnabled(entityId));
        } catch (Exception error) {
            throw new RuntimeException("Failed to read AI state for entity " + entityId, error);
        }
    }

    /**
     * Sets whether the living entity with the given unique id has AI enabled.
     *
     * @return {@code true} if the entity was found and updated
     */
    public boolean setEntityAiEnabled(UUID entityId, boolean ai) {
        String worldName = resolveEntityWorld(entityId);
        if (worldName == null) {
            return false;
        }
        WorldAccess world = requireWorld(worldName);
        Location location = getEntityLocation(entityId);
        if (location == null) {
            return false;
        }
        RegionKey key = regionKeyFor(worldName, location.getBlockX(), location.getBlockZ());
        try {
            return lockFor(key).write(() -> {
                boolean success = world.setEntityAiEnabled(entityId, ai);
                if (success) {
                    activeRegions.add(key);
                }
                return success;
            });
        } catch (Exception error) {
            throw new RuntimeException("Failed to set AI state for entity " + entityId, error);
        }
    }

    /**
     * Returns whether the living entity with the given unique id is collidable.
     */
    public boolean isEntityCollidable(UUID entityId) {
        String worldName = resolveEntityWorld(entityId);
        if (worldName == null) {
            return true;
        }
        WorldAccess world = requireWorld(worldName);
        Location location = getEntityLocation(entityId);
        if (location == null) {
            return true;
        }
        RegionKey key = regionKeyFor(worldName, location.getBlockX(), location.getBlockZ());
        try {
            return lockFor(key).read(() -> world.isEntityCollidable(entityId));
        } catch (Exception error) {
            throw new RuntimeException("Failed to read collidable state for entity " + entityId, error);
        }
    }

    /**
     * Sets whether the living entity with the given unique id is collidable.
     *
     * @return {@code true} if the entity was found and updated
     */
    public boolean setEntityCollidable(UUID entityId, boolean collidable) {
        String worldName = resolveEntityWorld(entityId);
        if (worldName == null) {
            return false;
        }
        WorldAccess world = requireWorld(worldName);
        Location location = getEntityLocation(entityId);
        if (location == null) {
            return false;
        }
        RegionKey key = regionKeyFor(worldName, location.getBlockX(), location.getBlockZ());
        try {
            return lockFor(key).write(() -> {
                boolean success = world.setEntityCollidable(entityId, collidable);
                if (success) {
                    activeRegions.add(key);
                }
                return success;
            });
        } catch (Exception error) {
            throw new RuntimeException("Failed to set collidable state for entity " + entityId, error);
        }
    }

    /**
     * Returns the game mode of the player with the given unique id.
     */
    public org.bukkit.GameMode getPlayerGameMode(UUID playerId) {
        String worldName = resolveEntityWorld(playerId);
        if (worldName == null) {
            return null;
        }
        WorldAccess world = worldAccessFactory.access(worldName);
        return world == null ? null : world.getPlayerGameMode(playerId);
    }

    /**
     * Sets the game mode of the player with the given unique id.
     *
     * @return {@code true} if the player was found and updated
     */
    public boolean setPlayerGameMode(UUID playerId, org.bukkit.GameMode gameMode) {
        String worldName = resolveEntityWorld(playerId);
        if (worldName == null) {
            return false;
        }
        WorldAccess world = requireWorld(worldName);
        Location location = getEntityLocation(playerId);
        if (location == null) {
            return false;
        }
        RegionKey key = regionKeyFor(worldName, location.getBlockX(), location.getBlockZ());
        try {
            return lockFor(key).write(() -> world.setPlayerGameMode(playerId, gameMode));
        } catch (Exception error) {
            throw new RuntimeException("Failed to set game mode for player " + playerId, error);
        }
    }

    /**
     * Disconnects the player with the given unique id from the server.
     */
    public void kickPlayer(UUID playerId, String message) {
        String worldName = resolveEntityWorld(playerId);
        if (worldName == null) {
            return;
        }
        WorldAccess world = worldAccessFactory.access(worldName);
        if (world == null) {
            return;
        }
        world.kickPlayer(playerId, message);
    }

    /**
     * Sends a title and optional subtitle to the player with the given unique id.
     */
    public void sendTitle(UUID playerId, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        String worldName = resolveEntityWorld(playerId);
        if (worldName == null) {
            return;
        }
        WorldAccess world = worldAccessFactory.access(worldName);
        if (world == null) {
            return;
        }
        world.sendTitle(playerId, title, subtitle, fadeIn, stay, fadeOut);
    }

    /**
     * Resets the title currently displayed to the player with the given unique id.
     */
    public void resetTitle(UUID playerId) {
        String worldName = resolveEntityWorld(playerId);
        if (worldName == null) {
            return;
        }
        WorldAccess world = worldAccessFactory.access(worldName);
        if (world == null) {
            return;
        }
        world.resetTitle(playerId);
    }

    /**
     * Executes a command as the player with the given unique id.
     *
     * @return {@code true} if the command was found and executed
     */
    public boolean performCommand(UUID playerId, String command) {
        String worldName = resolveEntityWorld(playerId);
        if (worldName == null) {
            return false;
        }
        WorldAccess world = worldAccessFactory.access(worldName);
        if (world == null) {
            return false;
        }
        return world.performCommand(playerId, command);
    }

    /**
     * Sends the current inventory contents to the player with the given unique id.
     */
    public void updateInventory(UUID playerId) {
        String worldName = resolveEntityWorld(playerId);
        if (worldName == null) {
            return;
        }
        WorldAccess world = worldAccessFactory.access(worldName);
        if (world == null) {
            return;
        }
        world.updateInventory(playerId);
    }

    /**
     * Opens the given inventory for the player with the given unique id.
     *
     * @return {@code true} if the inventory was opened
     */
    public boolean openInventory(UUID playerId, org.bukkit.inventory.Inventory inventory) {
        String worldName = resolveEntityWorld(playerId);
        if (worldName == null) {
            return false;
        }
        WorldAccess world = worldAccessFactory.access(worldName);
        if (world == null) {
            return false;
        }
        return world.openInventory(playerId, inventory);
    }

    /**
     * Sets the resource pack URL for the player with the given unique id.
     */
    public void setResourcePack(UUID playerId, String url) {
        String worldName = resolveEntityWorld(playerId);
        if (worldName == null) {
            return;
        }
        WorldAccess world = worldAccessFactory.access(worldName);
        if (world == null) {
            return;
        }
        world.setResourcePack(playerId, url);
    }

    /**
     * Returns whether the player with the given unique id is sneaking.
     */
    public boolean isSneaking(UUID playerId) {
        String worldName = resolveEntityWorld(playerId);
        if (worldName == null) {
            return false;
        }
        WorldAccess world = requireWorld(worldName);
        Location location = getEntityLocation(playerId);
        if (location == null) {
            return false;
        }
        RegionKey key = regionKeyFor(worldName, location.getBlockX(), location.getBlockZ());
        try {
            return lockFor(key).read(() -> world.isSneaking(playerId));
        } catch (Exception error) {
            throw new RuntimeException("Failed to read sneaking state for player " + playerId, error);
        }
    }

    /**
     * Sets whether the player with the given unique id is sneaking.
     */
    public void setSneaking(UUID playerId, boolean sneaking) {
        String worldName = resolveEntityWorld(playerId);
        if (worldName == null) {
            return;
        }
        WorldAccess world = requireWorld(worldName);
        Location location = getEntityLocation(playerId);
        if (location == null) {
            return;
        }
        RegionKey key = regionKeyFor(worldName, location.getBlockX(), location.getBlockZ());
        try {
            lockFor(key).write(() -> {
                world.setSneaking(playerId, sneaking);
                activeRegions.add(key);
            });
        } catch (Exception error) {
            throw new RuntimeException("Failed to set sneaking state for player " + playerId, error);
        }
    }

    /**
     * Returns whether the player with the given unique id is sprinting.
     */
    public boolean isSprinting(UUID playerId) {
        String worldName = resolveEntityWorld(playerId);
        if (worldName == null) {
            return false;
        }
        WorldAccess world = requireWorld(worldName);
        Location location = getEntityLocation(playerId);
        if (location == null) {
            return false;
        }
        RegionKey key = regionKeyFor(worldName, location.getBlockX(), location.getBlockZ());
        try {
            return lockFor(key).read(() -> world.isSprinting(playerId));
        } catch (Exception error) {
            throw new RuntimeException("Failed to read sprinting state for player " + playerId, error);
        }
    }

    /**
     * Sets whether the player with the given unique id is sprinting.
     */
    public void setSprinting(UUID playerId, boolean sprinting) {
        String worldName = resolveEntityWorld(playerId);
        if (worldName == null) {
            return;
        }
        WorldAccess world = requireWorld(worldName);
        Location location = getEntityLocation(playerId);
        if (location == null) {
            return;
        }
        RegionKey key = regionKeyFor(worldName, location.getBlockX(), location.getBlockZ());
        try {
            lockFor(key).write(() -> {
                world.setSprinting(playerId, sprinting);
                activeRegions.add(key);
            });
        } catch (Exception error) {
            throw new RuntimeException("Failed to set sprinting state for player " + playerId, error);
        }
    }

    /**
     * Returns the armor contents of the player with the given unique id.
     */
    public org.bukkit.inventory.ItemStack[] getPlayerArmor(UUID playerId) {
        String worldName = resolveEntityWorld(playerId);
        if (worldName == null) {
            return new org.bukkit.inventory.ItemStack[4];
        }
        WorldAccess world = requireWorld(worldName);
        Location location = getEntityLocation(playerId);
        if (location == null) {
            return new org.bukkit.inventory.ItemStack[4];
        }
        RegionKey key = regionKeyFor(worldName, location.getBlockX(), location.getBlockZ());
        try {
            return lockFor(key).read(() -> world.getPlayerArmor(playerId));
        } catch (Exception error) {
            throw new RuntimeException("Failed to read armor for player " + playerId, error);
        }
    }

    /**
     * Sets the armor contents of the player with the given unique id.
     */
    public void setPlayerArmor(UUID playerId, org.bukkit.inventory.ItemStack[] armor) {
        String worldName = resolveEntityWorld(playerId);
        if (worldName == null) {
            return;
        }
        WorldAccess world = requireWorld(worldName);
        Location location = getEntityLocation(playerId);
        if (location == null) {
            return;
        }
        RegionKey key = regionKeyFor(worldName, location.getBlockX(), location.getBlockZ());
        try {
            lockFor(key).write(() -> {
                world.setPlayerArmor(playerId, armor);
                activeRegions.add(key);
            });
        } catch (Exception error) {
            throw new RuntimeException("Failed to set armor for player " + playerId, error);
        }
    }

    /**
     * Returns the off-hand item of the player with the given unique id.
     */
    public org.bukkit.inventory.ItemStack getPlayerOffHand(UUID playerId) {
        String worldName = resolveEntityWorld(playerId);
        if (worldName == null) {
            return null;
        }
        WorldAccess world = requireWorld(worldName);
        Location location = getEntityLocation(playerId);
        if (location == null) {
            return null;
        }
        RegionKey key = regionKeyFor(worldName, location.getBlockX(), location.getBlockZ());
        try {
            return lockFor(key).read(() -> world.getPlayerOffHand(playerId));
        } catch (Exception error) {
            throw new RuntimeException("Failed to read off-hand item for player " + playerId, error);
        }
    }

    /**
     * Sets the off-hand item of the player with the given unique id.
     */
    public void setPlayerOffHand(UUID playerId, org.bukkit.inventory.ItemStack item) {
        String worldName = resolveEntityWorld(playerId);
        if (worldName == null) {
            return;
        }
        WorldAccess world = requireWorld(worldName);
        Location location = getEntityLocation(playerId);
        if (location == null) {
            return;
        }
        RegionKey key = regionKeyFor(worldName, location.getBlockX(), location.getBlockZ());
        try {
            lockFor(key).write(() -> {
                world.setPlayerOffHand(playerId, item);
                activeRegions.add(key);
            });
        } catch (Exception error) {
            throw new RuntimeException("Failed to set off-hand item for player " + playerId, error);
        }
    }

    /**
     * Returns the currently selected hotbar slot of the player with the given
     * unique id.
     */
    public int getPlayerHeldSlot(UUID playerId) {
        String worldName = resolveEntityWorld(playerId);
        if (worldName == null) {
            return 0;
        }
        WorldAccess world = requireWorld(worldName);
        Location location = getEntityLocation(playerId);
        if (location == null) {
            return 0;
        }
        RegionKey key = regionKeyFor(worldName, location.getBlockX(), location.getBlockZ());
        try {
            return lockFor(key).read(() -> world.getPlayerHeldSlot(playerId));
        } catch (Exception error) {
            throw new RuntimeException("Failed to read held slot for player " + playerId, error);
        }
    }

    /**
     * Sets the currently selected hotbar slot of the player with the given
     * unique id.
     */
    public void setPlayerHeldSlot(UUID playerId, int slot) {
        String worldName = resolveEntityWorld(playerId);
        if (worldName == null) {
            return;
        }
        WorldAccess world = requireWorld(worldName);
        Location location = getEntityLocation(playerId);
        if (location == null) {
            return;
        }
        RegionKey key = regionKeyFor(worldName, location.getBlockX(), location.getBlockZ());
        try {
            lockFor(key).write(() -> {
                world.setPlayerHeldSlot(playerId, slot);
                activeRegions.add(key);
            });
        } catch (Exception error) {
            throw new RuntimeException("Failed to set held slot for player " + playerId, error);
        }
    }

    /**
     * Deals damage to the entity with the given unique id.
     *
     * <p>Fires an {@link EntityDamageEvent} before applying damage. If the event
     * is cancelled, no damage is dealt.
     */
    public void damageEntity(UUID entityId, double amount) {
        String worldName = resolveEntityWorld(entityId);
        if (worldName == null) {
            return;
        }
        WorldAccess world = requireWorld(worldName);
        Location location = getEntityLocation(entityId);
        if (location == null) {
            return;
        }
        RegionKey key = regionKeyFor(worldName, location.getBlockX(), location.getBlockZ());
        try {
            lockFor(key).write(() -> {
                org.bukkit.entity.Entity entity = resolveEntity(entityId);
                if (entity == null) {
                    return;
                }
                EntityDamageEvent event = new EntityDamageEvent(entity, amount);
                eventBus.post(event);
                if (event.cancelled()) {
                    return;
                }
                world.damageEntity(entityId, event.getDamage());
                activeRegions.add(key);
            });
        } catch (Exception error) {
            throw new RuntimeException("Failed to damage entity " + entityId, error);
        }
    }

    /**
     * Resolves a tracked entity to its appropriate Bukkit view.
     *
     * <p>Players become {@code HyperCorePlayer}, living mobs become
     * {@code HyperCoreLivingEntity}, and everything else becomes a plain
     * {@code HyperCoreEntity}.
     */
    public org.bukkit.entity.Entity resolveEntity(UUID entityId) {
        org.bukkit.entity.EntityType type = getEntityType(entityId);
        if (type == null) {
            return null;
        }
        if (type == org.bukkit.entity.EntityType.PLAYER) {
            return resolvePlayer(entityId);
        }
        if (isLivingEntityType(type)) {
            return new HyperCoreLivingEntity(this, entityId);
        }
        return new HyperCoreEntity(this, entityId);
    }

    private static boolean isLivingEntityType(org.bukkit.entity.EntityType type) {
        return switch (type) {
            case ZOMBIE, SKELETON, CREEPER, SPIDER, CAVE_SPIDER, ENDERMAN, WITCH,
                 VILLAGER, PIG, COW, SHEEP, CHICKEN, HORSE -> true;
            default -> false;
        };
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

    /**
     * Returns all online players across all loaded worlds.
     */
    public Collection<Player> onlinePlayers() {
        List<Player> players = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (String worldName : worldAccessFactory.worldNames()) {
            WorldAccess world = worldAccessFactory.access(worldName);
            if (world == null) {
                continue;
            }
            for (UUID playerId : world.playerIds()) {
                if (seen.add(playerId)) {
                    Player player = resolvePlayer(playerId);
                    if (player != null) {
                        players.add(player);
                    }
                }
            }
        }
        return List.copyOf(players);
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

    private org.bukkit.block.Biome toBukkitBiome(String biomeKey) {
        if (biomeKey == null) {
            return org.bukkit.block.Biome.CUSTOM;
        }
        String key = biomeKey.toLowerCase(java.util.Locale.ROOT);
        return switch (key) {
            case "minecraft:ocean" -> org.bukkit.block.Biome.OCEAN;
            case "minecraft:plains" -> org.bukkit.block.Biome.PLAINS;
            case "minecraft:desert" -> org.bukkit.block.Biome.DESERT;
            case "minecraft:windswept_hills" -> org.bukkit.block.Biome.WINDSWEPT_HILLS;
            case "minecraft:forest" -> org.bukkit.block.Biome.FOREST;
            case "minecraft:taiga" -> org.bukkit.block.Biome.TAIGA;
            case "minecraft:swamp" -> org.bukkit.block.Biome.SWAMP;
            case "minecraft:river" -> org.bukkit.block.Biome.RIVER;
            case "minecraft:nether_wastes" -> org.bukkit.block.Biome.NETHER_WASTES;
            case "minecraft:the_end" -> org.bukkit.block.Biome.THE_END;
            case "minecraft:frozen_ocean" -> org.bukkit.block.Biome.FROZEN_OCEAN;
            case "minecraft:frozen_river" -> org.bukkit.block.Biome.FROZEN_RIVER;
            case "minecraft:snowy_plains" -> org.bukkit.block.Biome.SNOWY_PLAINS;
            case "minecraft:mushroom_fields" -> org.bukkit.block.Biome.MUSHROOM_FIELDS;
            case "minecraft:beach" -> org.bukkit.block.Biome.BEACH;
            case "minecraft:jungle" -> org.bukkit.block.Biome.JUNGLE;
            case "minecraft:birch_forest" -> org.bukkit.block.Biome.BIRCH_FOREST;
            case "minecraft:dark_forest" -> org.bukkit.block.Biome.DARK_FOREST;
            case "minecraft:savanna" -> org.bukkit.block.Biome.SAVANNA;
            case "minecraft:badlands" -> org.bukkit.block.Biome.BADLANDS;
            case "minecraft:wooded_badlands" -> org.bukkit.block.Biome.WOODED_BADLANDS;
            case "minecraft:meadow" -> org.bukkit.block.Biome.MEADOW;
            case "minecraft:cherry_grove" -> org.bukkit.block.Biome.CHERRY_GROVE;
            default -> org.bukkit.block.Biome.CUSTOM;
        };
    }

    private String toBiomeKey(org.bukkit.block.Biome biome) {
        return switch (biome) {
            case OCEAN -> "minecraft:ocean";
            case PLAINS -> "minecraft:plains";
            case DESERT -> "minecraft:desert";
            case WINDSWEPT_HILLS -> "minecraft:windswept_hills";
            case FOREST -> "minecraft:forest";
            case TAIGA -> "minecraft:taiga";
            case SWAMP -> "minecraft:swamp";
            case RIVER -> "minecraft:river";
            case NETHER_WASTES -> "minecraft:nether_wastes";
            case THE_END -> "minecraft:the_end";
            case FROZEN_OCEAN -> "minecraft:frozen_ocean";
            case FROZEN_RIVER -> "minecraft:frozen_river";
            case SNOWY_PLAINS -> "minecraft:snowy_plains";
            case MUSHROOM_FIELDS -> "minecraft:mushroom_fields";
            case BEACH -> "minecraft:beach";
            case JUNGLE -> "minecraft:jungle";
            case BIRCH_FOREST -> "minecraft:birch_forest";
            case DARK_FOREST -> "minecraft:dark_forest";
            case SAVANNA -> "minecraft:savanna";
            case BADLANDS -> "minecraft:badlands";
            case WOODED_BADLANDS -> "minecraft:wooded_badlands";
            case MEADOW -> "minecraft:meadow";
            case CHERRY_GROVE -> "minecraft:cherry_grove";
            case CUSTOM -> "minecraft:plains";
        };
    }

    private WorldAccess requireWorld(String worldName) {
        WorldAccess world = worldAccessFactory.access(worldName);
        if (world == null) {
            throw new IllegalArgumentException("World is not loaded: " + worldName);
        }
        return world;
    }
}
