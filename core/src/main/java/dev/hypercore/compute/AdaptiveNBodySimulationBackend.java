package dev.hypercore.compute;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Routes gravitational N-body simulation to Vulkan when ready and keeps a
 * deterministic CPU path available. Mirrors the adaptive pattern of
 * {@link AdaptiveParticleSimulationBackend} but for the O(n^2) N-body workload.
 *
 * <p>N-body is a denser workload than projectile physics: each body interacts
 * with every other body, so the GPU advantage grows with body count. The
 * self-test tolerance is looser than the particle backend because GPU
 * {@code inversesqrt} and CPU {@code Math.sqrt} are not guaranteed bit-identical.
 */
public final class AdaptiveNBodySimulationBackend implements NBodySimulationBackend, AutoCloseable {
    public static final String VULKAN_ID = "adaptive-vulkan-nbody";
    private static final Logger LOGGER = LoggerFactory.getLogger(AdaptiveNBodySimulationBackend.class);
    private static final long INITIALIZER_JOIN_MILLIS = 5_000L;

    private final NBodySimulationBackend cpu;
    private final GpuOffloadPolicy policy;
    private final AtomicLong cpuBatches = new AtomicLong();
    private final AtomicLong gpuBatches = new AtomicLong();
    private final AtomicLong gpuFailures = new AtomicLong();
    private final AtomicLong cpuBodies = new AtomicLong();
    private final AtomicLong gpuBodies = new AtomicLong();
    private final CountDownLatch initializationComplete;
    private final GpuBackendFactory gpuFactory;
    private final long initializationStartedNanos;
    private volatile long initializationFinishedNanos;
    private volatile InitializationState initializationState;
    private volatile ManagedNBodySimulationBackend gpu;
    private volatile String unavailableReason;
    private volatile Thread initializer;
    private volatile boolean closed;

    private AdaptiveNBodySimulationBackend(
        GpuOffloadPolicy policy,
        ManagedNBodySimulationBackend gpu,
        InitializationState state,
        String unavailableReason,
        GpuBackendFactory gpuFactory,
        NBodySimulationBackend cpu
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
    public static AdaptiveNBodySimulationBackend create(GpuOffloadPolicy policy, boolean enabled) {
        return create(policy, enabled, new ScalarNBodySimulationBackend());
    }

    public static AdaptiveNBodySimulationBackend create(
        GpuOffloadPolicy policy, boolean enabled, NBodySimulationBackend cpu
    ) {
        if (!enabled) {
            return unavailable(policy, "disabled by configuration", cpu);
        }
        AdaptiveNBodySimulationBackend backend = new AdaptiveNBodySimulationBackend(
            policy, null, InitializationState.INITIALIZING, "", VulkanSpatialComputeBackend::create, cpu);
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        Thread initializer = new Thread(() -> {
            Thread.currentThread().setContextClassLoader(contextLoader);
            backend.initializeGpu();
        }, "HyperCore-Vulkan-NBody-Init");
        initializer.setDaemon(true);
        backend.initializer = initializer;
        initializer.start();
        return backend;
    }

    public static AdaptiveNBodySimulationBackend unavailable(GpuOffloadPolicy policy, String reason) {
        return unavailable(policy, reason, new ScalarNBodySimulationBackend());
    }

    public static AdaptiveNBodySimulationBackend unavailable(
        GpuOffloadPolicy policy, String reason, NBodySimulationBackend cpu
    ) {
        return new AdaptiveNBodySimulationBackend(policy, null, InitializationState.UNAVAILABLE, reason, null, cpu);
    }

    static AdaptiveNBodySimulationBackend createForTesting(GpuOffloadPolicy policy, GpuBackendFactory gpuFactory) {
        return createForTesting(policy, gpuFactory, new ScalarNBodySimulationBackend());
    }

    static AdaptiveNBodySimulationBackend createForTesting(
        GpuOffloadPolicy policy, GpuBackendFactory gpuFactory, NBodySimulationBackend cpu
    ) {
        AdaptiveNBodySimulationBackend backend = new AdaptiveNBodySimulationBackend(
            policy, null, InitializationState.INITIALIZING, "",
            Objects.requireNonNull(gpuFactory, "gpuFactory"), cpu);
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        Thread initializer = new Thread(() -> {
            Thread.currentThread().setContextClassLoader(contextLoader);
            backend.initializeGpu();
        }, "HyperCore-Vulkan-NBody-Init-Test");
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
    public void simulate(
        float[] positions, float[] velocities, float[] masses,
        int count,
        float gravityConstant, float dt, float softening
    ) {
        NBodySimulationBackend.validate(positions, velocities, masses, count, gravityConstant, dt, softening);
        ManagedNBodySimulationBackend currentGpu = gpu;
        GpuOffloadPolicy.Decision decision = policy.evaluate(count, currentGpu != null, currentGpu != null);
        if (decision.offload()) {
            try {
                synchronized (this) {
                    if (closed || gpu != currentGpu) {
                        throw new IllegalStateException("Vulkan N-body backend is closing");
                    }
                    currentGpu.simulate(positions, velocities, masses, count, gravityConstant, dt, softening);
                }
                gpuBatches.incrementAndGet();
                gpuBodies.addAndGet(count);
                return;
            } catch (VulkanSpatialComputeBackend.BatchNotSupportedException unsupported) {
                // Fall through to CPU
            } catch (RuntimeException | LinkageError error) {
                disableGpu(currentGpu, error);
            }
        }

        cpu.simulate(positions, velocities, masses, count, gravityConstant, dt, softening);
        cpuBatches.incrementAndGet();
        cpuBodies.addAndGet(count);
    }

    public synchronized Status status() {
        ManagedNBodySimulationBackend currentGpu = gpu;
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
            cpuBodies.get(),
            gpuBodies.get(),
            unavailableReason
        );
    }

    boolean awaitInitialization(long timeout, TimeUnit unit) throws InterruptedException {
        return initializationComplete.await(timeout, unit);
    }

    @Override
    public void close() {
        Thread pendingInitializer;
        ManagedNBodySimulationBackend currentGpu;
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
                LOGGER.warn("Interrupted while waiting for N-body Vulkan initialization to stop");
            }
        }
        if (currentGpu != null) {
            closeGpu(currentGpu);
        }
    }

    private void initializeGpu() {
        ManagedNBodySimulationBackend created = null;
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
            LOGGER.info("Vulkan N-body compute initialized asynchronously on {}", created.deviceName());
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
                LOGGER.warn("Vulkan N-body compute initialization failed; using {}: {}", cpu.id(), reason, error);
            }
        } finally {
            initializationComplete.countDown();
        }
    }

    private synchronized void disableGpu(ManagedNBodySimulationBackend failedGpu, Throwable error) {
        if (gpu != failedGpu || closed) {
            return;
        }
        gpu = null;
        initializationState = InitializationState.UNAVAILABLE;
        gpuFailures.incrementAndGet();
        unavailableReason = error.getClass().getSimpleName() + ": " + normalize(error.getMessage());
        LOGGER.error("Vulkan N-body compute failed and has been disabled; using {}", cpu.id(), error);
        closeGpu(failedGpu);
    }

    private static void closeGpu(ManagedNBodySimulationBackend backend) {
        try {
            backend.close();
        } catch (RuntimeException | LinkageError closeError) {
            LOGGER.warn("Vulkan N-body compute cleanup failed", closeError);
        }
    }

    /**
     * Compares the GPU backend against the scalar CPU oracle on a small
     * deterministic case. Tolerance is looser than the particle backend because
     * GPU {@code inversesqrt} and CPU {@code Math.sqrt} are not bit-identical.
     */
    private static void verify(NBodySimulationBackend gpu) {
        int count = 128;
        float gravityConstant = 0.5f;
        float dt = 0.05f;
        float softening = 1.0f;
        float[] expectedPositions = new float[count * 3];
        float[] expectedVelocities = new float[count * 3];
        float[] actualPositions = new float[count * 3];
        float[] actualVelocities = new float[count * 3];
        float[] masses = new float[count];
        for (int i = 0; i < count; i++) {
            int base = i * 3;
            // Deterministic, well-separated initial state so forces stay bounded.
            expectedPositions[base + 0] = actualPositions[base + 0] = (i * 7) % 64;
            expectedPositions[base + 1] = actualPositions[base + 1] = (i * 13) % 48;
            expectedPositions[base + 2] = actualPositions[base + 2] = (i * 19) % 64;
            expectedVelocities[base + 0] = actualVelocities[base + 0] = ((i % 5) - 2) * 0.3f;
            expectedVelocities[base + 1] = actualVelocities[base + 1] = ((i % 7) - 3) * 0.2f;
            expectedVelocities[base + 2] = actualVelocities[base + 2] = ((i % 4) - 2) * 0.3f;
            masses[i] = 1.0f + (i % 3) * 0.5f;
        }

        new ScalarNBodySimulationBackend().simulate(expectedPositions, expectedVelocities, masses, count, gravityConstant, dt, softening);
        gpu.simulate(actualPositions, actualVelocities, masses, count, gravityConstant, dt, softening);
        float tolerance = 5.0e-3f;
        for (int index = 0; index < count * 3; index++) {
            if (!Float.isFinite(actualPositions[index]) || !Float.isFinite(actualVelocities[index])) {
                throw new IllegalStateException(
                    "Vulkan N-body self-test produced non-finite value at index " + index
                );
            }
            if (Math.abs(expectedPositions[index] - actualPositions[index]) > tolerance) {
                throw new IllegalStateException(
                    "Vulkan N-body self-test position mismatch at index " + index
                        + ": expected=" + expectedPositions[index]
                        + ", actual=" + actualPositions[index]
                );
            }
            if (Math.abs(expectedVelocities[index] - actualVelocities[index]) > tolerance) {
                throw new IllegalStateException(
                    "Vulkan N-body self-test velocity mismatch at index " + index
                        + ": expected=" + expectedVelocities[index]
                        + ", actual=" + actualVelocities[index]
                );
            }
        }
    }

    private static String normalize(String message) {
        return message == null || message.isBlank() ? "unknown" : message.trim();
    }

    @FunctionalInterface
    interface GpuBackendFactory {
        ManagedNBodySimulationBackend create();
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
        long cpuBodies,
        long gpuBodies,
        String unavailableReason
    ) {
    }
}
