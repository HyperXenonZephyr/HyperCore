package dev.hypercore.world.event;

import dev.hypercore.plugin.PluginEventBus;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Objects;

/**
 * Internal HyperCore event fired when a player interacts with a block or item.
 */
public final class PlayerInteractEvent implements PluginEventBus.CancellableEvent {
    private final Player player;
    private final Block block;
    private final Material material;
    private final Location location;
    private boolean cancelled;

    public PlayerInteractEvent(Player player, Block block, Material material, Location location) {
        this.player = player;
        this.block = block;
        this.material = material;
        this.location = location;
    }

    public Player getPlayer() {
        return player;
    }

    public Block getBlock() {
        return block;
    }

    public Material getMaterial() {
        return material;
    }

    public Location getLocation() {
        return location;
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
