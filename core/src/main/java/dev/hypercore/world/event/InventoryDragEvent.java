package dev.hypercore.world.event;

import dev.hypercore.plugin.PluginEventBus;

import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/**
 * Internal event fired when a viewer drags items across an inventory view.
 *
 * <p>If cancelled, the drag is ignored. Bukkit plugins observe the same
 * cancellation through {@link org.bukkit.event.inventory.InventoryDragEvent}.
 */
public final class InventoryDragEvent implements PluginEventBus.CancellableEvent {
    private final InventoryView view;
    private final ItemStack oldCursor;
    private final ItemStack newCursor;
    private boolean cancelled;

    public InventoryDragEvent(InventoryView view, ItemStack oldCursor, ItemStack newCursor) {
        this.view = Objects.requireNonNull(view, "view");
        this.oldCursor = oldCursor;
        this.newCursor = newCursor;
    }

    public InventoryView getView() {
        return view;
    }

    public ItemStack getOldCursor() {
        return oldCursor;
    }

    public ItemStack getNewCursor() {
        return newCursor;
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