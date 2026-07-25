package dev.hypercore.plugin;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class PluginManager implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final PluginPermissionService permissions = new PluginPermissionService();
    private final PluginCommandRegistry commands = new PluginCommandRegistry(permissions);
    private final PluginEventBus events = new PluginEventBus();
    private final Map<String, PluginContainer> plugins = new LinkedHashMap<>();
    private boolean enabled;

    public synchronized void register(PluginDescriptor descriptor, HyperPlugin plugin) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(plugin, "plugin");
        if (plugins.containsKey(descriptor.id())) {
            throw new IllegalArgumentException("Plugin is already registered: " + descriptor.id());
        }

        PluginContainer container = new PluginContainer(
            descriptor,
            plugin,
            new PluginContext(descriptor, commands, permissions, events),
            PluginState.REGISTERED
        );
        plugins.put(descriptor.id(), container);
        load(container);
        if (enabled && container.state() == PluginState.LOADED) {
            enable(container);
        }
    }

    public synchronized void enableAll() {
        enabled = true;
        for (PluginContainer container : plugins.values()) {
            if (container.state() == PluginState.DISABLED) {
                load(container);
            }
            if (container.state() == PluginState.LOADED) {
                enable(container);
            }
        }
    }

    public synchronized void disableAll() {
        List<PluginContainer> reverseOrder = new ArrayList<>(plugins.values());
        for (int index = reverseOrder.size() - 1; index >= 0; index--) {
            disable(reverseOrder.get(index));
        }
        enabled = false;
    }

    public synchronized Status status() {
        long enabledPlugins = plugins.values().stream()
            .filter(plugin -> plugin.state() == PluginState.ENABLED)
            .count();
        long failedPlugins = plugins.values().stream()
            .filter(plugin -> plugin.state() == PluginState.FAILED)
            .count();
        return new Status(
            plugins.size(),
            (int) enabledPlugins,
            (int) failedPlugins,
            commands.registeredCommands(),
            permissions.registeredPermissions(),
            events.registeredListeners()
        );
    }

    public PluginCommandRegistry commands() {
        return commands;
    }

    public PluginPermissionService permissions() {
        return permissions;
    }

    public PluginEventBus events() {
        return events;
    }

    @Override
    public void close() {
        disableAll();
    }

    private void load(PluginContainer container) {
        try {
            container.plugin().onLoad(container.context());
            container.state(PluginState.LOADED);
        } catch (RuntimeException error) {
            fail(container, "load", error);
        }
    }

    private void enable(PluginContainer container) {
        try {
            container.plugin().onEnable(container.context());
            container.state(PluginState.ENABLED);
        } catch (RuntimeException error) {
            fail(container, "enable", error);
        }
    }

    private void disable(PluginContainer container) {
        if (container.state() == PluginState.ENABLED) {
            try {
                container.plugin().onDisable(container.context());
            } catch (RuntimeException error) {
                LOGGER.error("Plugin {} failed during disable", container.descriptor().id(), error);
            }
        }
        cleanup(container.descriptor().id());
        if (container.state() != PluginState.FAILED) {
            container.state(PluginState.DISABLED);
        }
    }

    private void fail(PluginContainer container, String phase, RuntimeException error) {
        LOGGER.error("Plugin {} failed during {}", container.descriptor().id(), phase, error);
        cleanup(container.descriptor().id());
        container.state(PluginState.FAILED);
    }

    private void cleanup(String pluginId) {
        commands.unregisterPlugin(pluginId);
        permissions.unregisterPlugin(pluginId);
        events.unregisterPlugin(pluginId);
    }

    private enum PluginState {
        REGISTERED,
        LOADED,
        ENABLED,
        DISABLED,
        FAILED
    }

    private static final class PluginContainer {
        private final PluginDescriptor descriptor;
        private final HyperPlugin plugin;
        private final PluginContext context;
        private PluginState state;

        private PluginContainer(
            PluginDescriptor descriptor,
            HyperPlugin plugin,
            PluginContext context,
            PluginState state
        ) {
            this.descriptor = descriptor;
            this.plugin = plugin;
            this.context = context;
            this.state = state;
        }

        private PluginDescriptor descriptor() {
            return descriptor;
        }

        private HyperPlugin plugin() {
            return plugin;
        }

        private PluginContext context() {
            return context;
        }

        private PluginState state() {
            return state;
        }

        private void state(PluginState state) {
            this.state = state;
        }
    }

    public record Status(
        int registeredPlugins,
        int enabledPlugins,
        int failedPlugins,
        int registeredCommands,
        int registeredPermissions,
        int registeredListeners
    ) {
    }
}
