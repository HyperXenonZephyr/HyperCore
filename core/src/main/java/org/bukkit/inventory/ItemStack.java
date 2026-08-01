package org.bukkit.inventory;

import org.bukkit.Material;

import java.util.Objects;

/**
 * Minimal stub of the Bukkit {@code ItemStack} class.
 *
 * <p>Tracks material type and amount. Item meta, durability, and NBT are left
 * for later expansion.
 */
public class ItemStack implements Cloneable {

    private Material type;
    private int amount;

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

    @Override
    public ItemStack clone() {
        try {
            return (ItemStack) super.clone();
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
        return amount == other.amount && type == other.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, amount);
    }

    @Override
    public String toString() {
        return "ItemStack{" + "type=" + type + ", amount=" + amount + '}';
    }
}
