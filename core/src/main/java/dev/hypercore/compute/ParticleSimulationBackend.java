package dev.hypercore.compute;

import java.util.Objects;

/**
 * Simulates particle/projectile physics over a batch of entities.
 *
 * <p>Each particle has position {@code (x, y, z)} and velocity {@code (vx, vy, vz)}
 * stored in structure-of-arrays layout. The simulation applies Euler integration:
 * {@code position += velocity * dt}, {@code velocity.y -= gravity * dt}, with
 * elastic ground collision at {@code y=0}. CPU and GPU backends produce
 * bit-identical results.
 */
public interface ParticleSimulationBackend {
    String id();

    ComputeDeviceType deviceType();

    /**
     * Simulates one physics step for a batch of particles.
     *
     * @param positions   interleaved {@code [x0,y0,z0, x1,y1,z1, ...]} length = {@code count*3}
     * @param velocities  interleaved {@code [vx0,vy0,vz0, vx1,vy1,vz1, ...]} length = {@code count*3}
     * @param count       number of particles
     * @param gravity     downward acceleration (blocks/s^2), e.g. 9.8 or 0.08
     * @param dt          time step in seconds
     * @param restitution bounciness on ground collision, 0..1
     */
    void simulate(
        float[] positions, float[] velocities,
        int count,
        float gravity, float dt, float restitution
    );

    /**
     * Validates common arguments for {@link #simulate}.
     *
     * @return required number of floats per array ({@code count * 3})
     */
    static int validate(float[] positions, float[] velocities, int count,
                        float gravity, float dt, float restitution) {
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
        if (count > Integer.MAX_VALUE / 3) {
            throw new IllegalArgumentException("count too large");
        }
        int required = count * 3;
        Objects.requireNonNull(positions, "positions");
        Objects.requireNonNull(velocities, "velocities");
        if (positions.length < required) {
            throw new IllegalArgumentException("positions array too small");
        }
        if (velocities.length < required) {
            throw new IllegalArgumentException("velocities array too small");
        }
        if (!Float.isFinite(gravity) || gravity < 0.0f) {
            throw new IllegalArgumentException("gravity must be non-negative and finite");
        }
        if (!Float.isFinite(dt) || dt <= 0.0f) {
            throw new IllegalArgumentException("dt must be positive and finite");
        }
        if (!Float.isFinite(restitution) || restitution < 0.0f || restitution > 1.0f) {
            throw new IllegalArgumentException("restitution must be in [0,1]");
        }
        return required;
    }
}
