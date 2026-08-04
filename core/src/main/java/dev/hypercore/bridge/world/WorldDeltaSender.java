package dev.hypercore.bridge.world;

import dev.hypercore.bridge.BridgeEndpoint;
import dev.hypercore.bridge.ipc.packet.WorldDeltaBatchPacket;
import dev.hypercore.orchestrator.HyperCoreRole;
import dev.hypercore.world.DeltaSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Host-side delta sender.
 *
 * <p>Collects deltas published by the {@code RegionExecutionService} and ships
 * them to the orchestrator as one {@link WorldDeltaBatchPacket} per flush. The
 * loader adapter calls {@link #flush()} on every server tick so mutations reach
 * the orchestrator within one bridge tick. If the bridge is down, deltas are
 * dropped (with a counter) rather than buffered indefinitely; hosts that
 * reconnect are expected to resynchronize through a world snapshot.
 */
public final class WorldDeltaSender implements DeltaSink {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorldDeltaSender.class);

    private final HyperCoreRole role;
    private final BridgeLink link;
    private final List<WorldDelta> pending = new ArrayList<>();
    private final AtomicLong published = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();

    /**
     * @param role this host's role
     * @param link the bridge connection used to reach the orchestrator
     */
    public WorldDeltaSender(HyperCoreRole role, BridgeLink link) {
        this.role = Objects.requireNonNull(role, "role");
        this.link = Objects.requireNonNull(link, "link");
    }

    @Override
    public synchronized void publish(WorldDelta delta) {
        Objects.requireNonNull(delta, "delta");
        pending.add(delta);
        published.incrementAndGet();
    }

    /**
     * Sends all collected deltas to the orchestrator as one batch. Called once
     * per server tick in bridge mode.
     */
    public synchronized void flush() {
        if (pending.isEmpty()) {
            return;
        }
        List<WorldDelta> batch = List.copyOf(pending);
        pending.clear();
        if (!link.isConnected()) {
            dropped.addAndGet(batch.size());
            LOGGER.warn("Dropping {} world delta(s): bridge to orchestrator is not connected", batch.size());
            return;
        }
        if (!link.send(new WorldDeltaBatchPacket(role, batch))) {
            dropped.addAndGet(batch.size());
            LOGGER.warn("Failed to send {} world delta(s) to orchestrator", batch.size());
        }
    }

    /**
     * Returns the total number of deltas published since creation.
     */
    public long publishedCount() {
        return published.get();
    }

    /**
     * Returns the number of deltas that could not be delivered because the
     * bridge was unavailable.
     */
    public long droppedCount() {
        return dropped.get();
    }
}
