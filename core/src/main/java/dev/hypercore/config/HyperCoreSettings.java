package dev.hypercore.config;

import java.util.List;

/**
 * Loader-agnostic runtime settings shared by the Forge and Fabric adapters.
 *
 * <p>Each loader builds this record from its own configuration mechanism
 * (ForgeConfigSpec on Forge, a properties file on Fabric) and passes it to
 * {@code HyperCoreRuntime.start}. Keeping the record in core lets every
 * loader-agnostic component depend on a single settings type without pulling in
 * a loader-specific config API.
 */
public record HyperCoreSettings(
    int workerThreads,
    int queueCapacity,
    int tickSampleWindow,
    boolean probeGpu,
    boolean enableGpu,
    int gpuMinimumBatchSize,
    String cpuBackend
) {
    public static final List<String> CPU_BACKENDS = List.of("auto", "scalar", "vector");

    public HyperCoreSettings {
        if (workerThreads < 0) {
            throw new IllegalArgumentException("workerThreads cannot be negative");
        }
        if (queueCapacity < 0) {
            throw new IllegalArgumentException("queueCapacity cannot be negative");
        }
        if (tickSampleWindow < 1) {
            throw new IllegalArgumentException("tickSampleWindow must be positive");
        }
        if (gpuMinimumBatchSize < 1) {
            throw new IllegalArgumentException("gpuMinimumBatchSize must be positive");
        }
        if (cpuBackend == null || !CPU_BACKENDS.contains(cpuBackend)) {
            throw new IllegalArgumentException("cpuBackend must be auto, scalar, or vector");
        }
    }

    public int resolveWorkerThreads(int logicalProcessors) {
        if (logicalProcessors < 1) {
            throw new IllegalArgumentException("logicalProcessors must be positive");
        }
        return workerThreads == 0 ? Math.max(1, logicalProcessors - 1) : workerThreads;
    }

    public int resolveQueueCapacity(int resolvedWorkerThreads) {
        if (resolvedWorkerThreads < 1) {
            throw new IllegalArgumentException("resolvedWorkerThreads must be positive");
        }
        return queueCapacity == 0 ? Math.max(256, resolvedWorkerThreads * 64) : queueCapacity;
    }
}
