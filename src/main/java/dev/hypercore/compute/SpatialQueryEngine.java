package dev.hypercore.compute;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/** Executes read-only radius queries over immutable structure-of-arrays snapshots. */
public final class SpatialQueryEngine implements AutoCloseable {
    private final SpatialComputeBackend backend;
    private final Map<PositionBatch, SpatialComputeBackend.PositionSnapshot> snapshots =
        Collections.synchronizedMap(new WeakHashMap<>());
    private volatile boolean closed;

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
        if (closed) {
            throw new IllegalStateException("Spatial query engine is closed");
        }

        int size = positions.size();
        int[] maskWords = new int[SpatialComputeBackend.maskWordCount(size)];
        snapshotFor(positions).radiusMask(
            originX,
            originY,
            originZ,
            radius * radius,
            maskWords
        );

        QueryResult result = decodeResult(size, maskWords, 0);
        if (backend instanceof AdaptiveSpatialComputeBackend adaptive) {
            adaptive.recordSpatialQuery(size, result.matchCount());
        }
        return result;
    }

    public QueryResult[] withinRadii(PositionBatch positions, RadiusQuery... queries) {
        Objects.requireNonNull(positions, "positions");
        Objects.requireNonNull(queries, "queries");
        if (closed) {
            throw new IllegalStateException("Spatial query engine is closed");
        }
        if (queries.length == 0) {
            return new QueryResult[0];
        }
        SpatialComputeBackend.RadiusMaskQuery[] backendQueries =
            new SpatialComputeBackend.RadiusMaskQuery[queries.length];
        for (int queryIndex = 0; queryIndex < queries.length; queryIndex++) {
            RadiusQuery query = Objects.requireNonNull(queries[queryIndex], "queries[" + queryIndex + "]");
            backendQueries[queryIndex] = new SpatialComputeBackend.RadiusMaskQuery(
                query.originX(),
                query.originY(),
                query.originZ(),
                query.radius() * query.radius()
            );
        }

        int size = positions.size();
        int wordCount = SpatialComputeBackend.maskWordCount(size);
        long requiredWords = (long) wordCount * queries.length;
        if (requiredWords > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Batched query output is too large");
        }
        int[] maskWords = new int[(int) requiredWords];
        snapshotFor(positions).radiusMasks(backendQueries, maskWords);

        QueryResult[] results = new QueryResult[queries.length];
        for (int queryIndex = 0; queryIndex < queries.length; queryIndex++) {
            QueryResult result = decodeResult(size, maskWords, queryIndex * wordCount);
            results[queryIndex] = result;
            if (backend instanceof AdaptiveSpatialComputeBackend adaptive) {
                adaptive.recordSpatialQuery(size, result.matchCount());
            }
        }
        return results;
    }

    private static QueryResult decodeResult(int size, int[] maskWords, int wordOffset) {
        int wordCount = SpatialComputeBackend.maskWordCount(size);
        int[] matches = new int[size];
        int matchCount = 0;
        for (int word = 0; word < wordCount; word++) {
            int remaining = maskWords[wordOffset + word];
            while (remaining != 0) {
                int bit = Integer.numberOfTrailingZeros(remaining);
                int index = word * Integer.SIZE + bit;
                if (index < size) {
                    matches[matchCount++] = index;
                }
                remaining &= remaining - 1;
            }
        }
        return new QueryResult(size, Arrays.copyOf(matches, matchCount));
    }

    private SpatialComputeBackend.PositionSnapshot snapshotFor(PositionBatch positions) {
        synchronized (snapshots) {
            if (closed) {
                throw new IllegalStateException("Spatial query engine is closed");
            }
            return snapshots.computeIfAbsent(positions, batch -> {
                if (backend instanceof AdaptiveSpatialComputeBackend adaptive) {
                    return adaptive.prepareOwnedSnapshot(
                        batch.positionsX, batch.positionsY, batch.positionsZ
                    );
                }
                return backend.prepareSnapshot(
                    batch.positionsX, batch.positionsY, batch.positionsZ
                );
            });
        }
    }

    @Override
    public void close() {
        closed = true;
        synchronized (snapshots) {
            snapshots.values().forEach(SpatialComputeBackend.PositionSnapshot::close);
            snapshots.clear();
        }
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

    public record RadiusQuery(float originX, float originY, float originZ, float radius) {
        public RadiusQuery {
            requireFinite(originX, "originX");
            requireFinite(originY, "originY");
            requireFinite(originZ, "originZ");
            requireFinite(radius, "radius");
            if (radius < 0.0f) {
                throw new IllegalArgumentException("radius cannot be negative");
            }
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
