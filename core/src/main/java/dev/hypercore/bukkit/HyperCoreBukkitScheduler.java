package dev.hypercore.bukkit;

import dev.hypercore.plugin.PluginContext;
import dev.hypercore.plugin.PluginScheduler;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapts HyperCore's {@link dev.hypercore.plugin.PluginScheduler} to the Bukkit
 * {@link BukkitScheduler} interface. The {@code Plugin} parameter on each
 * method is accepted for API compatibility but ignored — task ownership is
 * tracked by the underlying {@link PluginContext}.
 */
final class HyperCoreBukkitScheduler implements BukkitScheduler {
    private final PluginContext context;

    HyperCoreBukkitScheduler(PluginContext context) {
        this.context = context;
    }

    @Override
    public BukkitTask runTask(Plugin plugin, Runnable task) {
        return new TaskAdapter(context.runTask(task), plugin, true);
    }

    @Override
    public BukkitTask runTaskLater(Plugin plugin, Runnable task, long delay) {
        return new TaskAdapter(context.runTaskLater(delay, task), plugin, true);
    }

    @Override
    public BukkitTask runTaskTimer(Plugin plugin, Runnable task, long delay, long period) {
        return new TaskAdapter(context.runTaskTimer(delay, period, task), plugin, true);
    }

    @Override
    public BukkitTask runTaskAsynchronously(Plugin plugin, Runnable task) {
        return new TaskAdapter(context.runTaskAsync(task), plugin, false);
    }

    @Override
    public BukkitTask runTaskLaterAsynchronously(Plugin plugin, Runnable task, long delay) {
        return new TaskAdapter(context.runTaskLaterAsync(delay, task), plugin, false);
    }

    @Override
    public BukkitTask runTaskTimerAsynchronously(Plugin plugin, Runnable task, long delay, long period) {
        return new TaskAdapter(context.runTaskTimerAsync(delay, period, task), plugin, false);
    }

    @Override
    public void cancelTasks(Plugin plugin) {
        // The HyperCore scheduler cancels by plugin id during plugin cleanup;
        // this method is a no-op in the minimal shim.
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
