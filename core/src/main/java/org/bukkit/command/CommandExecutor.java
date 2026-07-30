package org.bukkit.command;

/**
 * Minimal stub of the Bukkit {@code CommandExecutor} interface. A plugin sets
 * an executor on a {@link PluginCommand} to handle command dispatch.
 */
@FunctionalInterface
public interface CommandExecutor {
    boolean onCommand(CommandSender sender, Command command, String label, String[] args);
}
