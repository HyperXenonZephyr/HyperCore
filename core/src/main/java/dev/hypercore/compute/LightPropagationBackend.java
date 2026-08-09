package dev.hypercore.compute;

import java.util.Objects;

/**
 * Propagates light through a 3D voxel grid using iterative flood-fill.
 *
 * <p>Each transparent voxel ({@code opacity == 0}) takes the maximum of its
 * current light level and {@code neighbor - 1} for all six axis-aligned
 * neighbors. Opaque voxels ({@code opacity != 0}) are clamped to zero. One
 * {@code propagate} call performs {@code iterations} rounds of this relaxation,
 * which is sufficient to fully propagate light across a grid of diameter
 * {@code iterations}.
 *
 * <p>The algorithm is embarrassingly parallel across voxels within a single
 * iteration, making it a strong GPU offload candidate. The GPU path uses
 * double-buffering (separate read-only input and write-only output buffers)
 * so every invocation reads a consistent snapshot.
 *
 * <p>Light levels are integers in {@code [0, 15]}, matching Minecraft's
 * block-light and sky-light conventions. {@code opacity} uses 0 for
 * transparent and non-zero for opaque.
 */
public interface LightPropagationBackend {
    String id();

    ComputeDeviceType deviceType();

    /**
     * Propagates light through the grid, mutating {@code light} in place.
     *
     * @param light       per-voxel light levels (0-15), length {@code dimX * dimY * dimZ}
     * @param opacity     per-voxel opacity (0 = transparent, non-zero = opaque), same length
     * @param dimX        grid dimensions on X axis
     * @param dimY        grid dimensions on Y axis
     * @param dimZ        grid dimensions on Z axis
     * @param iterations  number of propagation rounds (at least {@code dimX + dimY + dimZ - 3} for full convergence)
     */
    void propagate(int[] light, int[] opacity, int dimX, int dimY, int dimZ, int iterations);

    /**
     * Validates common arguments for {@link #propagate}.
     *
     * @return required array length ({@code dimX * dimY * dimZ})
     */
    static int validate(int[] light, int[] opacity, int dimX, int dimY, int dimZ, int iterations) {
        if (dimX <= 0 || dimY <= 0 || dimZ <= 0) {
            throw new IllegalArgumentException("dimensions must be positive");
        }
        if (iterations <= 0) {
            throw new IllegalArgumentException("iterations must be positive");
        }
        long volume = (long) dimX * dimY * dimZ;
        if (volume > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("grid volume too large");
        }
        int required = (int) volume;
        Objects.requireNonNull(light, "light");
        Objects.requireNonNull(opacity, "opacity");
        if (light.length < required) {
            throw new IllegalArgumentException("light array too small");
        }
        if (opacity.length < required) {
            throw new IllegalArgumentException("opacity array too small");
        }
        return required;
    }
}
