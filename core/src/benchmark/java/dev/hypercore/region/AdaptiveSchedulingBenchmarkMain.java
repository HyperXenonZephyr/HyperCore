package dev.hypercore.region;

import dev.hypercore.concurrent.HyperCoreExecutor;
import dev.hypercore.plugin.PluginEventBus;
import dev.hypercore.world.NoOpWorldAccessFactory;
import dev.hypercore.world.RegionExecutionService;
import dev.hypercore.world.RegionTickTask;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Compares static hash-based region scheduling against adaptive per-owner
 * remapping under a deliberately skewed workload.
 *
 * <p>A fixed set of regions is partitioned so that, under the static hash, one
 * owner receives all "heavy" regions while the other owners sit nearly idle.
 * The static coordinator therefore bottlenecks on the overloaded owner every
 * tick. The adaptive coordinator observes the per-owner timing imbalance and,
 * after a few remap intervals, migrates roughly half of the heavy regions to
 * idle owners, cutting the per-tick wall-clock latency.
 *
 * <p>The benchmark reports p50/p95/mean tick latency for both modes and the
 * adaptive speedup. It runs on the CPU only (no GPU required).
 */
public final class AdaptiveSchedulingBenchmarkMain {
    private static final int OWNERS = 4;
    private static final int HEAVY_REGIONS_PER_OWNER_ZERO = 6;
    private static final int LIGHT_REGIONS_PER_OTHER_OWNER = 6;
    private static final int WARMUP_TICKS = 96;
    private static final int MEASURE_TICKS = 64;
    // Busy-loop iterations per heavy region. Calibrated so one heavy region
    // takes roughly half a millisecond, making the overloaded-owner bottleneck
    // dominate scheduling overhead.
    private static final int HEAVY_BUSY_ITERATIONS = 100_000;
    private static final String WORLD = "benchmark";

    private AdaptiveSchedulingBenchmarkMain() {
    }

    public static void main(String[] arguments) throws Exception {
        String outputPath = arguments.length >= 1
            ? arguments[0]
            : "core/build/reports/hypercore/adaptive-scheduling-benchmark.md";
        Path output = Path.of(outputPath);

        List<RegionKey> heavyRegions = new ArrayList<>();
        List<RegionKey> lightRegions = new ArrayList<>();
        selectSkewedRegions(heavyRegions, lightRegions);

        ModeResult staticResult = runMode(false, heavyRegions, lightRegions);
        ModeResult adaptiveResult = runMode(true, heavyRegions, lightRegions);

        String markdown = renderMarkdown(staticResult, adaptiveResult, heavyRegions.size(), lightRegions.size());
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, markdown, StandardCharsets.UTF_8);
        System.out.println(markdown);
    }

    /**
     * Probes the static hash to find {@code OWNERS} buckets of regions, then
     * selects all heavy regions from owner 0 and light regions from owners 1+
     * so the static assignment overloads owner 0.
     */
    private static void selectSkewedRegions(List<RegionKey> heavy, List<RegionKey> light) {
        HyperCoreExecutor probeExecutor = HyperCoreExecutor.create(1, 16);
        RegionTaskCoordinator probe = new RegionTaskCoordinator(probeExecutor, OWNERS, false);
        List<List<RegionKey>> buckets = new ArrayList<>(OWNERS);
        for (int owner = 0; owner < OWNERS; owner++) {
            buckets.add(new ArrayList<>());
        }
        int maxPerBucket = HEAVY_REGIONS_PER_OWNER_ZERO + LIGHT_REGIONS_PER_OTHER_OWNER;
        for (int rz = 0; buckets.stream().mapToInt(List::size).sum() < OWNERS * maxPerBucket && rz < 200; rz++) {
            for (int rx = 0; rx < 200; rx++) {
                RegionKey key = new RegionKey(WORLD, rx, rz);
                int owner = probe.ownerFor(key);
                if (owner >= 0 && owner < OWNERS && buckets.get(owner).size() < maxPerBucket) {
                    buckets.get(owner).add(key);
                }
                if (buckets.stream().mapToInt(List::size).sum() >= OWNERS * maxPerBucket) {
                    break;
                }
            }
        }
        probeExecutor.close();
        // Heavy regions all hash to owner 0 under the static assignment.
        heavy.addAll(buckets.get(0).subList(0, Math.min(HEAVY_REGIONS_PER_OWNER_ZERO, buckets.get(0).size())));
        for (int owner = 1; owner < OWNERS; owner++) {
            List<RegionKey> bucket = buckets.get(owner);
            int take = Math.min(LIGHT_REGIONS_PER_OTHER_OWNER, bucket.size());
            light.addAll(bucket.subList(0, take));
        }
    }

    private static ModeResult runMode(
        boolean adaptive,
        List<RegionKey> heavyRegions,
        List<RegionKey> lightRegions
    ) throws Exception {
        HyperCoreExecutor executor = HyperCoreExecutor.create(OWNERS, OWNERS * 64);
        RegionTaskCoordinator coordinator = new RegionTaskCoordinator(executor, OWNERS, adaptive);
        RegionExecutionService execution = new RegionExecutionService(
            new NoOpWorldAccessFactory(), coordinator, new PluginEventBus()
        );

        Set<RegionKey> heavy = new HashSet<>(heavyRegions);
        for (RegionKey region : heavyRegions) {
            execution.activateRegion(WORLD, region.regionX() * 128, region.regionZ() * 128);
        }
        for (RegionKey region : lightRegions) {
            execution.activateRegion(WORLD, region.regionX() * 128, region.regionZ() * 128);
        }

        RegionTickTask task = (exec, region, tickId) -> {
            if (heavy.contains(region)) {
                busyWork(HEAVY_BUSY_ITERATIONS);
            }
        };

        try {
            // Warmup: lets the adaptive coordinator observe imbalance and remap.
            for (int tick = 0; tick < WARMUP_TICKS; tick++) {
                execution.tickRegions(task).get();
            }
            long[] samples = new long[MEASURE_TICKS];
            for (int tick = 0; tick < MEASURE_TICKS; tick++) {
                long start = System.nanoTime();
                execution.tickRegions(task).get();
                samples[tick] = System.nanoTime() - start;
            }
            Arrays.sort(samples);
            long p50 = samples[(int) Math.ceil(0.50 * samples.length) - 1];
            long p95 = samples[(int) Math.ceil(0.95 * samples.length) - 1];
            long sum = 0;
            for (long sample : samples) {
                sum += sample;
            }
            long mean = sum / samples.length;
            return new ModeResult(adaptive, p50, p95, mean, MEASURE_TICKS);
        } finally {
            executor.close();
        }
    }

    private static volatile double BLACKHOLE;

    private static void busyWork(int iterations) {
        double accumulator = 0.0;
        for (int i = 1; i <= iterations; i++) {
            accumulator += Math.sqrt(i) * 0.5;
        }
        BLACKHOLE = accumulator;
    }

    private static String renderMarkdown(ModeResult staticResult, ModeResult adaptiveResult, int heavy, int light) {
        StringBuilder report = new StringBuilder();
        report.append("# HyperCore Adaptive Scheduling Benchmark\n\n")
            .append("Generated: ").append(Instant.now().toString()).append("\n\n")
            .append("- Owners: `").append(OWNERS).append("`\n")
            .append("- Java: `").append(System.getProperty("java.version")).append("`\n")
            .append("- OS: `").append(System.getProperty("os.name")).append(' ')
            .append(System.getProperty("os.arch")).append("`\n")
            .append("- Logical processors: ").append(Runtime.getRuntime().availableProcessors()).append("\n")
            .append("- Heavy regions: `").append(heavy).append("` (all hashed to owner 0 under static assignment)\n")
            .append("- Light regions: `").append(light).append("` (spread across owners 1..").append(OWNERS - 1).append(")\n")
            .append("- Heavy busy-loop iterations per region: `").append(HEAVY_BUSY_ITERATIONS).append("`\n")
            .append("- Warmup ticks per mode: `").append(WARMUP_TICKS).append("`\n")
            .append("- Measured ticks per mode: `").append(MEASURE_TICKS).append("`\n\n")
            .append("The static coordinator pins every heavy region to owner 0, so each tick ")
            .append("bottlenecks on owner 0 processing all heavy regions sequentially. The ")
            .append("adaptive coordinator records per-owner tick timings in a 64-sample sliding ")
            .append("window and, when the imbalance ratio exceeds 1.5x, migrates roughly half of ")
            .append("the slowest owner's regions to the fastest owner. After warmup the heavy ")
            .append("work is spread across owners, cutting the per-tick wall-clock latency.\n\n")
            .append("| Mode | p50 | p95 | Mean |\n")
            .append("| --- | ---: | ---: | ---: |\n");
        report.append(String.format(Locale.ROOT,
            "| static | %.3f ms | %.3f ms | %.3f ms |%n",
            millis(staticResult.p50Nanos), millis(staticResult.p95Nanos), millis(staticResult.meanNanos)));
        report.append(String.format(Locale.ROOT,
            "| adaptive | %.3f ms | %.3f ms | %.3f ms |%n",
            millis(adaptiveResult.p50Nanos), millis(adaptiveResult.p95Nanos), millis(adaptiveResult.meanNanos)));
        double p50Speedup = (double) staticResult.p50Nanos / adaptiveResult.p50Nanos;
        double meanSpeedup = (double) staticResult.meanNanos / adaptiveResult.meanNanos;
        report.append(String.format(Locale.ROOT,
            "\nAdaptive p50 speedup: `%.2fx` | Mean speedup: `%.2fx`%n",
            p50Speedup, meanSpeedup));
        report.append("\nThis microbenchmark isolates scheduler behavior with synthetic per-region ")
            .append("work; it is not an MSPT or world-simulation result.\n");
        return report.toString();
    }

    private static double millis(long nanos) {
        return nanos / 1_000_000.0;
    }

    private record ModeResult(boolean adaptive, long p50Nanos, long p95Nanos, long meanNanos, int samples) {
    }
}
