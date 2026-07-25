package dev.hypercore.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class HyperCoreConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.IntValue WORKER_THREADS = BUILDER
        .comment("Worker threads for isolated asynchronous work. Use 0 to reserve one logical processor automatically.")
        .defineInRange("execution.workerThreads", 0, 0, 512);

    private static final ForgeConfigSpec.IntValue QUEUE_CAPACITY = BUILDER
        .comment("Maximum queued asynchronous tasks. Use 0 to select 64 tasks per worker with a minimum of 256.")
        .defineInRange("execution.queueCapacity", 0, 0, 1_048_576);

    private static final ForgeConfigSpec.IntValue TICK_SAMPLE_WINDOW = BUILDER
        .comment("Number of recent server ticks retained for latency diagnostics.")
        .defineInRange("metrics.tickSampleWindow", 200, 20, 20_000);

    private static final ForgeConfigSpec.BooleanValue PROBE_GPU = BUILDER
        .comment("Probe graphics adapters during server startup. Probe failures never prevent startup.")
        .define("compute.probeGpu", true);

    private static final ForgeConfigSpec.IntValue GPU_MINIMUM_BATCH_SIZE = BUILDER
        .comment("Minimum element count considered for a future GPU compute offload.")
        .defineInRange("compute.gpuMinimumBatchSize", 16_384, 256, 16_777_216);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private HyperCoreConfig() {
    }

    public static Settings settings() {
        return new Settings(
            WORKER_THREADS.get(),
            QUEUE_CAPACITY.get(),
            TICK_SAMPLE_WINDOW.get(),
            PROBE_GPU.get(),
            GPU_MINIMUM_BATCH_SIZE.get()
        );
    }

    public record Settings(
        int workerThreads,
        int queueCapacity,
        int tickSampleWindow,
        boolean probeGpu,
        int gpuMinimumBatchSize
    ) {
        public Settings {
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
}
