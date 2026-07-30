package org.bukkit.command;

import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Minimal stub of the Bukkit {@code PluginCommand} class. A plugin obtains one
 * via {@link org.bukkit.plugin.java.JavaPlugin#getCommand(String)} and calls
 * {@link #setExecutor(CommandExecutor)} to handle dispatch.
 */
public final class PluginCommand extends Command {
    private final Plugin owningPlugin;
    private CommandExecutor executor;
    private TabCompleter tabCompleter;

    public PluginCommand(String name, Plugin owner) {
        super(name);
        this.owningPlugin = Objects.requireNonNull(owner, "owner");
    }

    public Plugin getPlugin() {
        return owningPlugin;
    }

    public CommandExecutor getExecutor() {
        return executor;
    }

    public void setExecutor(CommandExecutor executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public TabCompleter getTabCompleter() {
        return tabCompleter;
    }

    public void setTabCompleter(TabCompleter completer) {
        this.tabCompleter = completer;
    }

    /**
     * Dispatches the command to the registered executor. Returns {@code false}
     * if no executor is set.
     */
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (executor == null) {
            return false;
        }
        return executor.onCommand(sender, this, label, args);
    }

    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (tabCompleter == null) {
            return new ArrayList<>();
        }
        return tabCompleter.onTabComplete(sender, this, alias, args);
    }
}
