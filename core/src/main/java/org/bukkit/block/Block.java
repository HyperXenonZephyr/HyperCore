package org.bukkit.block;

import org.bukkit.World;

/**
 * Minimal stub of the Bukkit {@code Block} interface.
 */
public interface Block {

    /**
     * Returns the world containing this block.
     */
    World getWorld();

    /**
     * Returns the x coordinate.
     */
    int getX();

    /**
     * Returns the y coordinate.
     */
    int getY();

    /**
     * Returns the z coordinate.
     */
    int getZ();
}
