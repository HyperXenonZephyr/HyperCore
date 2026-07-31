package dev.hypercore.bukkit;

import dev.hypercore.plugin.PluginScheduler;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapts HyperCore's {@link dev.hypercore.plugin.PluginScheduler} to the Bukkit
 * {@link BukkitScheduler} interface. Task ownership is derived from the
 * {@link Plugin#getName()} passed to each method so that every Bukkit plugin
 * only sees its own tasks cancelled.
 */
final class HyperCoreBukkitScheduler implements BukkitScheduler {
    private final PluginScheduler scheduler;

    HyperCoreBukkitScheduler(PluginScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public BukkitTask runTask(Plugin plugin, Runnable task) {
        return new TaskAdapter(scheduler.runTask(pluginId(plugin), task), plugin, true);
    }

    @Override
    public BukkitTask runTaskLater(Plugin plugin, Runnable task, long delay) {
        return new TaskAdapter(scheduler.runTaskLater(pluginId(plugin), delay, task), plugin, true);
    }

    @Override
    public BukkitTask runTaskTimer(Plugin plugin, Runnable task, long delay, long period) {
        return new TaskAdapter(scheduler.runTaskTimer(pluginId(plugin), delay, period, task), plugin, true);
    }

    @Override
    public BukkitTask runTaskAsynchronously(Plugin plugin, Runnable task) {
        return new TaskAdapter(scheduler.runTaskAsync(pluginId(plugin), task), plugin, false);
    }

    @Override
    public BukkitTask runTaskLaterAsynchronously(Plugin plugin, Runnable task, long delay) {
        return new TaskAdapter(scheduler.runTaskLaterAsync(pluginId(plugin), delay, task), plugin, false);
    }

    @Override
    public BukkitTask runTaskTimerAsynchronously(Plugin plugin, Runnable task, long delay, long period) {
        return new TaskAdapter(scheduler.runTaskTimerAsync(pluginId(plugin), delay, period, task), plugin, false);
    }

    @Override
    public void cancelTasks(Plugin plugin) {
        scheduler.cancelPlugin(pluginId(plugin));
    }

    private static String pluginId(Plugin plugin) {
        return plugin == null ? "bukkit-unknown" : plugin.getName();
    }

    private static final class TaskAdapter implements BukkitTask {
        private final PluginScheduler.TaskHandle handle;
        private final Plugin plugin;
        private final boolean sync;

        TaskAdapter(PluginScheduler.TaskHandle handle, Plugin plugin, boolean sync) {
            this.handle = handle;
            this.plugin = plugin;
            this.sync = sync;
        }

        @Override
        public int getTaskId() {
            return (int) handle.taskId();
        }

        @Override
        public Plugin getOwner() {
            return plugin;
        }

        @Override
        public boolean isSync() {
            return sync;
        }

        @Override
        public void cancel() {
            handle.cancel();
        }
    }
}
