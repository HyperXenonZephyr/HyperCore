package dev.hypercore.plugin;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class PluginEventBus {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final AtomicLong nextSequence = new AtomicLong();
    private final CopyOnWriteArrayList<RegisteredListener<?>> listeners = new CopyOnWriteArrayList<>();

    public <E extends PluginEvent> Subscription register(
        String pluginId,
        Class<E> eventType,
        EventPriority priority,
        boolean ignoreCancelled,
        Consumer<E> listener
    ) {
        RegisteredListener<E> registration = new RegisteredListener<>(
            PluginPermissionService.normalizePluginId(pluginId),
            Objects.requireNonNull(eventType, "eventType"),
            Objects.requireNonNull(priority, "priority"),
            ignoreCancelled,
            nextSequence.getAndIncrement(),
            Objects.requireNonNull(listener, "listener")
        );
        listeners.add(registration);
        return () -> listeners.remove(registration);
    }

    public DispatchResult post(PluginEvent event) {
        Objects.requireNonNull(event, "event");
        List<RegisteredListener<?>> matchingListeners = listeners.stream()
            .filter(listener -> listener.eventType().isInstance(event))
            .sorted(Comparator
                .comparing((RegisteredListener<?> listener) -> listener.priority().ordinal())
                .thenComparingLong(RegisteredListener::sequence))
            .toList();

        int invoked = 0;
        int failures = 0;
        for (RegisteredListener<?> listener : matchingListeners) {
            if (listener.ignoreCancelled() && event instanceof CancellableEvent cancellable && cancellable.cancelled()) {
                continue;
            }
            try {
                listener.invoke(event);
                invoked++;
            } catch (RuntimeException error) {
                failures++;
                LOGGER.error(
                    "Plugin {} failed while handling {}",
                    listener.pluginId(),
                    event.getClass().getName(),
                    error
                );
            }
        }
        return new DispatchResult(invoked, failures);
    }

    public int registeredListeners() {
        return listeners.size();
    }

    public void unregisterPlugin(String pluginId) {
        String normalizedPluginId = PluginPermissionService.normalizePluginId(pluginId);
        listeners.removeIf(listener -> listener.pluginId().equals(normalizedPluginId));
    }

    public interface PluginEvent {
    }

    public interface CancellableEvent extends PluginEvent {
        boolean cancelled();

        void cancelled(boolean cancelled);
    }

    public enum EventPriority {
        LOWEST,
        LOW,
        NORMAL,
        HIGH,
        HIGHEST,
        MONITOR
    }

    @FunctionalInterface
    public interface Subscription extends AutoCloseable {
        @Override
        void close();
    }

    public record DispatchResult(int invokedListeners, int failedListeners) {
    }

    private record RegisteredListener<E extends PluginEvent>(
        String pluginId,
        Class<E> eventType,
        EventPriority priority,
        boolean ignoreCancelled,
        long sequence,
        Consumer<E> listener
    ) {
        private void invoke(PluginEvent event) {
            listener.accept(eventType.cast(event));
        }
    }
}
