package dev.hypercore.bridge.command;

import dev.hypercore.plugin.PluginCommandSender;

import java.util.Objects;

/**
 * A command source that represents the sender on the other host.
 *
 * <p>When the remote host executes a mirrored command, this sender is passed to
 * the local executor. Messages sent through {@link #sendMessage} are collected
 * and returned to the requesting host inside the {@code CommandExecuteResult}.
 * Operator state and permission overrides mirror the remote sender's.
 */
public final class RemoteCommandSender implements PluginCommandSender {
    private final String name;
    private final boolean operator;
    private final boolean console;
    private final StringBuilder messages = new StringBuilder();

    public RemoteCommandSender(String name, boolean operator, boolean console) {
        this.name = Objects.requireNonNullElse(name, "Remote");
        this.operator = operator;
        this.console = console;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean operator() {
        return operator;
    }

    /**
     * Returns whether the remote sender is the server console.
     */
    public boolean console() {
        return console;
    }

    @Override
    public void sendMessage(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        if (!messages.isEmpty()) {
            messages.append('\n');
        }
        messages.append(message);
    }

    /**
     * Returns the messages collected during execution and clears the buffer.
     */
    public String drainMessages() {
        String collected = messages.toString();
        messages.setLength(0);
        return collected;
    }
}
