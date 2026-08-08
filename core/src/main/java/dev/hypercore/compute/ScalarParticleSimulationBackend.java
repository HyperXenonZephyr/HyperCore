package dev.hypercore.compute;

/**
 * Pure-Java scalar reference for particle/projectile physics integration.
 *
 * <p>The Euler integration step and ground collision match the GLSL compute
 * shader bit-for-bit so that GPU output can be compared against this oracle.
 * Each particle is updated independently (no inter-particle forces).
 */
public final class ScalarParticleSimulationBackend implements ParticleSimulationBackend {
    public static final String ID = "cpu-scalar-particle";

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
        float[] positions, float[] velocities,
        int count,
        float gravity, float dt, float restitution
    ) {
        int required = ParticleSimulationBackend.validate(positions, velocities, count, gravity, dt, restitution);

        for (int i = 0; i < count; i++) {
            int base = i * 3;
            // position += velocity * dt
            positions[base + 0] = positions[base + 0] + velocities[base + 0] * dt;
            positions[base + 1] = positions[base + 1] + velocities[base + 1] * dt;
            positions[base + 2] = positions[base + 2] + velocities[base + 2] * dt;
            // velocity.y -= gravity * dt
            velocities[base + 1] = velocities[base + 1] - gravity * dt;
            // ground collision at y=0
            if (positions[base + 1] < 0.0f) {
                positions[base + 1] = 0.0f;
                velocities[base + 1] = -velocities[base + 1] * restitution;
            }
        }
        assert required == count * 3;
    }
}
