package dev.hypercore.concurrent;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HyperCoreExecutorTest {
    @Test
    void completesIsolatedWorkOnWorkerPool() throws Exception {
        try (HyperCoreExecutor executor = HyperCoreExecutor.create(1, 8)) {
            int result = executor.submit(() -> 40 + 2).get(5, TimeUnit.SECONDS);

            assertEquals(42, result);
            assertEquals(1, executor.submittedTasks());
            assertEquals(1, executor.completedTasks());
        }
    }

    @Test
    void rejectsWorkWhenTheBoundedQueueIsFull() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try (HyperCoreExecutor executor = HyperCoreExecutor.create(1, 1)) {
            var running = executor.submit(() -> await(started, release));
            started.await(5, TimeUnit.SECONDS);
            var queued = executor.submit(() -> 2);
            var rejected = executor.submit(() -> 3);

            assertThrows(ExecutionException.class, () -> rejected.get(5, TimeUnit.SECONDS));
            assertEquals(1, executor.rejectedTasks());
            assertEquals(1, executor.queuedTasks());

            release.countDown();
            assertEquals(1, running.get(5, TimeUnit.SECONDS));
            assertEquals(2, queued.get(5, TimeUnit.SECONDS));
            assertEquals(2, executor.completedTasks());
        }
    }

    @Test
    void failedTasksAreNotCountedAsCompleted() {
        try (HyperCoreExecutor executor = HyperCoreExecutor.create(1, 8)) {
            var failed = executor.submit(() -> {
                throw new IllegalStateException("boom");
            });

            assertThrows(ExecutionException.class, () -> failed.get(5, TimeUnit.SECONDS));
            assertEquals(1, executor.failedTasks());
            assertEquals(0, executor.completedTasks());
        }
    }

    private static int await(CountDownLatch started, CountDownLatch release) {
        started.countDown();
        try {
            if (!release.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for test release");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Worker interrupted", error);
        }
        return 1;
    }
}

