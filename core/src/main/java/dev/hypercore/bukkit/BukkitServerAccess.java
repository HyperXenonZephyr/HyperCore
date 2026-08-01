package dev.hypercore.bukkit;

import dev.hypercore.plugin.PluginManager;
import dev.hypercore.world.RegionExecutionService;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds the shared Bukkit {@link org.bukkit.Server} instance used by all Bukkit
 * plugins loaded into HyperCore. Bukkit expects a single global server, so every
 * {@link BukkitPluginAdapter} must acquire the same object rather than creating
 * its own.
 *
 * <p>The installed {@link RegionExecutionService} is also shared so that Bukkit
 * world/block/entity APIs reach the same region-locked execution pipeline.
 *
 * <p>A test-only {@link #reset()} hook is provided so that unit tests do not
 * leak static state between runs.
 */
public final class BukkitServerAccess {
    private static final AtomicReference<HyperCoreBukkitServer> SERVER = new AtomicReference<>();
    private static final AtomicReference<RegionExecutionService> REGION_EXECUTION = new AtomicReference<>();

    private BukkitServerAccess() {
    }

    /**
     * Returns the shared server, creating it if necessary.
     */
    static HyperCoreBukkitServer acquire(dev.hypercore.plugin.PluginManager plugins) {
        return SERVER.updateAndGet(current -> {
            if (current != null) {
                return current;
            }
            return new HyperCoreBukkitServer(plugins, REGION_EXECUTION::get);
        });
    }

    /**
     * Installs the region execution service that backs Bukkit world APIs.
     * Called by {@link dev.hypercore.runtime.HyperCoreRuntime} once the loader
     * adapter has registered a real {@link dev.hypercore.world.WorldAccessFactory}.
     */
    public static void installRegionExecution(RegionExecutionService execution) {
        REGION_EXECUTION.set(execution);
    }

    /**
     * Returns the currently installed shared server, or {@code null} if none has
     * been acquired yet.
     */
    static HyperCoreBukkitServer current() {
        return SERVER.get();
    }

    /**
     * Clears the shared server and region execution reference. Intended for tests only.
     */
    public static void reset() {
        SERVER.set(null);
        REGION_EXECUTION.set(null);
    }
}
