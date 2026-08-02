package org.bukkit.event.player;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * Bukkit event fired when a player is logging in.
 *
 * <p>This is a hand-written event with real fields so that the HyperCore event
 * bridge can populate it and plugins can cancel the login.
 */
public class PlayerLoginEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLER_LIST = new HandlerList();

    private String kickMessage;
    private boolean cancelled;

    public PlayerLoginEvent(Player player) {
        super(player);
    }

    /**
     * Returns the message shown to the player if the login is cancelled.
     */
    public String getKickMessage() {
        return kickMessage;
    }

    /**
     * Sets the message shown to the player if the login is cancelled.
     */
    public void setKickMessage(String kickMessage) {
        this.kickMessage = kickMessage;
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
