package dev.hypercore.compute;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScalarNBodySimulationBackendTest {
    private final ScalarNBodySimulationBackend backend = new ScalarNBodySimulationBackend();

    @Test
    @DisplayName("two equal bodies on x-axis swap positions in one step")
    void twoBodiesSwapPositions() {
        float[] positions = {
            0.0f, 0.0f, 0.0f,
            1.0f, 0.0f, 0.0f
        };
        float[] velocities = new float[6];
        float[] masses = {1.0f, 1.0f};
        float g = 1.0f;
        float dt = 1.0f;
        float softening = 0.0f;

        backend.simulate(positions, velocities, masses, 2, g, dt, softening);

        // Body 0: dx=1, distSq=1, invDist3=1, scale=G*m1*1=1, ax=1
        // vx += 1*1 = 1, x = 0 + 1*1 = 1
        assertEquals(1.0f, positions[0], "body 0 moves to x=1");
        assertEquals(0.0f, positions[1], "body 0 y unchanged");
        assertEquals(0.0f, positions[2], "body 0 z unchanged");
        assertEquals(1.0f, velocities[0], "body 0 vx");

        // Body 1: dx=-1, distSq=1, invDist3=1, scale=1, ax=-1
        // vx += -1*1 = -1, x = 1 + (-1)*1 = 0
        assertEquals(0.0f, positions[3], "body 1 moves to x=0");
        assertEquals(0.0f, positions[5], "body 1 z unchanged");
        assertEquals(-1.0f, velocities[3], "body 1 vx");
    }

    @Test
    @DisplayName("single body experiences no force and drifts at constant velocity")
    void singleBodyNoForce() {
        float[] positions = {1.0f, 2.0f, 3.0f};
        float[] velocities = {0.5f, -1.0f, 2.0f};
        float[] masses = {10.0f};

        backend.simulate(positions, velocities, masses, 1, 1.0f, 2.0f, 0.0f);

        assertEquals(1.0f + 0.5f * 2.0f, positions[0], "x drifts");
        assertEquals(2.0f + (-1.0f) * 2.0f, positions[1], "y drifts");
        assertEquals(3.0f + 2.0f * 2.0f, positions[2], "z drifts");
        assertEquals(0.5f, velocities[0], "vx unchanged");
        assertEquals(-1.0f, velocities[1], "vy unchanged");
        assertEquals(2.0f, velocities[2], "vz unchanged");
    }

    @Test
    @DisplayName("zero gravity means no acceleration and straight-line motion")
    void zeroGravityStraightLine() {
        float[] positions = {
            0.0f, 0.0f, 0.0f,
            10.0f, 10.0f, 10.0f
        };
        float[] velocities = {
            1.0f, 2.0f, 3.0f,
            -1.0f, -2.0f, -3.0f
        };
        float[] masses = {1.0f, 1.0f};

        backend.simulate(positions, velocities, masses, 2, 0.0f, 0.5f, 0.0f);

        assertEquals(0.5f, positions[0], "b0 x");
        assertEquals(1.0f, positions[1], "b0 y");
        assertEquals(1.5f, positions[2], "b0 z");
        assertEquals(10.0f - 0.5f, positions[3], "b1 x");
        assertEquals(10.0f - 1.0f, positions[4], "b1 y");
        assertEquals(10.0f - 1.5f, positions[5], "b1 z");
        // Velocities unchanged with zero gravity
        assertEquals(1.0f, velocities[0], "b0 vx unchanged");
        assertEquals(-3.0f, velocities[5], "b1 vz unchanged");
    }

    @Test
    @DisplayName("softening prevents singularity when bodies coincide")
    void softeningPreventsSingularity() {
        float[] positions = {0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
        float[] velocities = new float[6];
        float[] masses = {1.0f, 1.0f};
        float softening = 1.0f;

        backend.simulate(positions, velocities, masses, 2, 1.0f, 1.0f, softening);

        // With zero distance and softening=1: distSq=1, invDist=1, invDist3=1, scale=1
        // But dx=dy=dz=0 so acceleration is 0 — bodies don't move despite zero separation
        for (int i = 0; i < 6; i++) {
            assertTrue(Float.isFinite(positions[i]), "position finite at " + i);
            assertTrue(Float.isFinite(velocities[i]), "velocity finite at " + i);
        }
        assertEquals(0.0f, velocities[0], "no acceleration with zero separation and softening");
    }

    @Test
    @DisplayName("synchronous update: body i sees original positions of all others")
    void synchronousUpdateUsesOriginalPositions() {
        // Three bodies on x-axis: 0, 1, 2
        // Body 0 is pulled right by bodies 1 and 2 (both to the right)
        // Body 2 is pulled left by bodies 0 and 1 (both to the left)
        // Key: body 2's acceleration must use body 0's ORIGINAL position (0),
        // not its updated position. Since the update is synchronous, this holds.
        float[] positions = {
            0.0f, 0.0f, 0.0f,
            1.0f, 0.0f, 0.0f,
            2.0f, 0.0f, 0.0f
        };
        float[] velocities = new float[9];
        float[] masses = {1.0f, 1.0f, 1.0f};
        float g = 1.0f;
        float dt = 1.0f;
        float softening = 0.0f;

        backend.simulate(positions, velocities, masses, 3, g, dt, softening);

        // Body 0: pulled by body 1 (dx=1, distSq=1, invDist3=1, ax=1) and body 2 (dx=2, distSq=4, invDist=0.5, invDist3=0.125, ax=0.125*2=0.25)
        // Total ax = 1 + 0.25 = 1.25, vx = 1.25, x = 0 + 1.25 = 1.25
        float expectedAx0 = 1.0f + 0.25f;
        assertEquals(expectedAx0 * dt, velocities[0], "body 0 vx");
        assertEquals(0.0f + expectedAx0 * dt * dt, positions[0], "body 0 x");

        // Body 2: pulled by body 0 (dx=-2, distSq=4, invDist3=0.125, ax=0.125*(-2)=-0.25) and body 1 (dx=-1, distSq=1, invDist3=1, ax=-1)
        // Total ax = -0.25 - 1 = -1.25, vx = -1.25, x = 2 + (-1.25) = 0.75
        float expectedAx2 = -0.25f - 1.0f;
        assertEquals(expectedAx2 * dt, velocities[6], "body 2 vx");
        assertEquals(2.0f + expectedAx2 * dt * dt, positions[6], "body 2 x");
    }

    @Test
    @DisplayName("manual calculation matches backend output for 3-body system")
    void manualCalculationMatchesBackend() {
        int count = 3;
        float g = 0.5f;
        float dt = 0.1f;
        float softening = 0.01f;
        float[] positions = {
            0.0f, 0.0f, 0.0f,
            2.0f, 0.0f, 0.0f,
            0.0f, 3.0f, 0.0f
        };
        float[] velocities = {
            0.1f, 0.0f, 0.0f,
            0.0f, 0.1f, 0.0f,
            0.0f, 0.0f, 0.1f
        };
        float[] masses = {1.0f, 2.0f, 3.0f};
        float[] expectedPositions = positions.clone();
        float[] expectedVelocities = velocities.clone();

        // Snapshot original positions for synchronous force evaluation
        float[] origin = positions.clone();
        for (int i = 0; i < count; i++) {
            int baseI = i * 3;
            float xi = origin[baseI];
            float yi = origin[baseI + 1];
            float zi = origin[baseI + 2];
            float ax = 0.0f, ay = 0.0f, az = 0.0f;
            for (int j = 0; j < count; j++) {
                if (j == i) continue;
                int baseJ = j * 3;
                float dx = origin[baseJ] - xi;
                float dy = origin[baseJ + 1] - yi;
                float dz = origin[baseJ + 2] - zi;
                float distSq = dx * dx + dy * dy + dz * dz + softening;
                float invDist = 1.0f / (float) Math.sqrt(distSq);
                float invDist3 = invDist * invDist * invDist;
                float scale = g * masses[j] * invDist3;
                ax += scale * dx;
                ay += scale * dy;
                az += scale * dz;
            }
            expectedVelocities[baseI] += ax * dt;
            expectedVelocities[baseI + 1] += ay * dt;
            expectedVelocities[baseI + 2] += az * dt;
            expectedPositions[baseI] = origin[baseI] + expectedVelocities[baseI] * dt;
            expectedPositions[baseI + 1] = origin[baseI + 1] + expectedVelocities[baseI + 1] * dt;
            expectedPositions[baseI + 2] = origin[baseI + 2] + expectedVelocities[baseI + 2] * dt;
        }

        backend.simulate(positions, velocities, masses, count, g, dt, softening);

        assertArrayEquals(expectedPositions, positions, "positions match manual calculation");
        assertArrayEquals(expectedVelocities, velocities, "velocities match manual calculation");
        assertEquals(ScalarNBodySimulationBackend.ID, backend.id());
        assertEquals(ComputeDeviceType.CPU, backend.deviceType());
    }

    @Test
    @DisplayName("unequal masses produce asymmetric accelerations")
    void unequalMassesAsymmetricAcceleration() {
        // Body 0 (mass 1) at origin, Body 1 (mass 100) at (1,0,0)
        // Body 0 feels strong pull from heavy body 1
        // Body 1 feels weak pull from light body 0
        float[] positions = {
            0.0f, 0.0f, 0.0f,
            1.0f, 0.0f, 0.0f
        };
        float[] velocities = new float[6];
        float[] masses = {1.0f, 100.0f};
        float g = 1.0f;
        float dt = 1.0f;
        float softening = 0.0f;

        backend.simulate(positions, velocities, masses, 2, g, dt, softening);

        // Body 0: scale = G * mass[1] * invDist3 = 1 * 100 * 1 = 100, ax = 100*1 = 100
        assertEquals(100.0f, velocities[0], "body 0 vx (heavy attractor)");

        // Body 1: scale = G * mass[0] * invDist3 = 1 * 1 * 1 = 1, ax = 1*(-1) = -1
        assertEquals(-1.0f, velocities[3], "body 1 vx (light attractor)");

        // Body 0 moves much more than body 1
        assertTrue(Math.abs(positions[0]) > Math.abs(positions[3] - 1.0f),
            "light body moves more than heavy body");
    }

    @Test
    @DisplayName("3D configuration produces correct 3D accelerations")
    void threeDimensionalAcceleration() {
        // Body 0 at origin, body 1 at (3, 4, 0) — classic 3-4-5 triangle
        float[] positions = {
            0.0f, 0.0f, 0.0f,
            3.0f, 4.0f, 0.0f
        };
        float[] velocities = new float[6];
        float[] masses = {1.0f, 1.0f};
        float g = 1.0f;
        float dt = 1.0f;
        float softening = 0.0f;

        backend.simulate(positions, velocities, masses, 2, g, dt, softening);

        // distSq = 9 + 16 + 0 = 25, dist = 5, invDist = 0.2, invDist3 = 0.008
        // scale = G * m * invDist3 = 1 * 1 * 0.008 = 0.008
        // Body 0: ax = 0.008 * 3 = 0.024, ay = 0.008 * 4 = 0.032, az = 0
        float invDist3 = 0.008f;
        assertEquals(invDist3 * 3.0f, velocities[0], 1e-6f, "body 0 vx");
        assertEquals(invDist3 * 4.0f, velocities[1], 1e-6f, "body 0 vy");
        assertEquals(0.0f, velocities[2], "body 0 vz");
    }

    @Test
    @DisplayName("id and deviceType are correct")
    void idAndDeviceType() {
        assertEquals(ScalarNBodySimulationBackend.ID, backend.id());
        assertEquals(ComputeDeviceType.CPU, backend.deviceType());
        assertFalse(backend.id().isEmpty());
    }

    // ---- Validation tests ----

    @Test
    @DisplayName("validation rejects non-positive count")
    void rejectsNonPositiveCount() {
        assertThrows(IllegalArgumentException.class, () ->
            backend.simulate(new float[3], new float[3], new float[1], 0, 1.0f, 0.1f, 0.0f));
    }

    @Test
    @DisplayName("validation rejects undersized arrays")
    void rejectsUndersizedArrays() {
        assertThrows(IllegalArgumentException.class, () ->
            backend.simulate(new float[2], new float[3], new float[1], 1, 1.0f, 0.1f, 0.0f));
        assertThrows(IllegalArgumentException.class, () ->
            backend.simulate(new float[3], new float[1], new float[1], 1, 1.0f, 0.1f, 0.0f));
        assertThrows(IllegalArgumentException.class, () ->
            backend.simulate(new float[3], new float[3], new float[0], 1, 1.0f, 0.1f, 0.0f));
    }

    @Test
    @DisplayName("validation rejects null arrays")
    void rejectsNullArrays() {
        assertThrows(NullPointerException.class, () ->
            backend.simulate(null, new float[3], new float[1], 1, 1.0f, 0.1f, 0.0f));
        assertThrows(NullPointerException.class, () ->
            backend.simulate(new float[3], null, new float[1], 1, 1.0f, 0.1f, 0.0f));
        assertThrows(NullPointerException.class, () ->
            backend.simulate(new float[3], new float[3], null, 1, 1.0f, 0.1f, 0.0f));
    }

    @Test
    @DisplayName("validation rejects negative or non-finite gravityConstant")
    void rejectsInvalidGravity() {
        assertThrows(IllegalArgumentException.class, () ->
            backend.simulate(new float[3], new float[3], new float[1], 1, -1.0f, 0.1f, 0.0f));
        assertThrows(IllegalArgumentException.class, () ->
            backend.simulate(new float[3], new float[3], new float[1], 1, Float.NaN, 0.1f, 0.0f));
        assertThrows(IllegalArgumentException.class, () ->
            backend.simulate(new float[3], new float[3], new float[1], 1, Float.POSITIVE_INFINITY, 0.1f, 0.0f));
    }

    @Test
    @DisplayName("validation rejects non-positive or non-finite dt")
    void rejectsInvalidDt() {
        assertThrows(IllegalArgumentException.class, () ->
            backend.simulate(new float[3], new float[3], new float[1], 1, 1.0f, 0.0f, 0.0f));
        assertThrows(IllegalArgumentException.class, () ->
            backend.simulate(new float[3], new float[3], new float[1], 1, 1.0f, -0.1f, 0.0f));
        assertThrows(IllegalArgumentException.class, () ->
            backend.simulate(new float[3], new float[3], new float[1], 1, 1.0f, Float.NaN, 0.0f));
    }

    @Test
    @DisplayName("validation rejects negative or non-finite softening")
    void rejectsInvalidSoftening() {
        assertThrows(IllegalArgumentException.class, () ->
            backend.simulate(new float[3], new float[3], new float[1], 1, 1.0f, 0.1f, -0.1f));
        assertThrows(IllegalArgumentException.class, () ->
            backend.simulate(new float[3], new float[3], new float[1], 1, 1.0f, 0.1f, Float.NaN));
    }

    @Test
    @DisplayName("validation allows zero softening (no singularity guard)")
    void allowsZeroSoftening() {
        // Zero softening is valid as long as bodies don't coincide
        float[] positions = {0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f};
        float[] velocities = new float[6];
        float[] masses = {1.0f, 1.0f};

        backend.simulate(positions, velocities, masses, 2, 1.0f, 0.1f, 0.0f);

        assertTrue(Float.isFinite(positions[0]), "finite with zero softening");
    }
}
