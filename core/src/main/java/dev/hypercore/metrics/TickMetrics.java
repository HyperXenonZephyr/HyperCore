package dev.hypercore.metrics;

import java.util.Arrays;

/**
 * Rolling-window tick latency sampler with two measurement scopes.
 *
 * <p>{@code beginTick} / {@code endTick} measure the Minecraft server tick
 * body (the part between {@code ServerTickEvent} START and END). The full-tick
 * scope ({@code endFullTick}) additionally covers the region-parallel tick,
 * pending mutation flush, and bridge delta ship that run after {@code endTick}
 * in the loader adapters. Both scopes share the same {@code beginTick} anchor.
 */
public final class TickMetrics {
    private final long[] samples;
    private final long[] fullSamples;
    private long tickStartedAt;
    private int cursor;
    private int fullCursor;
    private int sampleCount;
    private int fullSampleCount;

    public TickMetrics() {
        this(200);
    }

    public TickMetrics(int windowSize) {
        if (windowSize < 1) {
            throw new IllegalArgumentException("windowSize must be positive");
        }
        this.samples = new long[windowSize];
        this.fullSamples = new long[windowSize];
    }

    public int windowSize() {
        return samples.length;
    }

    /**
     * Records the start of the server tick. Both {@code endTick} and
     * {@code endFullTick} measure from this anchor.
     */
    public synchronized void beginTick() {
        tickStartedAt = System.nanoTime();
    }

    /**
     * Records the end of the Minecraft server tick body (before region tick
     * and bridge flush). This is the narrower MSPT scope.
     */
    public synchronized void endTick() {
        if (tickStartedAt == 0L) {
            return;
        }
        samples[cursor] = System.nanoTime() - tickStartedAt;
        cursor = (cursor + 1) % samples.length;
        sampleCount = Math.min(sampleCount + 1, samples.length);
    }

    /**
     * Records the end of the full tick including region-parallel execution,
     * mutation flush, and bridge delta ship. Must be called after
     * {@code endTick} within the same tick. Does not reset the begin anchor
     * (that is done here since this is the true tick end).
     */
    public synchronized void endFullTick() {
        if (tickStartedAt == 0L) {
            return;
        }
        fullSamples[fullCursor] = System.nanoTime() - tickStartedAt;
        fullCursor = (fullCursor + 1) % fullSamples.length;
        fullSampleCount = Math.min(fullSampleCount + 1, fullSamples.length);
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

    /**
     * Returns the full-tick snapshot covering server tick + region tick +
     * bridge flush.
     */
    public synchronized Snapshot fullSnapshot() {
        if (fullSampleCount == 0) {
            return new Snapshot(0, 0.0, 0.0, 0.0);
        }

        long[] ordered = Arrays.copyOf(fullSamples, fullSampleCount);
        Arrays.sort(ordered);
        long total = 0L;
        for (long sample : ordered) {
            total += sample;
        }

        return new Snapshot(
            fullSampleCount,
            nanosToMillis((double) total / fullSampleCount),
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
