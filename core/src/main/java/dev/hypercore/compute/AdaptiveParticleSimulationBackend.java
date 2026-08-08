package dev.hypercore.compute;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Routes particle/projectile physics simulation to Vulkan when ready and keeps
 * a deterministic CPU path available.  Mirrors the adaptive pattern of
 * {@link AdaptiveNoiseComputeBackend} but for the particle workload.
 */
public final class AdaptiveParticleSimulationBackend implements ParticleSimulationBackend, AutoCloseable {
    public static final String VULKAN_ID = "adaptive-vulkan-particle";
    private static final Logger LOGGER = LoggerFactory.getLogger(AdaptiveParticleSimulationBackend.class);
    private static final long INITIALIZER_JOIN_MILLIS = 5_000L;

    private final ParticleSimulationBackend cpu;
    private final GpuOffloadPolicy policy;
    private final AtomicLong cpuBatches = new AtomicLong();
    private final AtomicLong gpuBatches = new AtomicLong();
    private final AtomicLong gpuFailures = new AtomicLong();
    private final AtomicLong cpuParticles = new AtomicLong();
    private final AtomicLong gpuParticles = new AtomicLong();
    private final CountDownLatch initializationComplete;
    private final GpuBackendFactory gpuFactory;
    private final long initializationStartedNanos;
    private volatile long initializationFinishedNanos;
    private volatile InitializationState initializationState;
    private volatile ManagedParticleSimulationBackend gpu;
    private volatile String unavailableReason;
    private volatile Thread initializer;
    private volatile boolean closed;

    private AdaptiveParticleSimulationBackend(
        GpuOffloadPolicy policy,
        ManagedParticleSimulationBackend gpu,
        InitializationState state,
        String unavailableReason,
        GpuBackendFactory gpuFactory,
        ParticleSimulationBackend cpu
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
    public static AdaptiveParticleSimulationBackend create(GpuOffloadPolicy policy, boolean enabled) {
        return create(policy, enabled, new ScalarParticleSimulationBackend());
    }

    public static AdaptiveParticleSimulationBackend create(
        GpuOffloadPolicy policy, boolean enabled, ParticleSimulationBackend cpu
    ) {
        if (!enabled) {
            return unavailable(policy, "disabled by configuration", cpu);
        }
        AdaptiveParticleSimulationBackend backend = new AdaptiveParticleSimulationBackend(
            policy, null, InitializationState.INITIALIZING, "", VulkanSpatialComputeBackend::create, cpu);
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        Thread initializer = new Thread(() -> {
            Thread.currentThread().setContextClassLoader(contextLoader);
            backend.initializeGpu();
        }, "HyperCore-Vulkan-Particle-Init");
        initializer.setDaemon(true);
        backend.initializer = initializer;
        initializer.start();
        return backend;
    }

    public static AdaptiveParticleSimulationBackend unavailable(GpuOffloadPolicy policy, String reason) {
        return unavailable(policy, reason, new ScalarParticleSimulationBackend());
    }

    public static AdaptiveParticleSimulationBackend unavailable(
        GpuOffloadPolicy policy, String reason, ParticleSimulationBackend cpu
    ) {
        return new AdaptiveParticleSimulationBackend(policy, null, InitializationState.UNAVAILABLE, reason, null, cpu);
    }

    static AdaptiveParticleSimulationBackend createForTesting(GpuOffloadPolicy policy, GpuBackendFactory gpuFactory) {
        return createForTesting(policy, gpuFactory, new ScalarParticleSimulationBackend());
    }

    static AdaptiveParticleSimulationBackend createForTesting(
        GpuOffloadPolicy policy, GpuBackendFactory gpuFactory, ParticleSimulationBackend cpu
    ) {
        AdaptiveParticleSimulationBackend backend = new AdaptiveParticleSimulationBackend(
            policy, null, InitializationState.INITIALIZING, "",
            Objects.requireNonNull(gpuFactory, "gpuFactory"), cpu);
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        Thread initializer = new Thread(() -> {
            Thread.currentThread().setContextClassLoader(contextLoader);
            backend.initializeGpu();
        }, "HyperCore-Vulkan-Particle-Init-Test");
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
        float[] positions, float[] velocities,
        int count,
        float gravity, float dt, float restitution
    ) {
        ParticleSimulationBackend.validate(positions, velocities, count, gravity, dt, restitution);
        ManagedParticleSimulationBackend currentGpu = gpu;
        GpuOffloadPolicy.Decision decision = policy.evaluate(count, currentGpu != null, currentGpu != null);
        if (decision.offload()) {
            try {
                synchronized (this) {
                    if (closed || gpu != currentGpu) {
                        throw new IllegalStateException("Vulkan particle backend is closing");
                    }
                    currentGpu.simulate(positions, velocities, count, gravity, dt, restitution);
                }
                gpuBatches.incrementAndGet();
                gpuParticles.addAndGet(count);
                return;
            } catch (VulkanSpatialComputeBackend.BatchNotSupportedException unsupported) {
                // Fall through to CPU
            } catch (RuntimeException | LinkageError error) {
                disableGpu(currentGpu, error);
            }
        }

        cpu.simulate(positions, velocities, count, gravity, dt, restitution);
        cpuBatches.incrementAndGet();
        cpuParticles.addAndGet(count);
    }

    public synchronized Status status() {
        ManagedParticleSimulationBackend currentGpu = gpu;
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
            cpuParticles.get(),
            gpuParticles.get(),
            unavailableReason
        );
    }

    boolean awaitInitialization(long timeout, TimeUnit unit) throws InterruptedException {
        return initializationComplete.await(timeout, unit);
    }

    @Override
    public void close() {
        Thread pendingInitializer;
        ManagedParticleSimulationBackend currentGpu;
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
                LOGGER.warn("Interrupted while waiting for particle Vulkan initialization to stop");
            }
        }
        if (currentGpu != null) {
            closeGpu(currentGpu);
        }
    }

    private void initializeGpu() {
        ManagedParticleSimulationBackend created = null;
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
            LOGGER.info("Vulkan particle compute initialized asynchronously on {}", created.deviceName());
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
                LOGGER.warn("Vulkan particle compute initialization failed; using {}: {}", cpu.id(), reason, error);
            }
        } finally {
            initializationComplete.countDown();
        }
    }

    private synchronized void disableGpu(ManagedParticleSimulationBackend failedGpu, Throwable error) {
        if (gpu != failedGpu || closed) {
            return;
        }
        gpu = null;
        initializationState = InitializationState.UNAVAILABLE;
        gpuFailures.incrementAndGet();
        unavailableReason = error.getClass().getSimpleName() + ": " + normalize(error.getMessage());
        LOGGER.error("Vulkan particle compute failed and has been disabled; using {}", cpu.id(), error);
        closeGpu(failedGpu);
    }

    private static void closeGpu(ManagedParticleSimulationBackend backend) {
        try {
            backend.close();
        } catch (RuntimeException | LinkageError closeError) {
            LOGGER.warn("Vulkan particle compute cleanup failed", closeError);
        }
    }

    private static void verify(ParticleSimulationBackend gpu) {
        int count = 256;
        float gravity = 9.8f;
        float dt = 0.05f;
        float restitution = 0.6f;
        float[] expectedPositions = new float[count * 3];
        float[] expectedVelocities = new float[count * 3];
        float[] actualPositions = new float[count * 3];
        float[] actualVelocities = new float[count * 3];
        for (int i = 0; i < count; i++) {
            int base = i * 3;
            // Deterministic initial state: varying positions (y > 0) and velocities.
            expectedPositions[base + 0] = actualPositions[base + 0] = i * 0.5f;
            expectedPositions[base + 1] = actualPositions[base + 1] = (i % 32) * 0.25f + 1.0f;
            expectedPositions[base + 2] = actualPositions[base + 2] = (i % 17) * 0.75f;
            expectedVelocities[base + 0] = actualVelocities[base + 0] = (i % 5) * 1.5f;
            expectedVelocities[base + 1] = actualVelocities[base + 1] = -((i % 7) * 2.0f + 1.0f);
            expectedVelocities[base + 2] = actualVelocities[base + 2] = (i % 3) * 0.5f;
        }

        new ScalarParticleSimulationBackend().simulate(expectedPositions, expectedVelocities, count, gravity, dt, restitution);
        gpu.simulate(actualPositions, actualVelocities, count, gravity, dt, restitution);
        for (int index = 0; index < count * 3; index++) {
            float tolerance = 1.0e-5f;
            if (Math.abs(expectedPositions[index] - actualPositions[index]) > tolerance) {
                throw new IllegalStateException(
                    "Vulkan particle self-test position mismatch at index " + index
                        + ": expected=" + expectedPositions[index]
                        + ", actual=" + actualPositions[index]
                );
            }
            if (Math.abs(expectedVelocities[index] - actualVelocities[index]) > tolerance) {
                throw new IllegalStateException(
                    "Vulkan particle self-test velocity mismatch at index " + index
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
        ManagedParticleSimulationBackend create();
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
        long cpuParticles,
        long gpuParticles,
        String unavailableReason
    ) {
    }
}
