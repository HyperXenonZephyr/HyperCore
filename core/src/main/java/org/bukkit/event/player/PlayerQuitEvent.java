package org.bukkit.event.player;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * Bukkit event fired when a player leaves the server.
 *
 * <p>This is a hand-written event with real fields so that the HyperCore event
 * bridge can populate it and plugins can modify the quit message.
 */
public class PlayerQuitEvent extends PlayerEvent {
    private static final HandlerList HANDLER_LIST = new HandlerList();

    private String quitMessage;

    public PlayerQuitEvent(Player player, String quitMessage) {
        super(player);
        this.quitMessage = quitMessage;
    }

    /**
     * Returns the quit message that will be broadcast, or {@code null}.
     */
    public String getQuitMessage() {
        return quitMessage;
    }

    /**
     * Sets the quit message that will be broadcast.
     */
    public void setQuitMessage(String quitMessage) {
        this.quitMessage = quitMessage;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}
