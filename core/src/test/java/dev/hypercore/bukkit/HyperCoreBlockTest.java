package dev.hypercore.bukkit;

import dev.hypercore.world.RegionExecutionService;
import dev.hypercore.world.StubWorldAccess;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link HyperCoreBlock} verifying that the adapter delegates
 * reads and writes to {@link RegionExecutionService}, which in turn is backed
 * by a configurable {@link StubWorldAccess}.
 */
class HyperCoreBlockTest {

    @Test
    void getWorldReturnsHyperCoreWorld() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            HyperCoreBlock block = new HyperCoreBlock(execution, "world", 1, 2, 3);

            World world = block.getWorld();

            assertNotNull(world);
            assertTrue(world instanceof HyperCoreWorld);
            assertEquals("world", world.getName());
        }
    }

    @Test
    void getCoordinatesReturnConstructorValues() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            HyperCoreBlock block = new HyperCoreBlock(execution, "world", 1, 2, 3);

            assertEquals(1, block.getX());
            assertEquals(2, block.getY());
            assertEquals(3, block.getZ());
        }
    }

    @Test
    void getTypeDelegatesToStub() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            StubWorldAccess stub = holder.stub("world");
            stub.blockType = Material.STONE;

            HyperCoreBlock block = new HyperCoreBlock(execution, "world", 1, 2, 3);

            assertEquals(Material.STONE, block.getType());
        }
    }

    @Test
    void setTypeRecordsOnStub() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            StubWorldAccess stub = holder.stub("world");

            HyperCoreBlock block = new HyperCoreBlock(execution, "world", 1, 2, 3);
            block.setType(Material.DIRT);

            assertEquals(Material.DIRT, stub.recordedBlockType);
        }
    }

    @Test
    void getBlockDataReturnsConfiguredData() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            StubWorldAccess stub = holder.stub("world");
            stub.blockType = Material.STONE;
            stub.blockDataString = "minecraft:stone[axis=y]";

            HyperCoreBlock block = new HyperCoreBlock(execution, "world", 1, 2, 3);

            BlockData data = block.getBlockData();
            assertNotNull(data);
            assertEquals("minecraft:stone[axis=y]", data.getAsString());
            assertEquals(Material.STONE, data.getMaterial());
        }
    }

    @Test
    void getBlockDataFallsBackToMaterialWhenNull() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            StubWorldAccess stub = holder.stub("world");
            stub.blockType = Material.STONE;

            HyperCoreBlock block = new HyperCoreBlock(execution, "world", 1, 2, 3);

            BlockData data = block.getBlockData();
            assertNotNull(data);
            assertEquals(Material.STONE, data.getMaterial());
        }
    }

    @Test
    void setBlockDataDelegatesWhenValidString() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            StubWorldAccess stub = holder.stub("world");

            HyperCoreBlock block = new HyperCoreBlock(execution, "world", 1, 2, 3);
            BlockData data = new BlockData() {
                @Override
                public Material getMaterial() {
                    return Material.STONE;
                }

                @Override
                public String getAsString() {
                    return "minecraft:stone[axis=y]";
                }
            };
            block.setBlockData(data);

            assertEquals("minecraft:stone[axis=y]", stub.recordedBlockData);
        }
    }

    @Test
    void setBlockDataFallsBackToTypeWhenNoBrackets() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            StubWorldAccess stub = holder.stub("world");

            HyperCoreBlock block = new HyperCoreBlock(execution, "world", 1, 2, 3);
            BlockData data = new BlockData() {
                @Override
                public Material getMaterial() {
                    return Material.DIRT;
                }

                @Override
                public String getAsString() {
                    return "minecraft:dirt";
                }
            };
            block.setBlockData(data);

            assertEquals(Material.DIRT, stub.recordedBlockType);
            assertNull(stub.recordedBlockData);
        }
    }

    @Test
    void getLightLevelDelegatesToStub() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            StubWorldAccess stub = holder.stub("world");
            stub.blockLight = 12;

            HyperCoreBlock block = new HyperCoreBlock(execution, "world", 1, 2, 3);

            assertEquals(12, block.getLightLevel());
        }
    }

    @Test
    void getLightFromSkyDelegatesToStub() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            StubWorldAccess stub = holder.stub("world");
            stub.skyLight = 15;

            HyperCoreBlock block = new HyperCoreBlock(execution, "world", 1, 2, 3);

            assertEquals(15, block.getLightFromSky());
        }
    }

    @Test
    void isBlockPoweredDelegatesToStub() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            StubWorldAccess stub = holder.stub("world");
            stub.blockPowered = true;

            HyperCoreBlock block = new HyperCoreBlock(execution, "world", 1, 2, 3);

            assertTrue(block.isBlockPowered());
        }
    }

    @Test
    void isBlockIndirectlyPoweredDelegatesToStub() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            StubWorldAccess stub = holder.stub("world");
            stub.blockIndirectlyPowered = true;

            HyperCoreBlock block = new HyperCoreBlock(execution, "world", 1, 2, 3);

            assertTrue(block.isBlockIndirectlyPowered());
        }
    }

    @Test
    void getBlockPowerDelegatesToStub() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            StubWorldAccess stub = holder.stub("world");
            stub.blockPower = 7;

            HyperCoreBlock block = new HyperCoreBlock(execution, "world", 1, 2, 3);

            assertEquals(7, block.getBlockPower(BlockFace.NORTH));
        }
    }

    @Test
    void getRelativeReturnsOffsetBlock() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            HyperCoreBlock block = new HyperCoreBlock(execution, "world", 5, 10, 20);

            Block relative = block.getRelative(BlockFace.NORTH);

            assertNotNull(relative);
            assertEquals(5, relative.getX());
            assertEquals(10, relative.getY());
            assertEquals(19, relative.getZ());
        }
    }

    @Test
    void getStateReturnsBlockState() {
        try (StubExecutionService.Holder holder = StubExecutionService.create("world")) {
            RegionExecutionService execution = holder.service;
            StubWorldAccess stub = holder.stub("world");
            stub.blockType = Material.STONE;

            HyperCoreBlock block = new HyperCoreBlock(execution, "world", 1, 2, 3);

            BlockState state = block.getState();
            assertNotNull(state);
            assertEquals(Material.STONE, state.getType());
        }
    }
}
