package dev.hypercore.server.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HyperCoreConfigTest {
    @TempDir
    Path directory;

    @Test
    void missingFileWritesDefaultsAndReturnsThem() throws IOException {
        Path file = this.directory.resolve("hypercore.json");
        HyperCoreConfig config = HyperCoreConfig.load(file);
        assertTrue(Files.exists(file));
        assertEquals(HyperCoreConfig.defaults().regionMaxMutationsPerTick(), config.regionMaxMutationsPerTick());
        assertEquals(HyperCoreConfig.defaults().metricsWindowSize(), config.metricsWindowSize());
        assertEquals(Path.of("compute-providers"), config.computeProviderDirectory());
        assertEquals(Path.of("plugins"), config.pluginsDirectory());
        int threads = config.regionWorkerThreads();
        assertTrue(threads >= 1 && threads <= 64);
    }

    @Test
    void parsesOverridesWithinRange() {
        HyperCoreConfig config = HyperCoreConfig.parse("""
            {
              "region": {"workerThreads": 8, "maxMutationsPerTick": 1000},
              "compute": {"gpuMinimumBatchSize": 0, "providerDirectory": "providers"},
              "plugins": {"directory": "extensions"},
              "metrics": {"windowSize": 500}
            }
            """);
        assertEquals(8, config.regionWorkerThreads());
        assertEquals(1000, config.regionMaxMutationsPerTick());
        assertEquals(0, config.computeGpuMinimumBatchSize());
        assertEquals(Path.of("providers"), config.computeProviderDirectory());
        assertEquals(Path.of("extensions"), config.pluginsDirectory());
        assertEquals(500, config.metricsWindowSize());
    }

    @Test
    void absentSectionsFallBackToDefaults() {
        HyperCoreConfig config = HyperCoreConfig.parse("{}");
        assertEquals(HyperCoreConfig.defaults().regionMaxMutationsPerTick(), config.regionMaxMutationsPerTick());
        assertEquals(4096, config.computeGpuMinimumBatchSize());
    }

    @Test
    void rejectsMalformedJsonAndWrongRootType() {
        assertThrows(IllegalArgumentException.class, () -> HyperCoreConfig.parse("{not json"));
        assertThrows(IllegalArgumentException.class, () -> HyperCoreConfig.parse("[]"));
    }

    @Test
    void rejectsOutOfRangeValuesAndUnknownKeysWithNamedProblems() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> HyperCoreConfig.parse("""
            {"region": {"workerThreads": 0}, "totallyUnknown": 1}
            """));
        String message = error.getMessage();
        assertTrue(message.contains("workerThreads must be between 1 and 64"));
        assertTrue(message.contains("unknown key: totallyUnknown"));
        assertThrows(IllegalArgumentException.class, () -> HyperCoreConfig.parse(
            "{\"metrics\": {\"windowSize\": 5}}"));
        assertThrows(IllegalArgumentException.class, () -> HyperCoreConfig.parse(
            "{\"plugins\": {\"directory\": \"   \"}}"));
    }

    @Test
    void rejectsNonObjectSectionsAndWrongValueTypes() {
        assertThrows(IllegalArgumentException.class, () -> HyperCoreConfig.parse(
            "{\"region\": [1]}"));
        assertThrows(IllegalArgumentException.class, () -> HyperCoreConfig.parse(
            "{\"compute\": {\"gpuMinimumBatchSize\": \"many\"}}"));
    }
}
