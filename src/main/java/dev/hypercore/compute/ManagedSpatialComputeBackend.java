package dev.hypercore.compute;

interface ManagedSpatialComputeBackend extends SpatialComputeBackend, AutoCloseable {
    String deviceName();

    @Override
    void close();
}
