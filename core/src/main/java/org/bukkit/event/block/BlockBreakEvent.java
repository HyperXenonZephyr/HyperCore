package org.bukkit.event.block;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * Bukkit event fired when a block is broken (replaced by air).
 *
 * <p>This is a hand-written event with real fields so that the HyperCore event
 * bridge can populate it and plugins can cancel the break.
 */
public class BlockBreakEvent extends BlockEvent implements Cancellable {
    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Player player;
    private final Material type;
    private boolean cancelled;

    public BlockBreakEvent(Block block, Player player, Material type) {
        super(block);
        this.player = player;
        this.type = type;
    }

    /**
     * Returns the player breaking the block, or {@code null}.
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
