package dev.hypercore.region;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RegionKeyTest {
    @Test
    void mapsPositiveAndNegativeChunksWithFloorDivision() {
        assertEquals(
            new RegionKey("minecraft:overworld", 1, 0),
            RegionKey.fromChunk("minecraft:overworld", 8, 7, 8)
        );
        assertEquals(
            new RegionKey("minecraft:overworld", -1, -1),
            RegionKey.fromChunk("minecraft:overworld", -1, -8, 8)
        );
        assertEquals(
            new RegionKey("minecraft:overworld", -2, -2),
            RegionKey.fromChunk("minecraft:overworld", -9, -9, 8)
        );
    }

    @Test
    void rejectsInvalidKeys() {
        assertThrows(IllegalArgumentException.class, () -> new RegionKey(" ", 0, 0));
        assertThrows(
            IllegalArgumentException.class,
            () -> RegionKey.fromChunk("minecraft:overworld", 0, 0, 0)
        );
    }
}
