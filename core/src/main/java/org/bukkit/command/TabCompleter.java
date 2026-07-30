package org.bukkit.command;

import java.util.List;

/**
 * Minimal stub of the Bukkit {@code TabCompleter} interface.
 */
@FunctionalInterface
public interface TabCompleter {
    List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args);
}
