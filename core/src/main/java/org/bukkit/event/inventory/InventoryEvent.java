package org.bukkit.event.inventory;

import org.bukkit.event.Event;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Base class for inventory-related events.
 */
public abstract class InventoryEvent extends Event {
    private final InventoryView view;

    protected InventoryEvent() {
        this(null);
    }

    protected InventoryEvent(InventoryView view) {
        this.view = view;
    }

    /**
     * Returns the primary inventory involved in this event.
     */
    public Inventory getInventory() {
        return view == null ? null : view.getTopInventory();
    }

    /**
     * Returns the inventory view.
     */
    public InventoryView getView() {
        return view;
    }

    /**
     * Returns the inventories involved in this event.
     */
    public List<Inventory> getInventories() {
        if (view == null) {
            return List.of();
        }
        return List.of(view.getTopInventory(), view.getBottomInventory());
    }
}
