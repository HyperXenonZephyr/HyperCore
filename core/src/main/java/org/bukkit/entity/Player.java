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
    void kickPlayer(String message);

    /**
     * Returns whether this player is currently online.
     */
    default boolean isOnline() {
        return isValid();
    }

    /**
     * Sends a title and optional subtitle to this player.
     *
     * @param title    the title text, or {@code null} to clear
     * @param subtitle the subtitle text, or {@code null} to clear
     * @param fadeIn   ticks to fade in
     * @param stay     ticks to stay on screen
     * @param fadeOut  ticks to fade out
     */
    void sendTitle(String title, String subtitle, int fadeIn, int stay, int fadeOut);

    /**
     * Clears any title and subtitle currently shown to this player and resets
     * title timings.
     */
    void resetTitle();

    /**
     * Executes a command as this player.
     *
     * @param command the command line without leading slash
     * @return {@code true} if the command was found and executed
     */
    boolean performCommand(String command);

    /**
     * Sends the player's current inventory contents to the client.
     */
    void updateInventory();

    /**
     * Opens an inventory window for this player.
     *
     * @param inventory the inventory to open
     * @return the inventory view, or {@code null} if it could not be opened
     */
    org.bukkit.inventory.InventoryView openInventory(org.bukkit.inventory.Inventory inventory);

    /**
     * Sets the resource pack URL for this player.
     *
     * @param url the resource pack URL
     */
    void setResourcePack(String url);

    /**
     * Returns whether this player is sneaking.
     */
    boolean isSneaking();

    /**
     * Sets whether this player is sneaking.
     */
    void setSneaking(boolean sneaking);

    /**
     * Returns whether this player is sprinting.
     */
    boolean isSprinting();

    /**
     * Sets whether this player is sprinting.
     */
    void setSprinting(boolean sprinting);
}
