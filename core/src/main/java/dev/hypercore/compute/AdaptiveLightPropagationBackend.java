package dev.hypercore.compute;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Routes voxel light propagation to Vulkan when ready and keeps a
 * deterministic CPU path available. Mirrors the adaptive pattern of
 * {@link AdaptiveNBodySimulationBackend} but for the iterative flood-fill
 * light propagation workload.
 *
 * <p>Light propagation is an iterative relaxation over a 3D voxel grid where
 * each transparent voxel takes the maximum of its neighbors' light minus one.
 * The GPU advantage grows with grid volume. The self-test tolerance is zero
 * because both the GPU shader and the CPU oracle use integer arithmetic, so
 * results must be bit-identical.
 */
public final class AdaptiveLightPropagationBackend implements LightPropagationBackend, AutoCloseable {
    public static final String VULKAN_ID = "adaptive-vulkan-light";
    private static final Logger LOGGER = LoggerFactory.getLogger(AdaptiveLightPropagationBackend.class);
    private static final long INITIALIZER_JOIN_MILLIS = 5_000L;

    private final LightPropagationBackend cpu;
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
    private volatile ManagedLightPropagationBackend gpu;
    private volatile String unavailableReason;
    private volatile Thread initializer;
    private volatile boolean closed;

    private AdaptiveLightPropagationBackend(
        GpuOffloadPolicy policy,
        ManagedLightPropagationBackend gpu,
        InitializationState state,
        String unavailableReason,
        GpuBackendFactory gpuFactory,
        LightPropagationBackend cpu
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
    public static AdaptiveLightPropagationBackend create(GpuOffloadPolicy policy, boolean enabled) {
        return create(policy, enabled, new ScalarLightPropagationBackend());
    }

    public static AdaptiveLightPropagationBackend create(
        GpuOffloadPolicy policy, boolean enabled, LightPropagationBackend cpu
    ) {
        if (!enabled) {
            return unavailable(policy, "disabled by configuration", cpu);
        }
        AdaptiveLightPropagationBackend backend = new AdaptiveLightPropagationBackend(
            policy, null, InitializationState.INITIALIZING, "", VulkanSpatialComputeBackend::create, cpu);
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        Thread initializer = new Thread(() -> {
            Thread.currentThread().setContextClassLoader(contextLoader);
            backend.initializeGpu();
        }, "HyperCore-Vulkan-Light-Init");
        initializer.setDaemon(true);
        backend.initializer = initializer;
        initializer.start();
        return backend;
    }

    public static AdaptiveLightPropagationBackend unavailable(GpuOffloadPolicy policy, String reason) {
        return unavailable(policy, reason, new ScalarLightPropagationBackend());
    }

    public static AdaptiveLightPropagationBackend unavailable(
        GpuOffloadPolicy policy, String reason, LightPropagationBackend cpu
    ) {
        return new AdaptiveLightPropagationBackend(policy, null, InitializationState.UNAVAILABLE, reason, null, cpu);
    }

    static AdaptiveLightPropagationBackend createForTesting(GpuOffloadPolicy policy, GpuBackendFactory gpuFactory) {
        return createForTesting(policy, gpuFactory, new ScalarLightPropagationBackend());
    }

    static AdaptiveLightPropagationBackend createForTesting(
        GpuOffloadPolicy policy, GpuBackendFactory gpuFactory, LightPropagationBackend cpu
    ) {
        AdaptiveLightPropagationBackend backend = new AdaptiveLightPropagationBackend(
            policy, null, InitializationState.INITIALIZING, "",
            Objects.requireNonNull(gpuFactory, "gpuFactory"), cpu);
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        Thread initializer = new Thread(() -> {
            Thread.currentThread().setContextClassLoader(contextLoader);
            backend.initializeGpu();
        }, "HyperCore-Vulkan-Light-Init-Test");
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
    public void propagate(int[] light, int[] opacity, int dimX, int dimY, int dimZ, int iterations) {
        LightPropagationBackend.validate(light, opacity, dimX, dimY, dimZ, iterations);
        ManagedLightPropagationBackend currentGpu = gpu;
        int volume = dimX * dimY * dimZ;
        GpuOffloadPolicy.Decision decision = policy.evaluate(volume, currentGpu != null, currentGpu != null);
        if (decision.offload()) {
            try {
                synchronized (this) {
                    if (closed || gpu != currentGpu) {
                        throw new IllegalStateException("Vulkan light backend is closing");
                    }
                    currentGpu.propagate(light, opacity, dimX, dimY, dimZ, iterations);
                }
                gpuBatches.incrementAndGet();
                gpuVoxels.addAndGet(volume);
                return;
            } catch (VulkanSpatialComputeBackend.BatchNotSupportedException unsupported) {
                // Fall through to CPU
            } catch (RuntimeException | LinkageError error) {
                disableGpu(currentGpu, error);
            }
        }

        cpu.propagate(light, opacity, dimX, dimY, dimZ, iterations);
        cpuBatches.incrementAndGet();
        cpuVoxels.addAndGet(volume);
    }

    public synchronized Status status() {
        ManagedLightPropagationBackend currentGpu = gpu;
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
        ManagedLightPropagationBackend currentGpu;
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
                LOGGER.warn("Interrupted while waiting for light Vulkan initialization to stop");
            }
        }
        if (currentGpu != null) {
            closeGpu(currentGpu);
        }
    }

    private void initializeGpu() {
        ManagedLightPropagationBackend created = null;
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
            LOGGER.info("Vulkan light compute initialized asynchronously on {}", created.deviceName());
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
                LOGGER.warn("Vulkan light compute initialization failed; using {}: {}", cpu.id(), reason, error);
            }
        } finally {
            initializationComplete.countDown();
        }
    }

    private synchronized void disableGpu(ManagedLightPropagationBackend failedGpu, Throwable error) {
        if (gpu != failedGpu || closed) {
            return;
        }
        gpu = null;
        initializationState = InitializationState.UNAVAILABLE;
        gpuFailures.incrementAndGet();
        unavailableReason = error.getClass().getSimpleName() + ": " + normalize(error.getMessage());
        LOGGER.error("Vulkan light compute failed and has been disabled; using {}", cpu.id(), error);
        closeGpu(failedGpu);
    }

    private static void closeGpu(ManagedLightPropagationBackend backend) {
        try {
            backend.close();
        } catch (RuntimeException | LinkageError closeError) {
            LOGGER.warn("Vulkan light compute cleanup failed", closeError);
        }
    }

    /**
     * Compares the GPU backend against the scalar CPU oracle on a small
     * deterministic case. Tolerance is zero because both the GPU shader and
     * the CPU oracle use integer arithmetic, so results must be bit-identical.
     */
    private static void verify(LightPropagationBackend gpu) {
        int dimX = 8;
        int dimY = 8;
        int dimZ = 8;
        int iterations = dimX + dimY + dimZ - 3;
        int volume = dimX * dimY * dimZ;
        int[] expectedLight = new int[volume];
        int[] actualLight = new int[volume];
        int[] opacity = new int[volume];
        // Single light source at the grid center with full brightness.
        int centerX = 4;
        int centerY = 4;
        int centerZ = 4;
        int centerIdx = centerX + centerY * dimX + centerZ * dimX * dimY;
        expectedLight[centerIdx] = 15;
        actualLight[centerIdx] = 15;

        new ScalarLightPropagationBackend().propagate(expectedLight, opacity, dimX, dimY, dimZ, iterations);
        gpu.propagate(actualLight, opacity, dimX, dimY, dimZ, iterations);
        for (int index = 0; index < volume; index++) {
            if (actualLight[index] < 0 || actualLight[index] > 15) {
                throw new IllegalStateException(
                    "Vulkan light self-test produced out-of-range value at index " + index
                        + ": actual=" + actualLight[index]
                );
            }
            if (expectedLight[index] != actualLight[index]) {
                throw new IllegalStateException(
                    "Vulkan light self-test mismatch at index " + index
                        + ": expected=" + expectedLight[index]
                        + ", actual=" + actualLight[index]
                );
            }
        }
    }

    private static String normalize(String message) {
        return message == null || message.isBlank() ? "unknown" : message.trim();
    }

    @FunctionalInterface
    interface GpuBackendFactory {
        ManagedLightPropagationBackend create();
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
