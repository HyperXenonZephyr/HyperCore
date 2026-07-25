package dev.hypercore.concurrent;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HyperCoreExecutorTest {
    @Test
    void completesIsolatedWorkOnWorkerPool() throws Exception {
        try (HyperCoreExecutor executor = HyperCoreExecutor.createDefault()) {
            int result = executor.submit(() -> 40 + 2).get(5, TimeUnit.SECONDS);

            assertEquals(42, result);
            assertEquals(1, executor.submittedTasks());
            assertEquals(1, executor.completedTasks());
        }
    }
}

