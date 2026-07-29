package dev.hypercore.compute;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Correctness tests for the Java Vector API CPU backend. The backend is loaded
 * through {@link VectorBackendFactory} so that the test class never statically
 * references the incubator {@code jdk.incubator.vector} types; on a JVM without
 * that module the tests are skipped instead of erroring at class-load time.
 * The scalar backend is the bit-exact reference for every assertion.
 */
class VectorSpatialComputeBackendTest {
    private SpatialComputeBackend backend;
    private final SpatialComputeBackend scalar = new ScalarSpatialComputeBackend();

    @BeforeEach
    void loadBackend() {
        backend = VectorBackendFactory.tryLoad().orElse(null);
        Assumptions.assumeTrue(backend != null, "jdk.incubator.vector not available");
        assertEquals("cpu-vector", backend.id());
        assertEquals(ComputeDeviceType.CPU, backend.deviceType());
    }

    @Test
    void squaredDistancesMatchScalarAcrossSizes() {
        for (int size : new int[]{1, 2, 7, 33, 35, 64, 100, 1_024, 1_025}) {
            float[] x = new float[size];
            float[] y = new float[size];
            float[] z = new float[size];
            for (int index = 0; index < size; index++) {
                x[index] = index * 0.25f - 30.0f;
                y[index] = index % 17 - 8.0f;
                z[index] = index % 31 * 0.5f;
            }
            float[] expected = new float[size];
            float[] actual = new float[size];
            scalar.squaredDistances(1.25f, -2.5f, 4.0f, x, y, z, expected);
            backend.squaredDistances(1.25f, -2.5f, 4.0f, x, y, z, actual);
            assertArrayEquals(expected, actual, "squaredDistances mismatch at size " + size);
        }
    }

    @Test
    void radiusMaskMatchesScalarAcrossSizes() {
        for (int size : new int[]{1, 32, 33, 35, 64, 100, 1_024, 1_025}) {
            float[] x = new float[size];
            float[] y = new float[size];
            float[] z = new float[size];
            for (int index = 0; index < size; index++) {
                x[index] = index * 0.25f - 30.0f;
                y[index] = index % 17 - 8.0f;
                z[index] = index % 31 * 0.5f;
            }
            int wordCount = SpatialComputeBackend.maskWordCount(size);
            int[] expected = new int[wordCount];
            int[] actual = new int[wordCount];
            scalar.radiusMask(1.25f, -2.5f, 4.0f, 4_096.0f, x, y, z, expected);
            backend.radiusMask(1.25f, -2.5f, 4.0f, 4_096.0f, x, y, z, actual);
            assertArrayEquals(expected, actual, "radiusMask mismatch at size " + size);
        }
    }

    @Test
    void radiusMaskIsInclusiveAtBoundary() {
        float[] x = {0.0f, 1.0f, 2.0f};
        float[] y = {0.0f, 0.0f, 0.0f};
        float[] z = {0.0f, 0.0f, 0.0f};
        // squared distances: 0, 1, 4. squaredRadius = 1.0 must include indices 0 and 1.
        int[] expected = new int[1];
        int[] actual = new int[1];
        scalar.radiusMask(0.0f, 0.0f, 0.0f, 1.0f, x, y, z, expected);
        backend.radiusMask(0.0f, 0.0f, 0.0f, 1.0f, x, y, z, actual);
        assertArrayEquals(expected, actual);
        assertEquals(0b011, expected[0]);
    }

    @Test
    void rejectsMismatchedBatchLengths() {
        assertThrows(IllegalArgumentException.class, () ->
            backend.squaredDistances(0.0f, 0.0f, 0.0f, new float[2], new float[1], new float[2], new float[2]));
    }

    @Test
    void rejectsUndersizedRadiusMaskOutput() {
        assertThrows(IllegalArgumentException.class, () ->
            backend.radiusMask(0.0f, 0.0f, 0.0f, 1.0f, new float[33], new float[33], new float[33], new int[1]));
    }

    @Test
    void rejectsNegativeSquaredRadius() {
        assertThrows(IllegalArgumentException.class, () ->
            backend.radiusMask(0.0f, 0.0f, 0.0f, -1.0f, new float[1], new float[1], new float[1], new int[1]));
    }

    @Test
    void snapshotRadiusMaskMatchesScalar() {
        int size = 100;
        float[] x = new float[size];
        float[] y = new float[size];
        float[] z = new float[size];
        for (int index = 0; index < size; index++) {
            x[index] = index * 0.5f - 25.0f;
            y[index] = index % 13 - 6.0f;
            z[index] = index % 19 * 0.5f;
        }
        int wordCount = SpatialComputeBackend.maskWordCount(size);
        int[] expected = new int[wordCount];
        int[] actual = new int[wordCount];
        scalar.radiusMask(2.0f, -3.0f, 1.0f, 2_500.0f, x, y, z, expected);
        try (SpatialComputeBackend.PositionSnapshot snapshot = backend.prepareSnapshot(x, y, z)) {
            snapshot.radiusMask(2.0f, -3.0f, 1.0f, 2_500.0f, actual);
        }
        assertArrayEquals(expected, actual);
    }
}
