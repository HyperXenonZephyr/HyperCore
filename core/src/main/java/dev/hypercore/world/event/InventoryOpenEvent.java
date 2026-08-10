package dev.hypercore.world.event;

import dev.hypercore.plugin.PluginEventBus;

import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.InventoryView;

import java.util.Objects;

/**
 * Internal event fired when an inventory is about to be opened for a viewer.
 *
 * <p>If cancelled, the inventory is not opened. Bukkit plugins observe the same
 * cancellation through {@link org.bukkit.event.inventory.InventoryOpenEvent}.
 */
public final class InventoryOpenEvent implements PluginEventBus.CancellableEvent {
    private final InventoryView view;
    private final HumanEntity player;
    private boolean cancelled;

    public InventoryOpenEvent(InventoryView view, HumanEntity player) {
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