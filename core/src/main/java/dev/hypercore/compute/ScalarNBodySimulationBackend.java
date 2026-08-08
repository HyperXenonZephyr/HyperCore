package dev.hypercore.compute;

/**
 * Pure-Java scalar reference for gravitational N-body integration.
 *
 * <p>Each body accumulates acceleration from every other body in index order
 * using a snapshot of the original positions, then applies semi-implicit Euler.
 * The arithmetic order mirrors the GLSL compute shader so GPU output stays
 * within tolerance of this oracle; exact bit-equality is not guaranteed
 * because GPU {@code inversesqrt} may differ from {@code Math.sqrt} in the
 * last ulp.
 *
 * <p>The original-position snapshot makes the update synchronous: body {@code i}
 * never observes a position that another body already moved this step, matching
 * the GPU's read-only-input / separate-output design.
 */
public final class ScalarNBodySimulationBackend implements NBodySimulationBackend {
    public static final String ID = "cpu-scalar-nbody";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public ComputeDeviceType deviceType() {
        return ComputeDeviceType.CPU;
    }

    @Override
    public void simulate(
        float[] positions, float[] velocities, float[] masses,
        int count,
        float gravityConstant, float dt, float softening
    ) {
        int required = NBodySimulationBackend.validate(
            positions, velocities, masses, count, gravityConstant, dt, softening
        );

        // Snapshot the original positions so the force evaluation is synchronous
        // (every body sees the same starting state) and matches the GPU shader,
        // which reads positions from a read-only input buffer.
        float[] origin = new float[required];
        System.arraycopy(positions, 0, origin, 0, required);

        for (int i = 0; i < count; i++) {
            int baseI = i * 3;
            float xi = origin[baseI + 0];
            float yi = origin[baseI + 1];
            float zi = origin[baseI + 2];
            float ax = 0.0f;
            float ay = 0.0f;
            float az = 0.0f;
            for (int j = 0; j < count; j++) {
                if (j == i) {
                    continue;
                }
                int baseJ = j * 3;
                float dx = origin[baseJ + 0] - xi;
                float dy = origin[baseJ + 1] - yi;
                float dz = origin[baseJ + 2] - zi;
                float distSq = dx * dx + dy * dy + dz * dz + softening;
                float invDist = 1.0f / (float) Math.sqrt(distSq);
                float invDist3 = invDist * invDist * invDist;
                float scale = gravityConstant * masses[j] * invDist3;
                ax += scale * dx;
                ay += scale * dy;
                az += scale * dz;
            }
            int vel = baseI;
            velocities[vel + 0] += ax * dt;
            velocities[vel + 1] += ay * dt;
            velocities[vel + 2] += az * dt;
            positions[baseI + 0] = origin[baseI + 0] + velocities[vel + 0] * dt;
            positions[baseI + 1] = origin[baseI + 1] + velocities[vel + 1] * dt;
            positions[baseI + 2] = origin[baseI + 2] + velocities[vel + 2] * dt;
        }
    }
}
