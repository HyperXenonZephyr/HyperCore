package org.bukkit.enchantments;

import org.bukkit.NamespacedKey;

/**
 * Minimal stub of the Bukkit {@code Enchantment} enum.
 *
 * <p>Covers the most common vanilla enchantments. Additional entries can be
 * appended without breaking existing code.
 */
public enum Enchantment {
    PROTECTION,
    FIRE_PROTECTION,
    FEATHER_FALLING,
    BLAST_PROTECTION,
    PROJECTILE_PROTECTION,
    RESPIRATION,
    AQUA_AFFINITY,
    THORNS,
    DEPTH_STRIDER,
    FROST_WALKER,
    BINDING_CURSE,
    SOUL_SPEED,
    SWIFT_SNEAK,
    SHARPNESS,
    SMITE,
    BANE_OF_ARTHROPODS,
    KNOCKBACK,
    FIRE_ASPECT,
    LOOTING,
    SWEEPING_EDGE,
    EFFICIENCY,
    SILK_TOUCH,
    UNBREAKING,
    FORTUNE,
    POWER,
    PUNCH,
    FLAME,
    INFINITY,
    LUCK_OF_THE_SEA,
    LURE,
    LOYALTY,
    IMPALING,
    RIPTIDE,
    CHANNELING,
    MULTISHOT,
    QUICK_CHARGE,
    PIERCING,
    MENDING,
    VANISHING_CURSE;

    /**
     * Returns the resource key for this enchantment in the {@code minecraft} namespace.
     */
    public NamespacedKey getKey() {
        return new NamespacedKey(name().toLowerCase());
    }
}
