package dev.hypercore.bridge.ipc.packet;

import dev.hypercore.bridge.ipc.Packet;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Keepalive and latency measurement packet. Sent periodically by both peers.
 * The peer can measure round-trip latency against its own clock by echoing
 * {@code timestampNanos} back.
 */
public record HeartbeatPacket(long sequence, long timestampNanos) implements Packet {
    public static final int TYPE_ID = 2;

    @Override
    public int typeId() {
        return TYPE_ID;
    }

    @Override
    public void write(DataOutput out) throws IOException {
        out.writeLong(sequence);
        out.writeLong(timestampNanos);
    }

    public static HeartbeatPacket read(DataInput in) throws IOException {
        return new HeartbeatPacket(in.readLong(), in.readLong());
    }
}
