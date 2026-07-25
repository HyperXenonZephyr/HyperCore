package dev.hypercore.plugin;

import com.mojang.logging.LogUtils;
import dev.hypercore.concurrent.HyperCoreExecutor;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;

/**
 * Tick-driven scheduler with explicit plugin ownership and a bounded async lane.
 * Sync callbacks must only touch server-owned state; async callbacks must not.
 */
public final class PluginScheduler implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Map<Long, ScheduledTask> tasks = new LinkedHashMap<>();
    private final Map<String, Set<Long>> tasksByPlugin = new HashMap<>();
    private long currentTick;
    private long nextTaskId = 1L;
    private long completedTasks;
    private long failedTasks;
    private long cancelledTasks;
    private HyperCoreExecutor executor;
    private boolean closed;

    public synchronized void attachExecutor(HyperCoreExecutor executor) {
        if (closed) {
            throw new IllegalStateException("Plugin scheduler is closed");
        }
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public synchronized void detachExecutor(HyperCoreExecutor executor) {
        if (this.executor == executor) {
            this.executor = null;
        }
    }

    public TaskHandle runTask(String pluginId, Runnable action) {
        return schedule(pluginId, TaskMode.SYNC, 0L, 0L, action);
    }

    public TaskHandle runTaskLater(String pluginId, long delayTicks, Runnable action) {
        return schedule(pluginId, TaskMode.SYNC, delayTicks, 0L, action);
    }

    public TaskHandle runTaskTimer(String pluginId, long delayTicks, long periodTicks, Runnable action) {
        requirePositivePeriod(periodTicks);
        return schedule(pluginId, TaskMode.SYNC, delayTicks, periodTicks, action);
    }

    public TaskHandle runTaskAsync(String pluginId, Runnable action) {
        return schedule(pluginId, TaskMode.ASYNC, 0L, 0L, action);
    }

    public TaskHandle runTaskLaterAsync(String pluginId, long delayTicks, Runnable action) {
        return schedule(pluginId, TaskMode.ASYNC, delayTicks, 0L, action);
    }

    public TaskHandle runTaskTimerAsync(String pluginId, long delayTicks, long periodTicks, Runnable action) {
        requirePositivePeriod(periodTicks);
        return schedule(pluginId, TaskMode.ASYNC, delayTicks, periodTicks, action);
    }

    public synchronized long currentTick() {
        return currentTick;
    }

    /** Executes all callbacks due for the next server tick on the calling thread. */
    public void tick() {
        List<ScheduledTask> dueTasks;
        synchronized (this) {
            if (closed) {
                return;
            }
            currentTick++;
            dueTasks = collectDueTasks(currentTick);
        }

        for (ScheduledTask task : dueTasks) {
            if (task.cancelled) {
                continue;
            }
            if (task.mode == TaskMode.SYNC) {
                executeSync(task);
            } else {
                executeAsync(task);
            }
        }
    }

    public synchronized int cancelPlugin(String pluginId) {
        String normalizedPluginId = PluginPermissionService.normalizePluginId(pluginId);
        Set<Long> ownedTasks = tasksByPlugin.remove(normalizedPluginId);
        if (ownedTasks == null || ownedTasks.isEmpty()) {
            return 0;
        }

        int cancelled = 0;
        for (Long taskId : ownedTasks) {
            ScheduledTask task = tasks.remove(taskId);
            if (task != null && !task.cancelled) {
                task.cancelled = true;
                cancelled++;
            }
        }
        cancelledTasks += cancelled;
        return cancelled;
    }

    public synchronized Status status() {
        return new Status(currentTick, tasks.size(), completedTasks, failedTasks, cancelledTasks);
    }

    public synchronized int cancel(long taskId) {
        ScheduledTask task = tasks.remove(taskId);
        if (task == null || task.cancelled) {
            return 0;
        }
        task.cancelled = true;
        removeOwnership(task);
        cancelledTasks++;
        return 1;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        int cancelled = 0;
        for (ScheduledTask task : tasks.values()) {
            if (!task.cancelled) {
                task.cancelled = true;
                cancelled++;
            }
        }
        tasks.clear();
        tasksByPlugin.clear();
        cancelledTasks += cancelled;
        executor = null;
    }

    private synchronized TaskHandle schedule(
        String pluginId,
        TaskMode mode,
        long delayTicks,
        long periodTicks,
        Runnable action
    ) {
        String normalizedPluginId = PluginPermissionService.normalizePluginId(pluginId);
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(action, "action");
        if (closed) {
            throw new IllegalStateException("Plugin scheduler is closed");
        }
        if (delayTicks < 0L) {
            throw new IllegalArgumentException("delayTicks cannot be negative");
        }
        if (periodTicks < 0L) {
            throw new IllegalArgumentException("periodTicks cannot be negative");
        }
        long taskId = nextTaskId++;
        long firstRunTick = currentTick + Math.max(1L, delayTicks);
        ScheduledTask task = new ScheduledTask(taskId, normalizedPluginId, mode, firstRunTick, periodTicks, action);
        tasks.put(taskId, task);
        tasksByPlugin.computeIfAbsent(normalizedPluginId, ignored -> new HashSet<>()).add(taskId);
        return new TaskHandle(task);
    }

    private List<ScheduledTask> collectDueTasks(long tick) {
        List<ScheduledTask> dueTasks = new ArrayList<>();
        Iterator<Map.Entry<Long, ScheduledTask>> iterator = tasks.entrySet().iterator();
        while (iterator.hasNext()) {
            ScheduledTask task = iterator.next().getValue();
            if (task.cancelled || task.nextRunTick > tick) {
                continue;
            }
            dueTasks.add(task);
            if (task.periodTicks == 0L) {
                iterator.remove();
                removeOwnership(task);
            } else {
                task.nextRunTick += task.periodTicks;
            }
        }
        return dueTasks;
    }

    private void executeSync(ScheduledTask task) {
        try {
            task.action.run();
            synchronized (this) {
                completedTasks++;
            }
        } catch (Throwable error) {
            taskFailed(task, error);
        }
    }

    private void executeAsync(ScheduledTask task) {
        HyperCoreExecutor currentExecutor;
        synchronized (this) {
            currentExecutor = executor;
        }
        if (currentExecutor == null) {
            taskFailed(task, new IllegalStateException("Async plugin task ran before the executor was attached"));
            return;
        }

        CompletableFuture<Void> future = currentExecutor.submit(() -> {
            task.action.run();
            return null;
        });
        future.whenComplete((ignored, error) -> {
            if (error == null) {
                synchronized (PluginScheduler.this) {
                    completedTasks++;
                }
            } else {
                taskFailed(task, error);
            }
        });
    }

    private void taskFailed(ScheduledTask task, Throwable error) {
        synchronized (this) {
            failedTasks++;
            if (!task.cancelled && task.periodTicks > 0L) {
                task.cancelled = true;
                tasks.remove(task.id);
                removeOwnership(task);
                cancelledTasks++;
            }
        }
        LOGGER.error("Plugin {} task {} failed", task.pluginId, task.id, error);
    }

    private void removeOwnership(ScheduledTask task) {
        Set<Long> ownedTasks = tasksByPlugin.get(task.pluginId);
        if (ownedTasks != null) {
            ownedTasks.remove(task.id);
            if (ownedTasks.isEmpty()) {
                tasksByPlugin.remove(task.pluginId);
            }
        }
    }

    private static void requirePositivePeriod(long periodTicks) {
        if (periodTicks < 1L) {
            throw new IllegalArgumentException("periodTicks must be positive");
        }
    }

    private enum TaskMode {
        SYNC,
        ASYNC
    }

    private static final class ScheduledTask {
        private final long id;
        private final String pluginId;
        private final TaskMode mode;
        private final long periodTicks;
        private final Runnable action;
        private volatile long nextRunTick;
        private volatile boolean cancelled;

        private ScheduledTask(
            long id,
            String pluginId,
            TaskMode mode,
            long nextRunTick,
            long periodTicks,
            Runnable action
        ) {
            this.id = id;
            this.pluginId = pluginId;
            this.mode = mode;
            this.nextRunTick = nextRunTick;
            this.periodTicks = periodTicks;
            this.action = action;
        }
    }

    public final class TaskHandle {
        private final ScheduledTask task;

        private TaskHandle(ScheduledTask task) {
            this.task = task;
        }

        public long taskId() {
            return task.id;
        }

        public boolean cancel() {
            return PluginScheduler.this.cancel(task.id) != 0;
        }

        public boolean cancelled() {
            return task.cancelled;
        }

        public boolean active() {
            synchronized (PluginScheduler.this) {
                return tasks.get(task.id) == task && !task.cancelled;
            }
        }
    }

    public record Status(
        long currentTick,
        int scheduledTasks,
        long completedTasks,
        long failedTasks,
        long cancelledTasks
    ) {
    }
}
