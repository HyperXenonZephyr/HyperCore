package dev.hypercore.concurrent;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

public final class HyperCoreExecutor implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final ExecutorService executor;
    private final int parallelism;
    private final LongAdder submittedTasks = new LongAdder();
    private final LongAdder completedTasks = new LongAdder();

    private HyperCoreExecutor(int parallelism) {
        this.parallelism = parallelism;
        this.executor = Executors.newFixedThreadPool(parallelism, new WorkerThreadFactory());
    }

    public static HyperCoreExecutor createDefault() {
        int processors = Runtime.getRuntime().availableProcessors();
        int parallelism = Math.max(1, processors - 1);
        return new HyperCoreExecutor(parallelism);
    }

    /**
     * Runs immutable or isolated work away from the server thread. Callers must
     * marshal all world mutations back to the owning server or region thread.
     */
    public <T> CompletableFuture<T> submit(Supplier<T> task) {
        Objects.requireNonNull(task, "task");
        submittedTasks.increment();
        return CompletableFuture.supplyAsync(task, executor)
            .whenComplete((result, error) -> completedTasks.increment());
    }

    public int parallelism() {
        return parallelism;
    }

    public long submittedTasks() {
        return submittedTasks.sum();
    }

    public long completedTasks() {
        return completedTasks.sum();
    }

    @Override
    public void close() {
        executor.shutdownNow();
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
