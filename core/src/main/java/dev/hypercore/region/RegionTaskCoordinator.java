package dev.hypercore.region;

import org.slf4j.LoggerFactory;
import dev.hypercore.concurrent.HyperCoreExecutor;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
            return CompletableFuture.completedFuture(finish(plan, List.of()));
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
                    return new OwnerResult(entry.getKey(), 0, 0, messages.size());
                }));
        }

        CompletableFuture<Void> allOwners = CompletableFuture.allOf(
            ownerFutures.toArray(CompletableFuture[]::new)
        );
        return allOwners.thenApply(ignored -> finish(
            plan,
            ownerFutures.stream().map(CompletableFuture::join).toList()
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
        return new OwnerResult(owner, messages.size(), failures, 0);
    }

    private synchronized void requeue(List<RegionMessage> messages) {
        for (RegionMessage message : messages) {
            pendingMessages.computeIfAbsent(message.target(), ignored -> new ArrayList<>()).add(message);
        }
    }

    private synchronized TickResult finish(TickPlan plan, List<OwnerResult> ownerResults) {
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
            plan.messages().stream().map(RegionMessage::target).distinct().count(),
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

    private record OwnerResult(
        int owner,
        int executedMessages,
        int failedMessages,
        int requeuedMessages
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
