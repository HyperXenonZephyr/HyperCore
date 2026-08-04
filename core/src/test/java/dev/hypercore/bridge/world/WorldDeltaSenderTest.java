package dev.hypercore.bridge.world;

import dev.hypercore.bridge.ipc.Packet;
import dev.hypercore.bridge.ipc.packet.WorldDeltaBatchPacket;
import dev.hypercore.orchestrator.HyperCoreRole;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the host-side delta sender batches deltas and ships them as a single
 * {@link WorldDeltaBatchPacket} per flush, dropping with a counter when the
 * bridge is unavailable.
 */
class WorldDeltaSenderTest {

    private static final class FakeLink implements BridgeLink {
        boolean connected;
        final List<Packet> sent = new ArrayList<>();

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public boolean send(Packet packet) {
            sent.add(packet);
            return true;
        }
    }

    @Test
    void batchesDeltasIntoOnePacketPerFlush() {
        FakeLink link = new FakeLink();
        link.connected = true;
        WorldDeltaSender sender = new WorldDeltaSender(HyperCoreRole.FORGE_HOST, link);

        sender.publish(new BlockDelta("w", 1, 2, 3, "STONE"));
        sender.publish(new BlockDelta("w", 4, 5, 6, "DIRT"));
        sender.flush();

        assertEquals(1, link.sent.size());
        WorldDeltaBatchPacket packet = (WorldDeltaBatchPacket) link.sent.get(0);
        assertEquals(HyperCoreRole.FORGE_HOST, packet.source());
        assertEquals(2, packet.deltas().size());

        // A flush with nothing pending sends nothing.
        sender.flush();
        assertEquals(1, link.sent.size());
        assertEquals(2, sender.publishedCount());
    }

    @Test
    void dropsDeltasWhenBridgeIsDown() {
        FakeLink link = new FakeLink();
        link.connected = false;
        WorldDeltaSender sender = new WorldDeltaSender(HyperCoreRole.FABRIC_HOST, link);

        sender.publish(new BlockDelta("w", 1, 2, 3, "STONE"));
        sender.flush();

        assertTrue(link.sent.isEmpty());
        assertEquals(1, sender.publishedCount());
        assertEquals(1, sender.droppedCount());
    }
}
