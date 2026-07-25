package dev.hypercore.compute;

import java.util.Objects;

public final class ScalarSpatialComputeBackend implements SpatialComputeBackend {
    public static final String ID = "cpu-scalar";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public ComputeDeviceType deviceType() {
        return ComputeDeviceType.CPU;
    }

    @Override
    public void squaredDistances(
        float originX,
        float originY,
        float originZ,
        float[] positionsX,
        float[] positionsY,
        float[] positionsZ,
        float[] output
    ) {
        Objects.requireNonNull(positionsX, "positionsX");
        Objects.requireNonNull(positionsY, "positionsY");
        Objects.requireNonNull(positionsZ, "positionsZ");
        Objects.requireNonNull(output, "output");

        int size = positionsX.length;
        if (positionsY.length != size || positionsZ.length != size || output.length < size) {
            throw new IllegalArgumentException("Position arrays must have equal lengths and fit in output");
        }

        for (int index = 0; index < size; index++) {
            float deltaX = positionsX[index] - originX;
            float deltaY = positionsY[index] - originY;
            float deltaZ = positionsZ[index] - originZ;
            output[index] = deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
        }
    }

    @Override
    public void radiusMask(
        float originX,
        float originY,
        float originZ,
        float squaredRadius,
        float[] positionsX,
        float[] positionsY,
        float[] positionsZ,
        int[] outputWords
    ) {
        Objects.requireNonNull(positionsX, "positionsX");
        Objects.requireNonNull(positionsY, "positionsY");
        Objects.requireNonNull(positionsZ, "positionsZ");
        Objects.requireNonNull(outputWords, "outputWords");
        int size = positionsX.length;
        int wordCount = SpatialComputeBackend.maskWordCount(size);
        if (positionsY.length != size || positionsZ.length != size || outputWords.length < wordCount) {
            throw new IllegalArgumentException("Position arrays must have equal lengths and fit in output mask");
        }
        if (Float.isNaN(squaredRadius) || squaredRadius < 0.0f) {
            throw new IllegalArgumentException("squaredRadius must be non-negative");
        }

        for (int word = 0; word < wordCount; word++) {
            int mask = 0;
            int start = word * Integer.SIZE;
            int end = Math.min(size, start + Integer.SIZE);
            for (int index = start; index < end; index++) {
                float deltaX = positionsX[index] - originX;
                float deltaY = positionsY[index] - originY;
                float deltaZ = positionsZ[index] - originZ;
                float distance = deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
                if (distance <= squaredRadius) {
                    mask |= 1 << (index - start);
                }
            }
            outputWords[word] = mask;
        }
    }
}
