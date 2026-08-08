package dev.hypercore.compute;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

public final class ParticleBenchmarkMain {
    private static final int[] PARTICLE_COUNTS = {256, 1024, 4096, 16384, 65536};

    private ParticleBenchmarkMain() {
    }

    public static void main(String[] arguments) throws IOException {
        String outputPath = arguments.length >= 1
            ? arguments[0]
            : "core/build/reports/hypercore/particle-sim-benchmark.md";
        Path output = Path.of(outputPath);
        try (VulkanSpatialComputeBackend gpu = VulkanSpatialComputeBackend.create()) {
            ParticleSimulationBenchmark.Report report = ParticleSimulationBenchmark.run(
                new ScalarParticleSimulationBackend(),
                gpu,
                gpu.deviceName(),
                gpu.transferMode(),
                PARTICLE_COUNTS,
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
