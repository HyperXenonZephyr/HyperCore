package org.bukkit.block.data;

import org.bukkit.Material;

/**
 * Minimal stub of the Bukkit {@code BlockData} interface.
 *
 * <p>Represents the data for a block, including its material type. In a full
 * Bukkit implementation this interface exposes state properties such as facing,
 * half, powered, etc. HyperCore currently tracks the material only.
 */
public interface BlockData {

    /**
     * Returns the material this block data represents.
     */
    Material getMaterial();

    /**
     * Returns the block data as a string, such as "minecraft:chest".
     */
    default String getAsString() {
        return getMaterial().getKey().toString();
    }
}
