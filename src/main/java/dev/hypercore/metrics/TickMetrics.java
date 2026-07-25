package dev.hypercore.metrics;

import java.util.Arrays;

public final class TickMetrics {
    private static final int WINDOW_SIZE = 200;

    private final long[] samples = new long[WINDOW_SIZE];
    private long tickStartedAt;
    private int cursor;
    private int sampleCount;

    public synchronized void beginTick() {
        tickStartedAt = System.nanoTime();
    }

    public synchronized void endTick() {
        if (tickStartedAt == 0L) {
            return;
        }
        samples[cursor] = System.nanoTime() - tickStartedAt;
        cursor = (cursor + 1) % samples.length;
        sampleCount = Math.min(sampleCount + 1, samples.length);
        tickStartedAt = 0L;
    }

    public synchronized Snapshot snapshot() {
        if (sampleCount == 0) {
            return new Snapshot(0, 0.0, 0.0, 0.0);
        }

        long[] ordered = Arrays.copyOf(samples, sampleCount);
        Arrays.sort(ordered);
        long total = 0L;
        for (long sample : ordered) {
            total += sample;
        }

        return new Snapshot(
            sampleCount,
            nanosToMillis((double) total / sampleCount),
            nanosToMillis(percentile(ordered, 0.95)),
            nanosToMillis(ordered[ordered.length - 1])
        );
    }

    private static long percentile(long[] ordered, double percentile) {
        int index = (int) Math.ceil(percentile * ordered.length) - 1;
        return ordered[Math.max(0, index)];
    }

    private static double nanosToMillis(double nanos) {
        return nanos / 1_000_000.0;
    }

    public record Snapshot(int samples, double averageMs, double p95Ms, double maximumMs) {
    }
}

