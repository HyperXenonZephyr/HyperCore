package org.bukkit.entity;

import org.bukkit.command.CommandSender;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Minimal stub of the Bukkit {@code HumanEntity} interface.
 */
public interface HumanEntity extends LivingEntity, CommandSender {

    /**
     * Returns the display name of this human entity.
     */
    default String getDisplayName() {
        return getName();
    }

    /**
     * Sets the display name of this human entity.
     *
     * @param name the new display name, or {@code null} to reset
     */
    default void setDisplayName(String name) {
        // No-op in this minimal stub.
    }

    /**
     * Returns the inventory of this human entity.
     */
    Inventory getInventory();

    /**
     * Returns the item currently in the entity's main hand.
     */
    default ItemStack getItemInHand() {
        Inventory inventory = getInventory();
        if (inventory instanceof PlayerInventory playerInventory) {
            return playerInventory.getItemInMainHand();
        }
        return null;
    }

    /**
     * Sets the item in the entity's main hand.
     */
    default void setItemInHand(ItemStack item) {
        Inventory inventory = getInventory();
        if (inventory instanceof PlayerInventory playerInventory) {
            playerInventory.setItemInMainHand(item);
        }
    }
}
