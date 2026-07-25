package dev.hypercore.plugin;

import java.util.Optional;

public interface PluginCommandSender {
    String name();

    boolean operator();

    default Optional<Boolean> permissionOverride(String permission) {
        return Optional.empty();
    }

    void sendMessage(String message);
}
