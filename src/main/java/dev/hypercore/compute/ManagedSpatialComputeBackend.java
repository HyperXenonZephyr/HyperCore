package dev.hypercore.compute;

interface ManagedSpatialComputeBackend extends SpatialComputeBackend, AutoCloseable {
    String deviceName();

    default String transferMode() {
        return "managed";
    }

    @Override
    void close();
}
