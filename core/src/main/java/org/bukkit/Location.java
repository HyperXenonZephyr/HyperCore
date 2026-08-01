package org.bukkit;

import org.bukkit.util.Vector;

import java.util.Objects;

/**
 * Minimal stub of the Bukkit {@code Location} class.
 *
 * <p>Represents a position in a {@link World}. Yaw and pitch default to zero
 * for callers that do not care about orientation.
 */
public final class Location {
    private final World world;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    /**
     * Creates a location without orientation.
     */
    public Location(World world, double x, double y, double z) {
        this(world, x, y, z, 0.0f, 0.0f);
    }

    /**
     * Creates a location with orientation.
     */
    public Location(World world, double x, double y, double z, float yaw, float pitch) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    /**
     * Returns the world containing this location, or {@code null} if this
     * location is world-agnostic.
     */
    public World getWorld() {
        return world;
    }

    /**
     * Returns the block x coordinate.
     */
    public int getBlockX() {
        return (int) Math.floor(x);
    }

    /**
     * Returns the block y coordinate.
     */
    public int getBlockY() {
        return (int) Math.floor(y);
    }

    /**
     * Returns the block z coordinate.
     */
    public int getBlockZ() {
        return (int) Math.floor(z);
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    /**
     * Returns an immutable position vector (no orientation).
     */
    public Vector toVector() {
        return new Vector(x, y, z);
    }

    /**
     * Returns the distance squared to another location in the same world.
     *
     * @throws IllegalArgumentException if the worlds differ
     */
    public double distanceSquared(Location other) {
        Objects.requireNonNull(other, "other");
        if (!Objects.equals(world, other.world)) {
            throw new IllegalArgumentException("Cannot measure distance between different worlds");
        }
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof Location other)) {
            return false;
        }
        return Double.compare(x, other.x) == 0
            && Double.compare(y, other.y) == 0
            && Double.compare(z, other.z) == 0
            && Float.compare(yaw, other.yaw) == 0
            && Float.compare(pitch, other.pitch) == 0
            && Objects.equals(world, other.world);
    }

    @Override
    public int hashCode() {
        return Objects.hash(world, x, y, z, yaw, pitch);
    }

    @Override
    public String toString() {
        return "Location{"
            + "world=" + (world == null ? "null" : world.getName())
            + ", x=" + x
            + ", y=" + y
            + ", z=" + z
            + ", yaw=" + yaw
            + ", pitch=" + pitch
            + '}';
    }
}
