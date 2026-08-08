package dev.hypercore.compute;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScalarParticleSimulationBackendTest {
    private final ScalarParticleSimulationBackend backend = new ScalarParticleSimulationBackend();

    @Test
    @DisplayName("gravity reduces velocity.y over time")
    void gravityReducesVerticalVelocity() {
        float[] positions = {0.0f, 10.0f, 0.0f};
        float[] velocities = {0.0f, 0.0f, 0.0f};
        float gravity = 9.8f;
        float dt = 0.5f;

        backend.simulate(positions, velocities, 1, gravity, dt, 0.5f);

        assertEquals(-gravity * dt, velocities[1], 0.0f, "velocity.y should be reduced by gravity * dt");
        assertEquals(0.0f, velocities[0], "velocity.x unchanged");
        assertEquals(0.0f, velocities[2], "velocity.z unchanged");
    }

    @Test
    @DisplayName("ground collision clamps y and bounces velocity")
    void groundCollisionClampsAndBounces() {
        float[] positions = {0.0f, -2.0f, 0.0f};
        float[] velocities = {1.0f, -5.0f, 2.0f};
        float gravity = 0.0f;
        float dt = 1.0f;
        float restitution = 0.5f;

        backend.simulate(positions, velocities, 1, gravity, dt, restitution);

        // position updated before velocity gravity, so y = -2 + (-5)*1 = -7, then clamped to 0
        assertEquals(0.0f, positions[1], "position.y clamped to ground");
        // velocity.y = -5 - 0 = -5, then bounced: -(-5) * 0.5 = 2.5
        assertEquals(2.5f, velocities[1], 0.0f, "velocity.y bounced with restitution");
        // horizontal motion preserved
        assertEquals(1.0f, positions[0], "position.x integrated");
        assertEquals(2.0f, positions[2], "position.z integrated");
    }

    @Test
    @DisplayName("restitution=0 means no bounce (particle stays at y=0)")
    void zeroRestitutionStopsAtGround() {
        float[] positions = {0.0f, -1.0f, 0.0f};
        float[] velocities = {0.0f, -10.0f, 0.0f};

        backend.simulate(positions, velocities, 1, 0.0f, 1.0f, 0.0f);

        assertEquals(0.0f, positions[1], "particle rests on ground");
        assertEquals(0.0f, velocities[1], "no bounce velocity");
    }

    @Test
    @DisplayName("restitution=1 means perfect bounce")
    void perfectBouncePreservesSpeed() {
        float[] positions = {0.0f, -1.0f, 0.0f};
        float[] velocities = {0.0f, -10.0f, 0.0f};

        backend.simulate(positions, velocities, 1, 0.0f, 1.0f, 1.0f);

        assertEquals(0.0f, positions[1], "particle clamped to ground");
        assertEquals(10.0f, velocities[1], "velocity perfectly reversed");
    }

    @Test
    @DisplayName("multiple particles simulated correctly and independently")
    void multipleParticlesSimulatedIndependently() {
        float[] positions = {
            0.0f, 5.0f, 0.0f,
            1.0f, -1.0f, 1.0f,
            2.0f, 10.0f, 2.0f
        };
        float[] velocities = {
            1.0f, 0.0f, 0.0f,
            0.0f, -3.0f, 0.0f,
            0.0f, 2.0f, 1.0f
        };

        backend.simulate(positions, velocities, 3, 9.8f, 0.1f, 0.5f);

        // Particle 0: no collision, simple fall
        assertEquals(0.1f, positions[0], "p0 x");
        assertEquals(5.0f, positions[1], "p0 y");
        assertEquals(0.0f, positions[2], "p0 z");
        assertEquals(-0.98f, velocities[1], "p0 vy");

        // Particle 1: collision (y = -1 + -3*0.1 = -1.3 -> clamp to 0)
        assertEquals(0.0f, positions[4], "p1 y clamped");
        // vy = -3 - 0.98 = -3.98, bounced: 3.98 * 0.5 = 1.99
        assertEquals(1.99f, velocities[4], "p1 vy bounced");

        // Particle 2: no collision — compute expected with same float ops
        float p2x = 2.0f + 0.0f * 0.1f;
        float p2y = 10.0f + 2.0f * 0.1f;
        float p2z = 2.0f + 1.0f * 0.1f;
        assertEquals(p2x, positions[6], "p2 x");
        assertEquals(p2y, positions[7], "p2 y");
        assertEquals(p2z, positions[8], "p2 z");
    }

    @Test
    @DisplayName("bit-exactness: manual calculation matches backend output")
    void manualCalculationMatchesBackend() {
        int count = 4;
        float gravity = 0.08f;
        float dt = 0.25f;
        float restitution = 0.7f;
        float[] positions = {
            1.5f, 0.5f, -2.0f,
            -3.0f, 2.25f, 4.0f,
            0.0f, -1.0f, 0.0f,
            7.0f, 10.0f, -5.0f
        };
        float[] velocities = {
            0.1f, -0.2f, 0.3f,
            -0.5f, 1.0f, -0.25f,
            0.0f, -8.0f, 0.0f,
            2.0f, 0.0f, -1.0f
        };
        float[] expectedPositions = positions.clone();
        float[] expectedVelocities = velocities.clone();

        for (int i = 0; i < count; i++) {
            int base = i * 3;
            expectedPositions[base + 0] = positions[base + 0] + velocities[base + 0] * dt;
            expectedPositions[base + 1] = positions[base + 1] + velocities[base + 1] * dt;
            expectedPositions[base + 2] = positions[base + 2] + velocities[base + 2] * dt;
            expectedVelocities[base + 1] = velocities[base + 1] - gravity * dt;
            if (expectedPositions[base + 1] < 0.0f) {
                expectedPositions[base + 1] = 0.0f;
                expectedVelocities[base + 1] = -expectedVelocities[base + 1] * restitution;
            }
        }

        backend.simulate(positions, velocities, count, gravity, dt, restitution);

        assertArrayEquals(expectedPositions, positions, "positions match manual calculation");
        assertArrayEquals(expectedVelocities, velocities, "velocities match manual calculation");
        assertEquals(ScalarParticleSimulationBackend.ID, backend.id());
        assertEquals(ComputeDeviceType.CPU, backend.deviceType());
    }

    @Test
    @DisplayName("validation rejects non-positive count")
    void rejectsNonPositiveCount() {
        assertThrows(IllegalArgumentException.class, () ->
            backend.simulate(new float[3], new float[3], 0, 9.8f, 0.05f, 0.5f));
    }

    @Test
    @DisplayName("validation rejects undersized arrays")
    void rejectsUndersizedArrays() {
        assertThrows(IllegalArgumentException.class, () ->
            backend.simulate(new float[2], new float[3], 1, 9.8f, 0.05f, 0.5f));
        assertThrows(IllegalArgumentException.class, () ->
            backend.simulate(new float[3], new float[1], 1, 9.8f, 0.05f, 0.5f));
    }

    @Test
    @DisplayName("validation rejects null arrays")
    void rejectsNullArrays() {
        assertThrows(NullPointerException.class, () ->
            backend.simulate(null, new float[3], 1, 9.8f, 0.05f, 0.5f));
        assertThrows(NullPointerException.class, () ->
            backend.simulate(new float[3], null, 1, 9.8f, 0.05f, 0.5f));
    }

    @Test
    @DisplayName("validation rejects non-finite or negative gravity")
    void rejectsInvalidGravity() {
        assertThrows(IllegalArgumentException.class, () ->
            backend.simulate(new float[3], new float[3], 1, -1.0f, 0.05f, 0.5f));
        assertThrows(IllegalArgumentException.class, () ->
            backend.simulate(new float[3], new float[3], 1, Float.NaN, 0.05f, 0.5f));
    }

    @Test
    @DisplayName("validation rejects non-positive or non-finite dt")
    void rejectsInvalidDt() {
        assertThrows(IllegalArgumentException.class, () ->
            backend.simulate(new float[3], new float[3], 1, 9.8f, 0.0f, 0.5f));
        assertThrows(IllegalArgumentException.class, () ->
            backend.simulate(new float[3], new float[3], 1, 9.8f, Float.POSITIVE_INFINITY, 0.5f));
    }

    @Test
    @DisplayName("validation rejects out-of-range restitution")
    void rejectsInvalidRestitution() {
        assertThrows(IllegalArgumentException.class, () ->
            backend.simulate(new float[3], new float[3], 1, 9.8f, 0.05f, -0.1f));
        assertThrows(IllegalArgumentException.class, () ->
            backend.simulate(new float[3], new float[3], 1, 9.8f, 0.05f, 1.5f));
        assertThrows(IllegalArgumentException.class, () ->
            backend.simulate(new float[3], new float[3], 1, 9.8f, 0.05f, Float.NaN));
    }

    @Test
    @DisplayName("boundary: particle resting exactly at y=0 with downward velocity bounces")
    void particleAtGroundBoundaryBounces() {
        float[] positions = {0.0f, 0.0f, 0.0f};
        float[] velocities = {0.0f, -1.0f, 0.0f};

        backend.simulate(positions, velocities, 1, 0.0f, 1.0f, 0.8f);

        // y = 0 + (-1)*1 = -1 < 0, so clamp to 0 and bounce
        assertEquals(0.0f, positions[1], "y clamped to ground");
        assertEquals(0.8f, velocities[1], "velocity bounced with restitution");
    }

    @Test
    @DisplayName("zero gravity keeps horizontal and vertical velocity constant")
    void zeroGravityPreservesVerticalVelocity() {
        float[] positions = {0.0f, 100.0f, 0.0f};
        float[] velocities = {3.0f, 4.0f, 5.0f};

        backend.simulate(positions, velocities, 1, 0.0f, 2.0f, 0.5f);

        assertEquals(6.0f, positions[0], "x integrated");
        assertEquals(108.0f, positions[1], "y integrated");
        assertEquals(10.0f, positions[2], "z integrated");
        assertEquals(4.0f, velocities[1], "vy unchanged with zero gravity");
    }
}
