package dev.hypercore.config;

import dev.hypercore.config.HyperCoreSettings;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * Forge-side configuration adapter.
 *
 * <p>Binds the loader-agnostic {@link HyperCoreSettings} to a ForgeConfigSpec so
 * server operators configure HyperCore through the standard Forge config file.
 * The validation and resolution logic lives on {@code HyperCoreSettings} in
 * core; this class only reads the Forge config values and assembles the record.
 */
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

    private static final ForgeConfigSpec.BooleanValue ENABLE_GPU = BUILDER
        .comment("Enable the Vulkan compute backend when initialization and correctness self-tests succeed.")
        .define("compute.enableGpu", true);

    private static final ForgeConfigSpec.IntValue GPU_MINIMUM_BATCH_SIZE = BUILDER
        .comment("Minimum element count eligible for Vulkan compute offload.")
        .defineInRange("compute.gpuMinimumBatchSize", 16_384, 256, 16_777_216);

    private static final ForgeConfigSpec.ConfigValue<String> CPU_BACKEND = BUILDER
        .comment(
            "CPU spatial compute backend used when work is not offloaded to Vulkan.",
            "auto selects the Java Vector API backend when jdk.incubator.vector is available",
            "and falls back to scalar otherwise; scalar and vector force that backend",
            "(vector falls back to scalar if the incubator module is unavailable)."
        )
        .defineInList("compute.cpuBackend", "auto", List.of("auto", "scalar", "vector"));

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private HyperCoreConfig() {
    }

    public static HyperCoreSettings settings() {
        return new HyperCoreSettings(
            WORKER_THREADS.get(),
            QUEUE_CAPACITY.get(),
            TICK_SAMPLE_WINDOW.get(),
            PROBE_GPU.get(),
            ENABLE_GPU.get(),
            GPU_MINIMUM_BATCH_SIZE.get(),
            CPU_BACKEND.get()
        );
    }
}
