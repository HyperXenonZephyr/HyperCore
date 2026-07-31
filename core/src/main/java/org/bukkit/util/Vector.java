package org.bukkit.util;

/**
 * Minimal stub of the Bukkit {@code Vector} class.
 */
public class Vector {
    private final double x;
    private final double y;
    private final double z;

    public Vector() {
        this(0.0, 0.0, 0.0);
    }

    public Vector(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
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
}
