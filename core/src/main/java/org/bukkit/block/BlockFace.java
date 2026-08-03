package org.bukkit.block;

/**
 * Represents the face of a block.
 */
public enum BlockFace {
    NORTH(0, 0, -1),
    EAST(1, 0, 0),
    SOUTH(0, 0, 1),
    WEST(-1, 0, 0),
    UP(0, 1, 0),
    DOWN(0, -1, 0),
    NORTH_EAST(1, 0, -1),
    NORTH_WEST(-1, 0, -1),
    SOUTH_EAST(1, 0, 1),
    SOUTH_WEST(-1, 0, 1),
    WEST_NORTH_WEST(-1, 0, -1),
    NORTH_NORTH_EAST(1, 0, -1),
    NORTH_NORTH_WEST(-1, 0, -1),
    EAST_NORTH_EAST(1, 0, -1),
    EAST_SOUTH_EAST(1, 0, 1),
    SOUTH_SOUTH_EAST(1, 0, 1),
    SOUTH_SOUTH_WEST(-1, 0, 1),
    WEST_SOUTH_WEST(-1, 0, 1),
    SELF(0, 0, 0);

    private final int modX;
    private final int modY;
    private final int modZ;

    BlockFace(int modX, int modY, int modZ) {
        this.modX = modX;
        this.modY = modY;
        this.modZ = modZ;
    }

    /**
     * Returns the x offset of this face.
     */
    public int getModX() {
        return modX;
    }

    /**
     * Returns the y offset of this face.
     */
    public int getModY() {
        return modY;
    }

    /**
     * Returns the z offset of this face.
     */
    public int getModZ() {
        return modZ;
    }

    /**
     * Returns the opposite face.
     */
    public BlockFace getOppositeFace() {
        return switch (this) {
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case EAST -> WEST;
            case WEST -> EAST;
            case UP -> DOWN;
            case DOWN -> UP;
            case NORTH_EAST -> SOUTH_WEST;
            case NORTH_WEST -> SOUTH_EAST;
            case SOUTH_EAST -> NORTH_WEST;
            case SOUTH_WEST -> NORTH_EAST;
            case WEST_NORTH_WEST -> EAST_SOUTH_EAST;
            case NORTH_NORTH_EAST -> SOUTH_SOUTH_WEST;
            case NORTH_NORTH_WEST -> SOUTH_SOUTH_EAST;
            case EAST_NORTH_EAST -> WEST_SOUTH_WEST;
            case EAST_SOUTH_EAST -> WEST_NORTH_WEST;
            case SOUTH_SOUTH_EAST -> NORTH_NORTH_WEST;
            case SOUTH_SOUTH_WEST -> NORTH_NORTH_EAST;
            case WEST_SOUTH_WEST -> EAST_NORTH_EAST;
            case SELF -> SELF;
        };
    }
}
