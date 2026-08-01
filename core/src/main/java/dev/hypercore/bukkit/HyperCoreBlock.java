package dev.hypercore.bukkit;

import dev.hypercore.world.RegionExecutionService;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;

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
    public BlockState getState() {
        return new HyperCoreBlockState(this, getType());
    }
}
