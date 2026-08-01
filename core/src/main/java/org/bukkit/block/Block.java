package org.bukkit.block;

import org.bukkit.Material;
import org.bukkit.World;

/**
 * Minimal stub of the Bukkit {@code Block} interface.
 *
 * <p>Concrete implementations are provided by the HyperCore Bukkit adapter and
 * delegate block reads and writes to the loader-specific world access layer.
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

    /**
     * Returns the material type of this block.
     */
    Material getType();

    /**
     * Sets the material type of this block.
     */
    void setType(Material type);

    /**
     * Sets the material type and optionally applies physics.
     *
     * <p>The applyPhysics flag is advisory; the current stub delegates to
     * {@link #setType(Material)} when physics is not applicable.
     */
    default void setType(Material type, boolean applyPhysics) {
        setType(type);
    }

    /**
     * Returns a snapshot of the current block state.
     */
    BlockState getState();
}
