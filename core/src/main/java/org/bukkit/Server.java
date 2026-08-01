package org.bukkit;

import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.List;
import java.util.logging.Logger;

/**
 * Minimal stub of the Bukkit {@code Server} interface. The HyperCore adapter
 * ({@code HyperCoreBukkitServer}) implements this to delegate scheduler,
 * plugin-manager, and world calls back to the HyperCore runtime.
 */
public interface Server {

    String getName();

    String getVersion();

    BukkitScheduler getScheduler();

    PluginManager getPluginManager();

    Logger getLogger();

    /**
     * Returns the world with the given name, or {@code null} if not found.
     */
    World getWorld(String name);

    /**
     * Returns a list of all loaded worlds.
     */
    List<World> getWorlds();
}
