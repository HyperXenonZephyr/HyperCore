package org.bukkit.scheduler;

import org.bukkit.plugin.Plugin;

/**
 * Minimal stub of the Bukkit {@code BukkitTask} interface.
 */
public interface BukkitTask {
    int getTaskId();

    Plugin getOwner();

    boolean isSync();

    void cancel();
}
