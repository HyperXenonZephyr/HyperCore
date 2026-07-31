package dev.hypercore.bukkit;

import dev.hypercore.plugin.PluginManager;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds the shared Bukkit {@link org.bukkit.Server} instance used by all Bukkit
 * plugins loaded into HyperCore. Bukkit expects a single global server, so every
 * {@link BukkitPluginAdapter} must acquire the same object rather than creating
 * its own.
 *
 * <p>A test-only {@link #reset()} hook is provided so that unit tests do not
 * leak static state between runs.
 */
public final class BukkitServerAccess {
    private static final AtomicReference<HyperCoreBukkitServer> SERVER = new AtomicReference<>();

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
            return new HyperCoreBukkitServer(plugins);
        });
    }

    /**
     * Returns the currently installed shared server, or {@code null} if none has
     * been acquired yet.
     */
    static HyperCoreBukkitServer current() {
        return SERVER.get();
    }

    /**
     * Clears the shared server. Intended for tests only.
     */
    public static void reset() {
        SERVER.set(null);
    }
}
