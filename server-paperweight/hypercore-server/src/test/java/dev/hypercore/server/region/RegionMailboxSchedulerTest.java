package dev.hypercore.server.region;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RegionMailboxSchedulerTest {
    private static final RegionKey OVERWORLD_ORIGIN = new RegionKey("minecraft:overworld", 0, 0);

    @Test
    void mapsNegativeChunkCoordinatesWithFloorDivision() {
        assertEquals(new RegionKey("minecraft:overworld", -1, -1), RegionKey.fromChunk("minecraft:overworld", -1, -8));
        assertEquals(new RegionKey("minecraft:overworld", -2, -2), RegionKey.fromChunk("minecraft:overworld", -9, -9));
    }

    @Test
    void assignsStableOwnersAndPreservesMailboxOrder() throws Exception {
        try (RegionMailboxScheduler scheduler = new RegionMailboxScheduler(Thread.currentThread(), 2, 8)) {
            assertEquals(scheduler.ownerFor(OVERWORLD_ORIGIN), scheduler.ownerFor(OVERWORLD_ORIGIN));
            List<Integer> order = new ArrayList<>();
            scheduler.enqueue(OVERWORLD_ORIGIN, snapshot -> order.add(1));
            scheduler.enqueue(OVERWORLD_ORIGIN, snapshot -> order.add(2));

            RegionMailboxScheduler.TickResult result = scheduler.beginTick(
                1L, List.of(new RegionSnapshot(OVERWORLD_ORIGIN, 4, 1L)), snapshot -> { }
            ).orElseThrow().get(5L, TimeUnit.SECONDS);

            assertEquals(List.of(1, 2), order);
            assertTrue(result.complete());
            assertEquals(2, result.executedMessages());
        }
    }

    @Test
    void defersMessagesSubmittedDuringAnInFlightTick() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<Integer> executions = new ArrayList<>();
        try (RegionMailboxScheduler scheduler = new RegionMailboxScheduler(Thread.currentThread(), 1, 8)) {
            var firstTick = scheduler.beginTick(
                1L,
                List.of(new RegionSnapshot(OVERWORLD_ORIGIN, 1, 1L)),
                snapshot -> {
                    entered.countDown();
                    await(release);
                }
            ).orElseThrow();
            assertTrue(entered.await(5L, TimeUnit.SECONDS));

            scheduler.enqueue(OVERWORLD_ORIGIN, snapshot -> executions.add(2));
            assertTrue(scheduler.beginTick(2L, List.of(new RegionSnapshot(OVERWORLD_ORIGIN, 1, 2L)), snapshot -> { }).isEmpty());

            release.countDown();
            firstTick.get(5L, TimeUnit.SECONDS);
            RegionMailboxScheduler.TickResult second = scheduler.beginTick(
                2L, List.of(new RegionSnapshot(OVERWORLD_ORIGIN, 1, 2L)), snapshot -> { }
            ).orElseThrow().get(5L, TimeUnit.SECONDS);

            assertEquals(List.of(2), executions);
            assertEquals(1, second.executedMessages());
        }
    }

    @Test
    void rejectsNewWorkAfterClose() {
        RegionMailboxScheduler scheduler = new RegionMailboxScheduler(Thread.currentThread(), 1, 1);
        scheduler.close();
        assertTrue(scheduler.status().closed());
        assertThrows(IllegalStateException.class, () -> scheduler.enqueue(OVERWORLD_ORIGIN, snapshot -> { }));
        assertFalse(scheduler.status().tickInFlight());
    }

    @Test
    void retriesFailedMailboxWorkOnTheNextTick() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        try (RegionMailboxScheduler scheduler = new RegionMailboxScheduler(Thread.currentThread(), 1, 8)) {
            scheduler.enqueue(OVERWORLD_ORIGIN, snapshot -> {
                if (attempts.incrementAndGet() == 1) {
                    throw new IllegalStateException("first attempt fails");
                }
            });

            RegionMailboxScheduler.TickResult first = scheduler.beginTick(
                1L, List.of(new RegionSnapshot(OVERWORLD_ORIGIN, 1, 1L)), snapshot -> { }
            ).orElseThrow().get(5L, TimeUnit.SECONDS);
            assertFalse(first.complete());
            assertEquals(1, first.failedRegions());
            assertEquals(1, first.requeuedMessages());

            RegionMailboxScheduler.TickResult second = scheduler.beginTick(
                2L, List.of(new RegionSnapshot(OVERWORLD_ORIGIN, 1, 2L)), snapshot -> { }
            ).orElseThrow().get(5L, TimeUnit.SECONDS);
            assertTrue(second.complete());
            assertEquals(1, second.executedMessages());
            assertEquals(2, attempts.get());
        }
    }

    private static void await(final CountDownLatch release) {
        try {
            if (!release.await(5L, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for worker release");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Worker interrupted", error);
        }
    }
}
