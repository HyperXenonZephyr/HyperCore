package org.bukkit.entity;

/**
 * Minimal stub of the Bukkit {@code LivingEntity} interface.
 */
public interface LivingEntity extends Entity {

    /**
     * Returns the current health of this living entity.
     */
    default double getHealth() {
        return 20.0;
    }

    /**
     * Sets the health of this living entity.
     *
     * @param health the new health value
     */
    default void setHealth(double health) {
        // No-op in this minimal stub.
    }

    /**
     * Returns the maximum health of this living entity.
     */
    default double getMaxHealth() {
        return 20.0;
    }

    /**
     * Sets the maximum health of this living entity.
     *
     * @param maxHealth the new maximum health value
     */
    default void setMaxHealth(double maxHealth) {
        // No-op in this minimal stub.
    }

    /**
     * Deals the given amount of damage to this living entity.
     *
     * @param amount the amount of damage
     */
    default void damage(double amount) {
        // No-op in this minimal stub.
    }

    /**
     * Deals damage to this living entity from the given entity.
     *
     * @param amount the amount of damage
     * @param source the entity dealing the damage
     */
    default void damage(double amount, Entity source) {
        damage(amount);
    }

    /**
     * Returns whether this living entity has AI.
     */
    default boolean hasAI() {
        return true;
    }

    /**
     * Sets whether this living entity has AI.
     */
    default void setAI(boolean ai) {
        // No-op in this minimal stub.
    }

    /**
     * Returns whether this living entity is collidable.
     */
    default boolean isCollidable() {
        return true;
    }

    /**
     * Sets whether this living entity is collidable.
     */
    default void setCollidable(boolean collidable) {
        // No-op in this minimal stub.
    }
}
