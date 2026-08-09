package dev.hypercore.compute;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;

/**
 * Measures iterative voxel light propagation across CPU and GPU backends.
 * Each grid size drives one {@code propagate} call that performs enough
 * iterations to fully relax light from a central source across the grid
 * ({@code dimX + dimY + dimZ - 3} rounds). The per-iteration cost is O(volume)
 * and the total cost is O(volume * iterations), so the GPU advantage grows
 * with grid volume.
 *
 * <p>Because {@code propagate} mutates the {@code light} array in place, the
 * initial state is captured and restored before every invocation so each
 * sample propagates from the same starting grid. Correctness is enforced with
 * zero tolerance: both the GPU shader and the CPU oracle use integer
 * arithmetic, so results must be bit-identical.
 */
public final class LightPropagationBenchmark {
    private static final double REQUIRED_GPU_ADVANTAGE = 0.95;
    private static final int MAX_LIGHT = 15;
    private static final long SEED = 0x4C494748L;
    // Prime the scalar reference on a representative grid so C2 compiles the
    // hot relaxation loop before timing begins.
    private static final int CPU_PRIME_DIM = 16;
    private static final int CPU_PRIME_ITERATIONS = 50;
    private static volatile int blackhole;

    private LightPropagationBenchmark() {
    }

    public static Report run(
        ScalarLightPropagationBackend cpu,
        LightPropagationBackend gpu,
        String gpuDevice,
        String gpuTransferMode,
        int[] gridSizes,
        int warmupIterations,
        int sampleIterations
    ) {
        Objects.requireNonNull(cpu, "cpu");
        Objects.requireNonNull(gpu, "gpu");
        Objects.requireNonNull(gpuDevice, "gpuDevice");
        Objects.requireNonNull(gpuTransferMode, "gpuTransferMode");
        Objects.requireNonNull(gridSizes, "gridSizes");
        if (gridSizes.length == 0) {
            throw new IllegalArgumentException("gridSizes cannot be empty");
        }
        if (warmupIterations < 0 || sampleIterations < 1) {
            throw new IllegalArgumentException("Benchmark iteration counts are invalid");
        }

        primeCpuBackend(cpu);

        List<GridResult> results = new ArrayList<>(gridSizes.length);
        for (int dim : gridSizes) {
            if (dim < 1) {
                throw new IllegalArgumentException("gridSizes must be positive");
            }
            int volume = dim * dim * dim;
            int iterations = dim + dim + dim - 3;
            if (iterations < 1) {
                iterations = 1;
            }
            int[] opacity = generateOpacity(dim, volume);
            int[] initialLight = generateLight(dim, volume);

            int[] cpuLight = initialLight.clone();
            int[] gpuLight = initialLight.clone();

            for (int iteration = 0; iteration < warmupIterations; iteration++) {
                restore(cpuLight, initialLight);
                cpu.propagate(cpuLight, opacity, dim, dim, dim, iterations);
                restore(gpuLight, initialLight);
                gpu.propagate(gpuLight, opacity, dim, dim, dim, iterations);
            }
            verifyOutput(cpu, gpu, opacity, initialLight, cpuLight, gpuLight, dim, iterations);

            long[] cpuSamples = new long[sampleIterations];
            long[] gpuSamples = new long[sampleIterations];
            for (int sample = 0; sample < sampleIterations; sample++) {
                if ((sample & 1) == 0) {
                    cpuSamples[sample] = measure(cpu, cpuLight, opacity, initialLight, dim, iterations);
                    gpuSamples[sample] = measure(gpu, gpuLight, opacity, initialLight, dim, iterations);
                } else {
                    gpuSamples[sample] = measure(gpu, gpuLight, opacity, initialLight, dim, iterations);
                    cpuSamples[sample] = measure(cpu, cpuLight, opacity, initialLight, dim, iterations);
                }
            }

            results.add(new GridResult(
                dim,
                volume,
                iterations,
                percentile(cpuSamples, 0.50),
                percentile(cpuSamples, 0.95),
                percentile(gpuSamples, 0.50),
                percentile(gpuSamples, 0.95),
                (long) volume * Integer.BYTES * 3L
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
        ScalarLightPropagationBackend cpu,
        LightPropagationBackend gpu,
        int[] opacity,
        int[] initialLight,
        int[] cpuLight,
        int[] gpuLight,
        int dim,
        int iterations
    ) {
        restore(cpuLight, initialLight);
        cpu.propagate(cpuLight, opacity, dim, dim, dim, iterations);
        restore(gpuLight, initialLight);
        gpu.propagate(gpuLight, opacity, dim, dim, dim, iterations);
        for (int index = 0; index < cpuLight.length; index++) {
            int cpuValue = cpuLight[index];
            int gpuValue = gpuLight[index];
            if (gpuValue < 0 || gpuValue > MAX_LIGHT) {
                throw new IllegalStateException(
                    "Light benchmark correctness mismatch at dim " + dim
                        + ", index " + index
                        + ": GPU produced out-of-range value " + gpuValue
                );
            }
            if (cpuValue != gpuValue) {
                throw new IllegalStateException(
                    "Light benchmark correctness mismatch at dim " + dim
                        + ", index " + index
                        + ": CPU=" + cpuValue + ", GPU=" + gpuValue
                );
            }
        }
    }

    private static long measure(
        LightPropagationBackend backend,
        int[] light,
        int[] opacity,
        int[] initialLight,
        int dim,
        int iterations
    ) {
        restore(light, initialLight);
        long started = System.nanoTime();
        backend.propagate(light, opacity, dim, dim, dim, iterations);
        long elapsed = System.nanoTime() - started;
        blackhole ^= light[light.length - 1];
        return elapsed;
    }

    private static void restore(int[] light, int[] initialLight) {
        System.arraycopy(initialLight, 0, light, 0, initialLight.length);
    }

    private static void primeCpuBackend(ScalarLightPropagationBackend backend) {
        int dim = CPU_PRIME_DIM;
        int volume = dim * dim * dim;
        int iterations = dim + dim + dim - 3;
        int[] opacity = generateOpacity(dim, volume);
        int[] initialLight = generateLight(dim, volume);
        int[] light = initialLight.clone();
        for (int iteration = 0; iteration < CPU_PRIME_ITERATIONS; iteration++) {
            restore(light, initialLight);
            backend.propagate(light, opacity, dim, dim, dim, iterations);
        }
    }

    private static int[] generateOpacity(int dim, int volume) {
        Random random = new Random(SEED ^ ((long) dim * 0x9E3779B9L));
        int[] opacity = new int[volume];
        // Sparse opaque voxels (~8%) create occlusion without blocking all
        // propagation paths, exercising the neighbor-max relaxation.
        for (int index = 0; index < volume; index++) {
            opacity[index] = random.nextInt(100) < 8 ? 1 : 0;
        }
        return opacity;
    }

    private static int[] generateLight(int dim, int volume) {
        int[] light = new int[volume];
        // Single full-brightness source at the grid center.
        int center = dim / 2;
        int centerIdx = center + center * dim + center * dim * dim;
        light[centerIdx] = MAX_LIGHT;
        return light;
    }

    public record GridResult(
        int gridSize,
        int voxels,
        int iterations,
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
        List<GridResult> grids
    ) {
        public Report {
            gpuDevice = Objects.requireNonNull(gpuDevice, "gpuDevice");
            gpuTransferMode = Objects.requireNonNull(gpuTransferMode, "gpuTransferMode");
            grids = List.copyOf(grids);
        }

        /**
         * Returns the smallest grid size where the GPU p50 is at least 5%
         * faster than the CPU p50 and stays at least 5% faster for every larger
         * tested grid.  Returns {@code -1} when no such crossover exists.
         */
        public int recommendedMinimumGridSize() {
            for (int index = 0; index < grids.size(); index++) {
                boolean sustained = true;
                for (int candidate = index; candidate < grids.size(); candidate++) {
                    GridResult result = grids.get(candidate);
                    if (result.gpuP50Nanos() > result.cpuP50Nanos() * REQUIRED_GPU_ADVANTAGE) {
                        sustained = false;
                        break;
                    }
                }
                if (sustained) {
                    return grids.get(index).gridSize();
                }
            }
            return -1;
        }

        public String markdown(String generatedAt) {
            StringBuilder output = new StringBuilder();
            output.append("# HyperCore Light Propagation Benchmark\n\n")
                .append("Generated: ").append(generatedAt).append("\n\n")
                .append("- GPU: `").append(gpuDevice).append("`\n")
                .append("- GPU transfer mode: `").append(gpuTransferMode).append("`\n")
                .append("- Java: `").append(System.getProperty("java.version")).append("`\n")
                .append("- OS: `").append(System.getProperty("os.name")).append(' ')
                .append(System.getProperty("os.arch")).append("`\n")
                .append("- Logical processors: ").append(Runtime.getRuntime().availableProcessors()).append("\n")
                .append("- Warmups per backend and grid: ").append(warmupIterations).append("\n")
                .append("- Timed samples per backend and grid: ").append(sampleIterations).append("\n")
                .append("- Max light level: `").append(MAX_LIGHT).append("`\n")
                .append("- Correctness tolerance: `0` (integer-exact)\n\n")
                .append("Each run propagates a single center light source (level 15) through a ")
                .append("cube grid with ~8% random opaque occluders. Iteration count is ")
                .append("`dimX + dimY + dimZ - 3` (full convergence). CPU timings include the ")
                .append("full scalar double-buffered relaxation loop. GPU timings include staging ")
                .append("upload of light and opacity grids, double-buffered compute dispatch with ")
                .append("inter-iteration memory barriers, fence wait, and readback of the result ")
                .append("light grid.\n\n")
                .append("| Grid | Voxels | Iterations | CPU p50 | CPU p95 | GPU p50 | GPU p95 | Speedup | Readback |\n")
                .append("| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");
            for (GridResult result : grids) {
                output.append(String.format(
                    Locale.ROOT,
                    "| %d³ | %,d | %d | %.3f ms | %.3f ms | %.3f ms | %.3f ms | %.2fx | %,d B |%n",
                    result.gridSize(),
                    result.voxels(),
                    result.iterations(),
                    nanosToMillis(result.cpuP50Nanos()),
                    nanosToMillis(result.cpuP95Nanos()),
                    nanosToMillis(result.gpuP50Nanos()),
                    nanosToMillis(result.gpuP95Nanos()),
                    result.p50Speedup(),
                    result.readbackBytes()
                ));
            }
            int recommendation = recommendedMinimumGridSize();
            output.append("\nConservative p50 crossover: ");
            if (recommendation < 0) {
                output.append("none in the tested range.\n");
            } else {
                output.append('`').append(recommendation).append("³` grid.\n");
            }
            output.append("A crossover requires the GPU p50 to be at least 5% lower at that grid and every larger tested grid.\n\n")
                .append("This microbenchmark is calibration evidence, not an MSPT or world-simulation result.\n");
            return output.toString();
        }

        private static double nanosToMillis(long nanos) {
            return nanos / 1_000_000.0;
        }
    }
}
