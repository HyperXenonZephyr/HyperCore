package dev.hypercore.plugin;

import dev.hypercore.plugin.PluginCommandRegistry.CommandDefinition;
import dev.hypercore.plugin.PluginEventBus.EventPriority;
import dev.hypercore.plugin.PluginEventBus.PluginEvent;
import dev.hypercore.plugin.PluginEventBus.Subscription;
import dev.hypercore.plugin.PluginPermissionService.PermissionDefault;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class PluginContext {
    private final PluginDescriptor descriptor;
    private final PluginCommandRegistry commands;
    private final PluginPermissionService permissions;
    private final PluginEventBus events;
    private final PluginScheduler scheduler;
    private final ClassLoader callbackClassLoader;

    PluginContext(
        PluginDescriptor descriptor,
        PluginCommandRegistry commands,
        PluginPermissionService permissions,
        PluginEventBus events,
        PluginScheduler scheduler,
        ClassLoader callbackClassLoader
    ) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.commands = Objects.requireNonNull(commands, "commands");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.events = Objects.requireNonNull(events, "events");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.callbackClassLoader = callbackClassLoader;
    }

    public PluginDescriptor descriptor() {
        return descriptor;
    }

    public void registerCommand(CommandDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        commands.register(descriptor.id(), new CommandDefinition(
            definition.name(),
            definition.aliases(),
            definition.permission(),
            definition.description(),
            definition.usage(),
            (sender, label, arguments) -> callWithContext(() ->
                definition.executor().execute(sender, label, arguments))
        ));
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
        Objects.requireNonNull(listener, "listener");
        return events.register(
            descriptor.id(),
            eventType,
            priority,
            ignoreCancelled,
            event -> runWithContext(() -> listener.accept(event))
        );
    }

    public PluginEventBus.DispatchResult postEvent(PluginEvent event) {
        return events.post(event);
    }

    public PluginScheduler.TaskHandle runTask(Runnable action) {
        return scheduler.runTask(descriptor.id(), contextual(action));
    }

    public PluginScheduler.TaskHandle runTaskLater(long delayTicks, Runnable action) {
        return scheduler.runTaskLater(descriptor.id(), delayTicks, contextual(action));
    }

    public PluginScheduler.TaskHandle runTaskTimer(long delayTicks, long periodTicks, Runnable action) {
        return scheduler.runTaskTimer(descriptor.id(), delayTicks, periodTicks, contextual(action));
    }

    public PluginScheduler.TaskHandle runTaskAsync(Runnable action) {
        return scheduler.runTaskAsync(descriptor.id(), contextual(action));
    }

    public PluginScheduler.TaskHandle runTaskLaterAsync(long delayTicks, Runnable action) {
        return scheduler.runTaskLaterAsync(descriptor.id(), delayTicks, contextual(action));
    }

    public PluginScheduler.TaskHandle runTaskTimerAsync(long delayTicks, long periodTicks, Runnable action) {
        return scheduler.runTaskTimerAsync(descriptor.id(), delayTicks, periodTicks, contextual(action));
    }

    void runWithContext(Runnable action) {
        callWithContext(() -> {
            action.run();
            return null;
        });
    }

    private Runnable contextual(Runnable action) {
        Objects.requireNonNull(action, "action");
        return () -> runWithContext(action);
    }

    private <T> T callWithContext(Supplier<T> action) {
        if (callbackClassLoader == null) {
            return action.get();
        }
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(callbackClassLoader);
        try {
            return action.get();
        } finally {
            thread.setContextClassLoader(previous);
        }
    }
}
