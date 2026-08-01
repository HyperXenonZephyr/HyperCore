package org.bukkit.inventory;

import org.bukkit.entity.HumanEntity;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal stub of the Bukkit {@code Inventory} interface.
 */
public interface Inventory {

    /**
     * Returns the size of this inventory.
     */
    default int getSize() {
        return 0;
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
