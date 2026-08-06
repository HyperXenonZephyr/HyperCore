package dev.hypercore.world;

import dev.hypercore.concurrent.HyperCoreExecutor;
import dev.hypercore.plugin.PluginEventBus;
import dev.hypercore.region.RegionKey;
import dev.hypercore.region.RegionTaskCoordinator;
import dev.hypercore.world.event.BlockBreakEvent;
import dev.hypercore.world.event.BlockPlaceEvent;
import dev.hypercore.world.event.EntitySpawnEvent;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for world events and cancellation emitted by
 * {@link RegionExecutionService}.
 */
class RegionExecutionEventTest {

    private HyperCoreExecutor executor;
    private RegionTaskCoordinator coordinator;
    private RegionExecutionService service;
    private PluginEventBus events;

    @BeforeEach
    void setUp() {
        executor = HyperCoreExecutor.create(2, 16);
        coordinator = new RegionTaskCoordinator(executor, 2);
        events = new PluginEventBus();
        service = new RegionExecutionService(new MemoryWorldAccessFactory(), coordinator, events);
    }

    @AfterEach
    void tearDown() {
        executor.close();
    }

    @Test
    void blockPlaceEventFiresWithCorrectCoordinates() {
        AtomicReference<BlockPlaceEvent> captured = new AtomicReference<>();
        events.register("test", BlockPlaceEvent.class, PluginEventBus.EventPriority.NORMAL, false, captured::set);

        service.setBlockType("world", 1, 2, 3, Material.STONE);

        BlockPlaceEvent event = captured.get();
        assertNotNull(event);
        assertEquals(Material.STONE, event.getType());
        assertEquals(1, event.getBlock().getX());
        assertEquals(2, event.getBlock().getY());
        assertEquals(3, event.getBlock().getZ());
    }

    @Test
    void cancellingBlockPlaceEventPreventsPlacement() {
        events.register("test", BlockPlaceEvent.class, PluginEventBus.EventPriority.NORMAL, false, event -> event.cancelled(true));

        service.setBlockType("world", 0, 0, 0, Material.STONE);

        assertEquals(Material.AIR, service.getBlockType("world", 0, 0, 0));
    }

    @Test
    void replacingBlockWithAirFiresBlockBreakEvent() {
        service.setBlockType("world", 0, 0, 0, Material.STONE);
        AtomicReference<BlockBreakEvent> captured = new AtomicReference<>();
        events.register("test", BlockBreakEvent.class, PluginEventBus.EventPriority.NORMAL, false, captured::set);

        service.setBlockType("world", 0, 0, 0, Material.AIR);

        BlockBreakEvent event = captured.get();
        assertNotNull(event);
        assertEquals(Material.STONE, event.getType());
    }

    @Test
    void cancellingBlockBreakEventKeepsBlock() {
        service.setBlockType("world", 0, 0, 0, Material.STONE);
        events.register("test", BlockBreakEvent.class, PluginEventBus.EventPriority.NORMAL, false, event -> event.cancelled(true));

        service.setBlockType("world", 0, 0, 0, Material.AIR);

        assertEquals(Material.STONE, service.getBlockType("world", 0, 0, 0));
    }

    @Test
    void entitySpawnEventFiresAndCanBeCancelled() {
        AtomicReference<EntitySpawnEvent> captured = new AtomicReference<>();
        events.register("test", EntitySpawnEvent.class, PluginEventBus.EventPriority.NORMAL, false, captured::set);

        Location location = new Location(service.world("world"), 5.0, 64.0, 5.0);
        UUID entityId = service.spawnEntity("world", EntityType.ZOMBIE, location);

        assertNotNull(entityId);
        assertNotNull(captured.get());
        assertEquals(EntityType.ZOMBIE, captured.get().getType());

        events.register("test", EntitySpawnEvent.class, PluginEventBus.EventPriority.HIGHEST, false, event -> event.cancelled(true));
        UUID cancelled = service.spawnEntity("world", EntityType.ZOMBIE, location);
        assertNull(cancelled);
    }

    @Test
    void activeRegionsAreTrackedAndClearedByTick() {
        service.setBlockType("world", 0, 0, 0, Material.STONE);
        service.setBlockType("world", 256, 0, 256, Material.DIRT);

        assertEquals(2, service.pendingActiveRegions());

        Collection<RegionKey> snapshot = service.activeRegions();
        assertEquals(2, snapshot.size());
        assertEquals(0, service.pendingActiveRegions());
    }

    @Test
    void regionTickTaskRunsForActiveRegions() throws Exception {
        service.setBlockType("world", 0, 0, 0, Material.STONE);
        service.setBlockType("world", 256, 0, 256, Material.DIRT);

        AtomicBoolean ticked = new AtomicBoolean(false);
        RegionTickTask task = (execution, region, tickId) -> ticked.set(true);

        RegionTaskCoordinator.TickResult result = service.tickRegions(task).get();

        assertTrue(ticked.get());
        assertTrue(result.targetRegions() >= 2);
        assertTrue(result.complete());
    }

    @Test
    void fullTimeIsIndependentFromTimeOfDay() {
        service.setTime("world", 6000L);
        service.setFullTime("world", 123456789L);

        assertEquals(6000L, service.getTime("world"));
        assertEquals(123456789L, service.getFullTime("world"));
    }

    private static final class MemoryWorldAccessFactory implements WorldAccessFactory {
        private final MemoryWorldAccess world = new MemoryWorldAccess("world");

        @Override
        public WorldAccess access(String worldName) {
            return world.worldName().equals(worldName) ? world : null;
        }

        @Override
        public Collection<String> worldNames() {
            return List.of("world");
        }
    }

    private static final class MemoryWorldAccess implements WorldAccess {
        private final String worldName;
        private final ConcurrentHashMap<Long, Material> blocks = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<UUID, Position> entities = new ConcurrentHashMap<>();
        private long time;
        private long fullTime;

        MemoryWorldAccess(String worldName) {
            this.worldName = worldName;
        }

        @Override
        public String worldName() {
            return worldName;
        }

        @Override
        public long getTime() {
            return time;
        }

        @Override
        public void setTime(long time) {
            this.time = time;
        }

        @Override
        public long getFullTime() {
            return fullTime;
        }

        @Override
        public void setFullTime(long time) {
            this.fullTime = time;
        }

        @Override
        public Material getBlockType(int x, int y, int z) {
            return blocks.getOrDefault(key(x, y, z), Material.AIR);
        }

        @Override
        public void setBlockType(int x, int y, int z, Material type) {
            blocks.put(key(x, y, z), type);
        }

        @Override
        public org.bukkit.inventory.Inventory getBlockInventory(int x, int y, int z) {
            return null;
        }

        @Override
        public UUID spawnEntity(EntityType type, Position position) {
            UUID id = UUID.randomUUID();
            entities.put(id, position);
            return id;
        }

        @Override
        public Position getEntityPosition(UUID entityId) {
            return entities.get(entityId);
        }

        @Override
        public boolean teleportEntity(UUID entityId, Position position) {
            return entities.replace(entityId, position) != null;
        }

        @Override
        public org.bukkit.inventory.Inventory getPlayerInventory(UUID playerId) {
            return null;
        }

        @Override
        public Collection<UUID> entityIds() {
            return List.copyOf(entities.keySet());
        }

        private static long key(int x, int y, int z) {
            return ((long) x & 0x1fffffL)
                | (((long) y & 0x1fffffL) << 21)
                | (((long) z & 0x1fffffL) << 42);
        }
    }
}
