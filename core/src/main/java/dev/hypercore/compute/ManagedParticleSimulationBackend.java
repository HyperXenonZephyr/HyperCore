package dev.hypercore.compute;

/**
 * GPU-side particle simulation backend with lifecycle and device metadata.
 * Used by {@link AdaptiveParticleSimulationBackend} for resource management.
 */
interface ManagedParticleSimulationBackend extends ParticleSimulationBackend, AutoCloseable {
    String deviceName();

    default String transferMode() {
        return "managed";
    }

    @Override
    void close();
}
