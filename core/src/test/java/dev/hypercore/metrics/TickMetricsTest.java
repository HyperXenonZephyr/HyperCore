package dev.hypercore.metrics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TickMetricsTest {
    @Test
    void recordsOneCompletedTick() {
        TickMetrics metrics = new TickMetrics();

        metrics.beginTick();
        metrics.endTick();

        TickMetrics.Snapshot snapshot = metrics.snapshot();
        assertEquals(1, snapshot.samples());
        assertTrue(snapshot.averageMs() >= 0.0);
        assertTrue(snapshot.p95Ms() >= 0.0);
        assertTrue(snapshot.maximumMs() >= snapshot.averageMs());
    }

    @Test
    void retainsOnlyTheConfiguredWindow() {
        TickMetrics metrics = new TickMetrics(3);

        for (int index = 0; index < 5; index++) {
            metrics.beginTick();
            metrics.endTick();
        }

        assertEquals(3, metrics.windowSize());
        assertEquals(3, metrics.snapshot().samples());
    }
}
