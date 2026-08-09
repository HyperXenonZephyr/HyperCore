package dev.hypercore.bukkit;

import dev.hypercore.world.RegionExecutionService;
import dev.hypercore.world.StubWorldAccess;
import dev.hypercore.world.WorldAccess;

import org.bukkit.Location;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link HyperCoreWorld} verifying that the adapter delegates
 * reads and writes to {@link RegionExecutionService}, which in turn is backed
 * by a configurable {@link StubWorldAccess}.
 */
class HyperCoreWorldTest {

    @Test
    void getNameReturnsWorldName() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            HyperCoreWorld world = new HyperCoreWorld(execution, "world");

            assertEquals("world", world.getName());
        }
    }

    @Test
    void getTimeDelegatesToStub() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            StubWorldAccess stub = holder.stub("world");
            stub.time = 6000L;

            HyperCoreWorld world = new HyperCoreWorld(execution, "world");

            assertEquals(6000L, world.getTime());
        }
    }

    @Test
    void setTimeRecordsOnStub() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            StubWorldAccess stub = holder.stub("world");

            HyperCoreWorld world = new HyperCoreWorld(execution, "world");
            world.setTime(12000L);

            assertEquals(Long.valueOf(12000L), stub.recordedTime);
        }
    }

    @Test
    void getFullTimeDelegatesToStub() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            StubWorldAccess stub = holder.stub("world");
            stub.fullTime = 100L;

            HyperCoreWorld world = new HyperCoreWorld(execution, "world");

            assertEquals(100L, world.getFullTime());
        }
    }

    @Test
    void hasStormDelegatesToStub() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            StubWorldAccess stub = holder.stub("world");
            stub.storm = true;

            HyperCoreWorld world = new HyperCoreWorld(execution, "world");

            assertTrue(world.hasStorm());
            world.setStorm(true);
            assertEquals(Boolean.TRUE, stub.recordedStorm);
        }
    }

    @Test
    void isThunderingDelegatesToStub() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            StubWorldAccess stub = holder.stub("world");
            stub.thundering = true;

            HyperCoreWorld world = new HyperCoreWorld(execution, "world");

            assertTrue(world.isThundering());
            world.setThundering(true);
            assertEquals(Boolean.TRUE, stub.recordedThundering);
        }
    }

    @Test
    void getSpawnLocationReturnsLocationWithWorld() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            StubWorldAccess stub = holder.stub("world");
            stub.spawnLocation = new WorldAccess.Position(1, 2, 3);

            HyperCoreWorld world = new HyperCoreWorld(execution, "world");

            Location spawn = world.getSpawnLocation();
            assertNotNull(spawn);
            assertEquals(1.0, spawn.getX());
            assertEquals(2.0, spawn.getY());
            assertEquals(3.0, spawn.getZ());
            assertNotNull(spawn.getWorld());
            assertEquals("world", spawn.getWorld().getName());
        }
    }

    @Test
    void getSpawnLocationReturnsNullWhenStubReturnsNull() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            HyperCoreWorld world = new HyperCoreWorld(execution, "world");

            assertNull(world.getSpawnLocation());
        }
    }

    @Test
    void setSpawnLocationRecordsPosition() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            StubWorldAccess stub = holder.stub("world");

            HyperCoreWorld world = new HyperCoreWorld(execution, "world");
            world.setSpawnLocation(new Location(world, 5, 10, 15));

            assertNotNull(stub.recordedSpawnLocation);
            assertEquals(5.0, stub.recordedSpawnLocation.x());
            assertEquals(10.0, stub.recordedSpawnLocation.y());
            assertEquals(15.0, stub.recordedSpawnLocation.z());
        }
    }

    @Test
    void getBiomeDelegatesThroughConversion() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            StubWorldAccess stub = holder.stub("world");
            stub.biomeKey = "minecraft:plains";

            HyperCoreWorld world = new HyperCoreWorld(execution, "world");

            assertEquals(Biome.PLAINS, world.getBiome(0, 0, 0));
        }
    }

    @Test
    void getBiomeReturnsCustomForUnknownKey() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            StubWorldAccess stub = holder.stub("world");
            stub.biomeKey = "minecraft:unknown";

            HyperCoreWorld world = new HyperCoreWorld(execution, "world");

            assertEquals(Biome.CUSTOM, world.getBiome(0, 0, 0));
        }
    }

    @Test
    void setBiomeRecordsConvertedKey() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            StubWorldAccess stub = holder.stub("world");

            HyperCoreWorld world = new HyperCoreWorld(execution, "world");
            world.setBiome(0, 0, 0, Biome.DESERT);

            assertEquals("minecraft:desert", stub.recordedBiomeKey);
        }
    }

    @Test
    void getBlockAtReturnsHyperCoreBlock() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            HyperCoreWorld world = new HyperCoreWorld(execution, "world");

            Block block = world.getBlockAt(1, 2, 3);

            assertNotNull(block);
            assertTrue(block instanceof HyperCoreBlock);
            assertEquals(1, block.getX());
            assertEquals(2, block.getY());
            assertEquals(3, block.getZ());
        }
    }

    @Test
    void getEntitiesReturnsEmptyByDefault() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            HyperCoreWorld world = new HyperCoreWorld(execution, "world");

            assertTrue(world.getEntities().isEmpty());
        }
    }

    @Test
    void getEntitiesByClassFiltersByType() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            HyperCoreWorld world = new HyperCoreWorld(execution, "world");

            Entity zombie = world.spawnEntity(new Location(world, 0, 64, 0), EntityType.ZOMBIE);
            Entity item = world.spawnEntity(new Location(world, 1, 64, 1), EntityType.ITEM);
            assertNotNull(zombie);
            assertNotNull(item);

            assertEquals(2, world.getEntities().size());

            Collection<HyperCoreLivingEntity> living = world.getEntitiesByClass(HyperCoreLivingEntity.class);
            assertEquals(1, living.size());
        }
    }

    @Test
    void spawnEntityReturnsEntity() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            HyperCoreWorld world = new HyperCoreWorld(execution, "world");

            Entity entity = world.spawnEntity(new Location(world, 0, 64, 0), EntityType.ZOMBIE);

            assertNotNull(entity);
            assertTrue(entity instanceof HyperCoreLivingEntity);
        }
    }
}
