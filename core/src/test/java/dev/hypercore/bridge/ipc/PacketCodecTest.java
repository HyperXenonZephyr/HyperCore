package dev.hypercore.bridge.ipc;

import dev.hypercore.bridge.ipc.packet.AckPacket;
import dev.hypercore.bridge.ipc.packet.CommandExecutePacket;
import dev.hypercore.bridge.ipc.packet.CommandExecuteResultPacket;
import dev.hypercore.bridge.ipc.packet.CommandRegistrySnapshotPacket;
import dev.hypercore.bridge.ipc.packet.EventPacket;
import dev.hypercore.bridge.ipc.packet.HandshakePacket;
import dev.hypercore.bridge.ipc.packet.HeartbeatPacket;
import dev.hypercore.bridge.ipc.packet.OrderedDeltaBatchPacket;
import dev.hypercore.bridge.ipc.packet.WorldDeltaBatchPacket;
import dev.hypercore.bridge.world.BlockDelta;
import dev.hypercore.bridge.world.EntityMoveDelta;
import dev.hypercore.bridge.world.EntitySpawnDelta;
import dev.hypercore.bridge.world.PlayerInventoryDelta;
import dev.hypercore.bridge.world.PlayerStateDelta;
import dev.hypercore.orchestrator.HyperCoreRole;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies the length-prefixed binary codec and the packet-type registry.
 */
class PacketCodecTest {

    private static Packet roundTrip(Packet packet) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PacketCodec.writeFrame(new DataOutputStream(buffer), packet);
        byte[] frame = buffer.toByteArray();
        return PacketCodec.readFrame(new DataInputStream(new ByteArrayInputStream(frame)));
    }

    @Test
    void roundTripsEveryPacketType() throws IOException {
        UUID id = UUID.randomUUID();
        List.of(
            new HandshakePacket(1, HyperCoreRole.FORGE_HOST, "1.21.1", "test-forge", 0),
            new HandshakePacket(1, HyperCoreRole.FABRIC_HOST, "1.21.1", "test-fabric", 1),
            new HeartbeatPacket(42, 1_234_567_890L),
            new AckPacket(7),
            new WorldDeltaBatchPacket(HyperCoreRole.FORGE_HOST, List.of(
                new BlockDelta("minecraft:overworld", 1, 2, 3, "STONE"),
                new EntitySpawnDelta("minecraft:overworld", id, "ZOMBIE", 1.5, 2.5, 3.5)
            )),
            new OrderedDeltaBatchPacket(11, HyperCoreRole.FABRIC_HOST, 100, List.of(
                new EntityMoveDelta("minecraft:overworld", id, 4.0, 5.0, 6.0)
            )),
            new CommandRegistrySnapshotPacket(List.of(
                new CommandRegistrySnapshotPacket.CommandDescriptor("hello", List.of("hi"), "", "Greets", "/hello", "demo")
            )),
            new CommandExecutePacket(99, "hello", List.of("world"), "Steve", true, false),
            new CommandExecuteResultPacket(99, true, "Hello world"),
            new EventPacket("BlockBreakEvent", true, "world=minecraft:overworld")
        ).forEach(packet -> {
            try {
                Packet decoded = roundTrip(packet);
                assertEquals(packet.getClass(), decoded.getClass());
                assertEquals(packet, decoded);
            } catch (IOException error) {
                throw new AssertionError("Round trip failed for " + packet.getClass().getSimpleName(), error);
            }
        });
    }

    @Test
    void roundTripsPlayerDeltas() throws IOException {
        UUID player = UUID.randomUUID();
        Packet packet = new WorldDeltaBatchPacket(HyperCoreRole.FABRIC_HOST, List.of(
            new PlayerStateDelta("minecraft:overworld", player, 19.5, 10.0, 64.0, 11.0),
            new PlayerInventoryDelta("minecraft:overworld", player, 0, "STONE", 32)
        ));
        WorldDeltaBatchPacket decoded = (WorldDeltaBatchPacket) roundTrip(packet);
        assertEquals(packet, decoded);
    }

    @Test
    void emptyBatchesRoundTrip() throws IOException {
        Packet packet = new WorldDeltaBatchPacket(HyperCoreRole.FORGE_HOST, List.of());
        assertEquals(packet, roundTrip(packet));
    }

    @Test
    void readFrameReturnsNullOnCleanEof() throws IOException {
        assertNull(PacketCodec.readFrame(new DataInputStream(new ByteArrayInputStream(new byte[0]))));
    }

    @Test
    void rejectsOversizedFrames() {
        byte[] frame = new byte[5];
        frame[0] = (byte) 0x7F;
        assertThrows(IOException.class, () -> PacketCodec.decode(frame));
    }
}
