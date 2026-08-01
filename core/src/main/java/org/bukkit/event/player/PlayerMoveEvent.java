package org.bukkit.event.player;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * Bukkit event fired when a player moves or is teleported.
 *
 * <p>This is a hand-written event with real fields so that the HyperCore event
 * bridge can populate it and plugins can cancel the movement.
 */
public class PlayerMoveEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Location from;
    private Location to;
    private boolean cancelled;

    public PlayerMoveEvent(Player player, Location from, Location to) {
        super(player);
        this.from = from;
        this.to = to;
    }

    /**
     * Protected no-argument constructor used by generated subclasses.
     */
    protected PlayerMoveEvent() {
        super(null);
        this.from = null;
        this.to = null;
    }

    /**
     * Returns the player's current location.
     */
    public Location getFrom() {
        return from;
    }

    /**
     * Returns the destination location.
     */
    public Location getTo() {
        return to;
    }

    /**
     * Sets the destination location.
     */
    public void setTo(Location to) {
        this.to = to;
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
