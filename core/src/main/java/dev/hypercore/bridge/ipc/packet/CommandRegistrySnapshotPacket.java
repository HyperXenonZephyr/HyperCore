package dev.hypercore.bridge.ipc.packet;

import dev.hypercore.bridge.ipc.Packet;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A serializable snapshot of a host's plugin command registry. Sent to the
 * remote host (through the orchestrator) so the remote {@code CommandProxy} can
 * mirror the commands and forward executions.
 */
public record CommandRegistrySnapshotPacket(List<CommandDescriptor> commands) implements Packet {
    public static final int TYPE_ID = 6;

    public CommandRegistrySnapshotPacket {
        commands = List.copyOf(commands == null ? List.of() : commands);
    }

    @Override
    public int typeId() {
        return TYPE_ID;
    }

    @Override
    public void write(DataOutput out) throws IOException {
        out.writeInt(commands.size());
        for (CommandDescriptor command : commands) {
            command.write(out);
        }
    }

    public static CommandRegistrySnapshotPacket read(DataInput in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > 1_000_000) {
            throw new IOException("Invalid command snapshot size: " + count);
        }
        List<CommandDescriptor> commands = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            commands.add(CommandDescriptor.read(in));
        }
        return new CommandRegistrySnapshotPacket(commands);
    }

    /**
     * Serialized command metadata. Executors and tab completers are not
     * transferred; they run on the owning host.
     */
    public record CommandDescriptor(
        String name,
        List<String> aliases,
        String permission,
        String description,
        String usage,
        String pluginId
    ) {
        public CommandDescriptor {
            name = Objects.requireNonNull(name, "name");
            aliases = List.copyOf(aliases == null ? List.of() : aliases);
            permission = Objects.requireNonNullElse(permission, "");
            description = Objects.requireNonNullElse(description, "");
            usage = Objects.requireNonNullElse(usage, "");
            pluginId = Objects.requireNonNullElse(pluginId, "");
        }

        void write(DataOutput out) throws IOException {
            out.writeUTF(name);
            out.writeInt(aliases.size());
            for (String alias : aliases) {
                out.writeUTF(alias);
            }
            out.writeUTF(permission);
            out.writeUTF(description);
            out.writeUTF(usage);
            out.writeUTF(pluginId);
        }

        static CommandDescriptor read(DataInput in) throws IOException {
            String name = in.readUTF();
            int aliasCount = in.readInt();
            List<String> aliases = new ArrayList<>(aliasCount);
            for (int index = 0; index < aliasCount; index++) {
                aliases.add(in.readUTF());
            }
            return new CommandDescriptor(
                name,
                aliases,
                in.readUTF(),
                in.readUTF(),
                in.readUTF(),
                in.readUTF()
            );
        }
    }
}
