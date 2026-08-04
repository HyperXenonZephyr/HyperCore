package dev.hypercore.bridge.ipc.packet;

import dev.hypercore.bridge.ipc.Packet;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Objects;

/**
 * Forwards a selected HyperCore/Bukkit event across the bridge and propagates
 * its cancellation state. Used by the {@code EventProxy} so a plugin listener on
 * one host can veto a mutation initiated on the other host.
 *
 * <p>The payload is a compact {@code key=value} list serialized by the sending
 * host and decoded by the receiving host's event adapter.
 */
public record EventPacket(String eventName, boolean cancelled, String payload) implements Packet {
    public static final int TYPE_ID = 9;

    public EventPacket {
        eventName = Objects.requireNonNull(eventName, "eventName");
        payload = Objects.requireNonNullElse(payload, "");
    }

    @Override
    public int typeId() {
        return TYPE_ID;
    }

    @Override
    public void write(DataOutput out) throws IOException {
        out.writeUTF(eventName);
        out.writeBoolean(cancelled);
        out.writeUTF(payload);
    }

    public static EventPacket read(DataInput in) throws IOException {
        return new EventPacket(in.readUTF(), in.readBoolean(), in.readUTF());
    }
}
