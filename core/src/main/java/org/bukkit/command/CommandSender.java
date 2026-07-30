package org.bukkit.command;

import org.bukkit.permissions.Permissible;

/**
 * Minimal stub of the Bukkit {@code CommandSender} interface.
 */
public interface CommandSender extends Permissible {
    void sendMessage(String message);

    void sendMessage(String[] messages);

    String getName();
}
