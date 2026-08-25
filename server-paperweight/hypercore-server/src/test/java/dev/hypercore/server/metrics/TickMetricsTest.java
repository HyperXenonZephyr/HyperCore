package dev.hypercore.server.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TickMetricsTest {
    @Test
    void emptyWindowReportsZeroStatistics() {
        TickMetrics metrics = new TickMetrics(10);
        assertEquals(10, metrics.capacity());
        assertEquals(0, metrics.count());
        assertEquals(0L, metrics.total());
        assertEquals(0.0d, metrics.average());
        assertEquals(0L, metrics.median());
        assertEquals(0L, metrics.percentile95());
        assertEquals(0L, metrics.maximum());
    }

    @Test
    void computesExactStatisticsForKnownSequences() {
        TickMetrics metrics = new TickMetrics(100);
        for (long nanos = 1; nanos <= 100; nanos++) {
            metrics.record(nanos);
        }
        assertEquals(100, metrics.count());
        assertEquals(5050L, metrics.total());
        assertEquals(50.5d, metrics.average());
        assertEquals(50L, metrics.median());
        assertEquals(95L, metrics.percentile95());
        assertEquals(100L, metrics.maximum());

        TickMetrics uneven = new TickMetrics(10);
        for (long nanos : new long[] {40, 10, 30, 20}) {
            uneven.record(nanos);
        }
        assertEquals(4, uneven.count());
        assertEquals(100L, uneven.total());
        assertEquals(25.0d, uneven.average());
        assertEquals(20L, uneven.median());
        assertEquals(40L, uneven.percentile95());
        assertEquals(40L, uneven.maximum());
    }

    @Test
    void evictsOldestSampleOnceTheWindowIsFull() {
        TickMetrics metrics = new TickMetrics(10);
        for (long nanos = 1; nanos <= 10; nanos++) {
            metrics.record(nanos);
        }
        assertEquals(55L, metrics.total());
        metrics.record(100);
        assertEquals(10, metrics.count());
        assertEquals(154L, metrics.total());
        assertEquals(6L, metrics.median());
        assertEquals(100L, metrics.percentile95());
        assertEquals(100L, metrics.maximum());
    }

    @Test
    void rejectsInvalidWindowSizesAndNegativeDurations() {
        assertThrows(IllegalArgumentException.class, () -> new TickMetrics(9));
        assertThrows(IllegalArgumentException.class, () -> new TickMetrics(100001));
        TickMetrics metrics = new TickMetrics(10);
        assertThrows(IllegalArgumentException.class, () -> metrics.record(-1L));
        assertEquals(0, metrics.count());
    }

    @Test
    void clearResetsEveryStatisticWhileKeepingCapacity() {
        TickMetrics metrics = new TickMetrics(10);
        metrics.record(25);
        metrics.record(35);
        metrics.clear();
        assertEquals(10, metrics.capacity());
        assertEquals(0, metrics.count());
        assertEquals(0L, metrics.total());
        assertEquals(0.0d, metrics.average());
        assertEquals(0L, metrics.median());
        assertEquals(0L, metrics.percentile95());
        assertEquals(0L, metrics.maximum());
        metrics.record(7);
        assertEquals(1, metrics.count());
        assertEquals(7L, metrics.maximum());
    }
}
