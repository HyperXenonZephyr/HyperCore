package org.bukkit.inventory;

import org.bukkit.entity.HumanEntity;

/**
 * Minimal stub of the Bukkit {@code InventoryView} interface.
 */
public interface InventoryView {

    /**
     * Returns the top inventory.
     */
    Inventory getTopInventory();

    /**
     * Returns the bottom inventory.
     */
    Inventory getBottomInventory();

    /**
     * Returns the player viewing this inventory.
     */
    HumanEntity getPlayer();
}
