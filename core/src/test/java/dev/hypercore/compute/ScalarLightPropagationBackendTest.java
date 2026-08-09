package dev.hypercore.compute;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ScalarLightPropagationBackend}, the CPU scalar reference
 * implementation of the voxel light flood-fill. Light decays by one per
 * axis-aligned step (manhattan distance), opaque voxels are clamped to zero,
 * and levels are integers in [0, 15].
 */
class ScalarLightPropagationBackendTest {
    private static final int MAX_LIGHT = 15;
    private final ScalarLightPropagationBackend backend = new ScalarLightPropagationBackend();

    private static int idx(int x, int y, int z, int dimX, int dimY) {
        return x + y * dimX + z * dimX * dimY;
    }

    @Test
    @DisplayName("single source propagates with 15-d attenuation along manhattan distance")
    void singleSourcePropagation() {
        int dimX = 8, dimY = 8, dimZ = 8;
        int[] light = new int[dimX * dimY * dimZ];
        int[] opacity = new int[dimX * dimY * dimZ];
        int sx = 4, sy = 4, sz = 4;
        light[idx(sx, sy, sz, dimX, dimY)] = MAX_LIGHT;

        backend.propagate(light, opacity, dimX, dimY, dimZ, 21);

        // Source retains full brightness.
        assertEquals(MAX_LIGHT, light[idx(sx, sy, sz, dimX, dimY)], "source stays 15");

        // Immediate axis neighbors are 14.
        assertEquals(14, light[idx(sx + 1, sy, sz, dimX, dimY)], "+x neighbor 14");
        assertEquals(14, light[idx(sx - 1, sy, sz, dimX, dimY)], "-x neighbor 14");
        assertEquals(14, light[idx(sx, sy + 1, sz, dimX, dimY)], "+y neighbor 14");
        assertEquals(14, light[idx(sx, sy - 1, sz, dimX, dimY)], "-y neighbor 14");
        assertEquals(14, light[idx(sx, sy, sz + 1, dimX, dimY)], "+z neighbor 14");
        assertEquals(14, light[idx(sx, sy, sz - 1, dimX, dimY)], "-z neighbor 14");

        // Distance d (manhattan) -> 15 - d when d <= 15.
        assertEquals(15 - 2, light[idx(sx + 1, sy + 1, sz, dimX, dimY)], "distance 2");
        assertEquals(15 - 3, light[idx(sx + 1, sy + 1, sz + 1, dimX, dimY)], "distance 3");

        // Boundary attenuation: (0,0,0) is manhattan distance 12 from (4,4,4) -> 3.
        assertEquals(15 - 12, light[idx(0, 0, 0, dimX, dimY)], "corner (0,0,0) distance 12 -> 3");
        // (7,7,7) is distance 9 -> 6.
        assertEquals(15 - 9, light[idx(7, 7, 7, dimX, dimY)], "corner (7,7,7) distance 9 -> 6");
    }

    @Test
    @DisplayName("opaque block between source and target reduces light below the unblocked value")
    void opaqueBlockReducesLight() {
        int dimX = 8, dimY = 8, dimZ = 8;
        int sx = 4, sy = 4, sz = 4;
        int tx = 4, ty = 4, tz = 0; // target, distance 4 from source along -z

        // Unblocked baseline.
        int[] lightClear = new int[dimX * dimY * dimZ];
        int[] opacityClear = new int[dimX * dimY * dimZ];
        lightClear[idx(sx, sy, sz, dimX, dimY)] = MAX_LIGHT;
        backend.propagate(lightClear, opacityClear, dimX, dimY, dimZ, 21);
        int unblocked = lightClear[idx(tx, ty, tz, dimX, dimY)];

        // Blocked: place an opaque voxel on the direct path.
        int[] lightBlocked = new int[dimX * dimY * dimZ];
        int[] opacityBlocked = new int[dimX * dimY * dimZ];
        lightBlocked[idx(sx, sy, sz, dimX, dimY)] = MAX_LIGHT;
        opacityBlocked[idx(4, 4, 2, dimX, dimY)] = 1;
        backend.propagate(lightBlocked, opacityBlocked, dimX, dimY, dimZ, 21);
        int blocked = lightBlocked[idx(tx, ty, tz, dimX, dimY)];

        assertEquals(15 - 4, unblocked, "unblocked target at distance 4 -> 11");
        assertTrue(blocked < unblocked, "blocked target is dimmer than unblocked");
        assertTrue(blocked > 0, "blocked target still receives light via detour");
        assertEquals(MAX_LIGHT, lightBlocked[idx(sx, sy, sz, dimX, dimY)], "source unaffected by block");
    }

    @Test
    @DisplayName("zero iterations throws and leaves the input array unmodified")
    void zeroIterationsDoesNotPropagate() {
        int dimX = 8, dimY = 8, dimZ = 8;
        int[] light = new int[dimX * dimY * dimZ];
        int[] opacity = new int[dimX * dimY * dimZ];
        light[idx(4, 4, 4, dimX, dimY)] = MAX_LIGHT;

        // validate throws before any mutation, so the array is untouched.
        assertThrows(IllegalArgumentException.class, () ->
            backend.propagate(light, opacity, dimX, dimY, dimZ, 0));

        assertEquals(MAX_LIGHT, light[idx(4, 4, 4, dimX, dimY)], "source keeps its value");
        assertEquals(0, light[idx(0, 0, 0, dimX, dimY)], "non-source stays 0");
        assertEquals(0, light[idx(4, 4, 5, dimX, dimY)], "neighbor stays 0");
    }

    @Test
    @DisplayName("fully opaque grid is dark everywhere, including the source")
    void fullyOpaqueGridIsDark() {
        int dimX = 8, dimY = 8, dimZ = 8;
        int[] light = new int[dimX * dimY * dimZ];
        int[] opacity = new int[dimX * dimY * dimZ];
        java.util.Arrays.fill(opacity, 1);
        light[idx(4, 4, 4, dimX, dimY)] = MAX_LIGHT;

        backend.propagate(light, opacity, dimX, dimY, dimZ, 21);

        for (int i = 0; i < light.length; i++) {
            assertEquals(0, light[i], "voxel " + i + " is dark");
        }
    }

    @Test
    @DisplayName("two sources combine as the max of their individual 15-d fields")
    void multipleSourcesCombineAsMax() {
        int dimX = 8, dimY = 8, dimZ = 8;
        int[] light = new int[dimX * dimY * dimZ];
        int[] opacity = new int[dimX * dimY * dimZ];
        // Two sources 5 apart along x.
        int ax = 1, ay = 4, az = 4;
        int bx = 6, by = 4, bz = 4;
        light[idx(ax, ay, az, dimX, dimY)] = MAX_LIGHT;
        light[idx(bx, by, bz, dimX, dimY)] = MAX_LIGHT;

        backend.propagate(light, opacity, dimX, dimY, dimZ, 21);

        // Middle voxel (3,4,4): d1=2, d2=3 -> max(13,12)=13.
        int mx = 3, my = 4, mz = 4;
        int d1 = Math.abs(mx - ax) + Math.abs(my - ay) + Math.abs(mz - az);
        int d2 = Math.abs(mx - bx) + Math.abs(my - by) + Math.abs(mz - bz);
        int expected = Math.max(MAX_LIGHT - d1, MAX_LIGHT - d2);
        assertEquals(expected, light[idx(mx, my, mz, dimX, dimY)],
            "middle voxel = max(15-d1, 15-d2)");

        assertEquals(MAX_LIGHT, light[idx(ax, ay, az, dimX, dimY)], "source A unchanged");
        assertEquals(MAX_LIGHT, light[idx(bx, by, bz, dimX, dimY)], "source B unchanged");
    }

    @Test
    @DisplayName("corner source propagates along axes only, not diagonally")
    void cornerSourceAxisOnlyPropagation() {
        int dimX = 8, dimY = 8, dimZ = 8;
        int[] light = new int[dimX * dimY * dimZ];
        int[] opacity = new int[dimX * dimY * dimZ];
        light[idx(0, 0, 0, dimX, dimY)] = MAX_LIGHT;

        backend.propagate(light, opacity, dimX, dimY, dimZ, 21);

        assertEquals(MAX_LIGHT, light[idx(0, 0, 0, dimX, dimY)], "source");
        assertEquals(14, light[idx(1, 0, 0, dimX, dimY)], "distance 1");
        // (1,1,0) is manhattan distance 2 -> 13 (not euclidean ~1.41).
        assertEquals(13, light[idx(1, 1, 0, dimX, dimY)], "diagonal pair distance 2");
        // (1,1,1) is manhattan distance 3 -> 12 (not euclidean ~1.73).
        assertEquals(12, light[idx(1, 1, 1, dimX, dimY)], "full diagonal distance 3");
        // (7,7,7) is manhattan distance 21 -> 0 (clamped, 21 > 15).
        assertEquals(0, light[idx(7, 7, 7, dimX, dimY)], "far corner distance 21 clamped to 0");
    }

    @Test
    @DisplayName("16x16x16 grid converges; farthest corner at distance 45 clamps to 0")
    void largeGridConvergence() {
        int dimX = 16, dimY = 16, dimZ = 16;
        int[] light = new int[dimX * dimY * dimZ];
        int[] opacity = new int[dimX * dimY * dimZ];
        light[idx(0, 0, 0, dimX, dimY)] = MAX_LIGHT;

        // 48 >= 16+16+16-3 = 45 iterations needed for full convergence.
        backend.propagate(light, opacity, dimX, dimY, dimZ, 48);

        // Farthest corner (15,15,15): manhattan distance 45 > 15 -> 0.
        assertEquals(0, light[idx(15, 15, 15, dimX, dimY)], "far corner distance 45 -> 0");
        // Interior voxel (5,0,0): distance 5 -> 10 (confirms propagation actually ran).
        assertEquals(15 - 5, light[idx(5, 0, 0, dimX, dimY)], "interior distance 5 -> 10");
        // (15,0,0): distance 15 -> exactly 0 at the reach limit.
        assertEquals(0, light[idx(15, 0, 0, dimX, dimY)], "distance 15 -> 0");
    }

    @Test
    @DisplayName("validate rejects non-positive dims, non-positive iterations, null and undersized arrays")
    void rejectsInvalidArguments() {
        int[] light = new int[8];
        int[] opacity = new int[8];

        // Non-positive dimensions.
        assertThrows(IllegalArgumentException.class, () ->
            backend.propagate(light, opacity, 0, 2, 2, 1));
        assertThrows(IllegalArgumentException.class, () ->
            backend.propagate(light, opacity, 2, 0, 2, 1));
        assertThrows(IllegalArgumentException.class, () ->
            backend.propagate(light, opacity, 2, 2, 0, 1));

        // Non-positive iterations.
        assertThrows(IllegalArgumentException.class, () ->
            backend.propagate(light, opacity, 2, 2, 2, 0));
        assertThrows(IllegalArgumentException.class, () ->
            backend.propagate(light, opacity, 2, 2, 2, -1));

        // Null arrays.
        assertThrows(NullPointerException.class, () ->
            backend.propagate(null, opacity, 2, 2, 2, 1));
        assertThrows(NullPointerException.class, () ->
            backend.propagate(light, null, 2, 2, 2, 1));

        // Undersized arrays.
        assertThrows(IllegalArgumentException.class, () ->
            backend.propagate(new int[7], opacity, 2, 2, 2, 1));
        assertThrows(IllegalArgumentException.class, () ->
            backend.propagate(light, new int[7], 2, 2, 2, 1));
    }

    @Test
    @DisplayName("id is cpu-scalar-light and deviceType is CPU")
    void idAndDeviceType() {
        assertEquals(ScalarLightPropagationBackend.ID, backend.id());
        assertEquals("cpu-scalar-light", backend.id());
        assertEquals(ComputeDeviceType.CPU, backend.deviceType());
    }

    @Test
    @DisplayName("even and odd iteration counts converge to the same result")
    void evenOddIterationConsistency() {
        int dimX = 8, dimY = 8, dimZ = 8;
        int[] opacity = new int[dimX * dimY * dimZ];

        int[] lightEven = new int[dimX * dimY * dimZ];
        lightEven[idx(4, 4, 4, dimX, dimY)] = MAX_LIGHT;
        backend.propagate(lightEven, opacity, dimX, dimY, dimZ, 20);

        int[] lightOdd = new int[dimX * dimY * dimZ];
        lightOdd[idx(4, 4, 4, dimX, dimY)] = MAX_LIGHT;
        backend.propagate(lightOdd, opacity, dimX, dimY, dimZ, 21);

        // 20 iterations already converges an 8x8x8 grid (max distance 12),
        // so the double-buffer swap parity (even vs odd) must yield identical output.
        assertArrayEquals(lightEven, lightOdd, "20 and 21 iterations converge identically");
    }
}
