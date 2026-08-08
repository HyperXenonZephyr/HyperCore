package dev.hypercore.compute;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Measures 3D value-noise density generation on a regular voxel grid across
 * CPU and GPU backends.  Each volume is a cube whose edge length determines the
 * total voxel count ({@code edgeLength ^ 3}).  CPU and GPU output are compared
 * against a tolerance before timing begins so the reported numbers measure
 * steady-state throughput of validated kernels.
 */
public final class NoiseComputeBenchmark {
    private static final double REQUIRED_GPU_ADVANTAGE = 0.95;
    private static final float ORIGIN_X = -3.5f;
    private static final float ORIGIN_Y = 2.0f;
    private static final float ORIGIN_Z = -7.25f;
    private static final float FREQUENCY = 0.05f;
    private static final float CORRECTNESS_TOLERANCE = 1e-5f;
    // The scalar reference runs interpreted until HotSpot C2 compiles its hot
    // loops, which needs far more invocations than the per-volume warmup.  Prime
    // on a representative volume before timing so the first volumes measure
    // steady-state throughput instead of interpreter overhead.
    private static final int CPU_PRIME_EDGE_LENGTH = 64;
    private static final int CPU_PRIME_ITERATIONS = 3_000;
    private static volatile int blackhole;

    private NoiseComputeBenchmark() {
    }

    public static Report run(
        ScalarNoiseComputeBackend cpu,
        NoiseComputeBackend gpu,
        String gpuDevice,
        String gpuTransferMode,
        int[] volumeSizes,
        int warmupIterations,
        int sampleIterations
    ) {
        Objects.requireNonNull(cpu, "cpu");
        Objects.requireNonNull(gpu, "gpu");
        Objects.requireNonNull(gpuDevice, "gpuDevice");
        Objects.requireNonNull(gpuTransferMode, "gpuTransferMode");
        Objects.requireNonNull(volumeSizes, "volumeSizes");
        if (volumeSizes.length == 0) {
            throw new IllegalArgumentException("volumeSizes cannot be empty");
        }
        if (warmupIterations < 0 || sampleIterations < 1) {
            throw new IllegalArgumentException("Benchmark iteration counts are invalid");
        }

        primeCpuBackend(cpu);

        List<VolumeResult> results = new ArrayList<>(volumeSizes.length);
        for (int edgeLength : volumeSizes) {
            if (edgeLength < 1) {
                throw new IllegalArgumentException("volumeSizes must be positive");
            }
            int voxelCount = edgeLength * edgeLength * edgeLength;
            float[] cpuOutput = new float[voxelCount];
            float[] gpuOutput = new float[voxelCount];

            for (int iteration = 0; iteration < warmupIterations; iteration++) {
                generate(cpu, edgeLength, cpuOutput);
                generate(gpu, edgeLength, gpuOutput);
            }
            verifyOutput(cpu, gpu, edgeLength, cpuOutput, gpuOutput);

            long[] cpuSamples = new long[sampleIterations];
            long[] gpuSamples = new long[sampleIterations];
            for (int sample = 0; sample < sampleIterations; sample++) {
                if ((sample & 1) == 0) {
                    cpuSamples[sample] = measure(cpu, edgeLength, cpuOutput);
                    gpuSamples[sample] = measure(gpu, edgeLength, gpuOutput);
                } else {
                    gpuSamples[sample] = measure(gpu, edgeLength, gpuOutput);
                    cpuSamples[sample] = measure(cpu, edgeLength, cpuOutput);
                }
            }

            results.add(new VolumeResult(
                edgeLength,
                voxelCount,
                percentile(cpuSamples, 0.50),
                percentile(cpuSamples, 0.95),
                percentile(gpuSamples, 0.50),
                percentile(gpuSamples, 0.95),
                (long) voxelCount * Float.BYTES
            ));
        }
        return new Report(
            gpuDevice, gpuTransferMode, warmupIterations, sampleIterations,
            List.copyOf(results)
        );
    }

    static long percentile(long[] samples, double percentile) {
        if (samples.length == 0 || percentile <= 0.0 || percentile > 1.0) {
            throw new IllegalArgumentException("Invalid percentile input");
        }
        long[] sorted = samples.clone();
        Arrays.sort(sorted);
        int index = Math.max(0, (int) Math.ceil(percentile * sorted.length) - 1);
        return sorted[index];
    }

    private static void verifyOutput(
        ScalarNoiseComputeBackend cpu,
        NoiseComputeBackend gpu,
        int edgeLength,
        float[] cpuOutput,
        float[] gpuOutput
    ) {
        generate(cpu, edgeLength, cpuOutput);
        generate(gpu, edgeLength, gpuOutput);
        for (int index = 0; index < cpuOutput.length; index++) {
            float cpuValue = cpuOutput[index];
            float gpuValue = gpuOutput[index];
            if (!Float.isFinite(cpuValue) || !Float.isFinite(gpuValue)) {
                throw new IllegalStateException(
                    "Benchmark correctness mismatch at edge " + edgeLength
                        + ", voxel " + index
                        + ": non-finite value"
                );
            }
            if (Math.abs(cpuValue - gpuValue) > CORRECTNESS_TOLERANCE) {
                throw new IllegalStateException(
                    "Benchmark correctness mismatch at edge " + edgeLength
                        + ", voxel " + index
                        + ": |" + cpuValue + " - " + gpuValue
                        + "| > " + CORRECTNESS_TOLERANCE
                );
            }
        }
    }

    private static long measure(NoiseComputeBackend backend, int edgeLength, float[] output) {
        long started = System.nanoTime();
        generate(backend, edgeLength, output);
        long elapsed = System.nanoTime() - started;
        blackhole ^= Float.floatToIntBits(output[output.length - 1]);
        return elapsed;
    }

    private static void generate(NoiseComputeBackend backend, int edgeLength, float[] output) {
        backend.generateDensity(
            ORIGIN_X, ORIGIN_Y, ORIGIN_Z,
            edgeLength, edgeLength, edgeLength,
            FREQUENCY,
            output
        );
    }

    private static void primeCpuBackend(ScalarNoiseComputeBackend backend) {
        float[] output = new float[CPU_PRIME_EDGE_LENGTH * CPU_PRIME_EDGE_LENGTH * CPU_PRIME_EDGE_LENGTH];
        for (int iteration = 0; iteration < CPU_PRIME_ITERATIONS; iteration++) {
            generate(backend, CPU_PRIME_EDGE_LENGTH, output);
        }
    }

    public record VolumeResult(
        int edgeLength,
        int voxelCount,
        long cpuP50Nanos,
        long cpuP95Nanos,
        long gpuP50Nanos,
        long gpuP95Nanos,
        long readbackBytes
    ) {
        public double p50Speedup() {
            return (double) cpuP50Nanos / gpuP50Nanos;
        }
    }

    public record Report(
        String gpuDevice,
        String gpuTransferMode,
        int warmupIterations,
        int sampleIterations,
        List<VolumeResult> volumes
    ) {
        public Report {
            gpuDevice = Objects.requireNonNull(gpuDevice, "gpuDevice");
            gpuTransferMode = Objects.requireNonNull(gpuTransferMode, "gpuTransferMode");
            volumes = List.copyOf(volumes);
        }

        /**
         * Returns the smallest edge length where the GPU p50 is at least 5%
         * faster than the CPU p50 and stays at least 5% faster for every larger
         * tested volume.  Returns {@code -1} when no such crossover exists.
         */
        public int recommendedMinimumVolumeSize() {
            for (int index = 0; index < volumes.size(); index++) {
                boolean sustained = true;
                for (int candidate = index; candidate < volumes.size(); candidate++) {
                    VolumeResult volume = volumes.get(candidate);
                    if (volume.gpuP50Nanos() > volume.cpuP50Nanos() * REQUIRED_GPU_ADVANTAGE) {
                        sustained = false;
                        break;
                    }
                }
                if (sustained) {
                    return volumes.get(index).edgeLength();
                }
            }
            return -1;
        }

        public String markdown(String generatedAt) {
            StringBuilder output = new StringBuilder();
            output.append("# HyperCore Noise Compute Benchmark\n\n")
                .append("Generated: ").append(generatedAt).append("\n\n")
                .append("- GPU: `").append(gpuDevice).append("`\n")
                .append("- GPU transfer mode: `").append(gpuTransferMode).append("`\n")
                .append("- Java: `").append(System.getProperty("java.version")).append("`\n")
                .append("- OS: `").append(System.getProperty("os.name")).append(' ')
                .append(System.getProperty("os.arch")).append("`\n")
                .append("- Logical processors: ").append(Runtime.getRuntime().availableProcessors()).append("\n")
                .append("- Warmups per backend and volume: ").append(warmupIterations).append("\n")
                .append("- Timed samples per backend and volume: ").append(sampleIterations).append("\n")
                .append("- Origin: `(").append(ORIGIN_X).append(", ").append(ORIGIN_Y)
                .append(", ").append(ORIGIN_Z).append(")`\n")
                .append("- Frequency: `").append(FREQUENCY).append("`\n")
                .append("- Correctness tolerance: `").append(CORRECTNESS_TOLERANCE).append("`\n\n")
                .append("Each volume is a cube whose edge length determines the total voxel count ")
                .append("(`edgeLength ^ 3`). CPU timings include the full scalar noise loop. GPU ")
                .append("timings include staging upload, compute dispatch, fence wait, and density ")
                .append("readback. Output arrays are allocated before timing.\n\n")
                .append("| Edge length | Voxels | CPU p50 | CPU p95 | GPU p50 | GPU p95 | Speedup | Readback |\n")
                .append("| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");
            for (VolumeResult volume : volumes) {
                output.append(String.format(
                    Locale.ROOT,
                    "| %,d | %,d | %.3f ms | %.3f ms | %.3f ms | %.3f ms | %.2fx | %,d B |%n",
                    volume.edgeLength(),
                    volume.voxelCount(),
                    nanosToMillis(volume.cpuP50Nanos()),
                    nanosToMillis(volume.cpuP95Nanos()),
                    nanosToMillis(volume.gpuP50Nanos()),
                    nanosToMillis(volume.gpuP95Nanos()),
                    volume.p50Speedup(),
                    volume.readbackBytes()
                ));
            }
            int recommendation = recommendedMinimumVolumeSize();
            output.append("\nConservative p50 crossover: ");
            if (recommendation < 0) {
                output.append("none in the tested range.\n");
            } else {
                output.append('`').append(recommendation).append("` edge length (`")
                    .append(recommendation).append("^3 = ")
                    .append(recommendation * recommendation * recommendation)
                    .append("` voxels).\n");
            }
            output.append("A crossover requires the GPU p50 to be at least 5% lower at that volume and every larger tested volume.\n\n")
                .append("This microbenchmark is calibration evidence, not an MSPT or world-simulation result.\n");
            return output.toString();
        }

        private static double nanosToMillis(long nanos) {
            return nanos / 1_000_000.0;
        }
    }
}
