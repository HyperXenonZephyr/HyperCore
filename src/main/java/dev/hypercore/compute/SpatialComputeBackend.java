package dev.hypercore.compute;

public interface SpatialComputeBackend {
    String id();

    ComputeDeviceType deviceType();

    void squaredDistances(
        float originX,
        float originY,
        float originZ,
        float[] positionsX,
        float[] positionsY,
        float[] positionsZ,
        float[] output
    );
}
