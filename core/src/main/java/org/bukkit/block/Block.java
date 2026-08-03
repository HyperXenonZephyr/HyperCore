package org.bukkit.block;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

/**
 * Stub of the Bukkit {@code Block} interface.
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
     * Returns the block relative to this block in the given face direction.
     */
    default Block getRelative(BlockFace face) {
        return getRelative(face, 1);
    }

    /**
     * Returns the block relative to this block in the given face direction,
     * offset by the given distance.
     */
    default Block getRelative(BlockFace face, int distance) {
        return getWorld().getBlockAt(getX() + face.getModX() * distance, getY() + face.getModY() * distance, getZ() + face.getModZ() * distance);
    }

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
     * Returns the block data for this block.
     */
    default BlockData getBlockData() {
        return new org.bukkit.block.data.BlockData() {
            @Override
            public Material getMaterial() {
                return Block.this.getType();
            }
        };
    }

    /**
     * Sets the block data for this block.
     */
    default void setBlockData(BlockData data) {
        setType(data.getMaterial());
    }

    /**
     * Returns the light level emitted by or received at this block.
     */
    default int getLightLevel() {
        throw new UnsupportedOperationException("getLightLevel");
    }

    /**
     * Returns the light level emitted by blocks at this block.
     */
    default int getLightFromBlocks() {
        return getLightLevel();
    }

    /**
     * Returns the light level received from the sky at this block.
     */
    default int getLightFromSky() {
        throw new UnsupportedOperationException("getLightFromSky");
    }

    /**
     * Returns whether this block is directly powered by redstone.
     */
    default boolean isBlockPowered() {
        throw new UnsupportedOperationException("isBlockPowered");
    }

    /**
     * Returns whether this block is indirectly powered by redstone.
     */
    default boolean isBlockIndirectlyPowered() {
        throw new UnsupportedOperationException("isBlockIndirectlyPowered");
    }

    /**
     * Returns the redstone power level received from the given face.
     */
    default int getBlockPower(BlockFace face) {
        throw new UnsupportedOperationException("getBlockPower");
    }

    /**
     * Returns a snapshot of the current block state.
     */
    BlockState getState();
}
