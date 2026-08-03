package dev.hypercore.bukkit;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Default {@link ItemMeta} implementation used by HyperCore's Bukkit
 * {@link org.bukkit.inventory.ItemStack}.
 *
 * <p>This implementation stores display name, lore, and enchantments in plain
 * Java fields. Loader-specific {@code ItemStack} wrappers (such as
 * {@code ForgeItemStack} and {@code FabricItemStack}) are responsible for
 * synchronizing these values with the native Minecraft item NBT.
 */
public final class HyperCoreItemMeta implements ItemMeta {
    private String displayName;
    private List<String> lore;
    private final EnumMap<Enchantment, Integer> enchantments = new EnumMap<>(Enchantment.class);

    public HyperCoreItemMeta() {
    }

    private HyperCoreItemMeta(HyperCoreItemMeta other) {
        this.displayName = other.displayName;
        this.lore = other.lore == null ? null : new ArrayList<>(other.lore);
        this.enchantments.putAll(other.enchantments);
    }

    @Override
    public boolean hasDisplayName() {
        return displayName != null;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public void setDisplayName(String name) {
        this.displayName = name;
    }

    @Override
    public boolean hasLore() {
        return lore != null && !lore.isEmpty();
    }

    @Override
    public List<String> getLore() {
        return lore == null ? null : Collections.unmodifiableList(lore);
    }

    @Override
    public void setLore(List<String> lore) {
        this.lore = lore == null ? null : new ArrayList<>(lore);
    }

    @Override
    public boolean hasEnchant(Enchantment enchantment) {
        return enchantments.containsKey(enchantment);
    }

    @Override
    public int getEnchantLevel(Enchantment enchantment) {
        return enchantments.getOrDefault(enchantment, 0);
    }

    @Override
    public boolean addEnchant(Enchantment enchantment, int level, boolean ignoreLevelRestriction) {
        Objects.requireNonNull(enchantment, "enchantment");
        if (level < 0) {
            return false;
        }
        if (!ignoreLevelRestriction && level == 0) {
            return false;
        }
        enchantments.put(enchantment, level);
        return true;
    }

    @Override
    public boolean removeEnchant(Enchantment enchantment) {
        return enchantments.remove(enchantment) != null;
    }

    @Override
    public Map<Enchantment, Integer> getEnchants() {
        return Collections.unmodifiableMap(new EnumMap<>(enchantments));
    }

    @Override
    public HyperCoreItemMeta clone() {
        return new HyperCoreItemMeta(this);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof HyperCoreItemMeta other)) {
            return false;
        }
        return Objects.equals(displayName, other.displayName)
            && Objects.equals(lore, other.lore)
            && enchantments.equals(other.enchantments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(displayName, lore, enchantments);
    }
}
