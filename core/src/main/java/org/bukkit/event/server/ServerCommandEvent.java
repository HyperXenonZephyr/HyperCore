package org.bukkit.event.server;

import org.bukkit.command.CommandSender;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

import java.util.Objects;

/**
 * Called when the server console or RCON issues a command.
 */
public class ServerCommandEvent extends ServerEvent implements Cancellable {
    private static final HandlerList HANDLER_LIST = new HandlerList();
    private final CommandSender sender;
    private String command;
    private boolean cancelled;

    protected ServerCommandEvent() {
        this(null, "");
    }

    public ServerCommandEvent(CommandSender sender, String command) {
        this.sender = sender;
        this.command = command == null ? "" : command;
    }

    /**
     * Returns the command sender.
     */
    public CommandSender getSender() {
        return sender;
    }

    /**
     * Returns the command string, including the leading slash.
     */
    public String getCommand() {
        return command;
    }

    /**
     * Sets the command string.
     */
    public void setCommand(String command) {
        this.command = Objects.requireNonNull(command, "command");
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
