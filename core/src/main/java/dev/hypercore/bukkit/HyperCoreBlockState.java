package dev.hypercore.bukkit;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;

import java.util.Objects;

/**
 * Bukkit {@link BlockState} implementation backed by a captured
 * {@link HyperCoreBlock} snapshot.
 */
public final class HyperCoreBlockState implements BlockState {
    private final HyperCoreBlock block;
    private Material type;

    public HyperCoreBlockState(HyperCoreBlock block, Material type) {
        this.block = Objects.requireNonNull(block, "block");
        this.type = Objects.requireNonNull(type, "type");
    }

    @Override
    public Block getBlock() {
        return block;
    }

    @Override
    public World getWorld() {
        return block.getWorld();
    }

    @Override
    public int getX() {
        return block.getX();
    }

    @Override
    public int getY() {
        return block.getY();
    }

    @Override
    public int getZ() {
        return block.getZ();
    }

    @Override
    public Material getType() {
        return type;
    }

    @Override
    public void setType(Material type) {
        this.type = Objects.requireNonNull(type, "type");
    }

    @Override
    public boolean update() {
        block.setType(type);
        return true;
    }

    @Override
    public org.bukkit.inventory.Inventory getInventory() {
        return block.getInventory();
    }
}
