package dev.hypercore.bridge.ipc.packet;

import dev.hypercore.bridge.ipc.Packet;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Delivery acknowledgement for reliable packets. The sender can correlate it
 * with an outgoing packet through {@code sequence}.
 */
public record AckPacket(long sequence) implements Packet {
    public static final int TYPE_ID = 3;

    @Override
    public int typeId() {
        return TYPE_ID;
    }

    @Override
    public void write(DataOutput out) throws IOException {
        out.writeLong(sequence);
    }

    public static AckPacket read(DataInput in) throws IOException {
        return new AckPacket(in.readLong());
    }
}
