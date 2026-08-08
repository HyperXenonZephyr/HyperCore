package dev.hypercore.compute;

import java.util.Objects;

/**
 * Generates 3D value-noise density fields over a regular voxel grid.
 *
 * <p>Each voxel at grid coordinate {@code (x, y, z)} produces a single float in
 * {@code [0, 1)} computed from a deterministic integer hash of the surrounding
 * lattice points.  The implementation is intentionally simple so that CPU and
 * GPU backends can produce bit-identical output.
 *
 * <p>The output layout is row-major: {@code output[x + y * sizeX + z * sizeX * sizeY]}.
 */
public interface NoiseComputeBackend {
    String id();

    ComputeDeviceType deviceType();

    /**
     * Generates a 3D density field.
     *
     * @param originX   world-space X coordinate of voxel (0, 0, 0)
     * @param originY   world-space Y coordinate of voxel (0, 0, 0)
     * @param originZ   world-space Z coordinate of voxel (0, 0, 0)
     * @param sizeX     grid size along X
     * @param sizeY     grid size along Y
     * @param sizeZ     grid size along Z
     * @param frequency controls the distance between lattice points (higher = finer noise)
     * @param output    pre-allocated array of length {@code sizeX * sizeY * sizeZ}
     */
    void generateDensity(
        float originX, float originY, float originZ,
        int sizeX, int sizeY, int sizeZ,
        float frequency,
        float[] output
    );

    /**
     * Validates common arguments for {@link #generateDensity}.
     *
     * @return total voxel count
     */
    static int validate(
        int sizeX, int sizeY, int sizeZ, float frequency, float[] output
    ) {
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
            throw new IllegalArgumentException("Grid dimensions must be positive");
        }
        long total = (long) sizeX * sizeY * sizeZ;
        if (total > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Grid volume exceeds Java array capacity");
        }
        Objects.requireNonNull(output, "output");
        if (output.length < (int) total) {
            throw new IllegalArgumentException("Output array is too small for the grid volume");
        }
        if (!Float.isFinite(frequency) || frequency <= 0.0f) {
            throw new IllegalArgumentException("frequency must be positive and finite");
        }
        return (int) total;
    }
}
