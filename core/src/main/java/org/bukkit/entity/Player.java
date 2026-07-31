package org.bukkit.entity;

/**
 * Minimal stub of the Bukkit {@code Player} interface. It extends
 * {@link HumanEntity} so that generated player events can use it as both entity
 * and command sender.
 */
public interface Player extends HumanEntity {

    /**
     * Returns the unique id for this player. Stub returns the player name.
     */
    default String getUniqueId() {
        return getName();
    }
}
