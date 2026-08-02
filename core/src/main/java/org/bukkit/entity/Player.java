package org.bukkit.entity;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.inventory.PlayerInventory;

import java.util.UUID;

/**
 * Minimal stub of the Bukkit {@code Player} interface. It extends
 * {@link HumanEntity} so that generated player events can use it as both entity
 * and command sender.
 */
public interface Player extends HumanEntity {

    /**
     * Returns the unique id for this player.
     *
     * <p>This overrides {@link Entity#getUniqueId()} to document the player
     * specialization; real Bukkit also returns a {@link UUID} here.
     */
    @Override
    UUID getUniqueId();

    /**
     * Returns the inventory of this player.
     */
    PlayerInventory getInventory();

    /**
     * Returns the current location of this player.
     */
    @Override
    Location getLocation();

    /**
     * Teleports this player to the given location.
     */
    @Override
    boolean teleport(Location location);

    /**
     * Returns the game mode of this player.
     */
    GameMode getGameMode();

    /**
     * Sets the game mode of this player.
     *
     * @param gameMode the new game mode
     */
    void setGameMode(GameMode gameMode);

    /**
     * Disconnects this player from the server with the given reason.
     *
     * @param message the kick message
     */
    default void kickPlayer(String message) {
        // No-op in this minimal stub.
    }

    /**
     * Returns whether this player is currently online.
     */
    default boolean isOnline() {
        return isValid();
    }

    /**
     * Returns whether this player is sneaking.
     */
    default boolean isSneaking() {
        return false;
    }

    /**
     * Sets whether this player is sneaking.
     */
    default void setSneaking(boolean sneaking) {
        // No-op in this minimal stub.
    }

    /**
     * Returns whether this player is sprinting.
     */
    default boolean isSprinting() {
        return false;
    }

    /**
     * Sets whether this player is sprinting.
     */
    default void setSprinting(boolean sprinting) {
        // No-op in this minimal stub.
    }
}
