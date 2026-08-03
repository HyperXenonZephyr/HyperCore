package org.bukkit.block;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.Inventory;

/**
 * Stub of the Bukkit {@code BlockState} interface.
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
     * Returns the block data captured in this state.
     */
    default BlockData getBlockData() {
        return new BlockData() {
            @Override
            public Material getMaterial() {
                return BlockState.this.getType();
            }
        };
    }

    /**
     * Sets the block data in this state snapshot.
     */
    default void setBlockData(BlockData data) {
        setType(data.getMaterial());
    }

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
        if (applyPhysics) {
            getBlock().setType(getType(), true);
            return true;
        }
        return update();
    }

    /**
     * Returns the inventory of the block entity at this block, or {@code null}
     * if the block is not a container.
     */
    default Inventory getInventory() {
        return null;
    }
}
