package dev.hypercore.compute;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;

/**
 * Benchmarks the consolidated shared Vulkan descriptor pool against the
 * previous per-pipeline split-pool strategy. Reuses an initialized
 * {@link VulkanSpatialComputeBackend} so the comparison runs against a real
 * {@code VkDevice} and the production descriptor-set layout.
 *
 * <p>The "split" strategy models the prior design: one small descriptor pool
 * per compute pipeline (5 pools, each {@code maxSets=1}), with a single set
 * allocated from each. The "shared" strategy models the current design: one
 * pool ({@code maxSets=8}, 32 descriptors) backing all pipelines. Each
 * measured cycle creates, allocates, and tears down, so the split strategy
 * performs 5x the {@code vkCreateDescriptorPool}/{@code vkDestroyDescriptorPool}
 * calls — directly quantifying the overhead the consolidation removes.
 */
public final class DescriptorPoolBenchmarkMain {
    private static final int ITERATIONS = 200;

    private DescriptorPoolBenchmarkMain() {
    }

    public static void main(String[] arguments) throws IOException {
        String outputPath = arguments.length >= 1
            ? arguments[0]
            : "core/build/reports/hypercore/descriptor-pool-benchmark.md";
        Path output = Path.of(outputPath);
        try (VulkanSpatialComputeBackend gpu = VulkanSpatialComputeBackend.create()) {
            VulkanSpatialComputeBackend.DescriptorPoolComparison result =
                gpu.runDescriptorPoolComparison(ITERATIONS);
            String markdown = renderMarkdown(gpu.deviceName(), result);
            Files.createDirectories(output.toAbsolutePath().getParent());
            Files.writeString(output, markdown, StandardCharsets.UTF_8);
            System.out.println(markdown);
        }
    }

    private static String renderMarkdown(
        String gpuDevice,
        VulkanSpatialComputeBackend.DescriptorPoolComparison result
    ) {
        StringBuilder report = new StringBuilder();
        report.append("# HyperCore Descriptor Pool Sharing Benchmark\n\n")
            .append("Generated: ").append(Instant.now().toString()).append("\n\n")
            .append("- GPU: `").append(gpuDevice).append("`\n")
            .append("- Java: `").append(System.getProperty("java.version")).append("`\n")
            .append("- OS: `").append(System.getProperty("os.name")).append(' ')
            .append(System.getProperty("os.arch")).append("`\n")
            .append("- Measured cycles per strategy: `").append(result.iterations()).append("`\n\n")
            .append("Each cycle creates descriptor pool(s), allocates 5 descriptor sets, and ")
            .append("destroys the pool(s). The **split** strategy models the prior per-pipeline ")
            .append("design (5 pools, maxSets=1 each); the **shared** strategy models the ")
            .append("consolidated design (1 pool, maxSets=8). The split strategy issues 5x the ")
            .append("pool create/destroy calls per cycle.\n\n")
            .append("| Strategy | p50 | p95 | Mean |\n")
            .append("| --- | ---: | ---: | ---: |\n");
        report.append(String.format(Locale.ROOT,
            "| split (5 pools) | %.3f us | %.3f us | %.3f us |%n",
            micros(result.splitP50Nanos()), micros(result.splitP95Nanos()), micros(result.splitMeanNanos())));
        report.append(String.format(Locale.ROOT,
            "| shared (1 pool) | %.3f us | %.3f us | %.3f us |%n",
            micros(result.sharedP50Nanos()), micros(result.sharedP95Nanos()), micros(result.sharedMeanNanos())));
        report.append(String.format(Locale.ROOT,
            "\nShared-pool p50 overhead reduction: `%.2fx`%n", result.p50Reduction()));
        report.append("\nThis microbenchmark isolates descriptor-pool allocation overhead; it is ")
            .append("not an MSPT or world-simulation result.\n");
        return report.toString();
    }

    private static double micros(long nanos) {
        return nanos / 1_000.0;
    }
}
