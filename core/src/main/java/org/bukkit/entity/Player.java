package org.bukkit.entity;

import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
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
}
