package dev.hypercore.plugin;

import dev.hypercore.plugin.PluginCommandRegistry.CommandDefinition;
import dev.hypercore.plugin.PluginEventBus.EventPriority;
import dev.hypercore.plugin.PluginEventBus.PluginEvent;
import dev.hypercore.plugin.PluginEventBus.Subscription;
import dev.hypercore.plugin.PluginPermissionService.PermissionDefault;

import java.util.Objects;
import java.util.function.Consumer;

public final class PluginContext {
    private final PluginDescriptor descriptor;
    private final PluginCommandRegistry commands;
    private final PluginPermissionService permissions;
    private final PluginEventBus events;

    PluginContext(
        PluginDescriptor descriptor,
        PluginCommandRegistry commands,
        PluginPermissionService permissions,
        PluginEventBus events
    ) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.commands = Objects.requireNonNull(commands, "commands");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.events = Objects.requireNonNull(events, "events");
    }

    public PluginDescriptor descriptor() {
        return descriptor;
    }

    public void registerCommand(CommandDefinition definition) {
        commands.register(descriptor.id(), definition);
    }

    public void registerPermission(String node, String description, PermissionDefault defaultValue) {
        permissions.register(descriptor.id(), node, description, defaultValue);
    }

    public <E extends PluginEvent> Subscription registerListener(
        Class<E> eventType,
        EventPriority priority,
        boolean ignoreCancelled,
        Consumer<E> listener
    ) {
        return events.register(descriptor.id(), eventType, priority, ignoreCancelled, listener);
    }

    public PluginEventBus.DispatchResult postEvent(PluginEvent event) {
        return events.post(event);
    }
}
