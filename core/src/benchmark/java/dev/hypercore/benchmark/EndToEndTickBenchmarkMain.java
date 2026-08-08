package dev.hypercore.benchmark;

import dev.hypercore.bridge.world.WorldDelta;
import dev.hypercore.bridge.world.WorldStateBridge;
import dev.hypercore.concurrent.HyperCoreExecutor;
import dev.hypercore.orchestrator.HyperCoreRole;
import dev.hypercore.plugin.PluginEventBus;
import dev.hypercore.region.RegionTaskCoordinator;
import dev.hypercore.world.NoOpWorldAccessFactory;
import dev.hypercore.world.RegionExecutionService;
import dev.hypercore.world.RegionTickTask;
import org.bukkit.Material;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * End-to-end tick latency benchmark that measures the full server tick cycle:
 * region-parallel block mutation dispatch, delta capture, world-state bridge
 * resolution, and ordered batch output. This is the closest standalone
 * approximation to production MSPT without running a real Minecraft server.
 *
 * <p>The benchmark does not include GPU compute or a real network bridge (the
 * bridge is in-process). It measures the CPU-side overhead that HyperCore
 * adds on top of the vanilla server tick: region locking, worker dispatch,
 * delta capture, conflict resolution, and batch serialization.
 */
public final class EndToEndTickBenchmarkMain {

    private static final int[] REGION_COUNTS = {1, 4, 16, 64, 256};
    private static final int WARMUP_TICKS = 100;
    private static final int MEASURED_TICKS = 500;
    private static final int BLOCK_MUTATIONS_PER_REGION = 32;

    private EndToEndTickBenchmarkMain() {
    }

    public static void main(String[] args) throws Exception {
        String outputPath = args.length > 0
            ? args[0]
            : "core/build/reports/hypercore/end-to-end-tick-benchmark.md";
        Path output = Path.of(outputPath);
        Files.createDirectories(output.getParent());

        String javaVersion = System.getProperty("java.version");
        String osName = System.getProperty("os.name");
        String osArch = System.getProperty("os.arch");
        int processors = Runtime.getRuntime().availableProcessors();

        StringBuilder report = new StringBuilder();
        report.append("# End-to-End Tick Latency Benchmark\n\n");
        report.append("Generated: ").append(Instant.now().toString()).append("\n\n");
        report.append("- Java: `").append(javaVersion).append("`\n");
        report.append("- OS: `").append(osName).append(" ").append(osArch).append("`\n");
        report.append("- Logical processors: `").append(processors).append("`\n");
        report.append("- Warmup ticks per configuration: `").append(WARMUP_TICKS).append("`\n");
        report.append("- Measured ticks per configuration: `").append(MEASURED_TICKS).append("`\n");
        report.append("- Block mutations per region per tick: `").append(BLOCK_MUTATIONS_PER_REGION).append("`\n");
        report.append("- Bridge: in-process (no network)\n\n");

        report.append("Each tick simulates the HyperCore post-server-tick workload: region-parallel ");
        report.append("block mutations under region locks, delta capture through the DeltaSink, ");
        report.append("world-state bridge conflict resolution, and ordered batch output. The vanilla ");
        report.append("Minecraft server tick body is NOT included — this measures only the overhead ");
        report.append("HyperCore adds.\n\n");

        report.append("| Regions | Workers | Tick avg (ms) | Tick p50 (ms) | Tick p95 (ms) | Tick max (ms) | Deltas/tick | Bridge flush (ms) |\n");
        report.append("| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");

        int workers = Math.max(2, processors - 1);

        for (int regionCount : REGION_COUNTS) {
            TickResult result = runConfiguration(regionCount, workers);
            report.append(String.format(
                "| %d | %d | %.4f | %.4f | %.4f | %.4f | %.1f | %.4f |%n",
                regionCount,
                workers,
                result.tickAvgMs,
                result.tickP50Ms,
                result.tickP95Ms,
                result.tickMaxMs,
                result.deltasPerTick,
                result.bridgeFlushAvgMs
            ));
            System.out.printf(
                "Regions=%d done: avg=%.4fms p95=%.4fms deltas/tick=%.1f%n",
                regionCount, result.tickAvgMs, result.tickP95Ms, result.deltasPerTick);
        }

        report.append("\n## Analysis\n\n");
        report.append("The benchmark isolates HyperCore's CPU-side overhead (region dispatch, ");
        report.append("delta capture, bridge resolution) from the vanilla server tick. In production, ");
        report.append("the full-tick MSPT (reported by `/hypercore timings` as \"Full tick\") equals the ");
        report.append("vanilla server tick body plus the numbers above. The bridge flush column shows ");
        report.append("the cost of `WorldStateBridge.flush()` (conflict resolution + batch output), which ");
        report.append("runs on the orchestrator side and is separate from the per-tick region dispatch.\n");

        Files.writeString(output, report.toString(), StandardCharsets.UTF_8);
        System.out.println("End-to-end tick benchmark report written to " + output.toAbsolutePath());
    }

    private static TickResult runConfiguration(int regionCount, int workers) throws Exception {
        try (HyperCoreExecutor executor = HyperCoreExecutor.create(workers, workers * 64)) {
            RegionTaskCoordinator coordinator = new RegionTaskCoordinator(executor, workers);
            RegionExecutionService execution = new RegionExecutionService(
                new NoOpWorldAccessFactory(), coordinator, new PluginEventBus()
            );

            // Delta capture: collect deltas into a list, then feed them to the bridge.
            List<WorldDelta> capturedDeltas = new ArrayList<>();
            execution.setDeltaSink(capturedDeltas::add);

            // In-process bridge: Forge host produces, Fabric host consumes.
            WorldStateBridge bridge = new WorldStateBridge();
            List<WorldDelta> receivedDeltas = new ArrayList<>();
            bridge.setOutbound(batch -> {
                for (var resolved : batch.deltas()) {
                    receivedDeltas.add(resolved.delta());
                }
            });

            // Pre-seed regions by writing a block at distinct coordinates.
            RegionTickTask seedTask = (exec, region, tickId) -> {
                for (int i = 0; i < BLOCK_MUTATIONS_PER_REGION; i++) {
                    int base = region.regionX() * 16 + i;
                    exec.setBlockType("world", base, 64, region.regionZ() * 16, Material.STONE);
                }
            };

            // Warmup
            for (int t = 0; t < WARMUP_TICKS; t++) {
                capturedDeltas.clear();
                execution.tickRegions(seedTask).join();
                execution.flushAllPendingMutations();
                if (!capturedDeltas.isEmpty()) {
                    bridge.submit(HyperCoreRole.FORGE_HOST, new ArrayList<>(capturedDeltas));
                    bridge.flush();
                    capturedDeltas.clear();
                    receivedDeltas.clear();
                }
            }

            // Measured
            long[] tickNanos = new long[MEASURED_TICKS];
            long[] bridgeNanos = new long[MEASURED_TICKS];
            long totalDeltas = 0;

            for (int t = 0; t < MEASURED_TICKS; t++) {
                capturedDeltas.clear();
                long tickStart = System.nanoTime();
                RegionTaskCoordinator.TickResult tickResult = execution.tickRegions(seedTask).join();
                execution.flushAllPendingMutations();
                long tickEnd = System.nanoTime();
                tickNanos[t] = tickEnd - tickStart;

                if (!capturedDeltas.isEmpty()) {
                    bridge.submit(HyperCoreRole.FORGE_HOST, new ArrayList<>(capturedDeltas));
                    long bridgeStart = System.nanoTime();
                    bridge.flush();
                    long bridgeEnd = System.nanoTime();
                    bridgeNanos[t] = bridgeEnd - bridgeStart;
                    totalDeltas += capturedDeltas.size();
                    capturedDeltas.clear();
                    receivedDeltas.clear();
                } else {
                    bridgeNanos[t] = 0;
                }
            }

            return computeResult(tickNanos, bridgeNanos, totalDeltas, MEASURED_TICKS);
        }
    }

    private static TickResult computeResult(long[] tickNanos, long[] bridgeNanos, long totalDeltas, int count) {
        long[] sortedTicks = tickNanos.clone();
        java.util.Arrays.sort(sortedTicks);
        long tickSum = 0;
        for (long n : tickNanos) tickSum += n;
        long bridgeSum = 0;
        for (long n : bridgeNanos) bridgeSum += n;

        return new TickResult(
            nanosToMs(tickSum / (double) count),
            nanosToMs(sortedTicks[count / 2]),
            nanosToMs(percentile(sortedTicks, 0.95)),
            nanosToMs(sortedTicks[count - 1]),
            totalDeltas / (double) count,
            nanosToMs(bridgeSum / (double) count)
        );
    }

    private static long percentile(long[] sorted, double p) {
        int index = (int) Math.ceil(p * sorted.length) - 1;
        return sorted[Math.max(0, index)];
    }

    private static double nanosToMs(double nanos) {
        return nanos / 1_000_000.0;
    }

    private record TickResult(
        double tickAvgMs,
        double tickP50Ms,
        double tickP95Ms,
        double tickMaxMs,
        double deltasPerTick,
        double bridgeFlushAvgMs
    ) {}
}
