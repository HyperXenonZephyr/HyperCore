package dev.hypercore.region;

import dev.hypercore.concurrent.HyperCoreExecutor;
import dev.hypercore.world.RegionExecutionService;
import dev.hypercore.world.RegionTickTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class RegionTaskCoordinator {
    public static final int DEFAULT_REGION_SIZE_CHUNKS = 8;

    private static final Logger LOGGER = LoggerFactory.getLogger(RegionTaskCoordinator.class);

    /**
     * Interval (in finished ticks) between adaptive remap checks. Prevents the
     * mapping from thrashing on short-term timing noise.
     */
    private static final int REMAP_CHECK_INTERVAL_TICKS = 32;

    private final HyperCoreExecutor executor;
    private final int ownerCount;
    private final boolean adaptiveScheduling;
    private final OwnerLoadTracker loadTracker;
    private final Map<RegionKey, List<RegionMessage>> pendingMessages = new HashMap<>();
    /**
     * Live region-to-owner mapping. Reads happen from the tick-driving thread
     * (and occasionally from callers of {@link #ownerFor(RegionKey)}). Writes
     * happen in two places: a cache miss populates the current map via
     * {@code computeIfAbsent}, and {@link #maybeRemap(Collection)} publishes a
     * fresh {@link ConcurrentHashMap} between ticks. The {@code volatile}
     * reference makes the swap visible without holding any monitor.
     */
    private volatile Map<RegionKey, Integer> ownerMap = new ConcurrentHashMap<>();
    private volatile long tickCounter;
    private long nextMessageSequence;
    private long nextTickId;
    private long submittedMessages;
    private long executedMessages;
    private long failedMessages;
    private long crossRegionMessages;
    private long finishedTicks;
    private long partialTicks;
    private boolean tickInFlight;

    public RegionTaskCoordinator(HyperCoreExecutor executor, int ownerCount) {
        this(executor, ownerCount, true);
    }

    /**
     * Constructs a coordinator with explicit control over adaptive scheduling.
     *
     * @param adaptiveScheduling when {@code true}, per-owner tick timings drive
     *                           periodic remapping of regions across owners;
     *                           when {@code false}, behavior is identical to
     *                           the original static hash assignment
     */
    public RegionTaskCoordinator(HyperCoreExecutor executor, int ownerCount, boolean adaptiveScheduling) {
        this.executor = Objects.requireNonNull(executor, "executor");
        if (ownerCount < 1) {
            throw new IllegalArgumentException("ownerCount must be positive");
        }
        this.ownerCount = ownerCount;
        this.adaptiveScheduling = adaptiveScheduling;
        this.loadTracker = new OwnerLoadTracker();
    }

    public RegionKey keyForChunk(String dimension, int chunkX, int chunkZ) {
        return RegionKey.fromChunk(dimension, chunkX, chunkZ, DEFAULT_REGION_SIZE_CHUNKS);
    }

    public int ownerFor(RegionKey region) {
        Objects.requireNonNull(region, "region");
        if (!adaptiveScheduling) {
            return staticHashFor(region);
        }
        return ownerMap.computeIfAbsent(region, this::staticHashFor);
    }

    /**
     * Original static hash-based owner assignment. Used as the fallback when a
     * region is absent from {@link #ownerMap} and as the entire assignment
     * strategy when adaptive scheduling is disabled.
     */
    private int staticHashFor(RegionKey region) {
        int hash = 31 * (31 * region.dimension().hashCode() + region.regionX()) + region.regionZ();
        return Math.floorMod(hash, ownerCount);
    }

    public synchronized void send(RegionKey source, RegionKey target, Runnable task) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(task, "task");

        RegionMessage message = new RegionMessage(nextMessageSequence++, source, target, task);
        pendingMessages.computeIfAbsent(target, ignored -> new ArrayList<>()).add(message);
        submittedMessages++;
        if (!source.equals(target)) {
            crossRegionMessages++;
        }
    }

    public CompletableFuture<TickResult> advanceTick() {
        TickPlan plan;
        synchronized (this) {
            if (tickInFlight) {
                throw new IllegalStateException("A region tick is already in flight");
            }
            plan = claimPendingMessages();
        }
        return execute(plan);
    }

    public Optional<CompletableFuture<TickResult>> dispatchPendingTick() {
        TickPlan plan;
        synchronized (this) {
            if (tickInFlight || pendingMessages.isEmpty()) {
                return Optional.empty();
            }
            plan = claimPendingMessages();
        }
        return Optional.of(execute(plan));
    }

    /**
     * Runs a full region tick: every active region is locked and ticked, then any
     * pending messages targeting that region are executed while the lock is still
     * held. Regions are grouped by owner and processed in parallel across the
     * HyperCore worker pool.
     *
     * @param activeRegions the regions that need ticking
     * @param task the per-region work to run under each region lock
     * @param execution the execution service that provides region locks
     */
    public CompletableFuture<TickResult> advanceTick(
        Collection<RegionKey> activeRegions,
        RegionTickTask task,
        RegionExecutionService execution
    ) {
        Objects.requireNonNull(activeRegions, "activeRegions");
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(execution, "execution");

        if (adaptiveScheduling) {
            maybeRemap(activeRegions);
        }

        TickPlan plan;
        synchronized (this) {
            if (tickInFlight) {
                throw new IllegalStateException("A region tick is already in flight");
            }
            plan = claimPendingMessages();
        }
        return execute(plan, activeRegions, task, execution);
    }

    public synchronized Status status() {
        return new Status(
            ownerCount,
            queuedMessageCount(),
            tickInFlight,
            submittedMessages,
            executedMessages,
            failedMessages,
            crossRegionMessages,
            finishedTicks,
            partialTicks
        );
    }

    /**
     * Rebalances the region-to-owner mapping between ticks. Runs before any
     * region lock is acquired and without holding the coordinator monitor, so
     * it cannot interfere with an in-flight tick or deadlock. The new mapping
     * is built as a fresh {@link ConcurrentHashMap} (seeded with the current
     * cache plus the suggested remap) and published by a single {@code volatile}
     * write to {@link #ownerMap}.
     */
    private void maybeRemap(Collection<RegionKey> activeRegions) {
        long tick = tickCounter;
        if (tick == 0L || tick % REMAP_CHECK_INTERVAL_TICKS != 0L) {
            return;
        }
        List<RegionKey> snapshot = List.copyOf(activeRegions);
        Map<RegionKey, Integer> remap = loadTracker.suggestRemap(snapshot, ownerCount);
        if (remap == null || remap.isEmpty()) {
            return;
        }
        Map<RegionKey, Integer> current = ownerMap;
        ConcurrentHashMap<RegionKey, Integer> next = new ConcurrentHashMap<>(current);
        next.putAll(remap);
        ownerMap = next;
        LOGGER.debug("Adaptive region scheduling remapped {} region(s)", remap.size());
    }

    private synchronized TickPlan claimPendingMessages() {
        long tickId = nextTickId++;
        List<RegionMessage> messages = pendingMessages.values().stream()
            .flatMap(List::stream)
            .sorted()
            .toList();
        pendingMessages.clear();
        tickInFlight = true;
        return new TickPlan(tickId, messages);
    }

    private CompletableFuture<TickResult> execute(TickPlan plan) {
        if (plan.messages().isEmpty()) {
            return CompletableFuture.completedFuture(finish(plan, List.of(), 0));
        }

        Map<Integer, List<RegionMessage>> messagesByOwner = new HashMap<>();
        for (RegionMessage message : plan.messages()) {
            messagesByOwner.computeIfAbsent(ownerFor(message.target()), ignored -> new ArrayList<>())
                .add(message);
        }

        List<CompletableFuture<OwnerResult>> ownerFutures = new ArrayList<>();
        for (Map.Entry<Integer, List<RegionMessage>> entry : messagesByOwner.entrySet()) {
            List<RegionMessage> messages = List.copyOf(entry.getValue());
            ownerFutures.add(executor.submit(() -> executeOwnerBatch(entry.getKey(), messages))
                .handle((result, error) -> {
                    if (error == null) {
                        return result;
                    }
                    requeue(messages);
                    return new OwnerResult(entry.getKey(), 0, 0, messages.size(), 0, 0L);
                }));
        }

        int distinctTargets = (int) plan.messages().stream().map(RegionMessage::target).distinct().count();
        CompletableFuture<Void> allOwners = CompletableFuture.allOf(
            ownerFutures.toArray(CompletableFuture[]::new)
        );
        return allOwners.thenApply(ignored -> finish(
            plan,
            ownerFutures.stream().map(CompletableFuture::join).toList(),
            distinctTargets
        ));
    }

    private CompletableFuture<TickResult> execute(
        TickPlan plan,
        Collection<RegionKey> activeRegions,
        RegionTickTask task,
        RegionExecutionService execution
    ) {
        Map<RegionKey, List<RegionMessage>> messagesByRegion = new HashMap<>();
        for (RegionMessage message : plan.messages()) {
            messagesByRegion.computeIfAbsent(message.target(), ignored -> new ArrayList<>()).add(message);
        }

        Set<RegionKey> allRegions = Set.copyOf(activeRegions);
        allRegions.forEach(region -> messagesByRegion.computeIfAbsent(region, ignored -> new ArrayList<>()));

        if (messagesByRegion.isEmpty()) {
            return CompletableFuture.completedFuture(finish(plan, List.of(), 0));
        }

        Map<Integer, List<RegionTickWork>> workByOwner = new HashMap<>();
        for (Map.Entry<RegionKey, List<RegionMessage>> entry : messagesByRegion.entrySet()) {
            RegionKey region = entry.getKey();
            List<RegionMessage> messages = List.copyOf(entry.getValue());
            workByOwner.computeIfAbsent(ownerFor(region), ignored -> new ArrayList<>())
                .add(new RegionTickWork(region, messages));
        }

        List<CompletableFuture<OwnerResult>> ownerFutures = new ArrayList<>();
        for (Map.Entry<Integer, List<RegionTickWork>> entry : workByOwner.entrySet()) {
            int owner = entry.getKey();
            List<RegionTickWork> workList = List.copyOf(entry.getValue());
            ownerFutures.add(executor.submit(() -> executeTickBatch(owner, workList, task, execution, plan.tickId()))
                .handle((result, error) -> {
                    if (error == null) {
                        return result;
                    }
                    for (RegionTickWork work : workList) {
                        requeue(work.messages());
                    }
                    return new OwnerResult(owner, 0, 0, countMessages(workList), workList.size(), 0L);
                }));
        }

        int allRegionCount = messagesByRegion.size();
        CompletableFuture<Void> allOwners = CompletableFuture.allOf(
            ownerFutures.toArray(CompletableFuture[]::new)
        );
        return allOwners.thenApply(ignored -> finish(
            plan,
            ownerFutures.stream().map(CompletableFuture::join).toList(),
            allRegionCount
        ));
    }

    private OwnerResult executeOwnerBatch(int owner, List<RegionMessage> messages) {
        int failures = 0;
        for (RegionMessage message : messages) {
            try {
                message.task().run();
            } catch (Throwable error) {
                failures++;
                LOGGER.error(
                    "Region owner {} failed message {} from {} to {}",
                    owner,
                    message.sequence(),
                    message.source(),
                    message.target(),
                    error
                );
            }
        }
        return new OwnerResult(owner, messages.size(), failures, 0, 0, 0L);
    }

    private OwnerResult executeTickBatch(
        int owner,
        List<RegionTickWork> workList,
        RegionTickTask task,
        RegionExecutionService execution,
        long tickId
    ) {
        long batchStart = adaptiveScheduling ? System.nanoTime() : 0L;
        int failures = 0;
        int executedMessages = 0;
        List<RegionTickWork> sorted = workList.stream()
            .sorted(java.util.Comparator.comparing(work -> work.region()))
            .toList();

        for (RegionTickWork work : sorted) {
            try {
                execution.lockFor(work.region()).write(() -> {
                    task.tick(execution, work.region(), tickId);
                    for (RegionMessage message : work.messages()) {
                        message.task().run();
                    }
                });
                executedMessages += work.messages().size();
            } catch (Throwable error) {
                failures++;
                LOGGER.error(
                    "Region owner {} failed to tick region {}",
                    owner,
                    work.region(),
                    error
                );
            }
        }
        long elapsed = adaptiveScheduling ? System.nanoTime() - batchStart : 0L;
        return new OwnerResult(owner, executedMessages, failures, 0, sorted.size(), elapsed);
    }

    private int countMessages(List<RegionTickWork> workList) {
        return workList.stream().mapToInt(work -> work.messages().size()).sum();
    }

    private synchronized void requeue(List<RegionMessage> messages) {
        for (RegionMessage message : messages) {
            pendingMessages.computeIfAbsent(message.target(), ignored -> new ArrayList<>()).add(message);
        }
    }

    private synchronized TickResult finish(TickPlan plan, List<OwnerResult> ownerResults, long targetRegions) {
        int executed = ownerResults.stream().mapToInt(OwnerResult::executedMessages).sum();
        int failures = ownerResults.stream().mapToInt(OwnerResult::failedMessages).sum();
        int requeued = ownerResults.stream().mapToInt(OwnerResult::requeuedMessages).sum();
        executedMessages += executed;
        failedMessages += failures;
        if (adaptiveScheduling) {
            for (OwnerResult result : ownerResults) {
                if (result.ownerNanos() > 0L) {
                    loadTracker.recordOwnerTiming(result.owner(), result.ownerNanos());
                }
            }
            tickCounter++;
        }
        finishedTicks++;
        if (failures > 0 || requeued > 0) {
            partialTicks++;
        }
        tickInFlight = false;
        return new TickResult(
            plan.tickId(),
            plan.messages().size(),
            targetRegions,
            ownerResults.size(),
            executed,
            failures,
            requeued
        );
    }

    private int queuedMessageCount() {
        return pendingMessages.values().stream().mapToInt(List::size).sum();
    }

    private record TickPlan(long tickId, List<RegionMessage> messages) {
    }

    private record RegionMessage(
        long sequence,
        RegionKey source,
        RegionKey target,
        Runnable task
    ) implements Comparable<RegionMessage> {
        @Override
        public int compareTo(RegionMessage other) {
            int targetOrder = target.compareTo(other.target);
            return targetOrder != 0 ? targetOrder : Long.compare(sequence, other.sequence);
        }
    }

    private record RegionTickWork(RegionKey region, List<RegionMessage> messages) {
    }

    private record OwnerResult(
        int owner,
        int executedMessages,
        int failedMessages,
        int requeuedMessages,
        int tickedRegions,
        long ownerNanos
    ) {
    }

    public record TickResult(
        long tickId,
        int submittedMessages,
        long targetRegions,
        int ownersUsed,
        int executedMessages,
        int failedMessages,
        int requeuedMessages
    ) {
        public boolean complete() {
            return failedMessages == 0 && requeuedMessages == 0;
        }
    }

    public record Status(
        int owners,
        int queuedMessages,
        boolean tickInFlight,
        long submittedMessages,
        long executedMessages,
        long failedMessages,
        long crossRegionMessages,
        long finishedTicks,
        long partialTicks
    ) {
    }

    /**
     * Rolling per-owner timing sampler used by adaptive scheduling. Each owner
     * keeps a small ring buffer of recent tick timings; the coordinator records
     * one sample per finished tick. {@link #imbalanceRatio()} and
     * {@link #suggestRemap(List, int)} are consulted between ticks to decide
     * whether any region should migrate from a slow owner to a fast one.
     *
     * <p>All mutating reads are {@code synchronized} on the tracker itself.
     * This monitor is never held while acquiring the coordinator monitor or any
     * region lock, so it cannot participate in a deadlock.
     */
    private final class OwnerLoadTracker {
        private static final int WINDOW_SIZE = 64;
        private static final float IMBALANCE_THRESHOLD = 1.5f;

        private final long[][] windows;
        private final int[] cursors;
        private final int[] counts;

        OwnerLoadTracker() {
            this.windows = new long[ownerCount][WINDOW_SIZE];
            this.cursors = new int[ownerCount];
            this.counts = new int[ownerCount];
        }

        /**
         * Records the wall-clock time an owner spent processing its regions in
         * a single tick.
         */
        synchronized void recordOwnerTiming(int owner, long nanos) {
            if (owner < 0 || owner >= ownerCount || nanos <= 0L) {
                return;
            }
            windows[owner][cursors[owner]] = nanos;
            cursors[owner] = (cursors[owner] + 1) % WINDOW_SIZE;
            counts[owner] = Math.min(counts[owner] + 1, WINDOW_SIZE);
        }

        /**
         * Returns {@code max(ownerAvg) / min(ownerAvg)} over owners that have
         * samples. Owners without samples are ignored. If no owner has samples
         * the workload is treated as balanced ({@code 1.0f}); if the smallest
         * average is zero the ratio is reported as {@link Float#MAX_VALUE} to
         * flag the imbalance.
         */
        synchronized float imbalanceRatio() {
            double maxAvg = 0.0;
            double minAvg = Double.POSITIVE_INFINITY;
            boolean any = false;
            for (int owner = 0; owner < ownerCount; owner++) {
                int count = counts[owner];
                if (count == 0) {
                    continue;
                }
                long sum = 0L;
                for (int i = 0; i < count; i++) {
                    sum += windows[owner][i];
                }
                double avg = (double) sum / count;
                any = true;
                if (avg > maxAvg) {
                    maxAvg = avg;
                }
                if (avg < minAvg) {
                    minAvg = avg;
                }
            }
            if (!any) {
                return 1.0f;
            }
            if (minAvg <= 0.0) {
                return Float.MAX_VALUE;
            }
            return (float) (maxAvg / minAvg);
        }

        /**
         * Builds a remapping that migrates roughly half of the slowest owner's
         * regions to the fastest owner. Returns {@code null} when the workload
         * is balanced enough, when there is not enough data, or when nothing
         * can be moved.
         *
         * @param activeRegions regions ticked in the most recent tick
         * @param owners        owner count (must match the coordinator's)
         * @return an immutable map of regions to their new owner, or
         *         {@code null} when no remap is needed
         */
        synchronized Map<RegionKey, Integer> suggestRemap(List<RegionKey> activeRegions, int owners) {
            if (activeRegions == null || activeRegions.isEmpty() || owners != ownerCount) {
                return null;
            }
            double maxAvg = 0.0;
            double minAvg = Double.POSITIVE_INFINITY;
            int slowest = -1;
            int fastest = -1;
            for (int owner = 0; owner < ownerCount; owner++) {
                int count = counts[owner];
                if (count == 0) {
                    continue;
                }
                long sum = 0L;
                for (int i = 0; i < count; i++) {
                    sum += windows[owner][i];
                }
                double avg = (double) sum / count;
                if (avg > maxAvg) {
                    maxAvg = avg;
                    slowest = owner;
                }
                if (avg < minAvg) {
                    minAvg = avg;
                    fastest = owner;
                }
            }
            if (slowest < 0 || fastest < 0 || slowest == fastest) {
                return null;
            }
            double ratio = (minAvg <= 0.0) ? Double.POSITIVE_INFINITY : (maxAvg / minAvg);
            if (ratio < IMBALANCE_THRESHOLD) {
                return null;
            }
            List<RegionKey> onSlowest = new ArrayList<>();
            for (RegionKey region : activeRegions) {
                if (ownerFor(region) == slowest) {
                    onSlowest.add(region);
                }
            }
            if (onSlowest.size() < 2) {
                return null;
            }
            onSlowest.sort(null);
            int moveCount = Math.max(1, onSlowest.size() / 2);
            Map<RegionKey, Integer> remap = new HashMap<>();
            for (int i = 0; i < moveCount; i++) {
                remap.put(onSlowest.get(i), fastest);
            }
            return Map.copyOf(remap);
        }
    }
}
