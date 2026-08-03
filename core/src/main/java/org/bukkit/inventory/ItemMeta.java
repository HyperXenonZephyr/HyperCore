package org.bukkit.inventory;

import org.bukkit.enchantments.Enchantment;

import java.util.List;
import java.util.Map;

/**
 * Minimal stub of the Bukkit {@code ItemMeta} interface.
 *
 * <p>Tracks display name, lore, and enchantments for an {@link ItemStack}. This
 * is enough for the majority of plugin item-manipulation use cases.
 */
public interface ItemMeta extends Cloneable {

    /**
     * Returns whether this meta has a display name.
     */
    boolean hasDisplayName();

    /**
     * Returns the display name, or {@code null} if none.
     */
    String getDisplayName();

    /**
     * Sets the display name. {@code null} clears it.
     */
    void setDisplayName(String name);

    /**
     * Returns whether this meta has lore.
     */
    boolean hasLore();

    /**
     * Returns the lore lines, or {@code null} if none.
     */
    List<String> getLore();

    /**
     * Sets the lore lines. {@code null} clears them.
     */
    void setLore(List<String> lore);

    /**
     * Returns whether this item has the given enchantment.
     */
    boolean hasEnchant(Enchantment enchantment);

    /**
     * Returns the level of the given enchantment, or 0 if absent.
     */
    int getEnchantLevel(Enchantment enchantment);

    /**
     * Adds an enchantment.
     *
     * @param enchantment the enchantment to add
     * @param level the level
     * @param ignoreLevelRestriction if {@code true}, bypass vanilla level caps
     * @return {@code true} if the enchantment was added or updated
     */
    boolean addEnchant(Enchantment enchantment, int level, boolean ignoreLevelRestriction);

    /**
     * Removes the given enchantment.
     *
     * @return {@code true} if the enchantment was present and removed
     */
    boolean removeEnchant(Enchantment enchantment);

    /**
     * Returns all enchantments on this item.
     */
    Map<Enchantment, Integer> getEnchants();

    /**
     * Returns a copy of this meta.
     */
    ItemMeta clone();
}
