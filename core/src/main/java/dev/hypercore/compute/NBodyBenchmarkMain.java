package dev.hypercore.compute;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

public final class NBodyBenchmarkMain {
    // N-body is O(n^2); keep counts smaller than the particle benchmark (O(n)).
    private static final int[] BODY_COUNTS = {256, 1024, 4096, 8192, 16384};

    private NBodyBenchmarkMain() {
    }

    public static void main(String[] arguments) throws IOException {
        String outputPath = arguments.length >= 1
            ? arguments[0]
            : "core/build/reports/hypercore/nbody-sim-benchmark.md";
        Path output = Path.of(outputPath);
        try (VulkanSpatialComputeBackend gpu = VulkanSpatialComputeBackend.create()) {
            NBodySimulationBenchmark.Report report = NBodySimulationBenchmark.run(
                new ScalarNBodySimulationBackend(),
                gpu,
                gpu.deviceName(),
                gpu.transferMode(),
                BODY_COUNTS,
                20,
                15
            );
            String markdown = report.markdown(Instant.now().toString());
            Files.createDirectories(output.toAbsolutePath().getParent());
            Files.writeString(output, markdown, StandardCharsets.UTF_8);
            System.out.println(markdown);
        }
    }
}
