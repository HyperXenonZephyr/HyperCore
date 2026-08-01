package dev.hypercore.world;

import net.minecraft.world.Container;

import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Wraps a Minecraft {@link Container} (chest, furnace, etc.) as a Bukkit
 * {@link Inventory}.
 */
final class ForgeContainerInventory implements Inventory {
    private final Container container;

    ForgeContainerInventory(Container container) {
        this.container = container;
    }

    @Override
    public int getSize() {
        return container.getContainerSize();
    }

    @Override
    public ItemStack getItem(int index) {
        if (index < 0 || index >= getSize()) {
            return null;
        }
        return ForgeWorldAccess.toBukkit(container.getItem(index));
    }

    @Override
    public void setItem(int index, ItemStack item) {
        if (index < 0 || index >= getSize()) {
            return;
        }
        container.setItem(index, ForgeWorldAccess.toMinecraft(item));
    }

    @Override
    public List<HumanEntity> getViewers() {
        return List.of();
    }
}
