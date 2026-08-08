package dev.hypercore.compute;

/**
 * Pure-Java scalar reference for 3D value-noise density generation.
 *
 * <p>The hash function and interpolation match the GLSL compute shader
 * bit-for-bit so that GPU output can be compared against this oracle.
 */
public final class ScalarNoiseComputeBackend implements NoiseComputeBackend {
    public static final String ID = "cpu-scalar-noise";

    private static final int HASH_X = 374761393;
    private static final int HASH_Y = 668265263;
    private static final int HASH_Z = 2147483647;
    private static final int HASH_FINAL = 1274126177;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public ComputeDeviceType deviceType() {
        return ComputeDeviceType.CPU;
    }

    @Override
    public void generateDensity(
        float originX, float originY, float originZ,
        int sizeX, int sizeY, int sizeZ,
        float frequency,
        float[] output
    ) {
        int total = NoiseComputeBackend.validate(sizeX, sizeY, sizeZ, frequency, output);

        int index = 0;
        for (int z = 0; z < sizeZ; z++) {
            float pz = (originZ + z) * frequency;
            int iz0 = floorInt(pz);
            int iz1 = iz0 + 1;
            float fz = pz - iz0;
            float fadeZ = fade(fz);

            for (int y = 0; y < sizeY; y++) {
                float py = (originY + y) * frequency;
                int iy0 = floorInt(py);
                int iy1 = iy0 + 1;
                float fy = py - iy0;
                float fadeY = fade(fy);

                for (int x = 0; x < sizeX; x++) {
                    float px = (originX + x) * frequency;
                    int ix0 = floorInt(px);
                    int ix1 = ix0 + 1;
                    float fx = px - ix0;
                    float fadeX = fade(fx);

                    float v000 = hashFloat(ix0, iy0, iz0);
                    float v100 = hashFloat(ix1, iy0, iz0);
                    float v010 = hashFloat(ix0, iy1, iz0);
                    float v110 = hashFloat(ix1, iy1, iz0);
                    float v001 = hashFloat(ix0, iy0, iz1);
                    float v101 = hashFloat(ix1, iy0, iz1);
                    float v011 = hashFloat(ix0, iy1, iz1);
                    float v111 = hashFloat(ix1, iy1, iz1);

                    float x00 = lerp(v000, v100, fadeX);
                    float x10 = lerp(v010, v110, fadeX);
                    float x01 = lerp(v001, v101, fadeX);
                    float x11 = lerp(v011, v111, fadeX);

                    float y0 = lerp(x00, x10, fadeY);
                    float y1 = lerp(x01, x11, fadeY);

                    output[index++] = lerp(y0, y1, fadeZ);
                }
            }
        }
        assert index == total;
    }

    /**
     * Deterministic 3D integer hash.  Uses the same constants and operations as
     * the GLSL {@code hash3d} function so CPU and GPU produce identical bits.
     *
     * <p>Java {@code int} arithmetic wraps identically to GLSL {@code uint} for
     * addition, multiplication, XOR, and unsigned right shift ({@code >>>}).
     */
    static int hash3d(int x, int y, int z) {
        int h = x * HASH_X + y * HASH_Y + z * HASH_Z;
        h = (h ^ (h >>> 13)) * HASH_FINAL;
        return h;
    }

    /** Converts the low 24 bits of a hash to a float in [0, 1). */
    static float hashFloat(int x, int y, int z) {
        return (hash3d(x, y, z) & 0x00FFFFFF) / (float) 0x01000000;
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
