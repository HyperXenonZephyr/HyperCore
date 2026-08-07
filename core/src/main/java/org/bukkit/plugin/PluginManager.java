package org.bukkit.plugin;

import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.permissions.Permission;

/**
 * Bukkit {@code PluginManager} interface. Provides plugin lookup, event
 * registration, permission registration, and event dispatch methods required
 * by Bukkit plugins.
 */
public interface PluginManager {

    /**
     * Registers all {@code @EventHandler} methods in the given listener object
     * so that they receive events for their declared event type.
     */
    void registerEvents(Listener listener, Plugin plugin);

    /**
     * Registers a listener for a specific event type using a custom executor.
     */
    void registerEvent(
        Class<? extends Event> event,
        Listener listener,
        EventPriority priority,
        EventExecutor executor,
        Plugin plugin
    );

    /**
     * Registers a listener for a specific event type using a custom executor and
     * specifying whether cancelled events are skipped.
     */
    void registerEvent(
        Class<? extends Event> event,
        Listener listener,
        EventPriority priority,
        EventExecutor executor,
        Plugin plugin,
        boolean ignoreCancelled
    );

    /**
     * Returns all plugins currently known to this manager.
     */
    Plugin[] getPlugins();

    /**
     * Returns the plugin with the given name, or {@code null} if not found.
     */
    Plugin getPlugin(String name);

    /**
     * Returns {@code true} if a plugin with the given name is loaded and enabled.
     */
    boolean isPluginEnabled(String name);

    /**
     * Returns {@code true} if the given plugin is enabled.
     */
    boolean isPluginEnabled(Plugin plugin);

    /**
     * Dispatches the event to all registered Bukkit listeners and, when
     * applicable, to the HyperCore internal event bus.
     */
    void callEvent(Event event);

    /**
     * Registers a permission with the server.
     */
    void addPermission(Permission permission);

    /**
     * Removes a permission from the server.
     */
    void removePermission(Permission permission);

    /**
     * Removes a permission from the server by name.
     */
    void removePermission(String name);

    /**
     * Returns the permission with the given name, or {@code null} if not found.
     */
    Permission getPermission(String name);

    /**
     * Disables the given plugin. The plugin's onDisable callback is called and
     * its registrations are cleaned up.
     */
    void disablePlugin(Plugin plugin);

    /**
     * Attempts to enable the given plugin. HyperCore does not support plugin
     * reload; this method always throws {@link UnsupportedOperationException}.
     *
     * @throws UnsupportedOperationException always — reload is not supported
     */
    void enablePlugin(Plugin plugin);
}
