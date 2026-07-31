package dev.hypercore.plugin.lifecycle;

import dev.hypercore.plugin.PluginEventBus;

import java.util.Objects;

/**
 * Internal HyperCore event fired when a player issues a command before it is
 * parsed.
 */
public final class PlayerCommandPreprocessEvent implements PluginEventBus.CancellableEvent {
    private final String playerName;
    private final String message;
    private boolean cancelled;

    public PlayerCommandPreprocessEvent(String playerName, String message) {
        this.playerName = Objects.requireNonNull(playerName, "playerName");
        this.message = Objects.requireNonNull(message, "message");
    }

    public String playerName() {
        return playerName;
    }

    public String message() {
        return message;
    }

    @Override
    public boolean cancelled() {
        return cancelled;
    }

    @Override
    public void cancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public void cancel() {
        this.cancelled = true;
    }
}
