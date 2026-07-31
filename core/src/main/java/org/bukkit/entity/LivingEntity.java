package org.bukkit.entity;

/**
 * Minimal stub of the Bukkit {@code LivingEntity} interface.
 */
public interface LivingEntity extends Entity {

    /**
     * Stub returning {@code 20}.
     */
    default double getHealth() {
        return 20.0;
    }
}
