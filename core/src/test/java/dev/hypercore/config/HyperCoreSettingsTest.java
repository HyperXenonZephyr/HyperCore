package dev.hypercore.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HyperCoreSettingsTest {
    @Test
    void resolvesAutomaticWorkerAndQueueValues() {
        HyperCoreSettings settings = new HyperCoreSettings(0, 0, 200, false, false, 16_384, "auto");

        assertEquals(7, settings.resolveWorkerThreads(8));
        assertEquals(448, settings.resolveQueueCapacity(7));
        assertEquals(1, settings.resolveWorkerThreads(1));
        assertEquals(256, settings.resolveQueueCapacity(1));
    }

    @Test
    void preservesExplicitWorkerAndQueueValues() {
        HyperCoreSettings settings = new HyperCoreSettings(4, 512, 400, true, true, 32_768, "scalar");

        assertEquals(4, settings.resolveWorkerThreads(32));
        assertEquals(512, settings.resolveQueueCapacity(4));
    }

    @Test
    void rejectsInvalidSettings() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new HyperCoreSettings(-1, 128, 200, false, false, 16_384, "auto")
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new HyperCoreSettings(1, -1, 200, false, false, 16_384, "auto")
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new HyperCoreSettings(1, 128, 0, false, false, 16_384, "auto")
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new HyperCoreSettings(1, 128, 200, false, false, 0, "auto")
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new HyperCoreSettings(1, 128, 200, false, false, 16_384, "bogus")
        );
    }
}
