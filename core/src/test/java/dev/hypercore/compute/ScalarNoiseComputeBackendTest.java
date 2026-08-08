package dev.hypercore.compute;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScalarNoiseComputeBackendTest {
    private final ScalarNoiseComputeBackend backend = new ScalarNoiseComputeBackend();

    @Test
    @DisplayName("4x4x4 volume produces values in [0, 1)")
    void generatesValuesInUnitRange() {
        int sizeX = 4;
        int sizeY = 4;
        int sizeZ = 4;
        float[] output = new float[sizeX * sizeY * sizeZ];

        backend.generateDensity(1.0f, 2.0f, 3.0f, sizeX, sizeY, sizeZ, 0.1f, output);

        for (float value : output) {
            assertTrue(value >= 0.0f && value < 1.0f, "value " + value + " out of [0, 1)");
        }
        assertEquals(ScalarNoiseComputeBackend.ID, backend.id());
        assertEquals(ComputeDeviceType.CPU, backend.deviceType());
    }

    @Test
    @DisplayName("same input produces identical output")
    void isDeterministic() {
        int sizeX = 4;
        int sizeY = 4;
        int sizeZ = 4;
        float[] first = new float[sizeX * sizeY * sizeZ];
        float[] second = new float[sizeX * sizeY * sizeZ];

        backend.generateDensity(-1.5f, 0.25f, 7.0f, sizeX, sizeY, sizeZ, 0.05f, first);
        backend.generateDensity(-1.5f, 0.25f, 7.0f, sizeX, sizeY, sizeZ, 0.05f, second);

        assertArrayEquals(first, second);
    }

    @Test
    @DisplayName("different origins produce different output")
    void differentOriginsProduceDifferentOutput() {
        int sizeX = 4;
        int sizeY = 4;
        int sizeZ = 4;
        float[] baseline = new float[sizeX * sizeY * sizeZ];
        float[] shifted = new float[sizeX * sizeY * sizeZ];

        backend.generateDensity(0.0f, 0.0f, 0.0f, sizeX, sizeY, sizeZ, 0.1f, baseline);
        backend.generateDensity(1.0f, 0.0f, 0.0f, sizeX, sizeY, sizeZ, 0.1f, shifted);

        assertNotEquals(toBoxed(baseline), toBoxed(shifted));
    }

    @Test
    @DisplayName("different frequencies produce different output")
    void differentFrequenciesProduceDifferentOutput() {
        int sizeX = 4;
        int sizeY = 4;
        int sizeZ = 4;
        float[] lowFrequency = new float[sizeX * sizeY * sizeZ];
        float[] highFrequency = new float[sizeX * sizeY * sizeZ];

        backend.generateDensity(0.0f, 0.0f, 0.0f, sizeX, sizeY, sizeZ, 0.05f, lowFrequency);
        backend.generateDensity(0.0f, 0.0f, 0.0f, sizeX, sizeY, sizeZ, 0.2f, highFrequency);

        assertNotEquals(toBoxed(lowFrequency), toBoxed(highFrequency));
    }

    @Test
    @DisplayName("single voxel (1x1x1) is generated without error and stays in range")
    void handlesSingleVoxel() {
        float[] output = new float[1];

        backend.generateDensity(0.5f, 0.5f, 0.5f, 1, 1, 1, 0.25f, output);

        assertEquals(1, output.length);
        assertTrue(output[0] >= 0.0f && output[0] < 1.0f);
    }

    @Test
    @DisplayName("zero or negative dimensions are rejected")
    void rejectsNonPositiveDimensions() {
        assertThrows(IllegalArgumentException.class, () ->
            backend.generateDensity(0.0f, 0.0f, 0.0f, 0, 4, 4, 0.1f, new float[1]));
        assertThrows(IllegalArgumentException.class, () ->
            backend.generateDensity(0.0f, 0.0f, 0.0f, 4, -1, 4, 0.1f, new float[1]));
        assertThrows(IllegalArgumentException.class, () ->
            backend.generateDensity(0.0f, 0.0f, 0.0f, 4, 4, 0, 0.1f, new float[1]));
    }

    @Test
    @DisplayName("null output is rejected")
    void rejectsNullOutput() {
        assertThrows(NullPointerException.class, () ->
            backend.generateDensity(0.0f, 0.0f, 0.0f, 1, 1, 1, 0.1f, null));
    }

    @Test
    @DisplayName("output array smaller than the grid volume is rejected")
    void rejectsUndersizedOutput() {
        assertThrows(IllegalArgumentException.class, () ->
            backend.generateDensity(0.0f, 0.0f, 0.0f, 2, 2, 2, 0.1f, new float[7]));
    }

    @Test
    @DisplayName("non-positive frequency is rejected")
    void rejectsNonPositiveFrequency() {
        assertThrows(IllegalArgumentException.class, () ->
            backend.generateDensity(0.0f, 0.0f, 0.0f, 2, 2, 2, 0.0f, new float[8]));
        assertThrows(IllegalArgumentException.class, () ->
            backend.generateDensity(0.0f, 0.0f, 0.0f, 2, 2, 2, -0.5f, new float[8]));
    }

    @Test
    @DisplayName("NaN frequency is rejected")
    void rejectsNaNFrequency() {
        assertThrows(IllegalArgumentException.class, () ->
            backend.generateDensity(0.0f, 0.0f, 0.0f, 2, 2, 2, Float.NaN, new float[8]));
    }

    @Test
    @DisplayName("output follows the documented row-major layout output[x + y*sizeX + z*sizeX*sizeY]")
    void outputMatchesDocumentedLayout() {
        int sizeX = 2;
        int sizeY = 2;
        int sizeZ = 2;
        float originX = 1.0f;
        float originY = 2.0f;
        float originZ = 3.0f;
        float frequency = 0.5f;
        float[] output = new float[sizeX * sizeY * sizeZ];

        backend.generateDensity(originX, originY, originZ, sizeX, sizeY, sizeZ, frequency, output);

        float[] expected = new float[sizeX * sizeY * sizeZ];
        for (int z = 0; z < sizeZ; z++) {
            float pz = (originZ + z) * frequency;
            int iz0 = floorInt(pz);
            int iz1 = iz0 + 1;
            float fadeZ = fade(pz - iz0);
            for (int y = 0; y < sizeY; y++) {
                float py = (originY + y) * frequency;
                int iy0 = floorInt(py);
                int iy1 = iy0 + 1;
                float fadeY = fade(py - iy0);
                for (int x = 0; x < sizeX; x++) {
                    float px = (originX + x) * frequency;
                    int ix0 = floorInt(px);
                    int ix1 = ix0 + 1;
                    float fadeX = fade(px - ix0);

                    float v000 = ScalarNoiseComputeBackend.hashFloat(ix0, iy0, iz0);
                    float v100 = ScalarNoiseComputeBackend.hashFloat(ix1, iy0, iz0);
                    float v010 = ScalarNoiseComputeBackend.hashFloat(ix0, iy1, iz0);
                    float v110 = ScalarNoiseComputeBackend.hashFloat(ix1, iy1, iz0);
                    float v001 = ScalarNoiseComputeBackend.hashFloat(ix0, iy0, iz1);
                    float v101 = ScalarNoiseComputeBackend.hashFloat(ix1, iy0, iz1);
                    float v011 = ScalarNoiseComputeBackend.hashFloat(ix0, iy1, iz1);
                    float v111 = ScalarNoiseComputeBackend.hashFloat(ix1, iy1, iz1);

                    float x00 = lerp(v000, v100, fadeX);
                    float x10 = lerp(v010, v110, fadeX);
                    float x01 = lerp(v001, v101, fadeX);
                    float x11 = lerp(v011, v111, fadeX);

                    float y0 = lerp(x00, x10, fadeY);
                    float y1 = lerp(x01, x11, fadeY);

                    int index = x + y * sizeX + z * sizeX * sizeY;
                    expected[index] = lerp(y0, y1, fadeZ);
                }
            }
        }

        assertArrayEquals(expected, output);
    }

    @Test
    @DisplayName("hash3d produces deterministic values for known inputs")
    void hash3dProducesKnownValues() {
        assertEquals(0, ScalarNoiseComputeBackend.hash3d(0, 0, 0));
        assertEquals(-2112556094, ScalarNoiseComputeBackend.hash3d(1, 0, 0));
        assertEquals(-995270520, ScalarNoiseComputeBackend.hash3d(0, 1, 0));
        assertEquals(41680896, ScalarNoiseComputeBackend.hash3d(0, 0, 1));
        assertEquals(-294612891, ScalarNoiseComputeBackend.hash3d(1, 1, 1));
    }

    @Test
    @DisplayName("hashFloat returns values in [0, 1)")
    void hashFloatStaysInUnitRange() {
        assertEquals(0.0f, ScalarNoiseComputeBackend.hashFloat(0, 0, 0));
        for (int z = -4; z <= 4; z++) {
            for (int y = -4; y <= 4; y++) {
                for (int x = -4; x <= 4; x++) {
                    float value = ScalarNoiseComputeBackend.hashFloat(x, y, z);
                    assertTrue(value >= 0.0f && value < 1.0f,
                        "hashFloat(" + x + "," + y + "," + z + ")=" + value + " out of [0, 1)");
                }
            }
        }
        assertNotNull(backend);
    }

    private static Float[] toBoxed(float[] values) {
        Float[] boxed = new Float[values.length];
        for (int index = 0; index < values.length; index++) {
            boxed[index] = values[index];
        }
        return boxed;
    }

    private static float fade(float t) {
        return t * t * (3.0f - 2.0f * t);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static int floorInt(float value) {
        return (int) Math.floor(value);
    }
}
