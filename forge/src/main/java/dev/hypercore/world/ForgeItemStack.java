package dev.hypercore.world;

import org.bukkit.Material;

import java.util.Objects;

/**
 * Forge-aware {@link org.bukkit.inventory.ItemStack} that wraps a native
 * Minecraft {@link net.minecraft.world.item.ItemStack}.
 *
 * <p>This preserves NBT, durability, and other native item data when items are
 * moved between HyperCore's Bukkit API and the underlying Minecraft container.
 */
public final class ForgeItemStack extends org.bukkit.inventory.ItemStack {
    private net.minecraft.world.item.ItemStack mcStack;

    public ForgeItemStack(net.minecraft.world.item.ItemStack mcStack) {
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
        net.minecraft.world.item.Item replacement = ForgeWorldAccess.toItem(type);
        this.mcStack = new net.minecraft.world.item.ItemStack(replacement, mcStack.getCount());
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

    /**
     * Returns the underlying Minecraft item stack.
     */
    public net.minecraft.world.item.ItemStack toMinecraft() {
        return mcStack;
    }

    @Override
    public ForgeItemStack clone() {
        return new ForgeItemStack(mcStack.copy());
    }
}
