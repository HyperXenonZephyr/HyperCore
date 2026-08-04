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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;

/**
 * Length-prefixed binary codec with a packet-type registry.
 *
 * <p>Frame layout on the wire:
 * <pre>
 *   [4-byte big-endian length][1-byte type id][payload]
 * </pre>
 * The length covers the type byte and the payload, so a peer can read a frame
 * with a single {@code readFully}. Payloads are capped at {@value #MAX_FRAME_SIZE}
 * bytes to bound memory usage. Unknown type ids are rejected with
 * {@link IOException}.
 */
public final class PacketCodec {
    public static final int PROTOCOL_VERSION = 1;
    public static final int MAX_FRAME_SIZE = 1 << 20;

    private PacketCodec() {
    }

    /**
     * Encodes a packet into a self-contained frame (length + type + payload).
     */
    public static byte[] encode(Packet packet) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(buffer)) {
            out.writeByte(packet.typeId());
            packet.write(out);
        }
        byte[] frame = buffer.toByteArray();
        if (frame.length > MAX_FRAME_SIZE) {
            throw new IOException(
                "Packet " + packet.getClass().getSimpleName() + " exceeds frame limit of "
                    + MAX_FRAME_SIZE + " bytes: " + frame.length
            );
        }
        ByteArrayOutputStream framed = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(framed)) {
            out.writeInt(frame.length);
            out.write(frame);
        }
        return framed.toByteArray();
    }

    /**
     * Decodes a packet from a single frame (length prefix included).
     */
    public static Packet decode(byte[] frame) throws IOException {
        if (frame.length < 4) {
            throw new IOException("Frame is shorter than its length prefix");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(frame))) {
            int length = in.readInt();
            if (length < 1 || length > MAX_FRAME_SIZE) {
                throw new IOException("Invalid frame length: " + length);
            }
            if (length != frame.length - 4) {
                throw new IOException(
                    "Frame length mismatch: declared " + length + " but received " + (frame.length - 4)
                );
            }
            int typeId = in.readUnsignedByte();
            return read(in, typeId);
        }
    }

    /**
     * Writes a frame (length + type + payload) to the given output stream.
     */
    public static void writeFrame(DataOutput out, Packet packet) throws IOException {
        out.write(encode(packet));
    }

    /**
     * Reads one frame from the given input stream. Blocks until a full frame is
     * available or the stream ends. Returns {@code null} on clean EOF at a frame
     * boundary.
     */
    public static Packet readFrame(DataInput in) throws IOException {
        int length;
        try {
            length = in.readInt();
        } catch (EOFException error) {
            return null;
        }
        if (length < 1 || length > MAX_FRAME_SIZE) {
            throw new IOException("Invalid frame length: " + length);
        }
        byte[] payload = new byte[length];
        in.readFully(payload);
        try (DataInputStream payloadIn = new DataInputStream(new ByteArrayInputStream(payload))) {
            int typeId = payloadIn.readUnsignedByte();
            return read(payloadIn, typeId);
        }
    }

    private static Packet read(DataInput in, int typeId) throws IOException {
        return switch (typeId) {
            case HandshakePacket.TYPE_ID -> HandshakePacket.read(in);
            case HeartbeatPacket.TYPE_ID -> HeartbeatPacket.read(in);
            case AckPacket.TYPE_ID -> AckPacket.read(in);
            case WorldDeltaBatchPacket.TYPE_ID -> WorldDeltaBatchPacket.read(in);
            case OrderedDeltaBatchPacket.TYPE_ID -> OrderedDeltaBatchPacket.read(in);
            case CommandRegistrySnapshotPacket.TYPE_ID -> CommandRegistrySnapshotPacket.read(in);
            case CommandExecutePacket.TYPE_ID -> CommandExecutePacket.read(in);
            case CommandExecuteResultPacket.TYPE_ID -> CommandExecuteResultPacket.read(in);
            case EventPacket.TYPE_ID -> EventPacket.read(in);
            default -> throw new IOException("Unknown packet type id: " + typeId);
        };
    }
}
