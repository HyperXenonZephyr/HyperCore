package org.bukkit.plugin;

import org.bukkit.event.Event;
import org.bukkit.event.Listener;

/**
 * Functional interface that invokes a listener method for a specific event.
 * The plugin manager builds executors automatically from {@code @EventHandler}
 * methods, but plugins may also register custom executors.
 */
@FunctionalInterface
public interface EventExecutor {

    /**
     * Executes the listener logic for the given event.
     *
     * @param listener the listener instance
     * @param event    the event being dispatched
     * @throws EventException if execution fails
     */
    void execute(Listener listener, Event event) throws EventException;
}
