package dev.hypercore.bukkit;

import dev.hypercore.plugin.PluginContext;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

/**
 * Minimal {@link PluginManager} stub backed by the per-plugin
 * {@link PluginContext}. Only the current plugin is visible; lookups for other
 * plugins return {@code null}/{@code false}.
 */
final class HyperCoreBukkitPluginManager implements PluginManager {
    private final PluginContext context;

    HyperCoreBukkitPluginManager(PluginContext context) {
        this.context = context;
    }

    @Override
    public Plugin getPlugin(String name) {
        // The minimal shim only knows about the current plugin, and the
        // JavaPlugin reference is held by BukkitPluginAdapter, not here.
        return null;
    }

    @Override
    public boolean isPluginEnabled(String name) {
        return false;
    }

    @Override
    public boolean isPluginEnabled(Plugin plugin) {
        return plugin != null && plugin.isEnabled();
    }
}
