package dev.hypercore.compute;

import java.util.Optional;

/**
 * Loads the optional Java Vector API backend reflectively so the main source set
 * never depends on the {@code jdk.incubator.vector} incubator module at compile time.
 *
 * <p>The vector backend class is only resolved when this method is actually called,
 * so environments without the incubator module (for example a server JVM launched
 * without {@code --add-modules jdk.incubator.vector}) simply get an empty result and
 * fall back to the scalar CPU backend instead of failing at class-load time.
 */
final class VectorBackendFactory {
    private static final String VECTOR_BACKEND_CLASS =
        "dev.hypercore.compute.VectorSpatialComputeBackend";

    private VectorBackendFactory() {
    }

    static Optional<SpatialComputeBackend> tryLoad() {
        try {
            Class<?> backendClass = Class.forName(VECTOR_BACKEND_CLASS);
            Object instance = backendClass.getDeclaredConstructor().newInstance();
            return Optional.of((SpatialComputeBackend) instance);
        } catch (LinkageError | ReflectiveOperationException error) {
            return Optional.empty();
        }
    }
}
