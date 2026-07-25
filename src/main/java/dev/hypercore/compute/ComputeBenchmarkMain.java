package dev.hypercore.compute;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

public final class ComputeBenchmarkMain {
    private static final int[] BATCH_SIZES = {4_096, 16_384, 65_536, 262_144, 1_048_576, 4_194_304};

    private ComputeBenchmarkMain() {
    }

    public static void main(String[] arguments) throws IOException {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("Expected the Markdown report output path");
        }
        Path output = Path.of(arguments[0]);
        try (VulkanSpatialComputeBackend gpu = VulkanSpatialComputeBackend.create()) {
            SpatialComputeBenchmark.Report report = SpatialComputeBenchmark.run(
                new ScalarSpatialComputeBackend(),
                gpu,
                gpu.deviceName(),
                BATCH_SIZES,
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
