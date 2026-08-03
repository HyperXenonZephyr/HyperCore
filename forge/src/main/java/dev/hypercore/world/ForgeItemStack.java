package dev.hypercore.world;

import dev.hypercore.bukkit.HyperCoreItemMeta;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Forge-aware {@link org.bukkit.inventory.ItemStack} that wraps a native
 * Minecraft {@link ItemStack}.
 *
 * <p>This preserves components, durability, and other native item data when
 * items are moved between HyperCore's Bukkit API and the underlying Minecraft
 * container. Display name and lore are synchronized with the native item
 * components so that Bukkit plugins observe the same values as the Minecraft
 * server. Enchantments are stored in the Bukkit {@link ItemMeta} layer; native
 * enchantment component synchronization is pending a loader-specific registry
 * helper.
 */
public final class ForgeItemStack extends org.bukkit.inventory.ItemStack {
    private ItemStack mcStack;

    public ForgeItemStack(ItemStack mcStack) {
        super(ForgeWorldAccess.toMaterial(mcStack.getItem()), mcStack.getCount());
        this.mcStack = Objects.requireNonNull(mcStack, "mcStack");
    }

    @Override
    public Material getType() {
        return ForgeWorldAccess.toMaterial(mcStack.getItem());
    }

    @Override
    public void setType(Material type) {
        super.setType(type);
        Item replacement = ForgeWorldAccess.toItem(type);
        this.mcStack = new ItemStack(replacement, mcStack.getCount());
    }

    @Override
    public int getAmount() {
        return mcStack.getCount();
    }

    @Override
    public void setAmount(int amount) {
        super.setAmount(amount);
        mcStack.setCount(Math.max(0, amount));
    }

    @Override
    public short getDurability() {
        return (short) mcStack.getDamageValue();
    }

    @Override
    public void setDurability(short durability) {
        mcStack.setDamageValue(durability);
    }

    @Override
    public ItemMeta getItemMeta() {
        HyperCoreItemMeta meta = readNativeMeta();
        ItemMeta fieldMeta = super.getItemMeta();
        if (fieldMeta != null && !fieldMeta.getEnchants().isEmpty()) {
            for (Map.Entry<Enchantment, Integer> entry : fieldMeta.getEnchants().entrySet()) {
                meta.addEnchant(entry.getKey(), entry.getValue(), true);
            }
        }
        return meta;
    }

    @Override
    public boolean setItemMeta(ItemMeta meta) {
        writeNativeMeta(meta);
        return super.setItemMeta(meta);
    }

    @Override
    public boolean hasItemMeta() {
        return super.hasItemMeta() || hasNativeMeta();
    }

    /**
     * Returns the underlying Minecraft item stack.
     */
    public ItemStack toMinecraft() {
        return mcStack;
    }

    @Override
    public ForgeItemStack clone() {
        ForgeItemStack clone = new ForgeItemStack(mcStack.copy());
        clone.setItemMeta(getItemMeta());
        return clone;
    }

    private HyperCoreItemMeta readNativeMeta() {
        HyperCoreItemMeta meta = new HyperCoreItemMeta();

        Component customName = mcStack.get(DataComponents.CUSTOM_NAME);
        if (customName != null) {
            meta.setDisplayName(customName.getString());
        }

        ItemLore lore = mcStack.get(DataComponents.LORE);
        if (lore != null && !lore.lines().isEmpty()) {
            List<String> lines = new ArrayList<>(lore.lines().size());
            for (Component line : lore.lines()) {
                lines.add(line.getString());
            }
            meta.setLore(lines);
        }

        net.minecraft.world.item.enchantment.ItemEnchantments enchantments = mcStack.getEnchantments();
        if (!enchantments.isEmpty()) {
            for (net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> holder : enchantments.keySet()) {
                holder.unwrapKey().ifPresent(key -> {
                    String path = key.location().getPath();
                    try {
                        Enchantment bukkitEnchant = Enchantment.valueOf(path.toUpperCase(java.util.Locale.ROOT));
                        meta.addEnchant(bukkitEnchant, enchantments.getLevel(holder), true);
                    } catch (IllegalArgumentException ignored) {
                        // Unknown enchantment: skip rather than fail meta creation.
                    }
                });
            }
        }

        return meta;
    }

    private void writeNativeMeta(ItemMeta meta) {
        if (meta == null) {
            mcStack.remove(DataComponents.CUSTOM_NAME);
            mcStack.remove(DataComponents.LORE);
            return;
        }

        if (meta.hasDisplayName()) {
            mcStack.set(DataComponents.CUSTOM_NAME, Component.literal(meta.getDisplayName()));
        } else {
            mcStack.remove(DataComponents.CUSTOM_NAME);
        }

        if (meta.hasLore()) {
            List<Component> lines = new ArrayList<>(meta.getLore().size());
            for (String line : meta.getLore()) {
                lines.add(Component.literal(line));
            }
            mcStack.set(DataComponents.LORE, new ItemLore(lines));
        } else {
            mcStack.remove(DataComponents.LORE);
        }
    }

    private boolean hasNativeMeta() {
        return mcStack.has(DataComponents.CUSTOM_NAME) || mcStack.has(DataComponents.LORE);
    }
}
