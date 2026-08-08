package dev.hypercore.compute;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;

/**
 * Measures gravitational N-body integration across CPU and GPU backends.
 * Each body count drives one synchronous force-evaluation step over interleaved
 * position/velocity arrays with per-body masses. The per-step cost is O(n^2),
 * so the GPU advantage grows faster with count than the independent-particle
 * {@link ParticleSimulationBenchmark}.
 *
 * <p>Because {@code simulate} mutates its input arrays in place, the initial
 * state is captured and restored before every invocation so each sample
 * integrates from the same starting point.
 */
public final class NBodySimulationBenchmark {
    private static final double REQUIRED_GPU_ADVANTAGE = 0.95;
    private static final float GRAVITY_CONSTANT = 0.5f;
    private static final float DT = 0.05f;
    private static final float SOFTENING = 1.0f;
    // Looser than the particle backend: GPU inversesqrt and CPU Math.sqrt are
    // not guaranteed bit-identical, and N-body accumulates n force terms.
    private static final float CORRECTNESS_TOLERANCE = 5.0e-3f;
    private static final long SEED = 0x4E424F44L;
    // The scalar reference is O(n^2); prime on a representative count so C2
    // compiles the hot loop before timing begins.
    private static final int CPU_PRIME_COUNT = 1_024;
    private static final int CPU_PRIME_ITERATIONS = 200;
    private static volatile int blackhole;

    private NBodySimulationBenchmark() {
    }

    public static Report run(
        ScalarNBodySimulationBackend cpu,
        NBodySimulationBackend gpu,
        String gpuDevice,
        String gpuTransferMode,
        int[] bodyCounts,
        int warmupIterations,
        int sampleIterations
    ) {
        Objects.requireNonNull(cpu, "cpu");
        Objects.requireNonNull(gpu, "gpu");
        Objects.requireNonNull(gpuDevice, "gpuDevice");
        Objects.requireNonNull(gpuTransferMode, "gpuTransferMode");
        Objects.requireNonNull(bodyCounts, "bodyCounts");
        if (bodyCounts.length == 0) {
            throw new IllegalArgumentException("bodyCounts cannot be empty");
        }
        if (warmupIterations < 0 || sampleIterations < 1) {
            throw new IllegalArgumentException("Benchmark iteration counts are invalid");
        }

        primeCpuBackend(cpu);

        List<CountResult> results = new ArrayList<>(bodyCounts.length);
        for (int count : bodyCounts) {
            if (count < 1) {
                throw new IllegalArgumentException("bodyCounts must be positive");
            }
            float[] initialPositions = generatePositions(count);
            float[] initialVelocities = generateVelocities(count);
            float[] masses = generateMasses(count);
            float[] cpuPositions = initialPositions.clone();
            float[] cpuVelocities = initialVelocities.clone();
            float[] gpuPositions = initialPositions.clone();
            float[] gpuVelocities = initialVelocities.clone();

            for (int iteration = 0; iteration < warmupIterations; iteration++) {
                restore(cpuPositions, cpuVelocities, initialPositions, initialVelocities);
                simulate(cpu, cpuPositions, cpuVelocities, masses, count);
                restore(gpuPositions, gpuVelocities, initialPositions, initialVelocities);
                simulate(gpu, gpuPositions, gpuVelocities, masses, count);
            }
            verifyOutput(cpu, gpu, masses, count, initialPositions, initialVelocities,
                cpuPositions, cpuVelocities, gpuPositions, gpuVelocities);

            long[] cpuSamples = new long[sampleIterations];
            long[] gpuSamples = new long[sampleIterations];
            for (int sample = 0; sample < sampleIterations; sample++) {
                if ((sample & 1) == 0) {
                    cpuSamples[sample] = measure(cpu, cpuPositions, cpuVelocities, masses, initialPositions, initialVelocities, count);
                    gpuSamples[sample] = measure(gpu, gpuPositions, gpuVelocities, masses, initialPositions, initialVelocities, count);
                } else {
                    gpuSamples[sample] = measure(gpu, gpuPositions, gpuVelocities, masses, initialPositions, initialVelocities, count);
                    cpuSamples[sample] = measure(cpu, cpuPositions, cpuVelocities, masses, initialPositions, initialVelocities, count);
                }
            }

            results.add(new CountResult(
                count,
                percentile(cpuSamples, 0.50),
                percentile(cpuSamples, 0.95),
                percentile(gpuSamples, 0.50),
                percentile(gpuSamples, 0.95),
                (long) count * Float.BYTES * 3L * 2L
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
        ScalarNBodySimulationBackend cpu,
        NBodySimulationBackend gpu,
        float[] masses,
        int count,
        float[] initialPositions,
        float[] initialVelocities,
        float[] cpuPositions,
        float[] cpuVelocities,
        float[] gpuPositions,
        float[] gpuVelocities
    ) {
        restore(cpuPositions, cpuVelocities, initialPositions, initialVelocities);
        simulate(cpu, cpuPositions, cpuVelocities, masses, count);
        restore(gpuPositions, gpuVelocities, initialPositions, initialVelocities);
        simulate(gpu, gpuPositions, gpuVelocities, masses, count);
        for (int index = 0; index < count * 3; index++) {
            float cpuPosition = cpuPositions[index];
            float gpuPosition = gpuPositions[index];
            if (!Float.isFinite(cpuPosition) || !Float.isFinite(gpuPosition)) {
                throw new IllegalStateException(
                    "N-body benchmark correctness mismatch at count " + count
                        + ", element " + index
                        + ": non-finite position"
                );
            }
            if (Math.abs(cpuPosition - gpuPosition) > CORRECTNESS_TOLERANCE) {
                throw new IllegalStateException(
                    "N-body benchmark correctness mismatch at count " + count
                        + ", position " + index
                        + ": |" + cpuPosition + " - " + gpuPosition
                        + "| > " + CORRECTNESS_TOLERANCE
                );
            }
            float cpuVelocity = cpuVelocities[index];
            float gpuVelocity = gpuVelocities[index];
            if (!Float.isFinite(cpuVelocity) || !Float.isFinite(gpuVelocity)) {
                throw new IllegalStateException(
                    "N-body benchmark correctness mismatch at count " + count
                        + ", element " + index
                        + ": non-finite velocity"
                );
            }
            if (Math.abs(cpuVelocity - gpuVelocity) > CORRECTNESS_TOLERANCE) {
                throw new IllegalStateException(
                    "N-body benchmark correctness mismatch at count " + count
                        + ", velocity " + index
                        + ": |" + cpuVelocity + " - " + gpuVelocity
                        + "| > " + CORRECTNESS_TOLERANCE
                );
            }
        }
    }

    private static long measure(
        NBodySimulationBackend backend,
        float[] positions,
        float[] velocities,
        float[] masses,
        float[] initialPositions,
        float[] initialVelocities,
        int count
    ) {
        restore(positions, velocities, initialPositions, initialVelocities);
        long started = System.nanoTime();
        simulate(backend, positions, velocities, masses, count);
        long elapsed = System.nanoTime() - started;
        blackhole ^= Float.floatToIntBits(positions[count * 3 - 1]);
        return elapsed;
    }

    private static void simulate(
        NBodySimulationBackend backend, float[] positions, float[] velocities, float[] masses, int count
    ) {
        backend.simulate(positions, velocities, masses, count, GRAVITY_CONSTANT, DT, SOFTENING);
    }

    private static void restore(
        float[] positions, float[] velocities,
        float[] initialPositions, float[] initialVelocities
    ) {
        System.arraycopy(initialPositions, 0, positions, 0, initialPositions.length);
        System.arraycopy(initialVelocities, 0, velocities, 0, initialVelocities.length);
    }

    private static void primeCpuBackend(ScalarNBodySimulationBackend backend) {
        int count = CPU_PRIME_COUNT;
        float[] positions = generatePositions(count);
        float[] velocities = generateVelocities(count);
        float[] masses = generateMasses(count);
        float[] initialPositions = positions.clone();
        float[] initialVelocities = velocities.clone();
        for (int iteration = 0; iteration < CPU_PRIME_ITERATIONS; iteration++) {
            restore(positions, velocities, initialPositions, initialVelocities);
            simulate(backend, positions, velocities, masses, count);
        }
    }

    private static float[] generatePositions(int count) {
        Random random = new Random(SEED);
        float[] positions = new float[count * 3];
        for (int i = 0; i < count; i++) {
            int base = i * 3;
            // Spread bodies across a volume with minimum separation to keep
            // forces bounded and avoid numerical instability.
            positions[base + 0] = (random.nextFloat() - 0.5f) * 200.0f;
            positions[base + 1] = (random.nextFloat() - 0.5f) * 200.0f;
            positions[base + 2] = (random.nextFloat() - 0.5f) * 200.0f;
        }
        return positions;
    }

    private static float[] generateVelocities(int count) {
        Random random = new Random(SEED ^ 0x9E3779B9L);
        float[] velocities = new float[count * 3];
        for (int i = 0; i < count; i++) {
            int base = i * 3;
            velocities[base + 0] = (random.nextFloat() - 0.5f) * 5.0f;
            velocities[base + 1] = (random.nextFloat() - 0.5f) * 5.0f;
            velocities[base + 2] = (random.nextFloat() - 0.5f) * 5.0f;
        }
        return velocities;
    }

    private static float[] generateMasses(int count) {
        Random random = new Random(SEED ^ 0x6D617373L);
        float[] masses = new float[count];
        for (int i = 0; i < count; i++) {
            masses[i] = 1.0f + random.nextFloat() * 4.0f;
        }
        return masses;
    }

    public record CountResult(
        int bodyCount,
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
        List<CountResult> counts
    ) {
        public Report {
            gpuDevice = Objects.requireNonNull(gpuDevice, "gpuDevice");
            gpuTransferMode = Objects.requireNonNull(gpuTransferMode, "gpuTransferMode");
            counts = List.copyOf(counts);
        }

        /**
         * Returns the smallest body count where the GPU p50 is at least 5%
         * faster than the CPU p50 and stays at least 5% faster for every larger
         * tested count.  Returns {@code -1} when no such crossover exists.
         */
        public int recommendedMinimumBodyCount() {
            for (int index = 0; index < counts.size(); index++) {
                boolean sustained = true;
                for (int candidate = index; candidate < counts.size(); candidate++) {
                    CountResult result = counts.get(candidate);
                    if (result.gpuP50Nanos() > result.cpuP50Nanos() * REQUIRED_GPU_ADVANTAGE) {
                        sustained = false;
                        break;
                    }
                }
                if (sustained) {
                    return counts.get(index).bodyCount();
                }
            }
            return -1;
        }

        public String markdown(String generatedAt) {
            StringBuilder output = new StringBuilder();
            output.append("# HyperCore N-Body Simulation Benchmark\n\n")
                .append("Generated: ").append(generatedAt).append("\n\n")
                .append("- GPU: `").append(gpuDevice).append("`\n")
                .append("- GPU transfer mode: `").append(gpuTransferMode).append("`\n")
                .append("- Java: `").append(System.getProperty("java.version")).append("`\n")
                .append("- OS: `").append(System.getProperty("os.name")).append(' ')
                .append(System.getProperty("os.arch")).append("`\n")
                .append("- Logical processors: ").append(Runtime.getRuntime().availableProcessors()).append("\n")
                .append("- Warmups per backend and count: ").append(warmupIterations).append("\n")
                .append("- Timed samples per backend and count: ").append(sampleIterations).append("\n")
                .append("- Gravity constant: `").append(GRAVITY_CONSTANT).append("`\n")
                .append("- Time step: `").append(DT).append("`\n")
                .append("- Softening: `").append(SOFTENING).append("`\n")
                .append("- Correctness tolerance: `").append(CORRECTNESS_TOLERANCE).append("`\n\n")
                .append("Each step evaluates all pairwise gravitational forces synchronously ")
                .append("(O(n^2)) and integrates semi-implicit Euler. CPU timings include the ")
                .append("full scalar O(n^2) force loop. GPU timings include staging upload of ")
                .append("positions, velocities, and masses, compute dispatch, fence wait, and ")
                .append("readback of the updated position and velocity arrays.\n\n")
                .append("| Bodies | CPU p50 | CPU p95 | GPU p50 | GPU p95 | Speedup | Readback |\n")
                .append("| ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");
            for (CountResult result : counts) {
                output.append(String.format(
                    Locale.ROOT,
                    "| %,d | %.3f ms | %.3f ms | %.3f ms | %.3f ms | %.2fx | %,d B |%n",
                    result.bodyCount(),
                    nanosToMillis(result.cpuP50Nanos()),
                    nanosToMillis(result.cpuP95Nanos()),
                    nanosToMillis(result.gpuP50Nanos()),
                    nanosToMillis(result.gpuP95Nanos()),
                    result.p50Speedup(),
                    result.readbackBytes()
                ));
            }
            int recommendation = recommendedMinimumBodyCount();
            output.append("\nConservative p50 crossover: ");
            if (recommendation < 0) {
                output.append("none in the tested range.\n");
            } else {
                output.append('`').append(recommendation).append("` bodies.\n");
            }
            output.append("A crossover requires the GPU p50 to be at least 5% lower at that count and every larger tested count.\n\n")
                .append("This microbenchmark is calibration evidence, not an MSPT or world-simulation result.\n");
            return output.toString();
        }

        private static double nanosToMillis(long nanos) {
            return nanos / 1_000_000.0;
        }
    }
}
