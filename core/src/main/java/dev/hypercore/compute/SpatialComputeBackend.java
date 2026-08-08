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

    /**
     * Computes radius masks for multiple origins against the same candidate set
     * in a single batch. The output layout is contiguous: query {@code q} writes
     * {@code maskWordCount(candidateCount)} words starting at index
     * {@code q * maskWordCount(candidateCount)}.
     *
     * <p>The default implementation loops over {@link #radiusMask} for each query.
     * GPU backends override this with a single 2D dispatch that processes all
     * queries simultaneously.
     *
     * @param originsX       per-query origin X coordinates (length >= queryCount)
     * @param originsY       per-query origin Y coordinates (length >= queryCount)
     * @param originsZ       per-query origin Z coordinates (length >= queryCount)
     * @param squaredRadii   per-query squared radii (length >= queryCount)
     * @param queryCount     number of queries
     * @param positionsX     candidate X coordinates
     * @param positionsY     candidate Y coordinates
     * @param positionsZ     candidate Z coordinates
     * @param outputWords    output mask buffer (length >= queryCount * maskWordCount(positionsX.length))
     */
    default void batchRadiusMask(
        float[] originsX, float[] originsY, float[] originsZ, float[] squaredRadii, int queryCount,
        float[] positionsX, float[] positionsY, float[] positionsZ,
        int[] outputWords
    ) {
        Objects.requireNonNull(positionsX, "positionsX");
        Objects.requireNonNull(positionsY, "positionsY");
        Objects.requireNonNull(positionsZ, "positionsZ");
        Objects.requireNonNull(outputWords, "outputWords");
        Objects.requireNonNull(originsX, "originsX");
        Objects.requireNonNull(originsY, "originsY");
        Objects.requireNonNull(originsZ, "originsZ");
        Objects.requireNonNull(squaredRadii, "squaredRadii");
        int size = positionsX.length;
        if (positionsY.length != size || positionsZ.length != size) {
            throw new IllegalArgumentException("Position arrays must have equal lengths");
        }
        if (queryCount < 0) {
            throw new IllegalArgumentException("queryCount cannot be negative");
        }
        if (originsX.length < queryCount || originsY.length < queryCount
            || originsZ.length < queryCount || squaredRadii.length < queryCount) {
            throw new IllegalArgumentException("Origin/radius arrays must have at least queryCount elements");
        }
        int wordCount = maskWordCount(size);
        long requiredWords = (long) wordCount * queryCount;
        if (requiredWords > outputWords.length) {
            throw new IllegalArgumentException("Output mask cannot fit every query result");
        }
        for (int q = 0; q < queryCount; q++) {
            if (Float.isNaN(squaredRadii[q]) || squaredRadii[q] < 0.0f) {
                throw new IllegalArgumentException("squaredRadii[" + q + "] must be non-negative");
            }
        }
        if (queryCount == 0 || size == 0) {
            return;
        }
        int[] singleMask = new int[wordCount];
        for (int q = 0; q < queryCount; q++) {
            radiusMask(
                originsX[q], originsY[q], originsZ[q], squaredRadii[q],
                positionsX, positionsY, positionsZ,
                singleMask
            );
            System.arraycopy(singleMask, 0, outputWords, q * wordCount, wordCount);
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
