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

public final class RegionTaskCoordinator {
    public static final int DEFAULT_REGION_SIZE_CHUNKS = 8;

    private static final Logger LOGGER = LoggerFactory.getLogger(RegionTaskCoordinator.class);

    private final HyperCoreExecutor executor;
    private final int ownerCount;
    private final Map<RegionKey, List<RegionMessage>> pendingMessages = new HashMap<>();
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
        this.executor = Objects.requireNonNull(executor, "executor");
        if (ownerCount < 1) {
            throw new IllegalArgumentException("ownerCount must be positive");
        }
        this.ownerCount = ownerCount;
    }

    public RegionKey keyForChunk(String dimension, int chunkX, int chunkZ) {
        return RegionKey.fromChunk(dimension, chunkX, chunkZ, DEFAULT_REGION_SIZE_CHUNKS);
    }

    public int ownerFor(RegionKey region) {
        Objects.requireNonNull(region, "region");
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
                    return new OwnerResult(entry.getKey(), 0, 0, messages.size(), 0);
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
                    return new OwnerResult(owner, 0, 0, countMessages(workList), workList.size());
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
        return new OwnerResult(owner, messages.size(), failures, 0, 0);
    }

    private OwnerResult executeTickBatch(
        int owner,
        List<RegionTickWork> workList,
        RegionTickTask task,
        RegionExecutionService execution,
        long tickId
    ) {
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
        return new OwnerResult(owner, executedMessages, failures, 0, sorted.size());
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
        int tickedRegions
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
}
