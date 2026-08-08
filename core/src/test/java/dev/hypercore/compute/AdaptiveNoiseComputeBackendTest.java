package dev.hypercore.compute;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdaptiveNoiseComputeBackendTest {
    @Test
    @DisplayName("unavailable backend routes to CPU and reports CPU identity")
    void unavailableBackendRoutesToCpu() {
        try (AdaptiveNoiseComputeBackend backend = AdaptiveNoiseComputeBackend.unavailable(
            new GpuOffloadPolicy(1),
            "test unavailable"
        )) {
            int sizeX = 4;
            int sizeY = 4;
            int sizeZ = 4;
            float[] output = new float[sizeX * sizeY * sizeZ];
            float[] expected = new float[sizeX * sizeY * sizeZ];
            new ScalarNoiseComputeBackend().generateDensity(
                1.0f, 2.0f, 3.0f, sizeX, sizeY, sizeZ, 0.1f, expected
            );

            backend.generateDensity(1.0f, 2.0f, 3.0f, sizeX, sizeY, sizeZ, 0.1f, output);

            assertArrayEquals(expected, output);
            assertEquals(ScalarNoiseComputeBackend.ID, backend.id());
            assertEquals(ComputeDeviceType.CPU, backend.deviceType());
            assertFalse(backend.status().gpuAvailable());
            assertEquals(AdaptiveNoiseComputeBackend.InitializationState.UNAVAILABLE,
                backend.status().initializationState());
            assertEquals(1, backend.status().cpuBatches());
            assertEquals(0, backend.status().gpuBatches());
            assertEquals(0, backend.status().gpuFailures());
            assertEquals("test unavailable", backend.status().unavailableReason());
            assertEquals("", backend.status().deviceName());
            assertEquals("", backend.status().transferMode());
        }
    }

    @Test
    @DisplayName("CPU fallback is used when the batch is below the minimum threshold")
    void fallsBackToCpuWhenBelowMinimumBatchSize() throws InterruptedException {
        AtomicBoolean gpuClosed = new AtomicBoolean();
        AdaptiveNoiseComputeBackend backend = AdaptiveNoiseComputeBackend.createForTesting(
            new GpuOffloadPolicy(100_000),
            () -> new TestNoiseGpuBackend(gpuClosed)
        );
        try {
            assertTrue(backend.awaitInitialization(1, TimeUnit.SECONDS));
            assertEquals(AdaptiveNoiseComputeBackend.InitializationState.READY,
                backend.status().initializationState());
            assertTrue(backend.status().gpuAvailable());

            int sizeX = 4;
            int sizeY = 4;
            int sizeZ = 4;
            float[] output = new float[sizeX * sizeY * sizeZ];
            backend.generateDensity(0.0f, 0.0f, 0.0f, sizeX, sizeY, sizeZ, 0.1f, output);

            assertEquals(1, backend.status().cpuBatches());
            assertEquals(0, backend.status().gpuBatches());
            assertEquals(0, backend.status().gpuFailures());
            assertFalse(gpuClosed.get());
        } finally {
            backend.close();
        }
        assertTrue(gpuClosed.get());
        assertEquals(AdaptiveNoiseComputeBackend.InitializationState.CLOSED,
            backend.status().initializationState());
    }

    @Test
    @DisplayName("GPU factory failure leaves the backend unavailable and routes to CPU")
    void fallsBackToCpuWhenGpuFactoryThrows() throws InterruptedException {
        AdaptiveNoiseComputeBackend backend = AdaptiveNoiseComputeBackend.createForTesting(
            new GpuOffloadPolicy(1),
            () -> {
                throw new IllegalStateException("no GPU for noise test");
            }
        );
        try {
            assertTrue(backend.awaitInitialization(1, TimeUnit.SECONDS));
            assertEquals(AdaptiveNoiseComputeBackend.InitializationState.UNAVAILABLE,
                backend.status().initializationState());
            assertFalse(backend.status().gpuAvailable());
            assertTrue(backend.status().unavailableReason().contains("no GPU for noise test"));

            int sizeX = 2;
            int sizeY = 2;
            int sizeZ = 2;
            float[] output = new float[sizeX * sizeY * sizeZ];
            backend.generateDensity(0.0f, 0.0f, 0.0f, sizeX, sizeY, sizeZ, 0.1f, output);

            assertEquals(1, backend.status().cpuBatches());
            assertEquals(0, backend.status().gpuBatches());
            assertEquals(ScalarNoiseComputeBackend.ID, backend.id());
            assertEquals(ComputeDeviceType.CPU, backend.deviceType());
        } finally {
            backend.close();
        }
        assertEquals(AdaptiveNoiseComputeBackend.InitializationState.CLOSED,
            backend.status().initializationState());
    }

    @Test
    @DisplayName("eligible batches are routed to the GPU and produce correct output")
    void routesToGpuWhenEligible() throws InterruptedException {
        AtomicBoolean gpuClosed = new AtomicBoolean();
        AdaptiveNoiseComputeBackend backend = AdaptiveNoiseComputeBackend.createForTesting(
            new GpuOffloadPolicy(1),
            () -> new TestNoiseGpuBackend(gpuClosed)
        );
        try {
            assertTrue(backend.awaitInitialization(1, TimeUnit.SECONDS));
            assertEquals(AdaptiveNoiseComputeBackend.InitializationState.READY,
                backend.status().initializationState());
            assertTrue(backend.status().gpuAvailable());
            assertEquals("test-gpu-noise", backend.status().deviceName());
            assertEquals("managed", backend.status().transferMode());
            assertEquals(AdaptiveNoiseComputeBackend.VULKAN_ID, backend.id());
            assertEquals(ComputeDeviceType.GPU, backend.deviceType());

            int sizeX = 4;
            int sizeY = 4;
            int sizeZ = 4;
            float[] output = new float[sizeX * sizeY * sizeZ];
            float[] expected = new float[sizeX * sizeY * sizeZ];
            new ScalarNoiseComputeBackend().generateDensity(
                -2.5f, 1.0f, 0.5f, sizeX, sizeY, sizeZ, 0.07f, expected
            );

            backend.generateDensity(-2.5f, 1.0f, 0.5f, sizeX, sizeY, sizeZ, 0.07f, output);

            assertArrayEquals(expected, output);
            assertEquals(0, backend.status().cpuBatches());
            assertEquals(1, backend.status().gpuBatches());
            assertEquals(sizeX * sizeY * sizeZ, backend.status().gpuVoxels());
            assertEquals(0, backend.status().gpuFailures());
        } finally {
            backend.close();
        }
        assertTrue(gpuClosed.get());
    }

    @Test
    @DisplayName("a GPU failure disables the GPU and falls back to CPU for current and future calls")
    void gpuFailureDisablesGpuAndFallsBackToCpu() throws InterruptedException {
        AtomicBoolean gpuClosed = new AtomicBoolean();
        TestNoiseGpuBackend gpu = new TestNoiseGpuBackend(gpuClosed);
        AdaptiveNoiseComputeBackend backend = AdaptiveNoiseComputeBackend.createForTesting(
            new GpuOffloadPolicy(1),
            () -> gpu
        );
        try {
            assertTrue(backend.awaitInitialization(1, TimeUnit.SECONDS));
            assertEquals(AdaptiveNoiseComputeBackend.InitializationState.READY,
                backend.status().initializationState());

            int sizeX = 4;
            int sizeY = 4;
            int sizeZ = 4;
            backend.generateDensity(0.0f, 0.0f, 0.0f, sizeX, sizeY, sizeZ, 0.1f, new float[sizeX * sizeY * sizeZ]);
            assertEquals(1, backend.status().gpuBatches());
            assertEquals(0, backend.status().cpuBatches());
            assertEquals(0, backend.status().gpuFailures());

            gpu.throwOnNext.set(true);
            backend.generateDensity(0.0f, 0.0f, 0.0f, sizeX, sizeY, sizeZ, 0.1f, new float[sizeX * sizeY * sizeZ]);

            assertEquals(1, backend.status().gpuBatches());
            assertEquals(1, backend.status().cpuBatches());
            assertEquals(1, backend.status().gpuFailures());
            assertFalse(backend.status().gpuAvailable());
            assertEquals(AdaptiveNoiseComputeBackend.InitializationState.UNAVAILABLE,
                backend.status().initializationState());
            assertTrue(gpuClosed.get());

            backend.generateDensity(0.0f, 0.0f, 0.0f, sizeX, sizeY, sizeZ, 0.1f, new float[sizeX * sizeY * sizeZ]);
            assertEquals(1, backend.status().gpuBatches());
            assertEquals(2, backend.status().cpuBatches());

            assertThrows(IllegalArgumentException.class, () ->
                backend.generateDensity(0.0f, 0.0f, 0.0f, 0, 1, 1, 0.1f, new float[1]));
            assertEquals(AdaptiveNoiseComputeBackend.InitializationState.UNAVAILABLE,
                backend.status().initializationState());
        } finally {
            backend.close();
        }
        assertEquals(AdaptiveNoiseComputeBackend.InitializationState.CLOSED,
            backend.status().initializationState());
    }

    @Test
    @DisplayName("close interrupts pending initialization and reports CLOSED")
    void closeInterruptsPendingInitialization() throws Exception {
        CountDownLatch factoryEntered = new CountDownLatch(1);
        AdaptiveNoiseComputeBackend backend = AdaptiveNoiseComputeBackend.createForTesting(
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
        assertEquals(AdaptiveNoiseComputeBackend.InitializationState.CLOSED,
            backend.status().initializationState());
        assertFalse(backend.status().gpuAvailable());
    }

    private static final class TestNoiseGpuBackend implements ManagedNoiseComputeBackend {
        private final ScalarNoiseComputeBackend scalar = new ScalarNoiseComputeBackend();
        private final AtomicBoolean closed;
        final AtomicBoolean throwOnNext = new AtomicBoolean();

        private TestNoiseGpuBackend(AtomicBoolean closed) {
            this.closed = closed;
        }

        @Override
        public String id() {
            return "test-gpu-noise";
        }

        @Override
        public ComputeDeviceType deviceType() {
            return ComputeDeviceType.GPU;
        }

        @Override
        public String deviceName() {
            return "test-gpu-noise";
        }

        @Override
        public void generateDensity(
            float originX, float originY, float originZ,
            int sizeX, int sizeY, int sizeZ,
            float frequency,
            float[] output
        ) {
            if (throwOnNext.get()) {
                throw new IllegalStateException("simulated GPU noise failure");
            }
            scalar.generateDensity(originX, originY, originZ, sizeX, sizeY, sizeZ, frequency, output);
        }

        @Override
        public void close() {
            closed.set(true);
        }
    }
}
