package dev.hypercore.region;

import dev.hypercore.concurrent.HyperCoreExecutor;
import dev.hypercore.plugin.PluginEventBus;
import dev.hypercore.world.NoOpWorldAccessFactory;
import dev.hypercore.world.RegionExecutionService;
import dev.hypercore.world.RegionTickTask;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark measuring the throughput and parallelism of the region tick
 * coordinator. A no-op world access factory is used so the benchmark isolates
 * scheduling overhead from Minecraft world I/O.
 */
public class RegionParallelBenchmarkMain {

    @State(Scope.Benchmark)
    public static class ExecutionState {
        @Param({"1", "2", "4", "8", "16"})
        public int regions;

        HyperCoreExecutor executor;
        RegionTaskCoordinator coordinator;
        RegionExecutionService execution;

        @Setup(Level.Trial)
        public void setup() {
            int workers = Math.max(2, Runtime.getRuntime().availableProcessors());
            executor = HyperCoreExecutor.create(workers, workers * 64);
            coordinator = new RegionTaskCoordinator(executor, workers);
            execution = new RegionExecutionService(new NoOpWorldAccessFactory(), coordinator, new PluginEventBus());
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            if (executor != null) {
                executor.close();
            }
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @Warmup(iterations = 2, time = 1)
    @Measurement(iterations = 3, time = 1)
    @Fork(1)
    public Object tickRegions(ExecutionState state) throws Exception {
        for (int index = 0; index < state.regions; index++) {
            state.execution.setBlockType("benchmark", index * 16, 64, index * 16, org.bukkit.Material.STONE);
        }
        RegionTickTask task = (execution, region, tickId) -> {
            // No-op per-region work: the benchmark measures scheduling throughput.
        };
        return state.execution.tickRegions(task).get();
    }

    public static void main(String[] args) throws Exception {
        String outputPath = args.length > 0 ? args[0] : "build/reports/hypercore/region-parallel-benchmark.md";
        Options options = new OptionsBuilder()
            .include(RegionParallelBenchmarkMain.class.getSimpleName())
            .resultFormat(ResultFormatType.JSON)
            .result(outputPath.replace(".md", ".json"))
            .build();
        Collection<org.openjdk.jmh.results.RunResult> results = new Runner(options).run();
        writeMarkdownReport(results, Paths.get(outputPath));
    }

    private static void writeMarkdownReport(
        Collection<org.openjdk.jmh.results.RunResult> results,
        Path output
    ) throws IOException {
        StringBuilder report = new StringBuilder();
        report.append("# Region Parallel Benchmark\n\n");
        report.append("Measures region-tick scheduling throughput across different numbers of active regions.\n\n");
        report.append("| Regions | Throughput (ops/s) | Error (ops/s) |\n");
        report.append("|--------:|-------------------:|--------------:|\n");
        for (org.openjdk.jmh.results.RunResult result : results) {
            String benchmarkName = result.getParams().getBenchmark();
            int regions = Integer.parseInt(result.getParams().getParam("regions"));
            double score = result.getPrimaryResult().getScore();
            double error = result.getPrimaryResult().getScoreError();
            report.append(String.format("| %d | %.2f | %.2f |%n", regions, score, error));
        }
        Files.createDirectories(output.getParent());
        Files.writeString(output, report.toString());
    }
}
