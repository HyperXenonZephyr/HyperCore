package org.bukkit.entity;

import org.bukkit.util.Vector;

/**
 * Minimal stub of the Bukkit {@code Entity} interface. Exists so that generated
 * event shells can declare entity getters with the correct return type.
 */
public interface Entity {

    /**
     * Returns a minimal identifier for this entity. Real Bukkit returns a UUID;
     * this stub leaves the implementation to concrete classes.
     */
    String getName();

    /**
     * Returns a zero velocity vector.
     */
    default Vector getVelocity() {
        return new Vector();
    }
}
