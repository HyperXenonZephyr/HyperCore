package dev.hypercore.compute;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScalarSpatialComputeBackendTest {
    private final ScalarSpatialComputeBackend backend = new ScalarSpatialComputeBackend();

    @Test
    void computesSquaredDistancesForStructureOfArraysInput() {
        float[] output = new float[3];

        backend.squaredDistances(
            1.0f,
            2.0f,
            3.0f,
            new float[]{1.0f, 4.0f, -1.0f},
            new float[]{2.0f, 6.0f, 0.0f},
            new float[]{3.0f, 3.0f, 5.0f},
            output
        );

        assertArrayEquals(new float[]{0.0f, 25.0f, 12.0f}, output);
        assertEquals("cpu-scalar", backend.id());
        assertEquals(ComputeDeviceType.CPU, backend.deviceType());
    }

    @Test
    void rejectsMismatchedBatchLengths() {
        assertThrows(
            IllegalArgumentException.class,
            () -> backend.squaredDistances(
                0.0f,
                0.0f,
                0.0f,
                new float[2],
                new float[1],
                new float[2],
                new float[2]
            )
        );
    }

    @Test
    void packsInclusiveRadiusMatchesAcrossWordBoundaries() {
        float[] x = new float[35];
        float[] y = new float[35];
        float[] z = new float[35];
        for (int index = 0; index < x.length; index++) {
            x[index] = index;
        }
        int[] output = new int[2];

        backend.radiusMask(0, 0, 0, 32 * 32, x, y, z, output);

        assertArrayEquals(new int[]{-1, 1}, output);
    }

    @Test
    void rejectsUndersizedRadiusMaskOutput() {
        assertThrows(
            IllegalArgumentException.class,
            () -> backend.radiusMask(
                0, 0, 0, 1,
                new float[33], new float[33], new float[33], new int[1]
            )
        );
    }
}
