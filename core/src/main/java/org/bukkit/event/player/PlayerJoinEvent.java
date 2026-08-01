package org.bukkit.event.player;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * Bukkit event fired when a player joins the server.
 *
 * <p>This is a hand-written event with real fields so that the HyperCore event
 * bridge can populate it and plugins can modify the join message.
 */
public class PlayerJoinEvent extends PlayerEvent {
    private static final HandlerList HANDLER_LIST = new HandlerList();

    private String joinMessage;

    public PlayerJoinEvent(Player player, String joinMessage) {
        super(player);
        this.joinMessage = joinMessage;
    }

    /**
     * Returns the join message that will be broadcast, or {@code null}.
     */
    public String getJoinMessage() {
        return joinMessage;
    }

    /**
     * Sets the join message that will be broadcast.
     */
    public void setJoinMessage(String joinMessage) {
        this.joinMessage = joinMessage;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}
