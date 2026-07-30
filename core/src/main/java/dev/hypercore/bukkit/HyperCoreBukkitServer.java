package dev.hypercore.bukkit;

import dev.hypercore.plugin.PluginContext;

import org.bukkit.Server;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.logging.Logger;

/**
 * Implements the Bukkit {@link Server} interface by delegating scheduler and
 * plugin-manager calls back to the HyperCore {@link PluginContext}. A new
 * instance is created per plugin lifecycle by {@link BukkitPluginAdapter}.
 */
final class HyperCoreBukkitServer implements Server {
    private final BukkitScheduler scheduler;
    private final PluginManager pluginManager;
    private final Logger logger;

    HyperCoreBukkitServer(PluginContext context) {
        this.scheduler = new HyperCoreBukkitScheduler(context);
        this.pluginManager = new HyperCoreBukkitPluginManager(context);
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
    public PluginManager getPluginManager() {
        return pluginManager;
    }

    @Override
    public Logger getLogger() {
        return logger;
    }
}
