package dev.hypercore.server.plugin;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class PluginTaskSchedulerTest {
    @Test
    void runsTasksAfterTheRequestedDelay() {
        AtomicLong clock = new AtomicLong();
        PluginTaskScheduler scheduler = new PluginTaskScheduler(clock::get);
        List<String> ran = new ArrayList<>();
        scheduler.runTaskLater(() -> ran.add("zero"), 0L);
        scheduler.runTaskLater(() -> ran.add("three"), 3L);
        assertEquals(2, scheduler.pending());
        tickOnce(clock, scheduler);
        assertEquals(List.of("zero"), ran);
        assertEquals(1, scheduler.pending());
        tickOnce(clock, scheduler);
        assertEquals(List.of("zero"), ran);
        tickOnce(clock, scheduler);
        assertEquals(List.of("zero", "three"), ran);
        assertEquals(0, scheduler.pending());
    }

    @Test
    void repeatsTimersOnTheirExactPhaseWithoutDrift() {
        AtomicLong clock = new AtomicLong();
        PluginTaskScheduler scheduler = new PluginTaskScheduler(clock::get);
        List<Long> firedAt = new ArrayList<>();
        scheduler.runTaskTimer(() -> firedAt.add(clock.get()), 2L, 3L);
        assertEquals(1, scheduler.pending());
        for (int tick = 1; tick <= 11; tick++) {
            tickOnce(clock, scheduler);
        }
        assertEquals(List.of(2L, 5L, 8L, 11L), firedAt);
        assertEquals(1, scheduler.pending());
    }

    @Test
    void cancelsPendingTasksBeforeTheyRun() {
        AtomicLong clock = new AtomicLong();
        PluginTaskScheduler scheduler = new PluginTaskScheduler(clock::get);
        AtomicInteger ran = new AtomicInteger();
        PluginTaskScheduler.Handle handle = scheduler.runTaskLater(ran::incrementAndGet, 2L);
        assertFalse(handle.isCancelled());
        assertTrue(handle.cancel());
        assertTrue(handle.isCancelled());
        assertEquals(0, scheduler.pending());
        assertFalse(handle.cancel());
        for (int tick = 1; tick <= 3; tick++) {
            tickOnce(clock, scheduler);
        }
        assertEquals(0, ran.get());
        assertEquals(0, scheduler.pending());
    }

    @Test
    void appliesCancellationFromOtherThreadsOnTheNextTick() throws Exception {
        AtomicLong clock = new AtomicLong();
        PluginTaskScheduler scheduler = new PluginTaskScheduler(clock::get);
        AtomicInteger ran = new AtomicInteger();
        PluginTaskScheduler.Handle handle = scheduler.runTaskLater(ran::incrementAndGet, 5L);
        Thread canceller = new Thread(handle::cancel);
        canceller.start();
        canceller.join();
        assertTrue(handle.isCancelled());
        for (int tick = 1; tick <= 6; tick++) {
            tickOnce(clock, scheduler);
        }
        assertEquals(0, ran.get());
        assertEquals(0, scheduler.pending());
    }

    @Test
    void isolatesFailingTasksFromTheRestOfTheTick() {
        AtomicLong clock = new AtomicLong();
        PluginTaskScheduler scheduler = new PluginTaskScheduler(clock::get);
        List<String> ran = new ArrayList<>();
        AtomicInteger brokenFires = new AtomicInteger();
        AtomicInteger healthyRepeats = new AtomicInteger();
        scheduler.runTaskLater(() -> { throw new IllegalStateException("expected one-shot"); }, 1L);
        scheduler.runTaskLater(() -> ran.add("healthy"), 1L);
        PluginTaskScheduler.Handle brokenTimer = scheduler.runTaskTimer(() -> { brokenFires.incrementAndGet(); throw new IllegalStateException("expected timer"); }, 1L, 2L);
        PluginTaskScheduler.Handle healthyTimer = scheduler.runTaskTimer(healthyRepeats::incrementAndGet, 1L, 2L);
        assertDoesNotThrow(() -> tickOnce(clock, scheduler));
        assertFalse(brokenTimer.isCancelled());
        assertEquals(List.of("healthy"), ran);
        assertEquals(1, brokenFires.get());
        assertEquals(1, healthyRepeats.get());
        assertEquals(1, scheduler.pending());
        tickOnce(clock, scheduler);
        assertEquals(List.of("healthy"), ran);
        tickOnce(clock, scheduler);
        assertEquals(1, brokenFires.get());
        assertEquals(2, healthyRepeats.get());
        assertEquals(1, scheduler.pending());
    }

    @Test
    void rejectsInvalidDelaysPeriodsAndNullTasks() {
        PluginTaskScheduler scheduler = new PluginTaskScheduler(() -> 0L);
        Runnable task = () -> { };
        assertThrows(IllegalArgumentException.class, () -> scheduler.runTaskLater(task, -1L));
        assertThrows(IllegalArgumentException.class, () -> scheduler.runTaskTimer(task, -1L, 1L));
        assertThrows(IllegalArgumentException.class, () -> scheduler.runTaskTimer(task, 0L, 0L));
        assertThrows(IllegalArgumentException.class, () -> scheduler.runTaskTimer(task, 0L, -2L));
        assertThrows(NullPointerException.class, () -> scheduler.runTaskTimer(null, 1L, 1L));
        assertThrows(NullPointerException.class, () -> scheduler.runTaskLater(null, 1L));
        assertEquals(0, scheduler.pending());
    }

    private static void tickOnce(final AtomicLong clock, final PluginTaskScheduler scheduler) {
        clock.incrementAndGet();
        scheduler.tick();
    }
}
