package org.bukkit.event.inventory;

import org.bukkit.event.HandlerList;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

/**
 * Bukkit event fired when a viewer clicks inside an inventory view.
 *
 * <p>This is a hand-written event with real fields so that the HyperCore event
 * bridge can populate it and plugins can cancel the click.
 */
public class InventoryClickEvent extends InventoryEvent implements org.bukkit.event.Cancellable {
    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final int slot;
    private final ItemStack current;
    private ItemStack cursor;
    private boolean cancelled;

    public InventoryClickEvent(InventoryView view, int slot, ItemStack current, ItemStack cursor) {
        super(view);
        this.slot = slot;
        this.current = current;
        this.cursor = cursor;
    }

    /**
     * No-argument constructor kept for generated subclasses (e.g.
     * {@code CraftItemEvent}, {@code InventoryCreativeEvent}) that call
     * {@code super()} from a skeleton.
     */
    protected InventoryClickEvent() {
        super();
        this.slot = -1;
        this.current = null;
        this.cursor = null;
    }

    /**
     * Returns the clicked slot, or {@code -1} if unknown.
     */
    public int getSlot() {
        return slot;
    }

    /**
     * Returns the item currently in the clicked slot, or {@code null}.
     */
    public ItemStack getCurrent() {
        return current;
    }

    /**
     * Returns the item on the cursor, or {@code null}.
     */
    public ItemStack getCursor() {
        return cursor;
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