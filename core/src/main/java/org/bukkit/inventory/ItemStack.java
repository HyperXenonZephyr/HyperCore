package org.bukkit.inventory;

import dev.hypercore.bukkit.HyperCoreItemMeta;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Minimal stub of the Bukkit {@code ItemStack} class.
 *
 * <p>Tracks material type, amount, and item meta. Loader-specific wrappers
 * such as {@code ForgeItemStack} and {@code FabricItemStack} synchronize the
 * meta fields with the native Minecraft item NBT.
 */
public class ItemStack implements Cloneable {

    private Material type;
    private int amount;
    private ItemMeta itemMeta;

    /**
     * Creates an empty item stack.
     */
    public ItemStack() {
        this(Material.AIR, 0);
    }

    /**
     * Creates an item stack of one unit of the given material.
     */
    public ItemStack(Material type) {
        this(type, 1);
    }

    /**
     * Creates an item stack with the given material and amount.
     */
    public ItemStack(Material type, int amount) {
        this.type = Objects.requireNonNull(type, "type");
        this.amount = Math.max(0, amount);
    }

    /**
     * Returns the material type of this item stack.
     */
    public Material getType() {
        return type;
    }

    /**
     * Sets the material type of this item stack.
     */
    public void setType(Material type) {
        this.type = Objects.requireNonNull(type, "type");
    }

    /**
     * Returns the amount of items in this stack.
     */
    public int getAmount() {
        return amount;
    }

    /**
     * Sets the amount of items in this stack.
     */
    public void setAmount(int amount) {
        this.amount = Math.max(0, amount);
    }

    /**
     * Returns {@code true} if this stack has zero amount or is air.
     */
    public boolean isEmpty() {
        return amount <= 0 || type == Material.AIR;
    }

    /**
     * Returns {@code true} if this stack is of the same type as another.
     *
     * <p>This minimal implementation compares material only; meta and NBT are
     * ignored.
     */
    public boolean isSimilar(ItemStack other) {
        if (other == null) {
            return false;
        }
        return type == other.type;
    }

    /**
     * Returns the damage value of this item stack.
     *
     * <p>Stub: real Bukkit returns the item's durability/damage meta.
     */
    public short getDurability() {
        return 0;
    }

    /**
     * Sets the damage value of this item stack.
     *
     * <p>Stub: real Bukkit updates the item's durability/damage meta.
     */
    public void setDurability(short durability) {
        // No-op in this minimal stub.
    }

    /**
     * Returns the maximum stack size for this item's material.
     *
     * <p>Stub: real Bukkit queries the material's max stack size.
     */
    public int getMaxStackSize() {
        return 64;
    }

    /**
     * Returns the item meta for this stack, or {@code null} if it has none.
     *
     * <p>The returned instance is a clone; modifying it does not affect this
     * stack until {@link #setItemMeta(ItemMeta)} is called.
     */
    public ItemMeta getItemMeta() {
        return itemMeta == null ? null : itemMeta.clone();
    }

    /**
     * Returns a mutable item meta for this stack, creating one if necessary.
     */
    public ItemMeta getItemMetaOrCreate() {
        if (itemMeta == null) {
            itemMeta = new HyperCoreItemMeta();
        }
        return itemMeta.clone();
    }

    /**
     * Sets the item meta for this stack.
     *
     * @param meta the new meta, or {@code null} to clear
     * @return {@code true} if the meta was applied
     */
    public boolean setItemMeta(ItemMeta meta) {
        this.itemMeta = meta == null ? null : meta.clone();
        return true;
    }

    /**
     * Returns whether this stack has item meta.
     */
    public boolean hasItemMeta() {
        return itemMeta != null && (itemMeta.hasDisplayName() || itemMeta.hasLore() || !itemMeta.getEnchants().isEmpty());
    }

    /**
     * Returns the enchantments on this stack.
     */
    public Map<Enchantment, Integer> getEnchantments() {
        if (itemMeta == null) {
            return Collections.emptyMap();
        }
        return itemMeta.getEnchants();
    }

    /**
     * Adds an unsafe enchantment to this stack, ignoring level restrictions.
     */
    public void addUnsafeEnchantment(Enchantment enchantment, int level) {
        Objects.requireNonNull(enchantment, "enchantment");
        if (itemMeta == null) {
            itemMeta = new HyperCoreItemMeta();
        }
        itemMeta.addEnchant(enchantment, level, true);
    }

    /**
     * Removes the given enchantment from this stack.
     */
    public void removeEnchantment(Enchantment enchantment) {
        if (itemMeta != null) {
            itemMeta.removeEnchant(enchantment);
        }
    }

    @Override
    public ItemStack clone() {
        try {
            ItemStack clone = (ItemStack) super.clone();
            if (itemMeta != null) {
                clone.itemMeta = itemMeta.clone();
            }
            return clone;
        } catch (CloneNotSupportedException error) {
            throw new AssertionError(error);
        }
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ItemStack other)) {
            return false;
        }
        if (amount != other.amount || type != other.type) {
            return false;
        }
        return Objects.equals(itemMeta, other.itemMeta);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, amount, itemMeta);
    }

    @Override
    public String toString() {
        return "ItemStack{" + "type=" + type + ", amount=" + amount + '}';
    }
}
