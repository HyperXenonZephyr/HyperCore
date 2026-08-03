package org.bukkit.inventory;

/**
 * Minimal stub of the Bukkit {@code InventoryView} interface.
 *
 * <p>Represents the association between a viewer and an inventory. HyperCore
 * does not currently implement packet-based inventory windows, so this view is
 * a simple marker object.
 */
public interface InventoryView {

    /**
     * Returns the top inventory of this view.
     */
    Inventory getTopInventory();

    /**
     * Returns the bottom inventory of this view (usually the player inventory).
     */
    Inventory getBottomInventory();

    /**
     * Returns the player viewing this inventory.
     */
    org.bukkit.entity.HumanEntity getPlayer();

    /**
     * Returns the title shown to the player.
     */
    default String getTitle() {
        return "Inventory";
    }
}
