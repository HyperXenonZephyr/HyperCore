package dev.hypercore.compute;

interface ManagedSpatialComputeBackend extends SpatialComputeBackend, AutoCloseable {
    String deviceName();

    @Override
    default PositionSnapshot prepareSnapshot(
        float[] positionsX,
        float[] positionsY,
        float[] positionsZ
    ) {
        return SpatialComputeBackend.super.prepareSnapshot(positionsX, positionsY, positionsZ);
    }

    default String transferMode() {
        return "managed";
    }

    @Override
    void close();
}
