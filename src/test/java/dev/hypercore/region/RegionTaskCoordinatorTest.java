package dev.hypercore.region;

import dev.hypercore.concurrent.HyperCoreExecutor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionTaskCoordinatorTest {
    private static final RegionKey REGION_A = new RegionKey("minecraft:overworld", 0, 0);
    private static final RegionKey REGION_B = new RegionKey("minecraft:overworld", 1, 0);

    @Test
    void preservesTargetOrderAndTracksCrossRegionMessages() throws Exception {
        List<Integer> regionAOrder = Collections.synchronizedList(new ArrayList<>());

        try (HyperCoreExecutor executor = HyperCoreExecutor.create(2, 8)) {
            RegionTaskCoordinator coordinator = new RegionTaskCoordinator(executor, 2);
            coordinator.send(REGION_A, REGION_A, () -> regionAOrder.add(1));
            coordinator.send(REGION_B, REGION_A, () -> regionAOrder.add(2));
            coordinator.send(REGION_A, REGION_B, () -> { });

            RegionTaskCoordinator.TickResult result = coordinator.advanceTick().get(5, TimeUnit.SECONDS);

            assertEquals(List.of(1, 2), regionAOrder);
            assertEquals(3, result.submittedMessages());
            assertEquals(3, result.executedMessages());
            assertEquals(2, result.targetRegions());
            assertTrue(result.complete());
            assertEquals(2, coordinator.status().crossRegionMessages());
        }
    }

    @Test
    void defersMessagesAddedWhileATickIsInFlight() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<Integer> executions = Collections.synchronizedList(new ArrayList<>());

        try (HyperCoreExecutor executor = HyperCoreExecutor.create(1, 8)) {
            RegionTaskCoordinator coordinator = new RegionTaskCoordinator(executor, 1);
            coordinator.send(REGION_A, REGION_A, () -> {
                started.countDown();
                await(release);
                executions.add(1);
            });
            var firstTick = coordinator.advanceTick();
            assertTrue(started.await(5, TimeUnit.SECONDS));

            coordinator.send(REGION_A, REGION_A, () -> executions.add(2));
            assertTrue(coordinator.status().tickInFlight());
            assertEquals(1, coordinator.status().queuedMessages());
            assertThrows(IllegalStateException.class, coordinator::advanceTick);

            release.countDown();
            firstTick.get(5, TimeUnit.SECONDS);
            assertEquals(List.of(1), executions);

            coordinator.advanceTick().get(5, TimeUnit.SECONDS);
            assertEquals(List.of(1, 2), executions);
        }
    }

    @Test
    void isolatesMessageFailuresWithinAnOwnerBatch() throws Exception {
        List<Integer> executions = new ArrayList<>();

        try (HyperCoreExecutor executor = HyperCoreExecutor.create(1, 8)) {
            RegionTaskCoordinator coordinator = new RegionTaskCoordinator(executor, 1);
            coordinator.send(REGION_A, REGION_A, () -> {
                throw new IllegalStateException("expected test failure");
            });
            coordinator.send(REGION_A, REGION_A, () -> executions.add(2));

            RegionTaskCoordinator.TickResult result = coordinator.advanceTick().get(5, TimeUnit.SECONDS);

            assertEquals(List.of(2), executions);
            assertEquals(2, result.executedMessages());
            assertEquals(1, result.failedMessages());
            assertFalse(result.complete());
            assertEquals(1, coordinator.status().partialTicks());
        }
    }

    @Test
    void requeuesOwnerBatchWhenExecutorAppliesBackpressure() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<Integer> executions = new ArrayList<>();

        try (HyperCoreExecutor executor = HyperCoreExecutor.create(1, 1)) {
            var running = executor.submit(() -> block(started, release));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            var queued = executor.submit(() -> 2);

            RegionTaskCoordinator coordinator = new RegionTaskCoordinator(executor, 1);
            coordinator.send(REGION_A, REGION_A, () -> executions.add(3));
            RegionTaskCoordinator.TickResult rejectedTick = coordinator.advanceTick()
                .get(5, TimeUnit.SECONDS);

            assertEquals(1, rejectedTick.requeuedMessages());
            assertEquals(1, coordinator.status().queuedMessages());
            assertFalse(rejectedTick.complete());

            release.countDown();
            assertEquals(1, running.get(5, TimeUnit.SECONDS));
            assertEquals(2, queued.get(5, TimeUnit.SECONDS));
            coordinator.advanceTick().get(5, TimeUnit.SECONDS);
            assertEquals(List.of(3), executions);
        }
    }

    @Test
    void mapsARegionToAStableOwner() {
        try (HyperCoreExecutor executor = HyperCoreExecutor.create(2, 8)) {
            RegionTaskCoordinator coordinator = new RegionTaskCoordinator(executor, 4);

            int owner = coordinator.ownerFor(REGION_A);
            assertEquals(owner, coordinator.ownerFor(REGION_A));
            assertTrue(owner >= 0 && owner < 4);
        }
    }

    private static int block(CountDownLatch started, CountDownLatch release) {
        started.countDown();
        await(release);
        return 1;
    }

    private static void await(CountDownLatch release) {
        try {
            if (!release.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for test release");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Worker interrupted", error);
        }
    }
}
