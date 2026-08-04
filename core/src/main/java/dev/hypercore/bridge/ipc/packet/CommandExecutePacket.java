package dev.hypercore.bridge.ipc.packet;

import dev.hypercore.bridge.ipc.Packet;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A request to execute a command on the remote host. Sent by a {@code
 * CommandProxy} when a player or the console invokes a mirrored command; the
 * result is returned as a {@link CommandExecuteResultPacket} with the same
 * {@code requestId}.
 */
public record CommandExecutePacket(
    long requestId,
    String label,
    List<String> arguments,
    String senderName,
    boolean operator,
    boolean console
) implements Packet {
    public static final int TYPE_ID = 7;

    public CommandExecutePacket {
        label = Objects.requireNonNull(label, "label");
        arguments = List.copyOf(arguments == null ? List.of() : arguments);
        senderName = Objects.requireNonNullElse(senderName, "Console");
    }

    @Override
    public int typeId() {
        return TYPE_ID;
    }

    @Override
    public void write(DataOutput out) throws IOException {
        out.writeLong(requestId);
        out.writeUTF(label);
        out.writeInt(arguments.size());
        for (String argument : arguments) {
            out.writeUTF(argument);
        }
        out.writeUTF(senderName);
        out.writeBoolean(operator);
        out.writeBoolean(console);
    }

    public static CommandExecutePacket read(DataInput in) throws IOException {
        long requestId = in.readLong();
        String label = in.readUTF();
        int count = in.readInt();
        if (count < 0 || count > 1_000_000) {
            throw new IOException("Invalid command argument count: " + count);
        }
        List<String> arguments = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            arguments.add(in.readUTF());
        }
        return new CommandExecutePacket(
            requestId,
            label,
            arguments,
            in.readUTF(),
            in.readBoolean(),
            in.readBoolean()
        );
    }
}
