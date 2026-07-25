package dev.hypercore.compute;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Measures complete backend calls over prebuilt position snapshots and warmed buffers. */
public final class SpatialComputeBenchmark {
    private static final double REQUIRED_GPU_ADVANTAGE = 0.95;
    private static volatile int blackhole;

    private SpatialComputeBenchmark() {
    }

    public static Report run(
        SpatialComputeBackend cpu,
        SpatialComputeBackend gpu,
        String gpuDevice,
        int[] batchSizes,
        int warmupIterations,
        int sampleIterations
    ) {
        Objects.requireNonNull(cpu, "cpu");
        Objects.requireNonNull(gpu, "gpu");
        Objects.requireNonNull(gpuDevice, "gpuDevice");
        Objects.requireNonNull(batchSizes, "batchSizes");
        if (batchSizes.length == 0) {
            throw new IllegalArgumentException("batchSizes cannot be empty");
        }
        if (warmupIterations < 0 || sampleIterations < 1) {
            throw new IllegalArgumentException("Benchmark iteration counts are invalid");
        }

        List<BatchResult> results = new ArrayList<>(batchSizes.length);
        for (int batchSize : batchSizes) {
            if (batchSize < 1) {
                throw new IllegalArgumentException("batchSizes must be positive");
            }
            PositionData positions = PositionData.create(batchSize);
            int[] cpuMask = new int[SpatialComputeBackend.maskWordCount(batchSize)];
            int[] gpuMask = new int[cpuMask.length];

            for (int iteration = 0; iteration < warmupIterations; iteration++) {
                execute(cpu, positions, cpuMask);
                execute(gpu, positions, gpuMask);
            }
            verifyMasks(cpu, gpu, positions, cpuMask, gpuMask);

            long[] cpuSamples = new long[sampleIterations];
            long[] gpuSamples = new long[sampleIterations];
            for (int sample = 0; sample < sampleIterations; sample++) {
                if ((sample & 1) == 0) {
                    cpuSamples[sample] = measure(cpu, positions, cpuMask);
                    gpuSamples[sample] = measure(gpu, positions, gpuMask);
                } else {
                    gpuSamples[sample] = measure(gpu, positions, gpuMask);
                    cpuSamples[sample] = measure(cpu, positions, cpuMask);
                }
            }
            results.add(new BatchResult(
                batchSize,
                percentile(cpuSamples, 0.50),
                percentile(cpuSamples, 0.95),
                percentile(gpuSamples, 0.50),
                percentile(gpuSamples, 0.95),
                (long) cpuMask.length * Integer.BYTES
            ));
        }
        return new Report(gpuDevice, warmupIterations, sampleIterations, List.copyOf(results));
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

    private static void verifyMasks(
        SpatialComputeBackend cpu,
        SpatialComputeBackend gpu,
        PositionData positions,
        int[] cpuMask,
        int[] gpuMask
    ) {
        execute(cpu, positions, cpuMask);
        execute(gpu, positions, gpuMask);
        if (!Arrays.equals(cpuMask, gpuMask)) {
            for (int word = 0; word < cpuMask.length; word++) {
                if (cpuMask[word] != gpuMask[word]) {
                    throw new IllegalStateException(
                        "Benchmark correctness mismatch at batch " + positions.size()
                            + ", word " + word
                    );
                }
            }
        }
    }

    private static long measure(SpatialComputeBackend backend, PositionData positions, int[] output) {
        long started = System.nanoTime();
        execute(backend, positions, output);
        long elapsed = System.nanoTime() - started;
        blackhole ^= output[output.length - 1];
        return elapsed;
    }

    private static void execute(SpatialComputeBackend backend, PositionData positions, int[] output) {
        backend.radiusMask(
            1.25f,
            -2.5f,
            4.0f,
            4_096.0f,
            positions.x(),
            positions.y(),
            positions.z(),
            output
        );
    }

    private record PositionData(float[] x, float[] y, float[] z) {
        private static PositionData create(int size) {
            float[] x = new float[size];
            float[] y = new float[size];
            float[] z = new float[size];
            for (int index = 0; index < size; index++) {
                int mixed = index * 0x9E3779B9;
                x[index] = (mixed & 2_047) * 0.125f - 128.0f;
                y[index] = ((mixed >>> 11) & 511) * 0.25f - 64.0f;
                z[index] = ((mixed >>> 20) & 2_047) * 0.125f - 128.0f;
            }
            return new PositionData(x, y, z);
        }

        private int size() {
            return x.length;
        }
    }

    public record BatchResult(
        int batchSize,
        long cpuP50Nanos,
        long cpuP95Nanos,
        long gpuP50Nanos,
        long gpuP95Nanos,
        long gpuReadbackBytes
    ) {
        public double p50Speedup() {
            return (double) cpuP50Nanos / gpuP50Nanos;
        }
    }

    public record Report(
        String gpuDevice,
        int warmupIterations,
        int sampleIterations,
        List<BatchResult> batches
    ) {
        public Report {
            gpuDevice = Objects.requireNonNull(gpuDevice, "gpuDevice");
            batches = List.copyOf(batches);
        }

        public int recommendedMinimumBatchSize() {
            for (int index = 0; index < batches.size(); index++) {
                boolean sustained = true;
                for (int candidate = index; candidate < batches.size(); candidate++) {
                    BatchResult batch = batches.get(candidate);
                    if (batch.gpuP50Nanos() >= batch.cpuP50Nanos() * REQUIRED_GPU_ADVANTAGE) {
                        sustained = false;
                        break;
                    }
                }
                if (sustained) {
                    return batches.get(index).batchSize();
                }
            }
            return -1;
        }

        public String markdown(String generatedAt) {
            StringBuilder output = new StringBuilder();
            output.append("# HyperCore Compute Benchmark\n\n")
                .append("Generated: ").append(generatedAt).append("\n\n")
                .append("- GPU: `").append(gpuDevice).append("`\n")
                .append("- Java: `").append(System.getProperty("java.version")).append("`\n")
                .append("- OS: `").append(System.getProperty("os.name")).append(' ')
                .append(System.getProperty("os.arch")).append("`\n")
                .append("- Logical processors: ").append(Runtime.getRuntime().availableProcessors()).append("\n")
                .append("- Warmups per backend and batch: ").append(warmupIterations).append("\n")
                .append("- Timed samples per backend and batch: ").append(sampleIterations).append("\n\n")
                .append("The position arrays and output masks are allocated before timing. CPU timings include scalar mask ")
                .append("construction. GPU timings include three host uploads, compute dispatch, fence wait, and packed-mask ")
                .append("readback through persistent buffers. Snapshot creation and result-index expansion are excluded.\n\n")
                .append("| Candidates | CPU p50 (ms) | CPU p95 (ms) | GPU p50 (ms) | GPU p95 (ms) | p50 speedup | GPU readback |\n")
                .append("| ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");
            for (BatchResult batch : batches) {
                output.append(String.format(
                    Locale.ROOT,
                    "| %,d | %.3f | %.3f | %.3f | %.3f | %.2fx | %,d B |%n",
                    batch.batchSize(),
                    nanosToMillis(batch.cpuP50Nanos()),
                    nanosToMillis(batch.cpuP95Nanos()),
                    nanosToMillis(batch.gpuP50Nanos()),
                    nanosToMillis(batch.gpuP95Nanos()),
                    batch.p50Speedup(),
                    batch.gpuReadbackBytes()
                ));
            }
            int recommendation = recommendedMinimumBatchSize();
            output.append("\nConservative p50 crossover: ");
            if (recommendation < 0) {
                output.append("none in the tested range.\n");
            } else {
                output.append('`').append(recommendation).append("` candidates.\n");
            }
            output.append("A crossover requires GPU p50 to be at least 5% lower at that batch and every larger tested batch.\n\n")
                .append("This microbenchmark is calibration evidence, not an MSPT or world-simulation result.\n");
            return output.toString();
        }

        private static double nanosToMillis(long nanos) {
            return nanos / 1_000_000.0;
        }
    }
}
