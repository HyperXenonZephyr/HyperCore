package dev.hypercore.world.event;

import dev.hypercore.plugin.PluginEventBus;

import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.InventoryView;

import java.util.Objects;

/**
 * Internal event fired when a viewer's inventory view is about to close.
 *
 * <p>If cancelled, the inventory is not closed. Bukkit plugins observe the same
 * cancellation through {@link org.bukkit.event.inventory.InventoryCloseEvent}.
 */
public final class InventoryCloseEvent implements PluginEventBus.CancellableEvent {
    private final InventoryView view;
    private final HumanEntity player;
    private boolean cancelled;

    public InventoryCloseEvent(InventoryView view, HumanEntity player) {
        this.view = Objects.requireNonNull(view, "view");
        this.player = player;
    }

    public InventoryView getView() {
        return view;
    }

    public HumanEntity getPlayer() {
        return player;
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