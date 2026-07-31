package dev.hypercore.bukkit;

import dev.hypercore.plugin.PluginManager;

import org.bukkit.Server;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.logging.Logger;

/**
 * Implements the Bukkit {@link Server} interface by delegating scheduler and
 * plugin-manager calls to the HyperCore {@link PluginManager}. A single shared
 * instance is used for every Bukkit plugin loaded into the runtime.
 */
final class HyperCoreBukkitServer implements Server {
    private final BukkitScheduler scheduler;
    private final org.bukkit.plugin.PluginManager pluginManager;
    private final Logger logger;

    HyperCoreBukkitServer(PluginManager plugins) {
        this.scheduler = new HyperCoreBukkitScheduler(plugins.scheduler());
        this.pluginManager = new HyperCoreBukkitPluginManager(plugins);
        this.logger = Logger.getLogger("HyperCore");
    }

    @Override
    public String getName() {
        return "HyperCore";
    }

    @Override
    public String getVersion() {
        return "0.1.0-SNAPSHOT";
    }

    @Override
    public BukkitScheduler getScheduler() {
        return scheduler;
    }

    @Override
    public org.bukkit.plugin.PluginManager getPluginManager() {
        return pluginManager;
    }

    @Override
    public Logger getLogger() {
        return logger;
    }
}
