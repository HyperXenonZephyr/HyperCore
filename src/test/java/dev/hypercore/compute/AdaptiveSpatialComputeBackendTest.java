package dev.hypercore.compute;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        AdaptiveSpatialComputeBackend backend = AdaptiveSpatialComputeBackend.createForTesting(
            new GpuOffloadPolicy(1),
            () -> {
                factoryEntered.countDown();
                await(allowCreation);
                return new TestGpuBackend(gpuClosed);
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

            backend.squaredDistances(0, 0, 0, new float[]{3}, new float[]{0}, new float[]{0}, new float[1]);
            assertEquals(1, backend.status().gpuBatches());
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

        private TestGpuBackend(AtomicBoolean closed) {
            this.closed = closed;
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
