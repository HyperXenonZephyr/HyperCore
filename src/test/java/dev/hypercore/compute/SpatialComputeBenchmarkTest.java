package dev.hypercore.compute;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpatialComputeBenchmarkTest {
    @Test
    void calculatesNearestRankPercentilesWithoutMutatingSamples() {
        long[] samples = {50, 10, 30, 20, 40};

        assertEquals(30, SpatialComputeBenchmark.percentile(samples, 0.50));
        assertEquals(50, SpatialComputeBenchmark.percentile(samples, 0.95));
        assertEquals(50, samples[0]);
    }

    @Test
    void recommendsFirstMeasuredGpuCrossover() {
        SpatialComputeBenchmark.Report report = new SpatialComputeBenchmark.Report(
            "test-gpu",
            "test-transfer",
            1,
            3,
            List.of(
                new SpatialComputeBenchmark.BatchResult(1_024, 10, 20, 20, 30, 128),
                new SpatialComputeBenchmark.BatchResult(4_096, 20, 30, 10, 20, 512),
                new SpatialComputeBenchmark.BatchResult(16_384, 40, 50, 10, 20, 2_048)
            )
        );

        assertEquals(4_096, report.recommendedMinimumBatchSize());
        assertTrue(report.markdown("test-time").contains("GPU transfer mode: `test-transfer`"));
    }

    @Test
    void rejectsAnIsolatedGpuWinAsThresholdEvidence() {
        SpatialComputeBenchmark.Report report = new SpatialComputeBenchmark.Report(
            "test-gpu",
            "test-transfer",
            1,
            3,
            List.of(
                new SpatialComputeBenchmark.BatchResult(1_024, 20, 30, 10, 20, 128),
                new SpatialComputeBenchmark.BatchResult(4_096, 10, 20, 20, 30, 512)
            )
        );

        assertEquals(-1, report.recommendedMinimumBatchSize());
    }

    @Test
    void rejectsInvalidPercentileInput() {
        assertThrows(IllegalArgumentException.class,
            () -> SpatialComputeBenchmark.percentile(new long[0], 0.50));
        assertThrows(IllegalArgumentException.class,
            () -> SpatialComputeBenchmark.percentile(new long[]{1}, 1.1));
    }
}
