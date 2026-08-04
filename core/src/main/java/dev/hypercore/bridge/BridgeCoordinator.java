package dev.hypercore.bridge;

import dev.hypercore.bridge.ipc.packet.OrderedDeltaBatchPacket;
import dev.hypercore.bridge.world.WorldStateBridge;
import dev.hypercore.orchestrator.HyperCoreRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Wires the orchestrator-side bridge: the listener, the logical world timeline,
 * the flush loop, and the packet router.
 *
 * <p>Deltas submitted by hosts are resolved on every bridge tick and broadcast
 * back to both hosts, grouped by origin so each host can skip its own
 * already-applied mutations. Command and event packets are forwarded to the
 * other host.
 */
public final class BridgeCoordinator implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(BridgeCoordinator.class);

    private final OrchestratorBridgeServer server;
    private final WorldStateBridge worldBridge = new WorldStateBridge();
    private final BridgePacketRouter router;
    private final ScheduledExecutorService ticker;
    private final long bridgeTickMillis;
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * @param forgePort port the Forge host connects to
     * @param fabricPort port the Fabric host connects to
     * @param bridgeTickMillis interval between world bridge flushes
     */
    public BridgeCoordinator(int forgePort, int fabricPort, long bridgeTickMillis) {
        this.server = new OrchestratorBridgeServer(forgePort, fabricPort);
        this.router = new BridgePacketRouter(server, worldBridge);
        this.bridgeTickMillis = bridgeTickMillis;
        this.ticker = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "bridge-tick");
            thread.setDaemon(true);
            return thread;
        });
        this.worldBridge.setOutbound(this::broadcast);
        this.server.onPacket(router);
    }

    /**
     * Binds the listener and starts the flush loop.
     */
    public void start() {
        try {
            server.start();
        } catch (IOException error) {
            throw new IllegalStateException("Failed to start the orchestrator bridge", error);
        }
        ticker.scheduleWithFixedDelay(worldBridge::flush, bridgeTickMillis, bridgeTickMillis, TimeUnit.MILLISECONDS);
        LOGGER.info("Bridge coordinator started with a {} ms bridge tick", bridgeTickMillis);
    }

    /**
     * Returns the underlying bridge server (for status and diagnostics).
     */
    public OrchestratorBridgeServer server() {
        return server;
    }

    /**
     * Returns the logical world timeline.
     */
    public WorldStateBridge worldBridge() {
        return worldBridge;
    }

    /**
     * Returns the packet router (for diagnostics).
     */
    public BridgePacketRouter router() {
        return router;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        ticker.shutdownNow();
        try {
            if (!ticker.awaitTermination(5, TimeUnit.SECONDS)) {
                LOGGER.warn("Bridge ticker did not terminate promptly");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
        server.close();
        LOGGER.info("Bridge coordinator stopped");
    }

    private void broadcast(WorldStateBridge.OrderedBatch batch) {
        Map<HyperCoreRole, List<WorldStateBridge.ResolvedDelta>> bySource =
            batch.deltas().stream().collect(Collectors.groupingBy(WorldStateBridge.ResolvedDelta::source));
        for (Map.Entry<HyperCoreRole, List<WorldStateBridge.ResolvedDelta>> entry : bySource.entrySet()) {
            HyperCoreRole source = entry.getKey();
            List<WorldStateBridge.ResolvedDelta> deltas = entry.getValue();
            OrderedDeltaBatchPacket packet = new OrderedDeltaBatchPacket(
                batch.logicalTick(),
                source,
                deltas.get(0).sequence(),
                deltas.stream().map(WorldStateBridge.ResolvedDelta::delta).toList()
            );
            // Each host already applied its own deltas at production time; only
            // the peer needs to mirror them.
            HyperCoreRole peer = peerOf(source);
            if (!server.sendTo(peer, packet)) {
                LOGGER.debug("Ordered batch for logical tick {} could not be delivered to {}", batch.logicalTick(), peer.displayName());
            }
        }
    }

    private static HyperCoreRole peerOf(HyperCoreRole role) {
        return role == HyperCoreRole.FORGE_HOST ? HyperCoreRole.FABRIC_HOST : HyperCoreRole.FORGE_HOST;
    }
}
