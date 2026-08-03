package org.bukkit.inventory;

import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stub of the Bukkit {@code Inventory} interface.
 */
public interface Inventory {

    /**
     * Returns the size of this inventory.
     */
    default int getSize() {
        return 0;
    }

    /**
     * Returns the first empty slot, or {@code -1} if the inventory is full.
     */
    default int firstEmpty() {
        for (int i = 0; i < getSize(); i++) {
            if (getItem(i) == null || getItem(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns the first slot containing the given item, or {@code -1} if none.
     */
    default int first(ItemStack item) {
        if (item == null) {
            return firstEmpty();
        }
        for (int i = 0; i < getSize(); i++) {
            ItemStack existing = getItem(i);
            if (existing != null && existing.isSimilar(item)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns the first slot containing the given material, or {@code -1} if none.
     */
    default int first(Material material) {
        for (int i = 0; i < getSize(); i++) {
            ItemStack existing = getItem(i);
            if (existing != null && !existing.isEmpty() && existing.getType() == material) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns whether this inventory contains at least one of the given material.
     */
    default boolean contains(Material material) {
        return first(material) >= 0;
    }

    /**
     * Returns whether this inventory contains the given item.
     */
    default boolean contains(ItemStack item) {
        return first(item) >= 0;
    }

    /**
     * Clears this inventory.
     */
    default void clear() {
        for (int i = 0; i < getSize(); i++) {
            setItem(i, null);
        }
    }

    /**
     * Returns the item in the given slot, or {@code null} if empty.
     */
    default ItemStack getItem(int index) {
        return null;
    }

    /**
     * Sets the item in the given slot.
     *
     * @param index the slot index
     * @param item the item, or {@code null} to clear the slot
     */
    default void setItem(int index, ItemStack item) {
        // Default no-op implementation for inventories that do not support mutation.
    }

    /**
     * Adds the given items to empty slots in this inventory.
     *
     * @param items the items to add
     * @return a map of slot index to leftover item that could not be added
     */
    default HashMap<Integer, ItemStack> addItem(ItemStack... items) {
        HashMap<Integer, ItemStack> leftovers = new HashMap<>();
        if (items == null) {
            return leftovers;
        }
        for (int i = 0; i < items.length; i++) {
            ItemStack item = items[i];
            if (item == null || item.getType() == null || item.getType().name().isEmpty()) {
                continue;
            }
            int amount = item.getAmount();
            for (int slot = 0; slot < getSize() && amount > 0; slot++) {
                ItemStack existing = getItem(slot);
                if (existing == null) {
                    setItem(slot, item.clone());
                    amount = 0;
                } else if (existing.isSimilar(item)) {
                    int space = Math.max(0, 64 - existing.getAmount());
                    int transfer = Math.min(space, amount);
                    if (transfer > 0) {
                        existing.setAmount(existing.getAmount() + transfer);
                        amount -= transfer;
                    }
                }
            }
            if (amount > 0) {
                ItemStack leftover = item.clone();
                leftover.setAmount(amount);
                leftovers.put(i, leftover);
            }
        }
        return leftovers;
    }

    /**
     * Removes the given items from this inventory.
     *
     * @param items the items to remove
     * @return a map of slot index to leftover item that could not be removed
     */
    default HashMap<Integer, ItemStack> removeItem(ItemStack... items) {
        HashMap<Integer, ItemStack> leftovers = new HashMap<>();
        if (items == null) {
            return leftovers;
        }
        for (int i = 0; i < items.length; i++) {
            ItemStack item = items[i];
            if (item == null) {
                continue;
            }
            int amount = item.getAmount();
            for (int slot = 0; slot < getSize() && amount > 0; slot++) {
                ItemStack existing = getItem(slot);
                if (existing != null && existing.isSimilar(item)) {
                    int remove = Math.min(existing.getAmount(), amount);
                    existing.setAmount(existing.getAmount() - remove);
                    if (existing.getAmount() <= 0) {
                        setItem(slot, null);
                    }
                    amount -= remove;
                }
            }
            if (amount > 0) {
                ItemStack leftover = item.clone();
                leftover.setAmount(amount);
                leftovers.put(i, leftover);
            }
        }
        return leftovers;
    }

    /**
     * Returns the viewers of this inventory.
     */
    default List<HumanEntity> getViewers() {
        return Collections.emptyList();
    }

    /**
     * Returns the contents of this inventory.
     */
    default ItemStack[] getContents() {
        ItemStack[] contents = new ItemStack[getSize()];
        for (int i = 0; i < contents.length; i++) {
            contents[i] = getItem(i);
        }
        return contents;
    }

    /**
     * Sets the contents of this inventory.
     */
    default void setContents(ItemStack[] items) {
        int size = getSize();
        for (int i = 0; i < size; i++) {
            setItem(i, i < items.length ? items[i] : null);
        }
    }
}
