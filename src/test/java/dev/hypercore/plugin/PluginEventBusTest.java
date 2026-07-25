package dev.hypercore.plugin;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static dev.hypercore.plugin.PluginEventBus.EventPriority.HIGH;
import static dev.hypercore.plugin.PluginEventBus.EventPriority.LOW;
import static dev.hypercore.plugin.PluginEventBus.EventPriority.NORMAL;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PluginEventBusTest {
    @Test
    void dispatchesByPriorityAndSkipsCancelledListenersWhenRequested() {
        PluginEventBus bus = new PluginEventBus();
        List<String> order = new ArrayList<>();
        bus.register("low", TestEvent.class, LOW, false, event -> order.add("low"));
        bus.register("cancel", TestEvent.class, NORMAL, false, event -> event.cancelled(true));
        bus.register("ignored", TestEvent.class, HIGH, true, event -> order.add("ignored"));
        bus.register("monitor", TestEvent.class, HIGH, false, event -> order.add("monitor"));

        PluginEventBus.DispatchResult result = bus.post(new TestEvent());

        assertEquals(List.of("low", "monitor"), order);
        assertEquals(3, result.invokedListeners());
    }

    @Test
    void subscriptionAndPluginCleanupAreIdempotent() {
        PluginEventBus bus = new PluginEventBus();
        PluginEventBus.Subscription subscription = bus.register(
            "one", TestEvent.class, NORMAL, false, event -> { }
        );
        bus.register("two", TestEvent.class, NORMAL, false, event -> { });
        assertEquals(2, bus.registeredListeners());

        subscription.close();
        subscription.close();
        bus.unregisterPlugin("two");
        bus.unregisterPlugin("two");

        assertEquals(0, bus.registeredListeners());
    }

    private static final class TestEvent implements PluginEventBus.CancellableEvent {
        private boolean cancelled;

        @Override
        public boolean cancelled() {
            return cancelled;
        }

        @Override
        public void cancelled(boolean cancelled) {
            this.cancelled = cancelled;
        }
    }
}
