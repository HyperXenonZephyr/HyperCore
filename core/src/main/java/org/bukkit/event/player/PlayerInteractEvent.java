package org.bukkit.event.player;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * Bukkit event fired when a player interacts with a block or air.
 *
 * <p>This is a hand-written event with real fields so that the HyperCore event
 * bridge can populate it and plugins can cancel the interaction.
 */
public class PlayerInteractEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Block block;
    private final Material material;
    private final Location location;
    private boolean cancelled;

    public PlayerInteractEvent(Player player, Block block, Material material, Location location) {
        super(player);
        this.block = block;
        this.material = material;
        this.location = location;
    }

    /**
     * Returns the clicked block, or {@code null} if the player clicked air.
     */
    public Block getClickedBlock() {
        return block;
    }

    /**
     * Returns the material of the item in the player's hand, or {@code null}.
     */
    public Material getMaterial() {
        return material;
    }

    /**
     * Returns the interaction location.
     */
    public Location getLocation() {
        return location;
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
