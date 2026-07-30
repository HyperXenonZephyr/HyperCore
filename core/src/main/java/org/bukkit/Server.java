package org.bukkit;

import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.logging.Logger;

/**
 * Minimal stub of the Bukkit {@code Server} interface. The HyperCore adapter
 * ({@code HyperCoreBukkitServer}) implements this to delegate scheduler and
 * plugin-manager calls back to the HyperCore runtime.
 */
public interface Server {
    String getName();

    String getVersion();

    BukkitScheduler getScheduler();

    PluginManager getPluginManager();

    Logger getLogger();
}
