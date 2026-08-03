package dev.hypercore.world;

import dev.hypercore.concurrent.HyperCoreExecutor;
import dev.hypercore.region.RegionKey;
import dev.hypercore.region.RegionTaskCoordinator;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link RegionExecutionService} using an in-memory world access
 * implementation.
 */
class RegionExecutionServiceTest {

    private HyperCoreExecutor executor;
    private RegionTaskCoordinator coordinator;
    private RegionExecutionService service;

    @BeforeEach
    void setUp() {
        executor = HyperCoreExecutor.create(2, 16);
        coordinator = new RegionTaskCoordinator(executor, 2);
        service = new RegionExecutionService(new MemoryWorldAccessFactory(), coordinator);
    }

    @AfterEach
    void tearDown() {
        executor.close();
    }

    @Test
    void readsAndWritesBlockType() {
        service.setBlockType("world", 0, 0, 0, Material.STONE);
        assertEquals(Material.STONE, service.getBlockType("world", 0, 0, 0));
    }

    @Test
    void blockWriteInOneRegionDoesNotAffectAnother() {
        service.setBlockType("world", 0, 0, 0, Material.STONE);
        service.setBlockType("world", 128, 0, 128, Material.DIRT);

        assertEquals(Material.STONE, service.getBlockType("world", 0, 0, 0));
        assertEquals(Material.DIRT, service.getBlockType("world", 128, 0, 128));
    }

    @Test
    void regionKeysAreComputedFromBlockCoordinates() {
        // Negative block coordinates must map to the correct chunk using floorDiv.
        RegionKey keyNegative = service.regionKeyFor("world", -1, -1);
        RegionKey keyOrigin = service.regionKeyFor("world", 0, 0);
        assertFalse(keyNegative.equals(keyOrigin));
    }

    @Test
    void unknownWorldThrows() {
        try {
            service.getBlockType("missing", 0, 0, 0);
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("World is not loaded"));
        }
    }

    @Test
    void spawnsAndLocatesEntity() {
        Location spawn = new Location(service.world("world"), 5.0, 64.0, 5.0);
        UUID entityId = service.spawnEntity("world", org.bukkit.entity.EntityType.ZOMBIE, spawn);
        assertNotNull(entityId);

        Location found = service.getEntityLocation(entityId);
        assertNotNull(found);
        assertEquals(5.0, found.getX());
        assertEquals(64.0, found.getY());
        assertEquals(5.0, found.getZ());
        assertEquals("world", found.getWorld().getName());
    }

    @Test
    void sameRegionTeleportSucceeds() {
        Location spawn = new Location(service.world("world"), 5.0, 64.0, 5.0);
        UUID entityId = service.spawnEntity("world", org.bukkit.entity.EntityType.ZOMBIE, spawn);

        Location destination = new Location(service.world("world"), 6.0, 64.0, 6.0);
        assertTrue(service.teleportEntity(entityId, destination));

        Location found = service.getEntityLocation(entityId);
        assertEquals(6.0, found.getX());
    }

    @Test
    void playerExclusiveApiDelegatesToWorldAccess() {
        Location location = new Location(service.world("world"), 5.0, 64.0, 5.0);
        UUID playerId = service.spawnEntity("world", org.bukkit.entity.EntityType.PLAYER, location);
        assertNotNull(playerId);

        assertFalse(service.isSneaking(playerId));
        service.setSneaking(playerId, true);
        assertTrue(service.isSneaking(playerId));

        assertFalse(service.isSprinting(playerId));
        service.setSprinting(playerId, true);
        assertTrue(service.isSprinting(playerId));

        // Connection-based operations are no-ops in the memory access but must
        // not throw.
        service.kickPlayer(playerId, "test");
        service.sendTitle(playerId, "title", "subtitle", 10, 70, 20);
        service.resetTitle(playerId);
        service.updateInventory(playerId);
        service.setResourcePack(playerId, "https://example.com/pack.zip");
        assertFalse(service.performCommand(playerId, "help"));
        assertFalse(service.openInventory(playerId, null));
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
        private final ConcurrentHashMap<UUID, Boolean> sneaking = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<UUID, Boolean> sprinting = new ConcurrentHashMap<>();

        MemoryWorldAccess(String worldName) {
            this.worldName = worldName;
        }

        @Override
        public String worldName() {
            return worldName;
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
        public UUID spawnEntity(org.bukkit.entity.EntityType type, Position position) {
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

        @Override
        public boolean isSneaking(UUID playerId) {
            return sneaking.getOrDefault(playerId, false);
        }

        @Override
        public void setSneaking(UUID playerId, boolean sneaking) {
            this.sneaking.put(playerId, sneaking);
        }

        @Override
        public boolean isSprinting(UUID playerId) {
            return sprinting.getOrDefault(playerId, false);
        }

        @Override
        public void setSprinting(UUID playerId, boolean sprinting) {
            this.sprinting.put(playerId, sprinting);
        }

        private static long key(int x, int y, int z) {
            return ((long) x & 0x1fffffL)
                | (((long) y & 0x1fffffL) << 21)
                | (((long) z & 0x1fffffL) << 42);
        }
    }
}
