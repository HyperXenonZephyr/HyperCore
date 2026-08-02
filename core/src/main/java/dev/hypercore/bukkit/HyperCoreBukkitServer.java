package dev.hypercore.bukkit;

import dev.hypercore.plugin.PluginManager;
import dev.hypercore.world.RegionExecutionService;

import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Implements the Bukkit {@link Server} interface by delegating scheduler,
 * plugin-manager, and world calls to the HyperCore {@link PluginManager}. A
 * single shared instance is used for every Bukkit plugin loaded into the
 * runtime.
 */
final class HyperCoreBukkitServer implements Server {
    private final BukkitScheduler scheduler;
    private final org.bukkit.plugin.PluginManager pluginManager;
    private final Logger logger;
    private final Supplier<RegionExecutionService> regionExecution;

    HyperCoreBukkitServer(PluginManager plugins, Supplier<RegionExecutionService> regionExecution) {
        this.scheduler = new HyperCoreBukkitScheduler(plugins.scheduler());
        this.pluginManager = new HyperCoreBukkitPluginManager(plugins);
        this.logger = Logger.getLogger("HyperCore");
        this.regionExecution = regionExecution;
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

    @Override
    public World getWorld(String name) {
        RegionExecutionService execution = regionExecution.get();
        if (execution == null) {
            return null;
        }
        if (execution.access(name) == null) {
            return null;
        }
        return execution.world(name);
    }

    @Override
    public List<World> getWorlds() {
        RegionExecutionService execution = regionExecution.get();
        if (execution == null) {
            return List.of();
        }
        List<World> worlds = new ArrayList<>();
        for (String name : execution.worldNames()) {
            worlds.add(execution.world(name));
        }
        return List.copyOf(worlds);
    }

    @Override
    public World createWorld(org.bukkit.WorldCreator creator) {
        RegionExecutionService execution = regionExecution.get();
        if (execution == null) {
            return null;
        }
        return execution.createWorld(creator);
    }

    @Override
    public Collection<org.bukkit.entity.Player> getOnlinePlayers() {
        RegionExecutionService execution = regionExecution.get();
        if (execution == null) {
            return List.of();
        }
        return List.copyOf(execution.onlinePlayers());
    }
}
