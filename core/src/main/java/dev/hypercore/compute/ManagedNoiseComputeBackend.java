package dev.hypercore.compute;

/**
 * GPU-side noise backend with lifecycle and device metadata.
 * Used by {@link AdaptiveNoiseComputeBackend} for resource management.
 */
interface ManagedNoiseComputeBackend extends NoiseComputeBackend, AutoCloseable {
    String deviceName();

    default String transferMode() {
        return "managed";
    }

    @Override
    void close();
}
