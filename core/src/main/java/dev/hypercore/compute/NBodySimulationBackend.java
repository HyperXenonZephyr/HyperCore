package dev.hypercore.compute;

import java.util.Objects;

/**
 * Integrates one step of gravitational N-body dynamics. Every body exerts
 * gravity on every other body, so the per-step cost is O(n^2) — far denser
 * than the independent-particle {@link ParticleSimulationBackend} and a
 * better fit for GPU offload as the body count grows.
 *
 * <p>Arrays use structure-of-arrays layout: positions and velocities are
 * interleaved {@code [x0,y0,z0, x1,y1,z1, ...]} (length {@code count*3});
 * masses are {@code [m0, m1, ...]} (length {@code count}). The integration is
 * semi-implicit Euler with a <em>synchronous</em> force evaluation: all
 * accelerations are computed from the original positions, then
 * {@code velocity += acceleration * dt} and {@code position += velocity * dt}
 * are applied. A softening term is added to the squared distance to avoid
 * singularities when two bodies coincide.
 *
 * <p>The synchronous update matters for the GPU path: the compute shader reads
 * positions from a read-only input buffer and writes the updated state to a
 * separate output buffer so every invocation sees the same original positions.
 */
public interface NBodySimulationBackend {
    String id();

    ComputeDeviceType deviceType();

    /**
     * Integrates one N-body gravity step, mutating {@code positions} and
     * {@code velocities} in place.
     *
     * @param positions       interleaved positions, length {@code count*3}
     * @param velocities      interleaved velocities, length {@code count*3}
     * @param masses          per-body masses, length {@code count}
     * @param count           number of bodies
     * @param gravityConstant gravitational constant G
     * @param dt              time step in seconds
     * @param softening       value added to squared distance before the inverse root (epsilon^2)
     */
    void simulate(
        float[] positions, float[] velocities, float[] masses,
        int count,
        float gravityConstant, float dt, float softening
    );

    /**
     * Validates common arguments for {@link #simulate}.
     *
     * @return required number of floats per position/velocity array ({@code count * 3})
     */
    static int validate(
        float[] positions, float[] velocities, float[] masses, int count,
        float gravityConstant, float dt, float softening
    ) {
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
        if (count > Integer.MAX_VALUE / 3) {
            throw new IllegalArgumentException("count too large");
        }
        int required = count * 3;
        Objects.requireNonNull(positions, "positions");
        Objects.requireNonNull(velocities, "velocities");
        Objects.requireNonNull(masses, "masses");
        if (positions.length < required) {
            throw new IllegalArgumentException("positions array too small");
        }
        if (velocities.length < required) {
            throw new IllegalArgumentException("velocities array too small");
        }
        if (masses.length < count) {
            throw new IllegalArgumentException("masses array too small");
        }
        if (!Float.isFinite(gravityConstant) || gravityConstant < 0.0f) {
            throw new IllegalArgumentException("gravityConstant must be non-negative and finite");
        }
        if (!Float.isFinite(dt) || dt <= 0.0f) {
            throw new IllegalArgumentException("dt must be positive and finite");
        }
        if (!Float.isFinite(softening) || softening < 0.0f) {
            throw new IllegalArgumentException("softening must be non-negative and finite");
        }
        return required;
    }
}
