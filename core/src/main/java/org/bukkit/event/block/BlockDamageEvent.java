package org.bukkit.event.block;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * Bukkit event fired when an entity begins damaging (mining) a block.
 *
 * <p>Cancelling this event prevents the block from being damaged. Plugins use
 * it to implement harder blocks, custom mining tools, or to block mining
 * outright.
 */
public class BlockDamageEvent extends BlockEvent implements Cancellable {
    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Player player;
    private long damage;
    private final Material type;
    private boolean cancelled;

    public BlockDamageEvent(Block block, Player player, Material type) {
        super(block);
        this.player = player;
        this.type = type;
    }

    /**
     * Returns the player damaging the block, or {@code null}.
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

    /**
     * Returns the current damage being done to the block.
     */
    public long getDamage() {
        return damage;
    }

    /**
     * Sets the amount of damage done to the block.
     */
    public void setDamage(long damage) {
        this.damage = damage;
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