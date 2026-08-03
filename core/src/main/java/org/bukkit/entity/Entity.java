package org.bukkit.entity;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.List;
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
     * Returns the Bukkit entity type (zombie, player, dropped item, etc.).
     */
    EntityType getType();

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
     * Returns the current velocity of this entity.
     */
    default Vector getVelocity() {
        return new Vector();
    }

    /**
     * Sets the velocity of this entity.
     */
    default void setVelocity(Vector velocity) {
        // No-op in this minimal stub.
    }

    /**
     * Returns the distance this entity has fallen.
     */
    default float getFallDistance() {
        return 0.0f;
    }

    /**
     * Sets the distance this entity has fallen.
     */
    default void setFallDistance(float distance) {
        // No-op in this minimal stub.
    }

    /**
     * Returns the number of ticks this entity is on fire.
     */
    default int getFireTicks() {
        return 0;
    }

    /**
     * Sets the number of ticks this entity is on fire.
     */
    default void setFireTicks(int ticks) {
        // No-op in this minimal stub.
    }

    /**
     * Returns the passengers of this entity.
     */
    default List<Entity> getPassengers() {
        return List.of();
    }

    /**
     * Adds a passenger to this entity.
     *
     * @return {@code true} if the passenger was added
     */
    default boolean addPassenger(Entity passenger) {
        return false;
    }

    /**
     * Removes a passenger from this entity.
     */
    default void removePassenger(Entity passenger) {
        // No-op in this minimal stub.
    }

    /**
     * Returns whether this entity is inside a vehicle.
     */
    default boolean isInsideVehicle() {
        return false;
    }

    /**
     * Makes this entity leave its vehicle.
     */
    default void leaveVehicle() {
        // No-op in this minimal stub.
    }

    /**
     * Returns the vehicle this entity is riding, or {@code null} if none.
     */
    default Entity getVehicle() {
        return null;
    }

    /**
     * Returns nearby entities within the given box centered on this entity.
     */
    default Collection<Entity> getNearbyEntities(double x, double y, double z) {
        return List.of();
    }

    /**
     * Returns the custom name of this entity, or {@code null} if none.
     */
    default String getCustomName() {
        return null;
    }

    /**
     * Sets the custom name of this entity. {@code null} clears the name.
     */
    default void setCustomName(String name) {
        // No-op in this minimal stub.
    }

    /**
     * Returns whether this entity has been removed from the world.
     */
    default boolean isDead() {
        return false;
    }

    /**
     * Returns whether this entity is still valid (loaded and not removed).
     */
    default boolean isValid() {
        return !isDead();
    }

    /**
     * Removes this entity from the world.
     */
    default void remove() {
        // No-op in this minimal stub.
    }
}
