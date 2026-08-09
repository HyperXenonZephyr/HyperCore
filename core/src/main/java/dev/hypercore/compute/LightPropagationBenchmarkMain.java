package dev.hypercore.compute;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

public final class LightPropagationBenchmarkMain {
    // Light propagation cost is O(volume * iterations); keep grids modest so
    // the benchmark finishes quickly while still showing the GPU crossover.
    private static final int[] GRID_SIZES = {8, 16, 32, 48};

    private LightPropagationBenchmarkMain() {
    }

    public static void main(String[] arguments) throws IOException {
        String outputPath = arguments.length >= 1
            ? arguments[0]
            : "core/build/reports/hypercore/light-propagation-benchmark.md";
        Path output = Path.of(outputPath);
        try (VulkanSpatialComputeBackend gpu = VulkanSpatialComputeBackend.create()) {
            LightPropagationBenchmark.Report report = LightPropagationBenchmark.run(
                new ScalarLightPropagationBackend(),
                gpu,
                gpu.deviceName(),
                gpu.transferMode(),
                GRID_SIZES,
                15,
                15
            );
            String markdown = report.markdown(Instant.now().toString());
            Files.createDirectories(output.toAbsolutePath().getParent());
            Files.writeString(output, markdown, StandardCharsets.UTF_8);
            System.out.println(markdown);
        }
    }
}
