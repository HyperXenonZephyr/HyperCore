package dev.hypercore.compute;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Routes 3D density-noise generation to Vulkan when ready and keeps a
 * deterministic CPU path available.  Mirrors the adaptive pattern of
 * {@link AdaptiveSpatialComputeBackend} but for the noise workload.
 */
public final class AdaptiveNoiseComputeBackend implements NoiseComputeBackend, AutoCloseable {
    public static final String VULKAN_ID = "adaptive-vulkan-noise";
    private static final Logger LOGGER = LoggerFactory.getLogger(AdaptiveNoiseComputeBackend.class);
    private static final long INITIALIZER_JOIN_MILLIS = 5_000L;

    private final NoiseComputeBackend cpu;
    private final GpuOffloadPolicy policy;
    private final AtomicLong cpuBatches = new AtomicLong();
    private final AtomicLong gpuBatches = new AtomicLong();
    private final AtomicLong gpuFailures = new AtomicLong();
    private final AtomicLong cpuVoxels = new AtomicLong();
    private final AtomicLong gpuVoxels = new AtomicLong();
    private final CountDownLatch initializationComplete;
    private final GpuBackendFactory gpuFactory;
    private final long initializationStartedNanos;
    private volatile long initializationFinishedNanos;
    private volatile InitializationState initializationState;
    private volatile ManagedNoiseComputeBackend gpu;
    private volatile String unavailableReason;
    private volatile Thread initializer;
    private volatile boolean closed;

    private AdaptiveNoiseComputeBackend(
        GpuOffloadPolicy policy,
        ManagedNoiseComputeBackend gpu,
        InitializationState state,
        String unavailableReason,
        GpuBackendFactory gpuFactory,
        NoiseComputeBackend cpu
    ) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.gpu = gpu;
        this.cpu = Objects.requireNonNull(cpu, "cpu");
        this.initializationState = Objects.requireNonNull(state, "state");
        this.unavailableReason = Objects.requireNonNullElse(unavailableReason, "");
        this.gpuFactory = gpuFactory;
        this.initializationComplete = new CountDownLatch(state == InitializationState.INITIALIZING ? 1 : 0);
        this.initializationStartedNanos = System.nanoTime();
        this.initializationFinishedNanos = state == InitializationState.INITIALIZING ? 0L : initializationStartedNanos;
    }

    /** Starts Vulkan creation off the server lifecycle thread. */
    public static AdaptiveNoiseComputeBackend create(GpuOffloadPolicy policy, boolean enabled) {
        return create(policy, enabled, new ScalarNoiseComputeBackend());
    }

    public static AdaptiveNoiseComputeBackend create(
        GpuOffloadPolicy policy, boolean enabled, NoiseComputeBackend cpu
    ) {
        if (!enabled) {
            return unavailable(policy, "disabled by configuration", cpu);
        }
        AdaptiveNoiseComputeBackend backend = new AdaptiveNoiseComputeBackend(
            policy, null, InitializationState.INITIALIZING, "", VulkanSpatialComputeBackend::create, cpu);
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        Thread initializer = new Thread(() -> {
            Thread.currentThread().setContextClassLoader(contextLoader);
            backend.initializeGpu();
        }, "HyperCore-Vulkan-Noise-Init");
        initializer.setDaemon(true);
        backend.initializer = initializer;
        initializer.start();
        return backend;
    }

    public static AdaptiveNoiseComputeBackend unavailable(GpuOffloadPolicy policy, String reason) {
        return unavailable(policy, reason, new ScalarNoiseComputeBackend());
    }

    public static AdaptiveNoiseComputeBackend unavailable(
        GpuOffloadPolicy policy, String reason, NoiseComputeBackend cpu
    ) {
        return new AdaptiveNoiseComputeBackend(policy, null, InitializationState.UNAVAILABLE, reason, null, cpu);
    }

    static AdaptiveNoiseComputeBackend createForTesting(GpuOffloadPolicy policy, GpuBackendFactory gpuFactory) {
        return createForTesting(policy, gpuFactory, new ScalarNoiseComputeBackend());
    }

    static AdaptiveNoiseComputeBackend createForTesting(
        GpuOffloadPolicy policy, GpuBackendFactory gpuFactory, NoiseComputeBackend cpu
    ) {
        AdaptiveNoiseComputeBackend backend = new AdaptiveNoiseComputeBackend(
            policy, null, InitializationState.INITIALIZING, "",
            Objects.requireNonNull(gpuFactory, "gpuFactory"), cpu);
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        Thread initializer = new Thread(() -> {
            Thread.currentThread().setContextClassLoader(contextLoader);
            backend.initializeGpu();
        }, "HyperCore-Vulkan-Noise-Init-Test");
        initializer.setDaemon(true);
        backend.initializer = initializer;
        initializer.start();
        return backend;
    }

    @Override
    public String id() {
        return gpu == null ? cpu.id() : VULKAN_ID;
    }

    @Override
    public ComputeDeviceType deviceType() {
        return gpu == null ? ComputeDeviceType.CPU : ComputeDeviceType.GPU;
    }

    @Override
    public void generateDensity(
        float originX, float originY, float originZ,
        int sizeX, int sizeY, int sizeZ,
        float frequency,
        float[] output
    ) {
        int total = NoiseComputeBackend.validate(sizeX, sizeY, sizeZ, frequency, output);
        ManagedNoiseComputeBackend currentGpu = gpu;
        GpuOffloadPolicy.Decision decision = policy.evaluate(total, currentGpu != null, currentGpu != null);
        if (decision.offload()) {
            try {
                synchronized (this) {
                    if (closed || gpu != currentGpu) {
                        throw new IllegalStateException("Vulkan noise backend is closing");
                    }
                    currentGpu.generateDensity(
                        originX, originY, originZ, sizeX, sizeY, sizeZ, frequency, output
                    );
                }
                gpuBatches.incrementAndGet();
                gpuVoxels.addAndGet(total);
                return;
            } catch (VulkanSpatialComputeBackend.BatchNotSupportedException unsupported) {
                // Fall through to CPU
            } catch (RuntimeException | LinkageError error) {
                disableGpu(currentGpu, error);
            }
        }

        cpu.generateDensity(originX, originY, originZ, sizeX, sizeY, sizeZ, frequency, output);
        cpuBatches.incrementAndGet();
        cpuVoxels.addAndGet(total);
    }

    public synchronized Status status() {
        ManagedNoiseComputeBackend currentGpu = gpu;
        long finished = initializationFinishedNanos;
        long duration = finished == 0L ? System.nanoTime() - initializationStartedNanos : finished - initializationStartedNanos;
        return new Status(
            currentGpu != null,
            initializationState,
            duration / 1_000_000L,
            currentGpu == null ? "" : currentGpu.deviceName(),
            currentGpu == null ? "" : currentGpu.transferMode(),
            policy.minimumBatchSize(),
            cpuBatches.get(),
            gpuBatches.get(),
            gpuFailures.get(),
            cpuVoxels.get(),
            gpuVoxels.get(),
            unavailableReason
        );
    }

    boolean awaitInitialization(long timeout, TimeUnit unit) throws InterruptedException {
        return initializationComplete.await(timeout, unit);
    }

    @Override
    public void close() {
        Thread pendingInitializer;
        ManagedNoiseComputeBackend currentGpu;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            initializationState = InitializationState.CLOSED;
            if (initializationFinishedNanos == 0L) {
                initializationFinishedNanos = System.nanoTime();
            }
            pendingInitializer = initializer;
            initializer = null;
            currentGpu = gpu;
            gpu = null;
        }
        if (pendingInitializer != null && pendingInitializer != Thread.currentThread()) {
            pendingInitializer.interrupt();
            try {
                pendingInitializer.join(INITIALIZER_JOIN_MILLIS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                LOGGER.warn("Interrupted while waiting for noise Vulkan initialization to stop");
            }
        }
        if (currentGpu != null) {
            closeGpu(currentGpu);
        }
    }

    private void initializeGpu() {
        ManagedNoiseComputeBackend created = null;
        try {
            created = gpuFactory.create();
            verify(created);
            synchronized (this) {
                if (closed) {
                    closeGpu(created);
                    return;
                }
                gpu = created;
                initializationState = InitializationState.READY;
                initializationFinishedNanos = System.nanoTime();
                unavailableReason = "";
                initializer = null;
            }
            LOGGER.info("Vulkan noise compute initialized asynchronously on {}", created.deviceName());
        } catch (RuntimeException | LinkageError error) {
            if (created != null) {
                closeGpu(created);
            }
            String reason = error.getClass().getSimpleName() + ": " + normalize(error.getMessage());
            synchronized (this) {
                if (!closed) {
                    initializationState = InitializationState.UNAVAILABLE;
                    initializationFinishedNanos = System.nanoTime();
                    unavailableReason = reason;
                    initializer = null;
                }
            }
            if (!closed) {
                LOGGER.warn("Vulkan noise compute initialization failed; using {}: {}", cpu.id(), reason, error);
            }
        } finally {
            initializationComplete.countDown();
        }
    }

    private synchronized void disableGpu(ManagedNoiseComputeBackend failedGpu, Throwable error) {
        if (gpu != failedGpu || closed) {
            return;
        }
        gpu = null;
        initializationState = InitializationState.UNAVAILABLE;
        gpuFailures.incrementAndGet();
        unavailableReason = error.getClass().getSimpleName() + ": " + normalize(error.getMessage());
        LOGGER.error("Vulkan noise compute failed and has been disabled; using {}", cpu.id(), error);
        closeGpu(failedGpu);
    }

    private static void closeGpu(ManagedNoiseComputeBackend backend) {
        try {
            backend.close();
        } catch (RuntimeException | LinkageError closeError) {
            LOGGER.warn("Vulkan noise compute cleanup failed", closeError);
        }
    }

    private static void verify(NoiseComputeBackend gpu) {
        int sizeX = 16;
        int sizeY = 16;
        int sizeZ = 16;
        float originX = -3.5f;
        float originY = 2.0f;
        float originZ = -7.25f;
        float frequency = 0.05f;
        int total = sizeX * sizeY * sizeZ;
        float[] expected = new float[total];
        float[] actual = new float[total];

        new ScalarNoiseComputeBackend().generateDensity(
            originX, originY, originZ, sizeX, sizeY, sizeZ, frequency, expected
        );
        gpu.generateDensity(
            originX, originY, originZ, sizeX, sizeY, sizeZ, frequency, actual
        );
        for (int index = 0; index < total; index++) {
            float tolerance = 1.0e-5f;
            if (Math.abs(expected[index] - actual[index]) > tolerance) {
                throw new IllegalStateException(
                    "Vulkan noise self-test mismatch at index " + index
                        + ": expected=" + expected[index]
                        + ", actual=" + actual[index]
                );
            }
        }
    }

    private static String normalize(String message) {
        return message == null || message.isBlank() ? "unknown" : message.trim();
    }

    @FunctionalInterface
    interface GpuBackendFactory {
        ManagedNoiseComputeBackend create();
    }

    public enum InitializationState {
        INITIALIZING,
        READY,
        UNAVAILABLE,
        CLOSED
    }

    public record Status(
        boolean gpuAvailable,
        InitializationState initializationState,
        long initializationDurationMillis,
        String deviceName,
        String transferMode,
        int minimumBatchSize,
        long cpuBatches,
        long gpuBatches,
        long gpuFailures,
        long cpuVoxels,
        long gpuVoxels,
        String unavailableReason
    ) {
    }
}
