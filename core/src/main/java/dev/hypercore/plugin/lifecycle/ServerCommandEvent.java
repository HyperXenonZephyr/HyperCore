package dev.hypercore.plugin.lifecycle;

import dev.hypercore.plugin.PluginEventBus;

import java.util.Objects;

/**
 * Internal HyperCore event fired when the server console executes a command.
 */
public record ServerCommandEvent(String senderName, String command) implements PluginEventBus.PluginEvent {

    public ServerCommandEvent {
        Objects.requireNonNull(senderName, "senderName");
        Objects.requireNonNull(command, "command");
    }
}
