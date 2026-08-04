package dev.hypercore.bridge.ipc.packet;

import dev.hypercore.bridge.ipc.Packet;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Objects;

/**
 * Reply to a {@link CommandExecutePacket}. The message is displayed to the
 * original command source on the requesting host.
 */
public record CommandExecuteResultPacket(long requestId, boolean success, String message) implements Packet {
    public static final int TYPE_ID = 8;

    public CommandExecuteResultPacket {
        message = Objects.requireNonNullElse(message, "");
    }

    @Override
    public int typeId() {
        return TYPE_ID;
    }

    @Override
    public void write(DataOutput out) throws IOException {
        out.writeLong(requestId);
        out.writeBoolean(success);
        out.writeUTF(message);
    }

    public static CommandExecuteResultPacket read(DataInput in) throws IOException {
        return new CommandExecuteResultPacket(in.readLong(), in.readBoolean(), in.readUTF());
    }
}
