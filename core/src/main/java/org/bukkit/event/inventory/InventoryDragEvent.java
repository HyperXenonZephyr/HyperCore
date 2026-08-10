package org.bukkit.event.inventory;

import org.bukkit.event.HandlerList;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

/**
 * Bukkit event fired when a viewer drags items across an inventory view.
 *
 * <p>This is a hand-written event with real fields so that the HyperCore event
 * bridge can populate it and plugins can cancel the drag.
 */
public class InventoryDragEvent extends InventoryEvent implements org.bukkit.event.Cancellable {
    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final ItemStack oldCursor;
    private final ItemStack newCursor;
    private boolean cancelled;

    public InventoryDragEvent(InventoryView view, ItemStack oldCursor, ItemStack newCursor) {
        super(view);
        this.oldCursor = oldCursor;
        this.newCursor = newCursor;
    }

    /**
     * Returns the item on the cursor before the drag.
     */
    public ItemStack getOldCursor() {
        return oldCursor;
    }

    /**
     * Returns the item on the cursor after the drag.
     */
    public ItemStack getNewCursor() {
        return newCursor;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}