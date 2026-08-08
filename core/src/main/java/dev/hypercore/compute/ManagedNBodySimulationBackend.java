package dev.hypercore.compute;

/**
 * GPU-side N-body simulation backend with lifecycle and device metadata.
 * Used by {@link AdaptiveNBodySimulationBackend} for resource management.
 */
interface ManagedNBodySimulationBackend extends NBodySimulationBackend, AutoCloseable {
    String deviceName();

    default String transferMode() {
        return "managed";
    }

    @Override
    void close();
}
