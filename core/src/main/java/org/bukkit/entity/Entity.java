package org.bukkit.entity;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * Minimal stub of the Bukkit {@code Entity} interface.
 *
 * <p>Exists so that generated event shells can declare entity getters with the
 * correct return type, and so plugins can query location and teleport entities.
 */
public interface Entity {

    /**
     * Returns the unique id for this entity.
     */
    UUID getUniqueId();

    /**
     * Returns a minimal identifier for this entity.
     */
    String getName();

    /**
     * Returns the world containing this entity.
     */
    World getWorld();

    /**
     * Returns the current location of this entity.
     */
    Location getLocation();

    /**
     * Returns the current location by writing into the provided instance.
     *
     * @param location the location to overwrite, or {@code null} to allocate
     * @return the provided location, or a new one if {@code null}
     */
    default Location getLocation(Location location) {
        Location current = getLocation();
        if (location == null) {
            return current;
        }
        // Location is immutable in this stub, so we cannot mutate the provided
        // instance. Return the current location instead of throwing.
        return current;
    }

    /**
     * Teleports this entity to the given location.
     *
     * @param location the destination
     * @return {@code true} if the teleport succeeded
     */
    boolean teleport(Location location);

    /**
     * Teleports this entity to the given destination entity.
     *
     * @param destination the entity whose location becomes the destination
     * @return {@code true} if the teleport succeeded
     */
    default boolean teleport(Entity destination) {
        if (destination == null) {
            return false;
        }
        return teleport(destination.getLocation());
    }

    /**
     * Returns a zero velocity vector.
     */
    default Vector getVelocity() {
        return new Vector();
    }
}
