package dev.hypercore.orchestrator.launcher;

import dev.hypercore.config.HyperCoreSettings;
import dev.hypercore.orchestrator.HyperCoreRole;
import dev.hypercore.runtime.HyperCoreRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

/**
 * Standalone entry point for the HyperCore Orchestrator process.
 *
 * <p>Starts the loader-agnostic runtime in {@code ORCHESTRATOR} role: it spins
 * up the two child host servers and the bridge coordinator. The process blocks
 * until terminated; the shutdown hook closes the runtime, which stops both
 * hosts cleanly.
 *
 * <p>Launch flags (all optional, matched by the distribution scripts):
 * <ul>
 *   <li>{@code -Dhypercore.role=ORCHESTRATOR} required (set by the scripts);</li>
 *   <li>{@code -Dhypercore.orchestrator.basePort} default 34177;</li>
 *   <li>{@code -Dhypercore.bridge.tickMillis} default 50;</li>
 *   <li>{@code -Dhypercore.orchestrator.root} directory hosting {@code forge-host}
 *       and {@code fabric-host}, default the working directory.</li>
 * </ul>
 */
public final class OrchestratorMain {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrchestratorMain.class);

    private OrchestratorMain() {
    }

    public static void main(String[] args) {
        System.setProperty("hypercore.role", HyperCoreRole.ORCHESTRATOR.name());
        int basePort = integerProperty("hypercore.orchestrator.basePort", HyperCoreSettings.OrchestratorSettings.DEFAULT_ORCHESTRATOR_PORT);
        long tickMillis = longProperty("hypercore.bridge.tickMillis", 50);

        HyperCoreSettings settings = new HyperCoreSettings(
            0,
            0,
            200,
            false,
            false,
            16_384,
            "scalar",
            new HyperCoreSettings.OrchestratorSettings(
                "",
                List.of(),
                List.of(),
                0,
                0,
                basePort,
                120_000,
                tickMillis,
                "forge-host",
                "fabric-host",
                "net.minecraftforge.server.ServerMain",
                "net.fabricmc.loader.impl.launch.knot.KnotServer",
                List.of("--nogui"),
                List.of("nogui"),
                "[hypercore] BRIDGE READY"
            )
        );

        HyperCoreRuntime runtime = new HyperCoreRuntime();
        Runtime.getRuntime().addShutdownHook(new Thread(runtime::close, "orchestrator-shutdown"));
        runtime.start(settings, Path.of(System.getProperty("hypercore.orchestrator.root", ".")).resolve("plugins"));
        LOGGER.info(
            "HyperCore Orchestrator is running on port {} (bridge tick {} ms); hosts are launched under {}",
            basePort,
            tickMillis,
            System.getProperty("hypercore.orchestrator.root", ".")
        );
        awaitTermination();
    }

    private static void awaitTermination() {
        try {
            Thread.currentThread().join();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }

    private static int integerProperty(String key, int fallback) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException error) {
            return fallback;
        }
    }

    private static long longProperty(String key, long fallback) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException error) {
            return fallback;
        }
    }
}
