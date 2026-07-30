package org.bukkit.plugin;

/**
 * Minimal stub of the Bukkit {@code PluginManager} interface.
 */
public interface PluginManager {
    Plugin getPlugin(String name);

    boolean isPluginEnabled(String name);

    boolean isPluginEnabled(Plugin plugin);
}
