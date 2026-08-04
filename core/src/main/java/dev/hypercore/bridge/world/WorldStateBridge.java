package dev.hypercore.bridge.world;

import dev.hypercore.orchestrator.HyperCoreRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Owns the single logical world timeline of an orchestrated deployment.
 *
 * <p>Every mutation arriving from a host is queued as a {@link WorldDelta}.
 * Periodically (on the bridge tick or when flushed) the queued deltas from both
 * hosts are merged, ordered, and resolved by the {@link ConflictResolver}. The
 * surviving deltas are stamped with a monotonic sequence number and logical tick
 * and handed to the outbound callback, which the bridge wiring uses to broadcast
 * an ordered batch to both hosts.
 *
 * <p>The orchestrator stays stateless apart from sequence numbers and ownership
 * maps, so a crashed orchestrator can be restarted and the hosts resynchronized
 * from a saved world snapshot.
 */
public final class WorldStateBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorldStateBridge.class);

    private final AtomicLong logicalTick = new AtomicLong();
    private final AtomicLong nextSequence = new AtomicLong();
    private final Map<UUID, HyperCoreRole> entityOwners = new ConcurrentHashMap<>();
    private final Map<UUID, HyperCoreRole> playerOwners = new ConcurrentHashMap<>();
    private final ConflictResolver resolver = new ConflictResolver(entityOwners, playerOwners);
    private final List<ConflictResolver.SourcedDelta> pending = new ArrayList<>();
    private Consumer<OrderedBatch> outbound;

    /**
     * Registers the callback that receives resolved, ordered batches. The
     * callback is typically the bridge server's broadcast method.
     */
    public synchronized void setOutbound(Consumer<OrderedBatch> outbound) {
        this.outbound = Objects.requireNonNull(outbound, "outbound");
    }

    /**
     * Queues deltas reported by a host for the current bridge tick.
     *
     * @param source the host that produced the deltas
     * @param deltas the deltas in production order
     */
    public synchronized void submit(HyperCoreRole source, List<WorldDelta> deltas) {
        if (source != HyperCoreRole.FORGE_HOST && source != HyperCoreRole.FABRIC_HOST) {
            throw new IllegalArgumentException("World deltas must originate from a host role: " + source);
        }
        Objects.requireNonNull(deltas, "deltas");
        for (WorldDelta delta : deltas) {
            pending.add(new ConflictResolver.SourcedDelta(source, delta));
        }
    }

    /**
     * Resolves all queued deltas and broadcasts the surviving batch. Called by
     * the bridge tick loop or by tests.
     */
    public synchronized void flush() {
        if (pending.isEmpty()) {
            return;
        }
        List<ConflictResolver.SourcedDelta> batch = List.copyOf(pending);
        pending.clear();
        List<ConflictResolver.SourcedDelta> resolved = resolver.resolve(batch);
        if (resolved.isEmpty()) {
            return;
        }
        long tick = logicalTick.incrementAndGet();
        long firstSequence = nextSequence.getAndAdd(resolved.size());
        List<ResolvedDelta> stamped = new ArrayList<>(resolved.size());
        for (int index = 0; index < resolved.size(); index++) {
            ConflictResolver.SourcedDelta sourced = resolved.get(index);
            stamped.add(new ResolvedDelta(firstSequence + index, tick, sourced.source(), sourced.delta()));
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Broadcasting {} ordered delta(s) for logical tick {}", stamped.size(), tick);
        }
        Consumer<OrderedBatch> outbound = this.outbound;
        if (outbound == null) {
            LOGGER.warn("Deltas were resolved for logical tick {} but no outbound callback is registered", tick);
            return;
        }
        outbound.accept(new OrderedBatch(tick, stamped));
    }

    /**
     * Returns the number of deltas queued but not yet broadcast.
     */
    public synchronized int pendingCount() {
        return pending.size();
    }

    /**
     * Returns the current logical tick counter.
     */
    public long logicalTick() {
        return logicalTick.get();
    }

    /**
     * Returns the next sequence number without consuming it (diagnostics).
     */
    public long nextSequence() {
        return nextSequence.get();
    }

    /**
     * Returns the entity ownership map for diagnostics and replay.
     */
    public Map<UUID, HyperCoreRole> entityOwners() {
        return entityOwners;
    }

    /**
     * Returns the player ownership map for diagnostics and replay.
     */
    public Map<UUID, HyperCoreRole> playerOwners() {
        return playerOwners;
    }

    /**
     * Records the authoritative host for a player. Used by the player proxy so
     * join/quit announcements are reflected in conflict resolution.
     */
    public void setPlayerOwner(UUID playerId, HyperCoreRole owner) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(owner, "owner");
        playerOwners.put(playerId, owner);
    }

    /**
     * Removes ownership information for a player that has quit.
     */
    public void removePlayerOwner(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        playerOwners.remove(playerId);
    }

    /**
     * A delta stamped with its position in the logical timeline and the host
     * that produced it.
     */
    public record ResolvedDelta(long sequence, long logicalTick, HyperCoreRole source, WorldDelta delta) {
    }

    /**
     * An ordered batch of deltas that must be broadcast to both hosts.
     */
    public record OrderedBatch(long logicalTick, List<ResolvedDelta> deltas) {
    }
}
