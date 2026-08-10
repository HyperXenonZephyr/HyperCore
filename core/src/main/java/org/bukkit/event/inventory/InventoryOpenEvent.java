package org.bukkit.event.inventory;

import org.bukkit.entity.HumanEntity;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.InventoryView;

/**
 * Bukkit event fired when an inventory is about to be opened for a viewer.
 *
 * <p>This is a hand-written event with real fields so that the HyperCore event
 * bridge can populate it and plugins can cancel the open.
 */
public class InventoryOpenEvent extends InventoryEvent implements org.bukkit.event.Cancellable {
    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final HumanEntity player;
    private boolean cancelled;

    public InventoryOpenEvent(InventoryView view, HumanEntity player) {
        super(view);
        this.player = player;
    }

    /**
     * Returns the player opening the inventory.
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