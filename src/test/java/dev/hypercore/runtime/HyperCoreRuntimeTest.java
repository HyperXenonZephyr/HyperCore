package dev.hypercore.runtime;

import dev.hypercore.config.HyperCoreConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HyperCoreRuntimeTest {
    @Test
    void appliesSettingsWhenRuntimeStarts() {
        HyperCoreRuntime runtime = new HyperCoreRuntime();

        runtime.start(new HyperCoreConfig.Settings(2, 32, 64, false, false, 8_192));
        try {
            assertTrue(runtime.isStarted());
            assertEquals(2, runtime.status().workers());
            assertEquals(32, runtime.status().queueCapacity());
            assertEquals(64, runtime.tickMetrics().windowSize());
            assertEquals("cpu-scalar", runtime.status().computeBackend());
            assertFalse(runtime.capabilities().gpu().attempted());
            assertEquals(2, runtime.regionTasks().status().owners());
            assertEquals(0, runtime.regionTasks().status().queuedMessages());
            assertFalse(runtime.vulkan().attempted());
            assertEquals(8_192, runtime.gpuOffloadPolicy().minimumBatchSize());
            assertEquals(0, runtime.plugins().status().registeredPlugins());
        } finally {
            runtime.close();
        }

        assertFalse(runtime.isStarted());
    }
}
