package dev.hypercore.runtime;

import dev.hypercore.config.HyperCoreConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HyperCoreRuntimeTest {
    @TempDir
    Path temporaryDirectory;

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

    @Test
    void scansAnEmptyExternalPluginDirectory() {
        HyperCoreRuntime runtime = new HyperCoreRuntime();
        Path pluginDirectory = temporaryDirectory.resolve("plugins");

        runtime.start(
            new HyperCoreConfig.Settings(2, 32, 64, false, false, 8_192),
            pluginDirectory
        );
        try {
            assertTrue(Files.isDirectory(pluginDirectory));
            assertEquals(0, runtime.externalPlugins().report().discovered());
            assertEquals(0, runtime.plugins().status().externalPlugins());
        } finally {
            runtime.close();
        }

        assertFalse(runtime.isStarted());
    }
}
