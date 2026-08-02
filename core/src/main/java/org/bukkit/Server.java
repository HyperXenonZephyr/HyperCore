package org.bukkit;

import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.Collection;
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

    /**
     * Creates or loads a world from the given creator configuration.
     *
     * @param creator the world creation configuration
     * @return the created or loaded world, or {@code null} on failure
     */
    World createWorld(WorldCreator creator);

    /**
     * Returns all online players.
     */
    default Collection<Player> getOnlinePlayers() {
        return List.of();
    }
}
