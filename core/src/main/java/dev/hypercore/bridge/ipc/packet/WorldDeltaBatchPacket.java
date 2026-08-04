package dev.hypercore.bridge.ipc.packet;

import dev.hypercore.bridge.ipc.Packet;
import dev.hypercore.bridge.world.WorldDelta;
import dev.hypercore.orchestrator.HyperCoreRole;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A host-to-orchestrator message carrying the deltas a host produced during one
 * bridge tick. The orchestrator orders and resolves them before broadcasting
 * the surviving deltas back to both hosts.
 */
public record WorldDeltaBatchPacket(HyperCoreRole source, List<WorldDelta> deltas) implements Packet {
    public static final int TYPE_ID = 4;

    public WorldDeltaBatchPacket {
        Objects.requireNonNull(source, "source");
        deltas = List.copyOf(deltas == null ? List.of() : deltas);
    }

    @Override
    public int typeId() {
        return TYPE_ID;
    }

    @Override
    public void write(DataOutput out) throws IOException {
        out.writeUTF(source.name());
        out.writeInt(deltas.size());
        for (WorldDelta delta : deltas) {
            out.writeByte(delta.typeId());
            delta.write(out);
        }
    }

    public static WorldDeltaBatchPacket read(DataInput in) throws IOException {
        HyperCoreRole source = HyperCoreRole.fromSystemProperty(in.readUTF());
        int count = in.readInt();
        if (count < 0 || count > 1_000_000) {
            throw new IOException("Invalid world delta batch size: " + count);
        }
        List<WorldDelta> deltas = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            deltas.add(WorldDelta.read(in));
        }
        return new WorldDeltaBatchPacket(source, deltas);
    }
}
