package dev.hypercore.concurrent;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

public final class HyperCoreExecutor implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(HyperCoreExecutor.class);

    private final ThreadPoolExecutor executor;
    private final int parallelism;
    private final int queueCapacity;
    private final LongAdder submittedTasks = new LongAdder();
    private final LongAdder completedTasks = new LongAdder();
    private final LongAdder rejectedTasks = new LongAdder();
    private final LongAdder cancelledTasks = new LongAdder();

    private HyperCoreExecutor(int parallelism, int queueCapacity) {
        if (parallelism < 1) {
            throw new IllegalArgumentException("parallelism must be positive");
        }
        if (queueCapacity < 1) {
            throw new IllegalArgumentException("queueCapacity must be positive");
        }

        this.parallelism = parallelism;
        this.queueCapacity = queueCapacity;
        this.executor = new ThreadPoolExecutor(
            parallelism,
            parallelism,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(queueCapacity),
            new WorkerThreadFactory(),
            new ThreadPoolExecutor.AbortPolicy()
        );
    }

    public static HyperCoreExecutor createDefault() {
        int processors = Runtime.getRuntime().availableProcessors();
        int parallelism = Math.max(1, processors - 1);
        int queueCapacity = Math.max(256, parallelism * 64);
        return create(parallelism, queueCapacity);
    }

    public static HyperCoreExecutor create(int parallelism, int queueCapacity) {
        return new HyperCoreExecutor(parallelism, queueCapacity);
    }

    /**
     * Runs immutable or isolated work away from the server thread. Callers must
     * marshal all world mutations back to the owning server or region thread.
     */
    public <T> CompletableFuture<T> submit(Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        submittedTasks.increment();

        TrackedTask<T> task = new TrackedTask<>(supplier);
        try {
            executor.execute(task);
        } catch (RejectedExecutionException error) {
            rejectedTasks.increment();
            task.reject(error);
        }
        return task.future();
    }

    public int parallelism() {
        return parallelism;
    }

    public int queueCapacity() {
        return queueCapacity;
    }

    public int activeTasks() {
        return executor.getActiveCount();
    }

    public int queuedTasks() {
        return executor.getQueue().size();
    }

    public long submittedTasks() {
        return submittedTasks.sum();
    }

    public long completedTasks() {
        return completedTasks.sum();
    }

    public long rejectedTasks() {
        return rejectedTasks.sum();
    }

    public long cancelledTasks() {
        return cancelledTasks.sum();
    }

    @Override
    public void close() {
        List<Runnable> pendingTasks = executor.shutdownNow();
        for (Runnable pendingTask : pendingTasks) {
            if (pendingTask instanceof TrackedTask<?> trackedTask) {
                trackedTask.cancel();
                cancelledTasks.increment();
            }
        }
    }

    private final class TrackedTask<T> implements Runnable {
        private final Supplier<T> supplier;
        private final CompletableFuture<T> future = new CompletableFuture<>();

        private TrackedTask(Supplier<T> supplier) {
            this.supplier = supplier;
        }

        @Override
        public void run() {
            try {
                T result = supplier.get();
                completedTasks.increment();
                future.complete(result);
            } catch (Throwable error) {
                completedTasks.increment();
                future.completeExceptionally(error);
            }
        }

        private CompletableFuture<T> future() {
            return future;
        }

        private void reject(RejectedExecutionException error) {
            future.completeExceptionally(error);
        }

        private void cancel() {
            future.cancel(false);
        }
    }

    private static final class WorkerThreadFactory implements ThreadFactory {
        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "HyperCore-Worker-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((worker, error) ->
                LOGGER.error("Uncaught exception on {}", worker.getName(), error));
            return thread;
        }
    }
}
