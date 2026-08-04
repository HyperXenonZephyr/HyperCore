package dev.hypercore.bridge.ipc.packet;

import dev.hypercore.bridge.ipc.Packet;
import dev.hypercore.orchestrator.HyperCoreRole;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Objects;

/**
 * First packet exchanged on a connection. Negotiates the protocol version,
 * the peer's role, the Minecraft version, and a per-connection sequence number
 * the other side acknowledges with an {@link AckPacket} so incompatible
 * combinations fail fast with a clear message.
 */
public record HandshakePacket(
    int protocolVersion,
    HyperCoreRole role,
    String minecraftVersion,
    String hostName,
    long sequence
) implements Packet {
    public static final int TYPE_ID = 1;

    public HandshakePacket {
        if (protocolVersion < 1) {
            throw new IllegalArgumentException("protocolVersion must be positive");
        }
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        hostName = Objects.requireNonNullElse(hostName, "");
    }

    @Override
    public int typeId() {
        return TYPE_ID;
    }

    @Override
    public void write(DataOutput out) throws IOException {
        out.writeInt(protocolVersion);
        out.writeUTF(role.name());
        out.writeUTF(minecraftVersion);
        out.writeUTF(hostName);
        out.writeLong(sequence);
    }

    public static HandshakePacket read(DataInput in) throws IOException {
        return new HandshakePacket(
            in.readInt(),
            HyperCoreRole.fromSystemProperty(in.readUTF()),
            in.readUTF(),
            in.readUTF(),
            in.readLong()
        );
    }
}
