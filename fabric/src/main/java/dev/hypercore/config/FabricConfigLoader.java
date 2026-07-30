package dev.hypercore.config;

import dev.hypercore.config.HyperCoreSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Reads HyperCore runtime settings from a {@code hypercore.properties} file in
 * the server's {@code config} directory, mirroring the ForgeConfigSpec defaults
 * on Fabric. Missing keys fall back to the same defaults Forge uses; the
 * {@link HyperCoreSettings} canonical constructor validates the assembled record.
 *
 * <p>This is intentionally a ~60-line plain-{@link Properties} reader with no
 * third-party dependency, so the Fabric adapter stays loader-agnostic at the
 * core level and only touches the filesystem here.
 */
public final class FabricConfigLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(FabricConfigLoader.class);

    private static final String FILE_NAME = "hypercore.properties";

    private static final int DEFAULT_WORKER_THREADS = 0;
    private static final int DEFAULT_QUEUE_CAPACITY = 0;
    private static final int DEFAULT_TICK_SAMPLE_WINDOW = 200;
    private static final boolean DEFAULT_PROBE_GPU = true;
    private static final boolean DEFAULT_ENABLE_GPU = true;
    private static final int DEFAULT_GPU_MINIMUM_BATCH_SIZE = 16_384;
    private static final String DEFAULT_CPU_BACKEND = "auto";

    private FabricConfigLoader() {
    }

    public static HyperCoreSettings load(Path configDirectory) {
        Properties properties = new Properties();
        Path file = configDirectory.resolve(FILE_NAME);
        if (Files.isRegularFile(file)) {
            try (var reader = Files.newBufferedReader(file)) {
                properties.load(reader);
            } catch (IOException error) {
                LOGGER.warn("Failed to read {}; using defaults: {}", file, error.getMessage());
            }
        } else {
            LOGGER.info("HyperCore config {} not found; using defaults", file);
        }
        return new HyperCoreSettings(
            parseInt(properties, "execution.workerThreads", DEFAULT_WORKER_THREADS),
            parseInt(properties, "execution.queueCapacity", DEFAULT_QUEUE_CAPACITY),
            parseInt(properties, "metrics.tickSampleWindow", DEFAULT_TICK_SAMPLE_WINDOW),
            parseBoolean(properties, "compute.probeGpu", DEFAULT_PROBE_GPU),
            parseBoolean(properties, "compute.enableGpu", DEFAULT_ENABLE_GPU),
            parseInt(properties, "compute.gpuMinimumBatchSize", DEFAULT_GPU_MINIMUM_BATCH_SIZE),
            parseString(properties, "compute.cpuBackend", DEFAULT_CPU_BACKEND)
        );
    }

    private static int parseInt(Properties properties, String key, int fallback) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException error) {
            LOGGER.warn("Invalid integer for {}={}; using default {}: ", key, value, fallback, error);
            return fallback;
        }
    }

    private static boolean parseBoolean(Properties properties, String key, boolean fallback) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return Boolean.parseBoolean(value.trim());
    }

    private static String parseString(Properties properties, String key, String fallback) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
