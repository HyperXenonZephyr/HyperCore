package dev.hypercore.region;

import java.util.Objects;

public record RegionKey(String dimension, int regionX, int regionZ) implements Comparable<RegionKey> {
    public RegionKey {
        dimension = Objects.requireNonNull(dimension, "dimension").trim();
        if (dimension.isEmpty()) {
            throw new IllegalArgumentException("dimension cannot be blank");
        }
    }

    public static RegionKey fromChunk(
        String dimension,
        int chunkX,
        int chunkZ,
        int regionSizeChunks
    ) {
        if (regionSizeChunks < 1) {
            throw new IllegalArgumentException("regionSizeChunks must be positive");
        }
        return new RegionKey(
            dimension,
            Math.floorDiv(chunkX, regionSizeChunks),
            Math.floorDiv(chunkZ, regionSizeChunks)
        );
    }

    @Override
    public int compareTo(RegionKey other) {
        int dimensionOrder = dimension.compareTo(other.dimension);
        if (dimensionOrder != 0) {
            return dimensionOrder;
        }
        int xOrder = Integer.compare(regionX, other.regionX);
        return xOrder != 0 ? xOrder : Integer.compare(regionZ, other.regionZ);
    }
}
