package dev.hypercore.compute;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

public final class NoiseBenchmarkMain {
    private static final int[] VOLUME_SIZES = {16, 32, 64, 128};

    private NoiseBenchmarkMain() {
    }

    public static void main(String[] arguments) throws IOException {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("Expected the Markdown report output path");
        }
        Path output = Path.of(arguments[0]);
        try (VulkanSpatialComputeBackend gpu = VulkanSpatialComputeBackend.create()) {
            NoiseComputeBenchmark.Report report = NoiseComputeBenchmark.run(
                new ScalarNoiseComputeBackend(),
                gpu,
                gpu.deviceName(),
                gpu.transferMode(),
                VOLUME_SIZES,
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
