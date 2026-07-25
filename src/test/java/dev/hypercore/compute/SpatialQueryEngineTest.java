package dev.hypercore.compute;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SpatialQueryEngineTest {
    @Test
    void returnsOrderedMatchesAndIncludesRadiusBoundary() {
        SpatialQueryEngine engine = new SpatialQueryEngine(new ScalarSpatialComputeBackend());
        SpatialQueryEngine.PositionBatch positions = new SpatialQueryEngine.PositionBatch(
            new float[]{0, 3, 4, -3, 8},
            new float[]{0, 4, 0, 0, 0},
            new float[]{0, 0, 3, 4, 0}
        );

        SpatialQueryEngine.QueryResult result = engine.withinRadius(0, 0, 0, 5, positions);

        assertEquals(5, result.candidateCount());
        assertEquals(4, result.matchCount());
        assertArrayEquals(new int[]{0, 1, 2, 3}, result.matchingIndices());
    }

    @Test
    void snapshotsInputAndDefensivelyCopiesResults() {
        float[] x = {1, 10};
        float[] y = {0, 0};
        float[] z = {0, 0};
        SpatialQueryEngine.PositionBatch positions = new SpatialQueryEngine.PositionBatch(x, y, z);
        x[0] = 100;

        SpatialQueryEngine.QueryResult result = new SpatialQueryEngine(new ScalarSpatialComputeBackend())
            .withinRadius(0, 0, 0, 2, positions);
        int[] firstRead = result.matchingIndices();
        firstRead[0] = 99;

        assertArrayEquals(new int[]{0}, result.matchingIndices());
    }

    @Test
    void recordsAdaptiveQueryMetrics() {
        try (AdaptiveSpatialComputeBackend backend = AdaptiveSpatialComputeBackend.unavailable(
            new GpuOffloadPolicy(1), "test unavailable")) {
            SpatialQueryEngine engine = new SpatialQueryEngine(backend);

            engine.withinRadius(0, 0, 0, 1, new SpatialQueryEngine.PositionBatch(
                new float[]{0, 1, 2}, new float[3], new float[3]));

            assertEquals(1, backend.status().spatialQueries());
            assertEquals(3, backend.status().spatialCandidates());
            assertEquals(2, backend.status().spatialMatches());
            assertEquals(1, backend.status().cpuRadiusMaskBatches());
            assertEquals(0, backend.status().gpuRadiusMaskBatches());
        }
    }

    @Test
    void rejectsInvalidGeometry() {
        SpatialQueryEngine engine = new SpatialQueryEngine(new ScalarSpatialComputeBackend());
        SpatialQueryEngine.PositionBatch empty = new SpatialQueryEngine.PositionBatch(
            new float[0], new float[0], new float[0]);

        assertThrows(IllegalArgumentException.class, () -> engine.withinRadius(0, 0, 0, -1, empty));
        assertThrows(IllegalArgumentException.class, () -> engine.withinRadius(Float.NaN, 0, 0, 1, empty));
        assertThrows(IllegalArgumentException.class, () -> new SpatialQueryEngine.PositionBatch(
            new float[1], new float[2], new float[1]));
    }
}
