package dev.hypercore.bukkit;

import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

import java.util.Objects;

/**
 * Simple {@link InventoryView} implementation used when HyperCore opens a
 * loader-backed inventory for a player.
 *
 * <p>This view records the top inventory and the viewer. It does not itself
 * manage packet-based window state; that is handled by the loader-specific
 * adapter that opened the inventory.
 */
final class HyperCoreInventoryView implements InventoryView {
    private final HumanEntity player;
    private final Inventory topInventory;
    private final String title;

    HyperCoreInventoryView(HumanEntity player, Inventory topInventory, String title) {
        this.player = Objects.requireNonNull(player, "player");
        this.topInventory = Objects.requireNonNull(topInventory, "topInventory");
        this.title = title == null ? "Inventory" : title;
    }

    @Override
    public Inventory getTopInventory() {
        return topInventory;
    }

    @Override
    public Inventory getBottomInventory() {
        return player.getInventory();
    }

    @Override
    public HumanEntity getPlayer() {
        return player;
    }

    @Override
    public String getTitle() {
        return title;
    }
}
