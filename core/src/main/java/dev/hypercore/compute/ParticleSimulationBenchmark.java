package dev.hypercore.compute;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;

/**
 * Measures particle/projectile physics integration across CPU and GPU backends.
 * Each particle count drives a one-step Euler integration over interleaved
 * position/velocity arrays. CPU and GPU output are compared against a tolerance
 * before timing begins so the reported numbers measure steady-state throughput
 * of validated kernels.
 *
 * <p>Because {@code simulate} mutates its input arrays in place, the initial
 * state is captured and restored before every invocation so each sample
 * integrates from the same starting point.
 */
public final class ParticleSimulationBenchmark {
    private static final double REQUIRED_GPU_ADVANTAGE = 0.95;
    private static final float GRAVITY = 9.8f;
    private static final float DT = 0.05f;
    private static final float RESTITUTION = 0.6f;
    private static final float CORRECTNESS_TOLERANCE = 1e-5f;
    private static final long SEED = 0x5EED4ECL;
    // The scalar reference runs interpreted until HotSpot C2 compiles its hot
    // loops, which needs far more invocations than the per-count warmup.  Prime
    // on a representative count before timing so the first counts measure
    // steady-state throughput instead of interpreter overhead.
    private static final int CPU_PRIME_COUNT = 4_096;
    private static final int CPU_PRIME_ITERATIONS = 3_000;
    private static volatile int blackhole;

    private ParticleSimulationBenchmark() {
    }

    public static Report run(
        ScalarParticleSimulationBackend cpu,
        ParticleSimulationBackend gpu,
        String gpuDevice,
        String gpuTransferMode,
        int[] particleCounts,
        int warmupIterations,
        int sampleIterations
    ) {
        Objects.requireNonNull(cpu, "cpu");
        Objects.requireNonNull(gpu, "gpu");
        Objects.requireNonNull(gpuDevice, "gpuDevice");
        Objects.requireNonNull(gpuTransferMode, "gpuTransferMode");
        Objects.requireNonNull(particleCounts, "particleCounts");
        if (particleCounts.length == 0) {
            throw new IllegalArgumentException("particleCounts cannot be empty");
        }
        if (warmupIterations < 0 || sampleIterations < 1) {
            throw new IllegalArgumentException("Benchmark iteration counts are invalid");
        }

        primeCpuBackend(cpu);

        List<CountResult> results = new ArrayList<>(particleCounts.length);
        for (int count : particleCounts) {
            if (count < 1) {
                throw new IllegalArgumentException("particleCounts must be positive");
            }
            float[] initialPositions = generatePositions(count);
            float[] initialVelocities = generateVelocities(count);
            float[] cpuPositions = initialPositions.clone();
            float[] cpuVelocities = initialVelocities.clone();
            float[] gpuPositions = initialPositions.clone();
            float[] gpuVelocities = initialVelocities.clone();

            for (int iteration = 0; iteration < warmupIterations; iteration++) {
                restore(cpuPositions, cpuVelocities, initialPositions, initialVelocities);
                simulate(cpu, cpuPositions, cpuVelocities, count);
                restore(gpuPositions, gpuVelocities, initialPositions, initialVelocities);
                simulate(gpu, gpuPositions, gpuVelocities, count);
            }
            verifyOutput(cpu, gpu, count, initialPositions, initialVelocities,
                cpuPositions, cpuVelocities, gpuPositions, gpuVelocities);

            long[] cpuSamples = new long[sampleIterations];
            long[] gpuSamples = new long[sampleIterations];
            for (int sample = 0; sample < sampleIterations; sample++) {
                if ((sample & 1) == 0) {
                    cpuSamples[sample] = measure(cpu, cpuPositions, cpuVelocities, initialPositions, initialVelocities, count);
                    gpuSamples[sample] = measure(gpu, gpuPositions, gpuVelocities, initialPositions, initialVelocities, count);
                } else {
                    gpuSamples[sample] = measure(gpu, gpuPositions, gpuVelocities, initialPositions, initialVelocities, count);
                    cpuSamples[sample] = measure(cpu, cpuPositions, cpuVelocities, initialPositions, initialVelocities, count);
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
        ScalarParticleSimulationBackend cpu,
        ParticleSimulationBackend gpu,
        int count,
        float[] initialPositions,
        float[] initialVelocities,
        float[] cpuPositions,
        float[] cpuVelocities,
        float[] gpuPositions,
        float[] gpuVelocities
    ) {
        restore(cpuPositions, cpuVelocities, initialPositions, initialVelocities);
        simulate(cpu, cpuPositions, cpuVelocities, count);
        restore(gpuPositions, gpuVelocities, initialPositions, initialVelocities);
        simulate(gpu, gpuPositions, gpuVelocities, count);
        for (int index = 0; index < count * 3; index++) {
            float cpuPosition = cpuPositions[index];
            float gpuPosition = gpuPositions[index];
            if (!Float.isFinite(cpuPosition) || !Float.isFinite(gpuPosition)) {
                throw new IllegalStateException(
                    "Benchmark correctness mismatch at count " + count
                        + ", element " + index
                        + ": non-finite position"
                );
            }
            if (Math.abs(cpuPosition - gpuPosition) > CORRECTNESS_TOLERANCE) {
                throw new IllegalStateException(
                    "Benchmark correctness mismatch at count " + count
                        + ", position " + index
                        + ": |" + cpuPosition + " - " + gpuPosition
                        + "| > " + CORRECTNESS_TOLERANCE
                );
            }
            float cpuVelocity = cpuVelocities[index];
            float gpuVelocity = gpuVelocities[index];
            if (!Float.isFinite(cpuVelocity) || !Float.isFinite(gpuVelocity)) {
                throw new IllegalStateException(
                    "Benchmark correctness mismatch at count " + count
                        + ", element " + index
                        + ": non-finite velocity"
                );
            }
            if (Math.abs(cpuVelocity - gpuVelocity) > CORRECTNESS_TOLERANCE) {
                throw new IllegalStateException(
                    "Benchmark correctness mismatch at count " + count
                        + ", velocity " + index
                        + ": |" + cpuVelocity + " - " + gpuVelocity
                        + "| > " + CORRECTNESS_TOLERANCE
                );
            }
        }
    }

    private static long measure(
        ParticleSimulationBackend backend,
        float[] positions,
        float[] velocities,
        float[] initialPositions,
        float[] initialVelocities,
        int count
    ) {
        restore(positions, velocities, initialPositions, initialVelocities);
        long started = System.nanoTime();
        simulate(backend, positions, velocities, count);
        long elapsed = System.nanoTime() - started;
        blackhole ^= Float.floatToIntBits(positions[count * 3 - 1]);
        return elapsed;
    }

    private static void simulate(
        ParticleSimulationBackend backend, float[] positions, float[] velocities, int count
    ) {
        backend.simulate(positions, velocities, count, GRAVITY, DT, RESTITUTION);
    }

    private static void restore(
        float[] positions, float[] velocities,
        float[] initialPositions, float[] initialVelocities
    ) {
        System.arraycopy(initialPositions, 0, positions, 0, initialPositions.length);
        System.arraycopy(initialVelocities, 0, velocities, 0, initialVelocities.length);
    }

    private static void primeCpuBackend(ScalarParticleSimulationBackend backend) {
        int count = CPU_PRIME_COUNT;
        float[] positions = generatePositions(count);
        float[] velocities = generateVelocities(count);
        float[] initialPositions = positions.clone();
        float[] initialVelocities = velocities.clone();
        for (int iteration = 0; iteration < CPU_PRIME_ITERATIONS; iteration++) {
            restore(positions, velocities, initialPositions, initialVelocities);
            simulate(backend, positions, velocities, count);
        }
    }

    private static float[] generatePositions(int count) {
        Random random = new Random(SEED);
        float[] positions = new float[count * 3];
        for (int i = 0; i < count; i++) {
            int base = i * 3;
            positions[base + 0] = random.nextFloat() * 100.0f;
            // y > 0 so particles start above the ground plane.
            positions[base + 1] = random.nextFloat() * 50.0f + 1.0f;
            positions[base + 2] = random.nextFloat() * 100.0f;
        }
        return positions;
    }

    private static float[] generateVelocities(int count) {
        Random random = new Random(SEED ^ 0x9E3779B9L);
        float[] velocities = new float[count * 3];
        for (int i = 0; i < count; i++) {
            int base = i * 3;
            velocities[base + 0] = (random.nextFloat() - 0.5f) * 40.0f;
            velocities[base + 1] = (random.nextFloat() - 0.5f) * 40.0f;
            velocities[base + 2] = (random.nextFloat() - 0.5f) * 40.0f;
        }
        return velocities;
    }

    public record CountResult(
        int particleCount,
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
         * Returns the smallest particle count where the GPU p50 is at least 5%
         * faster than the CPU p50 and stays at least 5% faster for every larger
         * tested count.  Returns {@code -1} when no such crossover exists.
         */
        public int recommendedMinimumParticleCount() {
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
                    return counts.get(index).particleCount();
                }
            }
            return -1;
        }

        public String markdown(String generatedAt) {
            StringBuilder output = new StringBuilder();
            output.append("# HyperCore Particle Simulation Benchmark\n\n")
                .append("Generated: ").append(generatedAt).append("\n\n")
                .append("- GPU: `").append(gpuDevice).append("`\n")
                .append("- GPU transfer mode: `").append(gpuTransferMode).append("`\n")
                .append("- Java: `").append(System.getProperty("java.version")).append("`\n")
                .append("- OS: `").append(System.getProperty("os.name")).append(' ')
                .append(System.getProperty("os.arch")).append("`\n")
                .append("- Logical processors: ").append(Runtime.getRuntime().availableProcessors()).append("\n")
                .append("- Warmups per backend and count: ").append(warmupIterations).append("\n")
                .append("- Timed samples per backend and count: ").append(sampleIterations).append("\n")
                .append("- Gravity: `").append(GRAVITY).append("`\n")
                .append("- Time step: `").append(DT).append("`\n")
                .append("- Restitution: `").append(RESTITUTION).append("`\n")
                .append("- Correctness tolerance: `").append(CORRECTNESS_TOLERANCE).append("`\n\n")
                .append("Each step integrates one Euler step (position += velocity * dt, ")
                .append("velocity.y -= gravity * dt) with elastic ground collision at y=0. ")
                .append("CPU timings include the full scalar integration loop. GPU timings ")
                .append("include staging upload of positions and velocities, compute dispatch, ")
                .append("fence wait, and readback of the updated arrays.\n\n")
                .append("| Particles | CPU p50 | CPU p95 | GPU p50 | GPU p95 | Speedup | Readback |\n")
                .append("| ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");
            for (CountResult result : counts) {
                output.append(String.format(
                    Locale.ROOT,
                    "| %,d | %.3f ms | %.3f ms | %.3f ms | %.3f ms | %.2fx | %,d B |%n",
                    result.particleCount(),
                    nanosToMillis(result.cpuP50Nanos()),
                    nanosToMillis(result.cpuP95Nanos()),
                    nanosToMillis(result.gpuP50Nanos()),
                    nanosToMillis(result.gpuP95Nanos()),
                    result.p50Speedup(),
                    result.readbackBytes()
                ));
            }
            int recommendation = recommendedMinimumParticleCount();
            output.append("\nConservative p50 crossover: ");
            if (recommendation < 0) {
                output.append("none in the tested range.\n");
            } else {
                output.append('`').append(recommendation).append("` particles.\n");
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
