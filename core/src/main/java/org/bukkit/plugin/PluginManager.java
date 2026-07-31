package org.bukkit.plugin;

import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Bukkit {@code PluginManager} interface. Provides plugin lookup, event
 * registration, and event dispatch methods required by Bukkit plugins.
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
}
