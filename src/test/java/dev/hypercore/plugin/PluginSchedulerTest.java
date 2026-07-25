package dev.hypercore.plugin;

import dev.hypercore.concurrent.HyperCoreExecutor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginSchedulerTest {
    @Test
    void runsDelayedAndRepeatingSyncTasksOnTickCaller() {
        PluginScheduler scheduler = new PluginScheduler();
        List<String> calls = new ArrayList<>();

        scheduler.runTaskLater("demo", 2, () -> calls.add("later@" + scheduler.currentTick()));
        PluginScheduler.TaskHandle repeating = scheduler.runTaskTimer(
            "demo",
            1,
            2,
            () -> calls.add("repeat@" + scheduler.currentTick())
        );

        scheduler.tick();
        assertEquals(List.of("repeat@1"), calls);
        scheduler.tick();
        assertEquals(List.of("repeat@1", "later@2"), calls);
        scheduler.tick();
        assertEquals(List.of("repeat@1", "later@2", "repeat@3"), calls);

        assertTrue(repeating.cancel());
        assertFalse(repeating.active());
        scheduler.tick();
        assertEquals(3L, scheduler.status().completedTasks());
    }

    @Test
    void dispatchesAsyncTaskThroughTheBoundedExecutor() throws Exception {
        try (HyperCoreExecutor executor = HyperCoreExecutor.create(1, 4)) {
            PluginScheduler scheduler = new PluginScheduler();
            scheduler.attachExecutor(executor);
            CountDownLatch completed = new CountDownLatch(1);

            scheduler.runTaskAsync("demo", () -> completed.countDown());
            scheduler.tick();

            assertTrue(completed.await(5, TimeUnit.SECONDS));
            waitFor(() -> scheduler.status().completedTasks() == 1L);
            assertEquals(1L, executor.submittedTasks());
            assertEquals(1L, scheduler.status().completedTasks());
        }
    }

    @Test
    void cancelsFailedRepeatingTasksAndPluginOwnedTasks() {
        PluginScheduler scheduler = new PluginScheduler();
        AtomicInteger calls = new AtomicInteger();
        scheduler.runTaskTimer("broken", 0, 1, () -> {
            calls.incrementAndGet();
            throw new IllegalStateException("expected scheduler failure");
        });
        PluginScheduler.TaskHandle owned = scheduler.runTaskTimer("demo", 1, 1, calls::incrementAndGet);

        scheduler.tick();
        assertEquals(2, calls.get());
        assertEquals(1L, scheduler.status().failedTasks());
        assertEquals(1L, scheduler.status().cancelledTasks());

        assertEquals(1, scheduler.cancelPlugin("demo"));
        assertFalse(owned.active());
        assertEquals(2L, scheduler.status().cancelledTasks());
    }

    @Test
    void rejectsInvalidDelaysAndPeriods() {
        PluginScheduler scheduler = new PluginScheduler();
        assertThrows(IllegalArgumentException.class, () -> scheduler.runTaskLater("demo", -1, () -> { }));
        assertThrows(IllegalArgumentException.class, () -> scheduler.runTaskTimer("demo", 0, 0, () -> { }));
        assertThrows(IllegalArgumentException.class, () -> scheduler.runTaskTimerAsync("demo", 0, -1, () -> { }));
    }

    @Test
    void pluginManagerCleanupCancelsScheduledTasks() {
        PluginManager manager = new PluginManager();
        manager.register(new PluginDescriptor("demo", "Demo", "1.0"), new HyperPlugin() {
            @Override
            public void onLoad(PluginContext context) {
                context.runTaskTimer(1, 1, () -> { });
            }
        });

        assertEquals(1, manager.status().scheduledTasks());
        manager.close();
        assertEquals(0, manager.status().scheduledTasks());
        assertEquals(1L, manager.status().cancelledScheduledTasks());
    }

    private static void waitFor(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(5L);
        }
        assertTrue(condition.getAsBoolean());
    }

    @FunctionalInterface
    private interface BooleanSupplier {
        boolean getAsBoolean();
    }
}
