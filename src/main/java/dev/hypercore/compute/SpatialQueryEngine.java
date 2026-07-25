package dev.hypercore.compute;

import java.util.Arrays;
import java.util.Objects;

/** Executes read-only radius queries over immutable structure-of-arrays snapshots. */
public final class SpatialQueryEngine {
    private final SpatialComputeBackend backend;

    public SpatialQueryEngine(SpatialComputeBackend backend) {
        this.backend = Objects.requireNonNull(backend, "backend");
    }

    public QueryResult withinRadius(
        float originX,
        float originY,
        float originZ,
        float radius,
        PositionBatch positions
    ) {
        requireFinite(originX, "originX");
        requireFinite(originY, "originY");
        requireFinite(originZ, "originZ");
        requireFinite(radius, "radius");
        if (radius < 0.0f) {
            throw new IllegalArgumentException("radius cannot be negative");
        }
        Objects.requireNonNull(positions, "positions");

        int size = positions.size();
        float[] squaredDistances = new float[size];
        backend.squaredDistances(
            originX,
            originY,
            originZ,
            positions.positionsX,
            positions.positionsY,
            positions.positionsZ,
            squaredDistances
        );

        float squaredRadius = radius * radius;
        int[] matches = new int[size];
        int matchCount = 0;
        for (int index = 0; index < size; index++) {
            if (squaredDistances[index] <= squaredRadius) {
                matches[matchCount++] = index;
            }
        }
        if (backend instanceof AdaptiveSpatialComputeBackend adaptive) {
            adaptive.recordSpatialQuery(size, matchCount);
        }
        return new QueryResult(size, Arrays.copyOf(matches, matchCount));
    }

    private static void requireFinite(float value, String name) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    public static final class PositionBatch {
        private final float[] positionsX;
        private final float[] positionsY;
        private final float[] positionsZ;

        public PositionBatch(float[] positionsX, float[] positionsY, float[] positionsZ) {
            Objects.requireNonNull(positionsX, "positionsX");
            Objects.requireNonNull(positionsY, "positionsY");
            Objects.requireNonNull(positionsZ, "positionsZ");
            if (positionsY.length != positionsX.length || positionsZ.length != positionsX.length) {
                throw new IllegalArgumentException("Position arrays must have equal lengths");
            }
            this.positionsX = positionsX.clone();
            this.positionsY = positionsY.clone();
            this.positionsZ = positionsZ.clone();
        }

        public int size() {
            return positionsX.length;
        }
    }

    public static final class QueryResult {
        private final int candidateCount;
        private final int[] matchingIndices;

        private QueryResult(int candidateCount, int[] matchingIndices) {
            this.candidateCount = candidateCount;
            this.matchingIndices = matchingIndices;
        }

        public int candidateCount() {
            return candidateCount;
        }

        public int matchCount() {
            return matchingIndices.length;
        }

        public int[] matchingIndices() {
            return matchingIndices.clone();
        }
    }
}
