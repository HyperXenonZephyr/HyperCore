package dev.hypercore.compute;

/**
 * GPU-side light propagation backend with lifecycle and device metadata.
 * Used by {@link AdaptiveLightPropagationBackend} for resource management.
 */
interface ManagedLightPropagationBackend extends LightPropagationBackend, AutoCloseable {
    String deviceName();

    default String transferMode() {
        return "managed";
    }

    @Override
    void close();
}
