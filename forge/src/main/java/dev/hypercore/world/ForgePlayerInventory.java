package dev.hypercore.world;

import net.minecraft.world.entity.player.Player;

import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.List;

/**
 * Wraps a Minecraft player inventory as a Bukkit {@link PlayerInventory}.
 */
final class ForgePlayerInventory implements PlayerInventory {
    private final net.minecraft.world.entity.player.Inventory inventory;
    private final ForgeWorldAccess worldAccess;

    ForgePlayerInventory(Player player, ForgeWorldAccess worldAccess) {
        this.inventory = player.getInventory();
        this.worldAccess = worldAccess;
    }

    @Override
    public int getSize() {
        return inventory.getContainerSize();
    }

    @Override
    public ItemStack getItem(int index) {
        if (index < 0 || index >= getSize()) {
            return null;
        }
        return ForgeWorldAccess.toBukkit(inventory.getItem(index));
    }

    @Override
    public void setItem(int index, ItemStack item) {
        if (index < 0 || index >= getSize()) {
            return;
        }
        inventory.setItem(index, worldAccess.toMinecraft(item));
    }

    @Override
    public int getHeldItemSlot() {
        return inventory.selected;
    }

    @Override
    public void setHeldItemSlot(int slot) {
        if (slot >= 0 && slot < 9) {
            inventory.selected = slot;
        }
    }

    @Override
    public List<HumanEntity> getViewers() {
        return List.of();
    }
}
