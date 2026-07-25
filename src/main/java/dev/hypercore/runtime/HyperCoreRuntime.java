package dev.hypercore.runtime;

import dev.hypercore.compute.ScalarSpatialComputeBackend;
import dev.hypercore.compute.SpatialComputeBackend;
import dev.hypercore.concurrent.HyperCoreExecutor;
import dev.hypercore.config.HyperCoreConfig;
import dev.hypercore.hardware.RuntimeCapabilities;
import dev.hypercore.metrics.TickMetrics;

public final class HyperCoreRuntime implements AutoCloseable {
    private final SpatialComputeBackend computeBackend = new ScalarSpatialComputeBackend();
    private volatile State state;

    public synchronized void start(HyperCoreConfig.Settings settings) {
        if (state != null) {
            throw new IllegalStateException("HyperCore runtime is already started");
        }

        RuntimeCapabilities capabilities = RuntimeCapabilities.detect(settings.probeGpu());
        int workers = settings.resolveWorkerThreads(capabilities.logicalProcessors());
        int queueCapacity = settings.resolveQueueCapacity(workers);
        state = new State(
            HyperCoreExecutor.create(workers, queueCapacity),
            new TickMetrics(settings.tickSampleWindow()),
            capabilities
        );
    }

    public boolean isStarted() {
        return state != null;
    }

    public HyperCoreExecutor executor() {
        return requireState().executor();
    }

    public TickMetrics tickMetrics() {
        return requireState().tickMetrics();
    }

    public RuntimeCapabilities capabilities() {
        return requireState().capabilities();
    }

    public SpatialComputeBackend computeBackend() {
        return computeBackend;
    }

    public Status status() {
        State current = requireState();
        HyperCoreExecutor executor = current.executor();
        Runtime runtime = Runtime.getRuntime();
        return new Status(
            executor.parallelism(),
            executor.activeTasks(),
            executor.queuedTasks(),
            executor.queueCapacity(),
            executor.submittedTasks(),
            executor.completedTasks(),
            executor.rejectedTasks(),
            runtime.totalMemory() - runtime.freeMemory(),
            runtime.maxMemory(),
            computeBackend.id()
        );
    }

    @Override
    public synchronized void close() {
        State current = state;
        state = null;
        if (current != null) {
            current.executor().close();
        }
    }

    private State requireState() {
        State current = state;
        if (current == null) {
            throw new IllegalStateException("HyperCore runtime is not started");
        }
        return current;
    }

    private record State(
        HyperCoreExecutor executor,
        TickMetrics tickMetrics,
        RuntimeCapabilities capabilities
    ) {
    }

    public record Status(
        int workers,
        int activeTasks,
        int queuedTasks,
        int queueCapacity,
        long submittedTasks,
        long completedTasks,
        long rejectedTasks,
        long usedHeapBytes,
        long maximumHeapBytes,
        String computeBackend
    ) {
    }
}
