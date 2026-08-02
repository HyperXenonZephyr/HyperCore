package dev.hypercore.bukkit;

import dev.hypercore.plugin.PluginEventBus;
import dev.hypercore.plugin.PluginManager;

import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventException;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Bukkit {@link org.bukkit.plugin.PluginManager} implementation backed by the
 * HyperCore {@link PluginManager}. It exposes every Bukkit-shaped plugin for
 * cross-plugin lookup and routes Bukkit events through the appropriate
 * {@link HandlerList} before bridging selected events back to HyperCore's
 * internal event bus.
 */
final class HyperCoreBukkitPluginManager implements org.bukkit.plugin.PluginManager {
    private final PluginManager plugins;

    HyperCoreBukkitPluginManager(PluginManager plugins) {
        this.plugins = Objects.requireNonNull(plugins, "plugins");
    }

    @Override
    public void registerEvents(Listener listener, Plugin plugin) {
        Objects.requireNonNull(listener, "listener");
        Objects.requireNonNull(plugin, "plugin");
        for (Method method : listener.getClass().getDeclaredMethods()) {
            EventHandler annotation = method.getAnnotation(EventHandler.class);
            if (annotation == null) {
                continue;
            }
            if (method.getParameterCount() != 1 || !Event.class.isAssignableFrom(method.getParameterTypes()[0])) {
                throw new IllegalArgumentException(
                    "Method " + method + " is annotated with @EventHandler but does not declare a single Event parameter"
                );
            }
            method.setAccessible(true);
            @SuppressWarnings("unchecked")
            Class<? extends Event> eventClass = (Class<? extends Event>) method.getParameterTypes()[0];
            EventExecutor executor = (owner, event) -> {
                try {
                    method.invoke(owner, event);
                } catch (InvocationTargetException error) {
                    Throwable cause = error.getCause();
                    throw new EventException(cause == null ? error : cause);
                } catch (IllegalAccessException error) {
                    throw new EventException(error);
                }
            };
            registerEvent(eventClass, listener, annotation.priority(), executor, plugin, annotation.ignoreCancelled());
        }
    }

    @Override
    public void registerEvent(
        Class<? extends Event> event,
        Listener listener,
        EventPriority priority,
        EventExecutor executor,
        Plugin plugin
    ) {
        registerEvent(event, listener, priority, executor, plugin, false);
    }

    @Override
    public void registerEvent(
        Class<? extends Event> event,
        Listener listener,
        EventPriority priority,
        EventExecutor executor,
        Plugin plugin,
        boolean ignoreCancelled
    ) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(listener, "listener");
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(plugin, "plugin");
        RegisteredListener registered = new RegisteredListener(listener, executor, priority, plugin, ignoreCancelled);
        HandlerList handlerList = getEventHandlers(event);
        handlerList.register(registered);
    }

    @Override
    public Plugin[] getPlugins() {
        List<Plugin> bukkitPlugins = new ArrayList<>();
        for (dev.hypercore.plugin.PluginManager.PluginContainer container : plugins.getPluginContainers()) {
            if (container.plugin() instanceof BukkitPluginAdapter adapter) {
                bukkitPlugins.add(adapter.plugin());
            }
        }
        return bukkitPlugins.toArray(new Plugin[0]);
    }

    @Override
    public Plugin getPlugin(String name) {
        Objects.requireNonNull(name, "name");
        dev.hypercore.plugin.PluginManager.PluginContainer container = plugins.getPlugin(name);
        if (container != null && container.plugin() instanceof BukkitPluginAdapter adapter) {
            return adapter.plugin();
        }
        return null;
    }

    @Override
    public boolean isPluginEnabled(String name) {
        Plugin plugin = getPlugin(name);
        return plugin != null && plugin.isEnabled();
    }

    @Override
    public boolean isPluginEnabled(Plugin plugin) {
        return plugin != null && plugin.isEnabled();
    }

    @Override
    public void addPermission(org.bukkit.permissions.Permission permission) {
        Objects.requireNonNull(permission, "permission");
        plugins.permissions().register("bukkit", permission);
    }

    @Override
    public void removePermission(org.bukkit.permissions.Permission permission) {
        Objects.requireNonNull(permission, "permission");
        removePermission(permission.getName());
    }

    @Override
    public void removePermission(String name) {
        Objects.requireNonNull(name, "name");
        plugins.permissions().unregisterPlugin(name);
    }

    @Override
    public org.bukkit.permissions.Permission getPermission(String name) {
        Objects.requireNonNull(name, "name");
        return plugins.permissions().getPermission(name);
    }

    @Override
    public void callEvent(Event event) {
        Objects.requireNonNull(event, "event");
        HandlerList handlerList = event.getHandlers();
        boolean cancelledBefore = event instanceof org.bukkit.event.Cancellable cancellable && cancellable.isCancelled();

        for (RegisteredListener registration : handlerList.getRegisteredListeners()) {
            if (registration.isIgnoreCancelled()
                && event instanceof org.bukkit.event.Cancellable cancellable
                && cancellable.isCancelled()) {
                continue;
            }
            try {
                registration.callEvent(event);
            } catch (org.bukkit.plugin.EventException error) {
                throw new RuntimeException("Event " + event.getEventName() + " threw an exception", error.getCause());
            }
        }

        // Bridge selected Bukkit events back to the HyperCore internal bus.
        BukkitEventBridge.bridgeToHyperCore(event, plugins.events());

        // If the HyperCore bus cancelled the internal counterpart, propagate the
        // cancellation back to the Bukkit event when possible.
        if (event instanceof org.bukkit.event.Cancellable cancellable
            && !cancelledBefore
            && cancellable.isCancelled()) {
            // Bukkit listeners already ran; the cancelled state is visible to
            // subsequent observers.
        }
    }

    /**
     * Unregisters all listeners owned by the given plugin from every handler list.
     */
    void unregisterPlugin(Plugin plugin) {
        HandlerList.unregisterAll(plugin);
    }

    private static HandlerList getEventHandlers(Class<? extends Event> eventClass) {
        try {
            Method method = eventClass.getMethod("getHandlerList");
            return (HandlerList) method.invoke(null);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException error) {
            throw new IllegalArgumentException(
                "Event class " + eventClass.getName() + " does not expose a static getHandlerList() method",
                error
            );
        }
    }
}
