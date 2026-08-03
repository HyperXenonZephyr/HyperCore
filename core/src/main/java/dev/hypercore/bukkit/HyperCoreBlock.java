package dev.hypercore.bukkit;

import dev.hypercore.world.RegionExecutionService;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.Inventory;

import java.util.Objects;

/**
 * Bukkit {@link Block} implementation backed by HyperCore's
 * {@link RegionExecutionService}.
 */
public final class HyperCoreBlock implements Block {
    private final RegionExecutionService execution;
    private final String worldName;
    private final int x;
    private final int y;
    private final int z;

    public HyperCoreBlock(RegionExecutionService execution, String worldName, int x, int y, int z) {
        this.execution = Objects.requireNonNull(execution, "execution");
        this.worldName = Objects.requireNonNull(worldName, "worldName");
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public World getWorld() {
        return execution.world(worldName);
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public int getZ() {
        return z;
    }

    @Override
    public Material getType() {
        return execution.getBlockType(worldName, x, y, z);
    }

    @Override
    public void setType(Material type) {
        execution.setBlockType(worldName, x, y, z, type);
    }

    @Override
    public BlockData getBlockData() {
        String data = execution.getBlockDataAsString(worldName, x, y, z);
        if (data != null && data.startsWith("minecraft:")) {
            Material material = getType();
            return new BlockData() {
                @Override
                public Material getMaterial() {
                    return material;
                }

                @Override
                public String getAsString() {
                    return data;
                }
            };
        }
        return Block.super.getBlockData();
    }

    @Override
    public void setBlockData(BlockData data) {
        String asString = data.getAsString();
        if (asString.startsWith("minecraft:") && asString.contains("[")) {
            execution.setBlockData(worldName, x, y, z, asString);
        } else {
            setType(data.getMaterial());
        }
    }

    @Override
    public int getLightLevel() {
        return execution.getBlockLight(worldName, x, y, z);
    }

    @Override
    public int getLightFromSky() {
        return execution.getSkyLight(worldName, x, y, z);
    }

    @Override
    public boolean isBlockPowered() {
        return execution.isBlockPowered(worldName, x, y, z);
    }

    @Override
    public boolean isBlockIndirectlyPowered() {
        return execution.isBlockIndirectlyPowered(worldName, x, y, z);
    }

    @Override
    public int getBlockPower(BlockFace face) {
        return execution.getBlockPower(worldName, x, y, z, face);
    }

    @Override
    public BlockState getState() {
        return new HyperCoreBlockState(this, getType());
    }

    /**
     * Returns the inventory of the block entity at these coordinates.
     */
    public Inventory getInventory() {
        return execution.getBlockInventory(worldName, x, y, z);
    }
}
