package org.bukkit.event.player;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

import java.util.Objects;

/**
 * Called early in the player command processing pipeline, before the command
 * has been parsed.
 */
public class PlayerCommandPreprocessEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLER_LIST = new HandlerList();
    private String message;
    private boolean cancelled;

    public PlayerCommandPreprocessEvent(Player player, String message) {
        super(player);
        this.message = Objects.requireNonNull(message, "message");
    }

    /**
     * Returns the raw chat/command message.
     */
    public String getMessage() {
        return message;
    }

    /**
     * Sets the raw chat/command message.
     */
    public void setMessage(String message) {
        this.message = Objects.requireNonNull(message, "message");
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
