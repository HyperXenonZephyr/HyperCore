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
    private final PluginScheduler scheduler;

    PluginContext(
        PluginDescriptor descriptor,
        PluginCommandRegistry commands,
        PluginPermissionService permissions,
        PluginEventBus events,
        PluginScheduler scheduler
    ) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.commands = Objects.requireNonNull(commands, "commands");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.events = Objects.requireNonNull(events, "events");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
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

    public PluginScheduler.TaskHandle runTask(Runnable action) {
        return scheduler.runTask(descriptor.id(), action);
    }

    public PluginScheduler.TaskHandle runTaskLater(long delayTicks, Runnable action) {
        return scheduler.runTaskLater(descriptor.id(), delayTicks, action);
    }

    public PluginScheduler.TaskHandle runTaskTimer(long delayTicks, long periodTicks, Runnable action) {
        return scheduler.runTaskTimer(descriptor.id(), delayTicks, periodTicks, action);
    }

    public PluginScheduler.TaskHandle runTaskAsync(Runnable action) {
        return scheduler.runTaskAsync(descriptor.id(), action);
    }

    public PluginScheduler.TaskHandle runTaskLaterAsync(long delayTicks, Runnable action) {
        return scheduler.runTaskLaterAsync(descriptor.id(), delayTicks, action);
    }

    public PluginScheduler.TaskHandle runTaskTimerAsync(long delayTicks, long periodTicks, Runnable action) {
        return scheduler.runTaskTimerAsync(descriptor.id(), delayTicks, periodTicks, action);
    }
}
