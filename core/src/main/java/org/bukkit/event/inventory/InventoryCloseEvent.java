package org.bukkit.event.inventory;

import org.bukkit.entity.HumanEntity;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.InventoryView;

/**
 * Bukkit event fired when a viewer's inventory view is about to close.
 *
 * <p>This is a hand-written event with real fields so that the HyperCore event
 * bridge can populate it and plugins can cancel the close.
 */
public class InventoryCloseEvent extends InventoryEvent implements org.bukkit.event.Cancellable {
    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final HumanEntity player;
    private boolean cancelled;

    public InventoryCloseEvent(InventoryView view, HumanEntity player) {
        super(view);
        this.player = player;
    }

    /**
     * Returns the player closing the inventory.
     */
    public HumanEntity getPlayer() {
        return player;
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