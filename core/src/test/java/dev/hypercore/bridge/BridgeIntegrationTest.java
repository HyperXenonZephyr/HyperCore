package dev.hypercore.bridge;

import dev.hypercore.bridge.ipc.Packet;
import dev.hypercore.bridge.ipc.packet.OrderedDeltaBatchPacket;
import dev.hypercore.bridge.world.BlockDelta;
import dev.hypercore.bridge.world.EntityMoveDelta;
import dev.hypercore.bridge.world.EntitySpawnDelta;
import dev.hypercore.orchestrator.HyperCoreRole;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end bridge integration: two simulated hosts connect to the bridge
 * coordinator, complete the handshake, exchange heartbeats, and receive
 * conflict-resolved world deltas broadcast by the orchestrator.
 */
class BridgeIntegrationTest {
    private static final String READY_MARKER = "[test] BRIDGE READY";

    @Test
    void hostsHandshakeAndReceiveOrderedDeltas() throws Exception {
        int[] ports = freePorts();
        int forgePort = ports[0];
        int fabricPort = ports[1];

        BridgeCoordinator coordinator = new BridgeCoordinator(forgePort, fabricPort, 50);
        coordinator.start();
        try {
            List<OrderedDeltaBatchPacket> forgeReceived = new CopyOnWriteArrayList<>();
            List<OrderedDeltaBatchPacket> fabricReceived = new CopyOnWriteArrayList<>();
            List<Packet> forgeControl = new CopyOnWriteArrayList<>();

            BridgeEndpoint forge = endpoint(HyperCoreRole.FORGE_HOST, forgePort, forgeReceived, forgeControl);
            BridgeEndpoint fabric = endpoint(HyperCoreRole.FABRIC_HOST, fabricPort, fabricReceived, new CopyOnWriteArrayList<>());
            forge.start();
            fabric.start();

            try {
                awaitConnected(forge, fabric);

                // Fabric produces a block change and an entity move; the
                // orchestrator orders them and broadcasts to both hosts.
                UUID entityId = UUID.randomUUID();
                List<dev.hypercore.bridge.world.WorldDelta> deltas = List.of(
                    new BlockDelta("minecraft:overworld", 100, 64, -200, "STONE"),
                    new EntitySpawnDelta("minecraft:overworld", entityId, "ZOMBIE", 10, 64, 10),
                    new EntityMoveDelta("minecraft:overworld", entityId, 11, 64, 10)
                );
                assertTrue(fabric.send(new dev.hypercore.bridge.ipc.packet.WorldDeltaBatchPacket(HyperCoreRole.FABRIC_HOST, deltas)));

                // Only the Forge peer should receive the mirrored batch; Fabric
                // already applied the deltas at production time.
                awaitBatch(forgeReceived, 1);

                OrderedDeltaBatchPacket forgeBatch = forgeReceived.get(0);
                assertEquals(HyperCoreRole.FABRIC_HOST, forgeBatch.source());
                assertEquals(3, forgeBatch.deltas().size());
                // Entity deltas keep production order; block winners are appended.
                assertTrue(forgeBatch.deltas().get(0) instanceof EntitySpawnDelta);
                assertTrue(forgeBatch.deltas().get(1) instanceof EntityMoveDelta);
                assertTrue(forgeBatch.deltas().get(2) instanceof BlockDelta);
            } finally {
                forge.close();
                fabric.close();
            }
        } finally {
            coordinator.close();
        }
    }

    @Test
    void endpointsReportConnectionAndLatency() throws Exception {
        int[] ports = freePorts();
        BridgeCoordinator coordinator = new BridgeCoordinator(ports[0], ports[1], 25);
        coordinator.start();
        try {
            BridgeEndpoint forge = endpoint(
                HyperCoreRole.FORGE_HOST,
                ports[0],
                new CopyOnWriteArrayList<>(),
                new CopyOnWriteArrayList<>()
            );
            forge.start();
            try {
                long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
                while (!forge.isConnected() && System.nanoTime() < deadline) {
                    Thread.sleep(50);
                }
                assertTrue(forge.isConnected(), "forge endpoint should connect");
                // Heartbeats are exchanged every 25 ms; latency should be
                // reported shortly after connecting.
                deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
                while (forge.lastLatencyMillis() < 0 && System.nanoTime() < deadline) {
                    Thread.sleep(50);
                }
                assertTrue(forge.lastLatencyMillis() >= 0, "latency should be measured via heartbeat echo");
            } finally {
                forge.close();
            }
        } finally {
            coordinator.close();
        }
    }

    private static BridgeEndpoint endpoint(
        HyperCoreRole role,
        int port,
        List<OrderedDeltaBatchPacket> batches,
        List<Packet> control
    ) {
        return new BridgeEndpoint(
            role,
            "127.0.0.1",
            port,
            25,
            "1.21.1-test",
            "host-" + role.displayName(),
            READY_MARKER,
            packet -> {
                if (packet instanceof OrderedDeltaBatchPacket batch) {
                    batches.add(batch);
                } else {
                    control.add(packet);
                }
            }
        );
    }

    private static void awaitConnected(BridgeEndpoint... endpoints) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            boolean allConnected = true;
            for (BridgeEndpoint endpoint : endpoints) {
                if (!endpoint.isConnected()) {
                    allConnected = false;
                    break;
                }
            }
            if (allConnected) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Endpoints did not connect to the bridge coordinator");
    }

    private static void awaitBatch(List<OrderedDeltaBatchPacket> batches, int minimum) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (batches.size() < minimum && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        assertTrue(batches.size() >= minimum, "expected at least " + minimum + " batches, got " + batches.size());
    }

    private static int[] freePorts() throws IOException {
        int[] ports = new int[2];
        try (ServerSocket first = new ServerSocket(0)) {
            ports[0] = first.getLocalPort();
        }
        try (ServerSocket second = new ServerSocket(0)) {
            ports[1] = second.getLocalPort();
        }
        return ports;
    }
}
