package dev.hypercore.compute;

import java.util.Objects;

public interface SpatialComputeBackend {
    String id();

    ComputeDeviceType deviceType();

    /**
     * Creates an immutable position snapshot that can be queried repeatedly.
     * Backends may retain an uploaded representation for the snapshot lifetime.
     */
    default PositionSnapshot prepareSnapshot(
        float[] positionsX,
        float[] positionsY,
        float[] positionsZ
    ) {
        Objects.requireNonNull(positionsX, "positionsX");
        Objects.requireNonNull(positionsY, "positionsY");
        Objects.requireNonNull(positionsZ, "positionsZ");
        if (positionsY.length != positionsX.length || positionsZ.length != positionsX.length) {
            throw new IllegalArgumentException("Position arrays must have equal lengths");
        }
        float[] snapshotX = positionsX.clone();
        float[] snapshotY = positionsY.clone();
        float[] snapshotZ = positionsZ.clone();
        return new PositionSnapshot() {
            @Override
            public int size() {
                return snapshotX.length;
            }

            @Override
            public void radiusMask(
                float originX,
                float originY,
                float originZ,
                float squaredRadius,
                int[] outputWords
            ) {
                SpatialComputeBackend.this.radiusMask(
                    originX,
                    originY,
                    originZ,
                    squaredRadius,
                    snapshotX,
                    snapshotY,
                    snapshotZ,
                    outputWords
                );
            }
        };
    }

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

    interface PositionSnapshot extends AutoCloseable {
        int size();

        void radiusMask(
            float originX,
            float originY,
            float originZ,
            float squaredRadius,
            int[] outputWords
        );

        default void radiusMasks(RadiusMaskQuery[] queries, int[] outputWords) {
            Objects.requireNonNull(queries, "queries");
            Objects.requireNonNull(outputWords, "outputWords");
            int wordCount = maskWordCount(size());
            long requiredWords = (long) wordCount * queries.length;
            if (requiredWords > outputWords.length) {
                throw new IllegalArgumentException("Output mask cannot fit every radius query");
            }
            int[] singleMask = new int[wordCount];
            for (int queryIndex = 0; queryIndex < queries.length; queryIndex++) {
                RadiusMaskQuery query = Objects.requireNonNull(queries[queryIndex], "queries[" + queryIndex + "]");
                radiusMask(
                    query.originX(),
                    query.originY(),
                    query.originZ(),
                    query.squaredRadius(),
                    singleMask
                );
                System.arraycopy(singleMask, 0, outputWords, queryIndex * wordCount, wordCount);
            }
        }

        @Override
        default void close() {
        }
    }

    record RadiusMaskQuery(float originX, float originY, float originZ, float squaredRadius) {
        public RadiusMaskQuery {
            if (Float.isNaN(squaredRadius) || squaredRadius < 0.0f) {
                throw new IllegalArgumentException("squaredRadius must be non-negative");
            }
        }
    }

}
