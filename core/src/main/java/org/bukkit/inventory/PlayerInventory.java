package org.bukkit.inventory;

/**
 * Minimal stub of the Bukkit {@code PlayerInventory} interface.
 *
 * <p>A player inventory is a standard {@link Inventory} with additional slots
 * for held items, armor, and off-hand items.
 */
public interface PlayerInventory extends Inventory {

    /**
     * Returns the item in the main hand.
     */
    default ItemStack getItemInMainHand() {
        return getItem(getHeldItemSlot());
    }

    /**
     * Sets the item in the main hand.
     */
    default void setItemInMainHand(ItemStack item) {
        setItem(getHeldItemSlot(), item);
    }

    /**
     * Returns the item in the off hand.
     */
    default ItemStack getItemInOffHand() {
        return getItem(40);
    }

    /**
     * Sets the item in the off hand.
     */
    default void setItemInOffHand(ItemStack item) {
        setItem(40, item);
    }

    /**
     * Returns the currently selected hotbar slot.
     */
    default int getHeldItemSlot() {
        return 0;
    }

    /**
     * Sets the currently selected hotbar slot.
     */
    default void setHeldItemSlot(int slot) {
        // No-op in this stub.
    }
}
