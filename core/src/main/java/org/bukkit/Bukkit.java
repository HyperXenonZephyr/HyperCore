package org.bukkit;

import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.Collection;
import java.util.Collections;
import java.util.logging.Logger;

/**
 * Minimal stub of the Bukkit {@code Bukkit} static accessor class. The adapter
 * installs the server instance via {@link #setServer(Server)} before any plugin
 * lifecycle callback fires, so {@link #getServer()} is safe to call from
 * {@code onEnable}. The {@code getScheduler()}/{@code getPluginManager()}
 * delegates mirror the real Bukkit static conveniences.
 */
public final class Bukkit {
    private static Server server;

    private Bukkit() {
    }

    public static Server getServer() {
        return server;
    }

    /**
     * Internal: called by the HyperCore adapter before plugin lifecycle.
     */
    public static void setServer(Server server) {
        Bukkit.server = server;
    }

    public static BukkitScheduler getScheduler() {
        return server.getScheduler();
    }

    public static PluginManager getPluginManager() {
        return server.getPluginManager();
    }

    public static Logger getLogger() {
        return server.getLogger();
    }

    /**
     * Returns all online players. The default implementation returns an empty
     * collection; server adapters can override {@link Server#getOnlinePlayers()}
     * to provide real data.
     */
    public static Collection<Player> getOnlinePlayers() {
        return server == null ? Collections.emptyList() : server.getOnlinePlayers();
    }

    /**
     * Reloads the server. Delegates to {@link Server#reload()}.
     *
     * @throws UnsupportedOperationException always — HyperCore does not support reload
     */
    public static void reload() {
        server.reload();
    }
}
