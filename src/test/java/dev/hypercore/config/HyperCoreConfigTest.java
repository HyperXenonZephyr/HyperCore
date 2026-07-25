package dev.hypercore.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HyperCoreConfigTest {
    @Test
    void resolvesAutomaticWorkerAndQueueValues() {
        HyperCoreConfig.Settings settings = new HyperCoreConfig.Settings(0, 0, 200, false, 16_384);

        assertEquals(7, settings.resolveWorkerThreads(8));
        assertEquals(448, settings.resolveQueueCapacity(7));
        assertEquals(1, settings.resolveWorkerThreads(1));
        assertEquals(256, settings.resolveQueueCapacity(1));
    }

    @Test
    void preservesExplicitWorkerAndQueueValues() {
        HyperCoreConfig.Settings settings = new HyperCoreConfig.Settings(4, 512, 400, true, 32_768);

        assertEquals(4, settings.resolveWorkerThreads(32));
        assertEquals(512, settings.resolveQueueCapacity(4));
    }

    @Test
    void rejectsInvalidSettings() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new HyperCoreConfig.Settings(-1, 128, 200, false, 16_384)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new HyperCoreConfig.Settings(1, -1, 200, false, 16_384)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new HyperCoreConfig.Settings(1, 128, 0, false, 16_384)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new HyperCoreConfig.Settings(1, 128, 200, false, 0)
        );
    }
}
