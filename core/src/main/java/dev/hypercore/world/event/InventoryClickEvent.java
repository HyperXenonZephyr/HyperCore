package dev.hypercore.world.event;

import dev.hypercore.plugin.PluginEventBus;

import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/**
 * Internal event fired when a viewer clicks inside an inventory view.
 *
 * <p>If cancelled, the click is ignored. Bukkit plugins observe the same
 * cancellation through {@link org.bukkit.event.inventory.InventoryClickEvent}.
 */
public final class InventoryClickEvent implements PluginEventBus.CancellableEvent {
    private final InventoryView view;
    private final int slot;
    private final ItemStack current;
    private final ItemStack cursor;
    private boolean cancelled;

    public InventoryClickEvent(InventoryView view, int slot, ItemStack current, ItemStack cursor) {
        this.view = Objects.requireNonNull(view, "view");
        this.slot = slot;
        this.current = current;
        this.cursor = cursor;
    }

    public InventoryView getView() {
        return view;
    }

    public int getSlot() {
        return slot;
    }

    public ItemStack getCurrent() {
        return current;
    }

    public ItemStack getCursor() {
        return cursor;
    }

    @Override
    public boolean cancelled() {
        return cancelled;
    }

    @Override
    public void cancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}