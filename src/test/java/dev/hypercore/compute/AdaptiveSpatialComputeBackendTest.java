package dev.hypercore.compute;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdaptiveSpatialComputeBackendTest {
    @Test
    void usesCpuFallbackAndTracksBatchesWhenGpuIsUnavailable() {
        try (AdaptiveSpatialComputeBackend backend = AdaptiveSpatialComputeBackend.unavailable(
            new GpuOffloadPolicy(1),
            "test unavailable"
        )) {
            float[] output = new float[3];
            backend.squaredDistances(
                1.0f,
                2.0f,
                3.0f,
                new float[]{1.0f, 4.0f, -1.0f},
                new float[]{2.0f, 6.0f, 0.0f},
                new float[]{3.0f, 3.0f, 5.0f},
                output
            );

            assertArrayEquals(new float[]{0.0f, 25.0f, 12.0f}, output);
            assertEquals(ScalarSpatialComputeBackend.ID, backend.id());
            assertFalse(backend.status().gpuAvailable());
            assertEquals(AdaptiveSpatialComputeBackend.InitializationState.UNAVAILABLE,
                backend.status().initializationState());
            assertEquals(1, backend.status().cpuBatches());
            assertEquals(0, backend.status().gpuBatches());
            assertEquals("test unavailable", backend.status().unavailableReason());
        }
    }

    @Test
    void servesCpuWhileInitializationRunsThenSwitchesAtomically() throws Exception {
        CountDownLatch factoryEntered = new CountDownLatch(1);
        CountDownLatch allowCreation = new CountDownLatch(1);
        AtomicBoolean gpuClosed = new AtomicBoolean();
        AtomicInteger snapshotPrepares = new AtomicInteger();
        AdaptiveSpatialComputeBackend backend = AdaptiveSpatialComputeBackend.createForTesting(
            new GpuOffloadPolicy(1),
            () -> {
                factoryEntered.countDown();
                await(allowCreation);
                return new TestGpuBackend(gpuClosed, snapshotPrepares);
            }
        );
        try {
            assertTrue(factoryEntered.await(1, TimeUnit.SECONDS));
            assertEquals(AdaptiveSpatialComputeBackend.InitializationState.INITIALIZING,
                backend.status().initializationState());

            float[] duringInitialization = new float[1];
            backend.squaredDistances(0, 0, 0, new float[]{2}, new float[]{0}, new float[]{0}, duringInitialization);
            assertArrayEquals(new float[]{4}, duringInitialization);
            assertEquals(1, backend.status().cpuBatches());

            allowCreation.countDown();
            assertTrue(backend.awaitInitialization(1, TimeUnit.SECONDS));
            assertEquals(AdaptiveSpatialComputeBackend.InitializationState.READY,
                backend.status().initializationState());
            assertEquals("test-gpu", backend.status().deviceName());
            assertEquals("managed", backend.status().transferMode());

            backend.squaredDistances(0, 0, 0, new float[]{3}, new float[]{0}, new float[]{0}, new float[1]);
            assertEquals(1, backend.status().gpuBatches());

            int[] mask = new int[1];
            backend.radiusMask(0, 0, 0, 4, new float[]{1}, new float[]{0}, new float[]{0}, mask);
            assertArrayEquals(new int[]{1}, mask);
            assertEquals(1, backend.status().gpuRadiusMaskBatches());
            assertEquals(Integer.BYTES, backend.status().gpuRadiusMaskReadbackBytes());

            assertThrows(IllegalArgumentException.class, () -> backend.radiusMask(
                0, 0, 0, -1, new float[]{1}, new float[]{0}, new float[]{0}, new int[1]
            ));
            assertEquals(AdaptiveSpatialComputeBackend.InitializationState.READY,
                backend.status().initializationState());
            assertEquals(0, backend.status().gpuFailures());

            SpatialQueryEngine engine = new SpatialQueryEngine(backend);
            SpatialQueryEngine.PositionBatch positions = new SpatialQueryEngine.PositionBatch(
                new float[]{1}, new float[]{0}, new float[]{0}
            );
            assertEquals(1, engine.withinRadius(0, 0, 0, 2, positions).matchCount());
            assertEquals(1, engine.withinRadius(0, 0, 0, 1, positions).matchCount());
            assertEquals(3, snapshotPrepares.get());
            assertEquals(3, backend.status().gpuRadiusMaskBatches());
            assertEquals(1, backend.status().gpuSnapshotUploads());
            assertEquals(1, backend.status().gpuSnapshotReuses());

            SpatialQueryEngine.QueryResult[] batched = engine.withinRadii(
                positions,
                new SpatialQueryEngine.RadiusQuery(0, 0, 0, 1),
                new SpatialQueryEngine.RadiusQuery(10, 0, 0, 1)
            );
            assertEquals(1, batched[0].matchCount());
            assertEquals(0, batched[1].matchCount());
            assertEquals(5, backend.status().gpuRadiusMaskBatches());
            assertEquals(1, backend.status().gpuMultiQueryBatches());
            assertEquals(2, backend.status().gpuMultiQueryQueries());
            assertEquals(2, backend.status().gpuSnapshotReuses());
            engine.close();
        } finally {
            allowCreation.countDown();
            backend.close();
        }
        assertTrue(gpuClosed.get());
        assertEquals(AdaptiveSpatialComputeBackend.InitializationState.CLOSED,
            backend.status().initializationState());
    }

    @Test
    void closeInterruptsPendingInitialization() throws Exception {
        CountDownLatch factoryEntered = new CountDownLatch(1);
        AdaptiveSpatialComputeBackend backend = AdaptiveSpatialComputeBackend.createForTesting(
            new GpuOffloadPolicy(1),
            () -> {
                factoryEntered.countDown();
                try {
                    new CountDownLatch(1).await();
                    throw new AssertionError("unreachable");
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("initialization interrupted", interrupted);
                }
            }
        );

        assertTrue(factoryEntered.await(1, TimeUnit.SECONDS));
        backend.close();

        assertTrue(backend.awaitInitialization(1, TimeUnit.SECONDS));
        assertEquals(AdaptiveSpatialComputeBackend.InitializationState.CLOSED,
            backend.status().initializationState());
        assertFalse(backend.status().gpuAvailable());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(1, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test initialization timed out");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("test initialization interrupted", interrupted);
        }
    }

    private static final class TestGpuBackend implements ManagedSpatialComputeBackend {
        private final ScalarSpatialComputeBackend scalar = new ScalarSpatialComputeBackend();
        private final AtomicBoolean closed;
        private final AtomicInteger snapshotPrepares;

        private TestGpuBackend(AtomicBoolean closed) {
            this(closed, new AtomicInteger());
        }

        private TestGpuBackend(AtomicBoolean closed, AtomicInteger snapshotPrepares) {
            this.closed = closed;
            this.snapshotPrepares = snapshotPrepares;
        }

        @Override
        public String id() {
            return "test-gpu";
        }

        @Override
        public ComputeDeviceType deviceType() {
            return ComputeDeviceType.GPU;
        }

        @Override
        public String deviceName() {
            return "test-gpu";
        }

        @Override
        public SpatialComputeBackend.PositionSnapshot prepareSnapshot(
            float[] positionsX,
            float[] positionsY,
            float[] positionsZ
        ) {
            snapshotPrepares.incrementAndGet();
            return ManagedSpatialComputeBackend.super.prepareSnapshot(positionsX, positionsY, positionsZ);
        }

        @Override
        public void squaredDistances(
            float originX,
            float originY,
            float originZ,
            float[] positionsX,
            float[] positionsY,
            float[] positionsZ,
            float[] output
        ) {
            scalar.squaredDistances(originX, originY, originZ, positionsX, positionsY, positionsZ, output);
        }

        @Override
        public void close() {
            closed.set(true);
        }
    }
}
