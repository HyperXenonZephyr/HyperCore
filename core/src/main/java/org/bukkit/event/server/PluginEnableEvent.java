package org.bukkit.event.server;

import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

/**
 * Called when a plugin is enabled.
 */
public class PluginEnableEvent extends ServerEvent {
    private static final HandlerList HANDLER_LIST = new HandlerList();
    private final Plugin plugin;

    public PluginEnableEvent(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    /**
     * Returns the plugin that was enabled.
     */
    public Plugin getPlugin() {
        return plugin;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}
