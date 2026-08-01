package dev.hypercore.world.event;

import dev.hypercore.plugin.PluginEventBus;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Objects;

/**
 * Internal event fired before a block is broken (replaced by air).
 *
 * <p>If cancelled, the world mutation is aborted and Bukkit plugins observe the
 * same cancellation through {@link org.bukkit.event.block.BlockBreakEvent}.
 */
public final class BlockBreakEvent implements PluginEventBus.CancellableEvent {
    private final Block block;
    private final Player player;
    private final Material type;
    private boolean cancelled;

    public BlockBreakEvent(Block block, Player player, Material type) {
        this.block = block;
        this.player = player;
        this.type = Objects.requireNonNull(type, "type");
    }

    /**
     * Returns the block being broken.
     */
    public Block getBlock() {
        return block;
    }

    /**
     * Returns the player breaking the block, or {@code null} if the change was
     * not initiated by a player.
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * Returns the material of the block before it is broken.
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
