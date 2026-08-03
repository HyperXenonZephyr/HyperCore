package org.bukkit.inventory;

/**
 * Stub of the Bukkit {@code PlayerInventory} interface.
 *
 * <p>A player inventory is a standard {@link Inventory} with additional slots
 * for held items, armor, and off-hand items.
 */
public interface PlayerInventory extends Inventory {

    /**
     * Armor slot indexes within the armor contents array.
     */
    int[] ARMOR_SLOTS = {36, 37, 38, 39};

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
    int getHeldItemSlot();

    /**
     * Sets the currently selected hotbar slot.
     */
    void setHeldItemSlot(int slot);

    /**
     * Returns the armor contents.
     */
    default ItemStack[] getArmorContents() {
        ItemStack[] armor = new ItemStack[4];
        for (int i = 0; i < 4; i++) {
            armor[i] = getItem(ARMOR_SLOTS[i]);
        }
        return armor;
    }

    /**
     * Sets the armor contents.
     */
    default void setArmorContents(ItemStack[] armor) {
        for (int i = 0; i < 4; i++) {
            setItem(ARMOR_SLOTS[i], i < armor.length ? armor[i] : null);
        }
    }

    /**
     * Returns the extra contents (currently only the off-hand item).
     */
    default ItemStack[] getExtraContents() {
        return new ItemStack[]{getItemInOffHand()};
    }

    /**
     * Sets the extra contents.
     */
    default void setExtraContents(ItemStack[] items) {
        if (items.length > 0) {
            setItemInOffHand(items[0]);
        }
    }
}
