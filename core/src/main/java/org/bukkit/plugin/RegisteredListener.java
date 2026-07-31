package org.bukkit.plugin;

import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Objects;

/**
 * A registered event listener. Combines the listener owner, the executor that
 * invokes it, its priority, the plugin that owns it, and whether it ignores
 * cancelled events.
 */
public final class RegisteredListener {
    private final Listener listener;
    private final EventPriority priority;
    private final Plugin plugin;
    private final EventExecutor executor;
    private final boolean ignoreCancelled;

    public RegisteredListener(
        Listener listener,
        EventExecutor executor,
        EventPriority priority,
        Plugin plugin,
        boolean ignoreCancelled
    ) {
        this.listener = Objects.requireNonNull(listener, "listener");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.priority = Objects.requireNonNull(priority, "priority");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.ignoreCancelled = ignoreCancelled;
    }

    public Listener getListener() {
        return listener;
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public EventPriority getPriority() {
        return priority;
    }

    public boolean isIgnoreCancelled() {
        return ignoreCancelled;
    }

    /**
     * Invokes this listener for the given event.
     */
    public void callEvent(Event event) throws EventException {
        Objects.requireNonNull(event, "event");
        executor.execute(listener, event);
    }
}
