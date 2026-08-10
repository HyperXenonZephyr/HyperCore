package dev.hypercore.world.event;

import dev.hypercore.plugin.PluginEventBus;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Objects;

/**
 * Internal event fired when an entity begins damaging (mining) a block.
 *
 * <p>If cancelled, the block cannot be damaged. Bukkit plugins observe the same
 * cancellation through {@link org.bukkit.event.block.BlockDamageEvent}.
 */
public final class BlockDamageEvent implements PluginEventBus.CancellableEvent {
    private final Block block;
    private final Player player;
    private final Material type;
    private boolean cancelled;

    public BlockDamageEvent(Block block, Player player, Material type) {
        this.block = Objects.requireNonNull(block, "block");
        this.player = player;
        this.type = Objects.requireNonNull(type, "type");
    }

    /**
     * Returns the block being damaged.
     */
    public Block getBlock() {
        return block;
    }

    /**
     * Returns the player damaging the block, or {@code null} if the damage was
     * not initiated by a player.
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * Returns the material of the block being damaged.
     */
    public Material getType() {
        return type;
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