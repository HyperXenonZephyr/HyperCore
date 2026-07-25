package dev.hypercore.compute;

import java.util.Objects;

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

    default void radiusMask(
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
        int wordCount = maskWordCount(size);
        if (positionsY.length != size || positionsZ.length != size || outputWords.length < wordCount) {
            throw new IllegalArgumentException("Position arrays must have equal lengths and fit in output mask");
        }
        if (Float.isNaN(squaredRadius) || squaredRadius < 0.0f) {
            throw new IllegalArgumentException("squaredRadius must be non-negative");
        }

        float[] distances = new float[size];
        squaredDistances(originX, originY, originZ, positionsX, positionsY, positionsZ, distances);
        for (int word = 0; word < wordCount; word++) {
            int mask = 0;
            int start = word * Integer.SIZE;
            int end = Math.min(size, start + Integer.SIZE);
            for (int index = start; index < end; index++) {
                if (distances[index] <= squaredRadius) {
                    mask |= 1 << (index - start);
                }
            }
            outputWords[word] = mask;
        }
    }

    static int maskWordCount(int candidateCount) {
        if (candidateCount < 0) {
            throw new IllegalArgumentException("candidateCount cannot be negative");
        }
        return Math.ceilDiv(candidateCount, Integer.SIZE);
    }
}
