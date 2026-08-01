package org.bukkit.block;

import org.bukkit.Material;
import org.bukkit.World;

/**
 * Minimal stub of the Bukkit {@code BlockState} interface.
 *
 * <p>A block state is a snapshot of a block that can be modified independently
 * and then written back to the world through {@link #update()}.
 */
public interface BlockState {

    /**
     * Returns the block this state was captured from.
     */
    Block getBlock();

    /**
     * Returns the world containing the block.
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
     * Returns the material type captured in this state.
     */
    Material getType();

    /**
     * Sets the material type in this state snapshot.
     */
    void setType(Material type);

    /**
     * Writes this state back to the world.
     *
     * @return {@code true} if the world was modified
     */
    boolean update();

    /**
     * Writes this state back to the world, optionally ignoring changes from
     * other sources.
     *
     * @param force if {@code true}, update even if the block has changed
     * @param applyPhysics if {@code true}, apply neighbor reactions
     * @return {@code true} if the world was modified
     */
    default boolean update(boolean force, boolean applyPhysics) {
        if (!force && getBlock().getType() != getType()) {
            // The real block has changed and force is disabled: do not overwrite.
            return false;
        }
        return update();
    }
}
