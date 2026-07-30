package org.bukkit.scheduler;

import org.bukkit.plugin.Plugin;

/**
 * Minimal stub of the Bukkit {@code BukkitScheduler} interface. Only the
 * task-scheduling methods are declared; the adapter delegates to HyperCore's
 * {@link dev.hypercore.plugin.PluginScheduler}.
 */
public interface BukkitScheduler {
    BukkitTask runTask(Plugin plugin, Runnable task);

    BukkitTask runTaskLater(Plugin plugin, Runnable task, long delay);

    BukkitTask runTaskTimer(Plugin plugin, Runnable task, long delay, long period);

    BukkitTask runTaskAsynchronously(Plugin plugin, Runnable task);

    BukkitTask runTaskLaterAsynchronously(Plugin plugin, Runnable task, long delay);

    BukkitTask runTaskTimerAsynchronously(Plugin plugin, Runnable task, long delay, long period);

    void cancelTasks(Plugin plugin);
}
