package dev.hypercore.compute;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Measures complete backend calls over prebuilt position snapshots and warmed buffers. */
public final class SpatialComputeBenchmark {
    private static final double REQUIRED_GPU_ADVANTAGE = 0.95;
    private static final int MULTI_QUERY_COUNT = 8;
    // The Java Vector API runs interpreted until HotSpot C2 compiles its intrinsic
    // methods, which needs far more invocations than the per-batch warmup. Prime on
    // a representative batch before timing so the first batches measure steady-state
    // throughput instead of interpreter overhead.
    private static final int CPU_PRIME_BATCH_SIZE = 65_536;
    private static final int CPU_PRIME_ITERATIONS = 3_000;
    private static volatile int blackhole;

    private SpatialComputeBenchmark() {
    }

    public static Report run(
        SpatialComputeBackend cpu,
        SpatialComputeBackend gpu,
        String gpuDevice,
        String gpuTransferMode,
        int[] batchSizes,
        int warmupIterations,
        int sampleIterations
    ) {
        return run(cpu, null, gpu, gpuDevice, gpuTransferMode, batchSizes, warmupIterations, sampleIterations);
    }

    public static Report run(
        SpatialComputeBackend cpu,
        SpatialComputeBackend vector,
        SpatialComputeBackend gpu,
        String gpuDevice,
        String gpuTransferMode,
        int[] batchSizes,
        int warmupIterations,
        int sampleIterations
    ) {
        Objects.requireNonNull(cpu, "cpu");
        Objects.requireNonNull(gpu, "gpu");
        Objects.requireNonNull(gpuDevice, "gpuDevice");
        Objects.requireNonNull(gpuTransferMode, "gpuTransferMode");
        Objects.requireNonNull(batchSizes, "batchSizes");
        if (batchSizes.length == 0) {
            throw new IllegalArgumentException("batchSizes cannot be empty");
        }
        if (warmupIterations < 0 || sampleIterations < 1) {
            throw new IllegalArgumentException("Benchmark iteration counts are invalid");
        }

        primeCpuBackend(cpu);
        if (vector != null) {
            primeCpuBackend(vector);
        }

        List<BatchResult> results = new ArrayList<>(batchSizes.length);
        for (int batchSize : batchSizes) {
            if (batchSize < 1) {
                throw new IllegalArgumentException("batchSizes must be positive");
            }
            PositionData positions = PositionData.create(batchSize);
            int[] cpuMask = new int[SpatialComputeBackend.maskWordCount(batchSize)];
            int[] gpuMask = new int[cpuMask.length];
            int[] vectorMask = new int[cpuMask.length];
            int[] residentMask = new int[cpuMask.length];
            SpatialComputeBackend.RadiusMaskQuery[] multiQueries = createQueries(MULTI_QUERY_COUNT);
            int[] individualMultiMask = new int[cpuMask.length * MULTI_QUERY_COUNT];
            int[] batchedMultiMask = new int[individualMultiMask.length];
            int[] singleMultiMask = new int[cpuMask.length];

            for (int iteration = 0; iteration < warmupIterations; iteration++) {
                execute(cpu, positions, cpuMask);
                execute(gpu, positions, gpuMask);
                if (vector != null) {
                    execute(vector, positions, vectorMask);
                }
            }
            verifyMasks(cpu, gpu, positions, cpuMask, gpuMask);
            if (vector != null) {
                verifyMasks(cpu, vector, positions, cpuMask, vectorMask);
            }

            long[] cpuSamples = new long[sampleIterations];
            long[] gpuSamples = new long[sampleIterations];
            long[] vectorSamples = new long[sampleIterations];
            for (int sample = 0; sample < sampleIterations; sample++) {
                if ((sample & 1) == 0) {
                    cpuSamples[sample] = measure(cpu, positions, cpuMask);
                    gpuSamples[sample] = measure(gpu, positions, gpuMask);
                    if (vector != null) {
                        vectorSamples[sample] = measure(vector, positions, vectorMask);
                    }
                } else {
                    if (vector != null) {
                        vectorSamples[sample] = measure(vector, positions, vectorMask);
                    }
                    gpuSamples[sample] = measure(gpu, positions, gpuMask);
                    cpuSamples[sample] = measure(cpu, positions, cpuMask);
                }
            }

            long[] residentSamples = new long[sampleIterations];
            long[] individualMultiSamples = new long[sampleIterations];
            long[] batchedMultiSamples = new long[sampleIterations];
            try (SpatialComputeBackend.PositionSnapshot residentSnapshot = gpu.prepareSnapshot(
                positions.x(), positions.y(), positions.z()
            )) {
                for (int iteration = 0; iteration < warmupIterations; iteration++) {
                    execute(residentSnapshot, residentMask);
                }
                execute(residentSnapshot, residentMask);
                verifyResidentMask(positions, cpuMask, residentMask);
                for (int sample = 0; sample < sampleIterations; sample++) {
                    residentSamples[sample] = measure(residentSnapshot, residentMask);
                }
                for (int iteration = 0; iteration < warmupIterations; iteration++) {
                    executeIndividualQueries(
                        residentSnapshot, multiQueries, singleMultiMask, individualMultiMask
                    );
                    residentSnapshot.radiusMasks(multiQueries, batchedMultiMask);
                }
                verifyMultiQueryMasks(positions, individualMultiMask, batchedMultiMask);
                for (int sample = 0; sample < sampleIterations; sample++) {
                    if ((sample & 1) == 0) {
                        individualMultiSamples[sample] = measureIndividualQueries(
                            residentSnapshot, multiQueries, singleMultiMask, individualMultiMask
                        );
                        batchedMultiSamples[sample] = measureBatchedQueries(
                            residentSnapshot, multiQueries, batchedMultiMask
                        );
                    } else {
                        batchedMultiSamples[sample] = measureBatchedQueries(
                            residentSnapshot, multiQueries, batchedMultiMask
                        );
                        individualMultiSamples[sample] = measureIndividualQueries(
                            residentSnapshot, multiQueries, singleMultiMask, individualMultiMask
                        );
                    }
                }
            }
            results.add(new BatchResult(
                batchSize,
                percentile(cpuSamples, 0.50),
                percentile(cpuSamples, 0.95),
                percentile(gpuSamples, 0.50),
                percentile(gpuSamples, 0.95),
                percentile(residentSamples, 0.50),
                percentile(residentSamples, 0.95),
                (long) cpuMask.length * Integer.BYTES,
                MULTI_QUERY_COUNT,
                percentile(individualMultiSamples, 0.50),
                percentile(individualMultiSamples, 0.95),
                percentile(batchedMultiSamples, 0.50),
                percentile(batchedMultiSamples, 0.95),
                vector == null ? 0L : percentile(vectorSamples, 0.50),
                vector == null ? 0L : percentile(vectorSamples, 0.95)
            ));
        }
        return new Report(gpuDevice, gpuTransferMode, warmupIterations, sampleIterations, List.copyOf(results));
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

    private static void verifyResidentMask(PositionData positions, int[] cpuMask, int[] residentMask) {
        if (!Arrays.equals(cpuMask, residentMask)) {
            for (int word = 0; word < cpuMask.length; word++) {
                if (cpuMask[word] != residentMask[word]) {
                    throw new IllegalStateException(
                        "Resident benchmark correctness mismatch at batch " + positions.size()
                            + ", word " + word
                    );
                }
            }
        }
    }

    private static void verifyMultiQueryMasks(
        PositionData positions,
        int[] individualMasks,
        int[] batchedMasks
    ) {
        if (!Arrays.equals(individualMasks, batchedMasks)) {
            for (int word = 0; word < individualMasks.length; word++) {
                if (individualMasks[word] != batchedMasks[word]) {
                    throw new IllegalStateException(
                        "Multi-query benchmark correctness mismatch at batch " + positions.size()
                            + ", flattened word " + word
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

    private static long measure(SpatialComputeBackend.PositionSnapshot snapshot, int[] output) {
        long started = System.nanoTime();
        execute(snapshot, output);
        long elapsed = System.nanoTime() - started;
        blackhole ^= output[output.length - 1];
        return elapsed;
    }

    private static long measureIndividualQueries(
        SpatialComputeBackend.PositionSnapshot snapshot,
        SpatialComputeBackend.RadiusMaskQuery[] queries,
        int[] singleMask,
        int[] output
    ) {
        long started = System.nanoTime();
        executeIndividualQueries(snapshot, queries, singleMask, output);
        long elapsed = System.nanoTime() - started;
        blackhole ^= output[output.length - 1];
        return elapsed;
    }

    private static long measureBatchedQueries(
        SpatialComputeBackend.PositionSnapshot snapshot,
        SpatialComputeBackend.RadiusMaskQuery[] queries,
        int[] output
    ) {
        long started = System.nanoTime();
        snapshot.radiusMasks(queries, output);
        long elapsed = System.nanoTime() - started;
        blackhole ^= output[output.length - 1];
        return elapsed;
    }

    private static void executeIndividualQueries(
        SpatialComputeBackend.PositionSnapshot snapshot,
        SpatialComputeBackend.RadiusMaskQuery[] queries,
        int[] singleMask,
        int[] output
    ) {
        int wordCount = singleMask.length;
        for (int queryIndex = 0; queryIndex < queries.length; queryIndex++) {
            SpatialComputeBackend.RadiusMaskQuery query = queries[queryIndex];
            snapshot.radiusMask(
                query.originX(), query.originY(), query.originZ(), query.squaredRadius(), singleMask
            );
            System.arraycopy(singleMask, 0, output, queryIndex * wordCount, wordCount);
        }
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

    private static void primeCpuBackend(SpatialComputeBackend backend) {
        PositionData positions = PositionData.create(CPU_PRIME_BATCH_SIZE);
        int[] mask = new int[SpatialComputeBackend.maskWordCount(CPU_PRIME_BATCH_SIZE)];
        for (int iteration = 0; iteration < CPU_PRIME_ITERATIONS; iteration++) {
            execute(backend, positions, mask);
        }
    }

    private static void execute(SpatialComputeBackend.PositionSnapshot snapshot, int[] output) {
        snapshot.radiusMask(1.25f, -2.5f, 4.0f, 4_096.0f, output);
    }

    private static SpatialComputeBackend.RadiusMaskQuery[] createQueries(int count) {
        SpatialComputeBackend.RadiusMaskQuery[] queries = new SpatialComputeBackend.RadiusMaskQuery[count];
        for (int queryIndex = 0; queryIndex < count; queryIndex++) {
            queries[queryIndex] = new SpatialComputeBackend.RadiusMaskQuery(
                queryIndex * 3.25f - 12.0f,
                queryIndex % 3 * 2.0f - 2.0f,
                queryIndex * -1.75f + 6.0f,
                3_600.0f + queryIndex * 128.0f
            );
        }
        return queries;
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
        long residentGpuP50Nanos,
        long residentGpuP95Nanos,
        long gpuReadbackBytes,
        int multiQueryCount,
        long individualMultiGpuP50Nanos,
        long individualMultiGpuP95Nanos,
        long batchedMultiGpuP50Nanos,
        long batchedMultiGpuP95Nanos,
        long vectorP50Nanos,
        long vectorP95Nanos
    ) {
        public BatchResult(
            int batchSize,
            long cpuP50Nanos,
            long cpuP95Nanos,
            long gpuP50Nanos,
            long gpuP95Nanos,
            long residentGpuP50Nanos,
            long residentGpuP95Nanos,
            long gpuReadbackBytes,
            int multiQueryCount,
            long individualMultiGpuP50Nanos,
            long individualMultiGpuP95Nanos,
            long batchedMultiGpuP50Nanos,
            long batchedMultiGpuP95Nanos
        ) {
            this(
                batchSize,
                cpuP50Nanos,
                cpuP95Nanos,
                gpuP50Nanos,
                gpuP95Nanos,
                residentGpuP50Nanos,
                residentGpuP95Nanos,
                gpuReadbackBytes,
                multiQueryCount,
                individualMultiGpuP50Nanos,
                individualMultiGpuP95Nanos,
                batchedMultiGpuP50Nanos,
                batchedMultiGpuP95Nanos,
                0L,
                0L
            );
        }

        public BatchResult(
            int batchSize,
            long cpuP50Nanos,
            long cpuP95Nanos,
            long gpuP50Nanos,
            long gpuP95Nanos,
            long residentGpuP50Nanos,
            long residentGpuP95Nanos,
            long gpuReadbackBytes
        ) {
            this(
                batchSize,
                cpuP50Nanos,
                cpuP95Nanos,
                gpuP50Nanos,
                gpuP95Nanos,
                residentGpuP50Nanos,
                residentGpuP95Nanos,
                gpuReadbackBytes,
                1,
                residentGpuP50Nanos,
                residentGpuP95Nanos,
                residentGpuP50Nanos,
                residentGpuP95Nanos
            );
        }

        public BatchResult(
            int batchSize,
            long cpuP50Nanos,
            long cpuP95Nanos,
            long gpuP50Nanos,
            long gpuP95Nanos,
            long gpuReadbackBytes
        ) {
            this(
                batchSize,
                cpuP50Nanos,
                cpuP95Nanos,
                gpuP50Nanos,
                gpuP95Nanos,
                gpuP50Nanos,
                gpuP95Nanos,
                gpuReadbackBytes
            );
        }

        public double p50Speedup() {
            return (double) cpuP50Nanos / gpuP50Nanos;
        }

        public double residentP50Speedup() {
            return (double) cpuP50Nanos / residentGpuP50Nanos;
        }

        public double multiQuerySpeedup() {
            return (double) individualMultiGpuP50Nanos / batchedMultiGpuP50Nanos;
        }

        public double vectorP50Speedup() {
            return vectorP50Nanos == 0L ? 0.0 : (double) cpuP50Nanos / vectorP50Nanos;
        }
    }

    public record Report(
        String gpuDevice,
        String gpuTransferMode,
        int warmupIterations,
        int sampleIterations,
        List<BatchResult> batches
    ) {
        public Report {
            gpuDevice = Objects.requireNonNull(gpuDevice, "gpuDevice");
            gpuTransferMode = Objects.requireNonNull(gpuTransferMode, "gpuTransferMode");
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

        public int recommendedResidentMinimumBatchSize() {
            for (int index = 0; index < batches.size(); index++) {
                boolean sustained = true;
                for (int candidate = index; candidate < batches.size(); candidate++) {
                    BatchResult batch = batches.get(candidate);
                    if (batch.residentGpuP50Nanos() >= batch.cpuP50Nanos() * REQUIRED_GPU_ADVANTAGE) {
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

        public boolean hasVectorData() {
            for (BatchResult batch : batches) {
                if (batch.vectorP50Nanos() > 0L) {
                    return true;
                }
            }
            return false;
        }

        private String mainTableHeader() {
            return hasVectorData()
                ? "| Candidates | CPU p50 | CPU p95 | Vector CPU p50 | Vector CPU p95 | Vector speedup | Full GPU p50 | Full GPU p95 | Resident GPU p50 | Resident GPU p95 | Full speedup | Resident speedup | Readback |\n"
                : "| Candidates | CPU p50 | CPU p95 | Full GPU p50 | Full GPU p95 | Resident GPU p50 | Resident GPU p95 | Full speedup | Resident speedup | Readback |\n";
        }

        private String mainTableSeparator() {
            int columns = hasVectorData() ? 13 : 10;
            StringBuilder separator = new StringBuilder("|");
            for (int column = 0; column < columns; column++) {
                separator.append(" ---: |");
            }
            return separator.append("\n").toString();
        }

        private String mainTableRow(BatchResult batch) {
            if (hasVectorData()) {
                return String.format(
                    Locale.ROOT,
                    "| %,d | %.3f ms | %.3f ms | %.3f ms | %.3f ms | %.2fx | %.3f ms | %.3f ms | %.3f ms | %.3f ms | %.2fx | %.2fx | %,d B |%n",
                    batch.batchSize(),
                    nanosToMillis(batch.cpuP50Nanos()),
                    nanosToMillis(batch.cpuP95Nanos()),
                    nanosToMillis(batch.vectorP50Nanos()),
                    nanosToMillis(batch.vectorP95Nanos()),
                    batch.vectorP50Speedup(),
                    nanosToMillis(batch.gpuP50Nanos()),
                    nanosToMillis(batch.gpuP95Nanos()),
                    nanosToMillis(batch.residentGpuP50Nanos()),
                    nanosToMillis(batch.residentGpuP95Nanos()),
                    batch.p50Speedup(),
                    batch.residentP50Speedup(),
                    batch.gpuReadbackBytes()
                );
            }
            return String.format(
                Locale.ROOT,
                "| %,d | %.3f ms | %.3f ms | %.3f ms | %.3f ms | %.3f ms | %.3f ms | %.2fx | %.2fx | %,d B |%n",
                batch.batchSize(),
                nanosToMillis(batch.cpuP50Nanos()),
                nanosToMillis(batch.cpuP95Nanos()),
                nanosToMillis(batch.gpuP50Nanos()),
                nanosToMillis(batch.gpuP95Nanos()),
                nanosToMillis(batch.residentGpuP50Nanos()),
                nanosToMillis(batch.residentGpuP95Nanos()),
                batch.p50Speedup(),
                batch.residentP50Speedup(),
                batch.gpuReadbackBytes()
            );
        }

        public String markdown(String generatedAt) {
            StringBuilder output = new StringBuilder();
            output.append("# HyperCore Compute Benchmark\n\n")
                .append("Generated: ").append(generatedAt).append("\n\n")
                .append("- GPU: `").append(gpuDevice).append("`\n")
                .append("- GPU transfer mode: `").append(gpuTransferMode).append("`\n")
                .append("- Java: `").append(System.getProperty("java.version")).append("`\n")
                .append("- OS: `").append(System.getProperty("os.name")).append(' ')
                .append(System.getProperty("os.arch")).append("`\n")
                .append("- Logical processors: ").append(Runtime.getRuntime().availableProcessors()).append("\n")
                .append("- Warmups per backend and batch: ").append(warmupIterations).append("\n")
                .append("- Timed samples per backend and batch: ").append(sampleIterations).append("\n\n")
                .append("The position arrays and output masks are allocated before timing. CPU timings include scalar mask ")
                .append("construction. Full GPU timings include three host uploads, compute dispatch, fence wait, and packed-mask ")
                .append("readback. Resident GPU timings reuse one prepared position snapshot and include dispatch, fence wait, and ")
                .append("packed-mask readback. Snapshot preparation and result-index expansion are excluded.\n\n")
                .append(mainTableHeader())
                .append(mainTableSeparator());
            for (BatchResult batch : batches) {
                output.append(mainTableRow(batch));
            }
            output.append("\n## Multi-Query Submission\n\n")
                .append("Each row compares repeated resident queries, each with its own queue submission and fence wait, against ")
                .append("one command buffer containing the same queries and one fence wait.\n\n")
                .append("| Candidates | Queries | Individual GPU p50 | Individual GPU p95 | Batched GPU p50 | Batched GPU p95 | Submission speedup | Total readback |\n")
                .append("| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");
            for (BatchResult batch : batches) {
                output.append(String.format(
                    Locale.ROOT,
                    "| %,d | %d | %.3f ms | %.3f ms | %.3f ms | %.3f ms | %.2fx | %,d B |%n",
                    batch.batchSize(),
                    batch.multiQueryCount(),
                    nanosToMillis(batch.individualMultiGpuP50Nanos()),
                    nanosToMillis(batch.individualMultiGpuP95Nanos()),
                    nanosToMillis(batch.batchedMultiGpuP50Nanos()),
                    nanosToMillis(batch.batchedMultiGpuP95Nanos()),
                    batch.multiQuerySpeedup(),
                    batch.gpuReadbackBytes() * batch.multiQueryCount()
                ));
            }
            int recommendation = recommendedMinimumBatchSize();
            output.append("\nConservative full-call p50 crossover: ");
            if (recommendation < 0) {
                output.append("none in the tested range.\n");
            } else {
                output.append('`').append(recommendation).append("` candidates.\n");
            }
            int residentRecommendation = recommendedResidentMinimumBatchSize();
            output.append("Conservative resident-snapshot p50 crossover: ");
            if (residentRecommendation < 0) {
                output.append("none in the tested range.\n");
            } else {
                output.append('`').append(residentRecommendation).append("` candidates.\n");
            }
            output.append("A crossover requires the relevant GPU p50 to be at least 5% lower at that batch and every larger tested batch.\n\n")
                .append("This microbenchmark is calibration evidence, not an MSPT or world-simulation result.\n");
            return output.toString();
        }

        private static double nanosToMillis(long nanos) {
            return nanos / 1_000_000.0;
        }
    }
}
