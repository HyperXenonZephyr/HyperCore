package dev.hypercore.bridge.world;

import dev.hypercore.concurrent.HyperCoreExecutor;
import dev.hypercore.region.RegionTaskCoordinator;
import dev.hypercore.world.RegionExecutionService;
import dev.hypercore.world.WorldAccess;
import dev.hypercore.world.WorldAccessFactory;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the {@link RegionExecutionService} publishes bridge deltas for
 * successful mutations when a delta sink is installed.
 */
class RegionExecutionDeltaCaptureTest {
    private HyperCoreExecutor executor;
    private RegionTaskCoordinator coordinator;
    private RegionExecutionService service;
    private List<WorldDelta> captured;

    @BeforeEach
    void setUp() {
        executor = HyperCoreExecutor.create(2, 16);
        coordinator = new RegionTaskCoordinator(executor, 2);
        service = new RegionExecutionService(new MemoryWorldAccessFactory(), coordinator);
        captured = new CopyOnWriteArrayList<>();
        service.setDeltaSink(captured::add);
    }

    @AfterEach
    void tearDown() {
        executor.close();
    }

    @Test
    void blockWritePublishesBlockDelta() {
        service.setBlockType("world", 10, 20, 30, Material.STONE);
        assertEquals(1, captured.size());
        BlockDelta delta = (BlockDelta) captured.get(0);
        assertEquals("world", delta.worldName());
        assertEquals(10, delta.x());
        assertEquals(20, delta.y());
        assertEquals(30, delta.z());
        assertEquals("STONE", delta.blockState());
    }

    @Test
    void entitySpawnPublishesSpawnDelta() {
        UUID entityId = service.spawnEntity("world", EntityType.ZOMBIE, new org.bukkit.Location(null, 1, 2, 3));
        assertTrue(entityId != null);
        assertEquals(1, captured.size());
        EntitySpawnDelta delta = (EntitySpawnDelta) captured.get(0);
        assertEquals(entityId, delta.entityId());
        assertEquals("ZOMBIE", delta.entityType());
    }

    @Test
    void removingASinkDisablesCapture() {
        service.setDeltaSink(null);
        service.setBlockType("world", 0, 0, 0, Material.DIRT);
        assertTrue(captured.isEmpty());
    }

    /** Minimal in-memory world used by the capture tests. */
    private static final class MemoryWorldAccessFactory implements WorldAccessFactory {
        private final Map<String, MemoryWorld> worlds = new ConcurrentHashMap<>();
        private final UUID entityId = UUID.randomUUID();

        @Override
        public WorldAccess access(String worldName) {
            return worlds.computeIfAbsent(worldName, MemoryWorld::new);
        }

        @Override
        public Collection<String> worldNames() {
            return List.copyOf(worlds.keySet());
        }

        private final class MemoryWorld implements WorldAccess {
            private final String name;
            private final Map<String, Material> blocks = new ConcurrentHashMap<>();
            private boolean entityPresent;

            private MemoryWorld(String name) {
                this.name = name;
            }

            @Override
            public String worldName() {
                return name;
            }

            @Override
            public Material getBlockType(int x, int y, int z) {
                return blocks.getOrDefault(x + "," + y + "," + z, Material.AIR);
            }

            @Override
            public void setBlockType(int x, int y, int z, Material type) {
                blocks.put(x + "," + y + "," + z, type);
            }

            @Override
            public Inventory getBlockInventory(int x, int y, int z) {
                return null;
            }

            @Override
            public UUID spawnEntity(EntityType type, Position position) {
                entityPresent = true;
                return entityId;
            }

            @Override
            public Position getEntityPosition(UUID id) {
                return entityPresent && id.equals(entityId) ? new Position(1, 2, 3) : null;
            }

            @Override
            public boolean teleportEntity(UUID id, Position position) {
                return entityPresent && id.equals(entityId);
            }

            @Override
            public Inventory getPlayerInventory(UUID playerId) {
                return null;
            }

            @Override
            public Collection<UUID> entityIds() {
                return entityPresent ? List.of(entityId) : List.of();
            }
        }
    }
}
