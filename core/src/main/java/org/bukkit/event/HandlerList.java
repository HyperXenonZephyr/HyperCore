package org.bukkit.event;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;

/**
 * Stores the registered listeners for a particular event type and dispatches
 * events to them in priority order.
 *
 * <p>Each concrete event class owns one static {@link HandlerList} instance.
 * The list is rebuilt ("baked") when registrations change so that iteration
 * during dispatch is fast and allocation-free.
 */
public final class HandlerList {
    private volatile List<RegisteredListener> bakedListeners = List.of();
    private final List<RegisteredListener> handlers = new ArrayList<>();
    private boolean needsBake = true;

    /**
     * Creates a new handler list and registers it for global operations.
     */
    public HandlerList() {
        HandlerListRegistration.register(this);
    }

    /**
     * Registers a listener for this event type.
     */
    public synchronized void register(RegisteredListener listener) {
        Objects.requireNonNull(listener, "listener");
        handlers.add(listener);
        needsBake = true;
    }

    /**
     * Registers a collection of listeners for this event type.
     */
    public synchronized void registerAll(Collection<RegisteredListener> listeners) {
        Objects.requireNonNull(listeners, "listeners");
        for (RegisteredListener listener : listeners) {
            register(listener);
        }
    }

    /**
     * Unregisters all listeners owned by the given plugin.
     */
    public synchronized void unregister(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        handlers.removeIf(listener -> listener.getPlugin().equals(plugin));
        needsBake = true;
    }

    /**
     * Unregisters the given listener owner object.
     */
    public synchronized void unregister(Listener listener) {
        Objects.requireNonNull(listener, "listener");
        handlers.removeIf(registered -> registered.getListener().equals(listener));
        needsBake = true;
    }

    /**
     * Unregisters a specific registration.
     */
    public synchronized void unregister(RegisteredListener listener) {
        Objects.requireNonNull(listener, "listener");
        handlers.remove(listener);
        needsBake = true;
    }

    /**
     * Returns the baked listener list, ordered from {@link EventPriority#LOWEST}
     * to {@link EventPriority#MONITOR}.
     */
    public List<RegisteredListener> getRegisteredListeners() {
        if (needsBake) {
            bake();
        }
        return bakedListeners;
    }

    /**
     * Returns all baked listeners across every registered handler list. Used by
     * the plugin manager for global operations such as unregistering a plugin
     * from every event.
     */
    public static List<RegisteredListener> getRegisteredListeners(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        List<RegisteredListener> owned = new ArrayList<>();
        for (HandlerList list : HandlerListRegistration.allLists()) {
            for (RegisteredListener listener : list.getRegisteredListeners()) {
                if (listener.getPlugin().equals(plugin)) {
                    owned.add(listener);
                }
            }
        }
        return owned;
    }

    /**
     * Unregisters all listeners owned by the plugin from every handler list.
     */
    public static void unregisterAll(Plugin plugin) {
        for (HandlerList list : HandlerListRegistration.allLists()) {
            list.unregister(plugin);
        }
    }

    /**
     * Unregisters the given listener owner from every handler list.
     */
    public static void unregisterAll(Listener listener) {
        for (HandlerList list : HandlerListRegistration.allLists()) {
            list.unregister(listener);
        }
    }

    /**
     * Unregisters every listener from every handler list.
     */
    public static void unregisterAll() {
        for (HandlerList list : HandlerListRegistration.allLists()) {
            synchronized (list) {
                list.handlers.clear();
                list.needsBake = true;
            }
        }
    }

    private synchronized void bake() {
        if (!needsBake) {
            return;
        }
        Map<EventPriority, List<RegisteredListener>> byPriority = new EnumMap<>(EventPriority.class);
        for (EventPriority priority : EventPriority.values()) {
            byPriority.put(priority, new ArrayList<>());
        }
        for (RegisteredListener listener : handlers) {
            byPriority.get(listener.getPriority()).add(listener);
        }
        List<RegisteredListener> baked = new ArrayList<>(handlers.size());
        for (EventPriority priority : EventPriority.values()) {
            baked.addAll(byPriority.get(priority));
        }
        bakedListeners = List.copyOf(baked);
        needsBake = false;
    }

    /**
     * Tracks every {@link HandlerList} instance so that global unregister
     * operations can reach them. Each event class registers its static handler
     * list here the first time it is requested.
     */
    static final class HandlerListRegistration {
        private static final List<HandlerList> ALL_LISTS = new ArrayList<>();

        private HandlerListRegistration() {
        }

        static synchronized void register(HandlerList list) {
            if (!ALL_LISTS.contains(list)) {
                ALL_LISTS.add(list);
            }
        }

        static synchronized List<HandlerList> allLists() {
            return List.copyOf(ALL_LISTS);
        }
    }
}
