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
 * Orchestrator-to-host broadcast of an ordered, conflict-resolved delta batch.
 *
 * <p>Deltas are already stamped with sequence numbers; each host applies the
 * batch only if it did not originate the batch (the originator applied the
 * changes locally at production time and treats the broadcast as an
 * acknowledgement). Sequence numbers are {@code firstSequence + index}.
 */
public record OrderedDeltaBatchPacket(
    long logicalTick,
    HyperCoreRole source,
    long firstSequence,
    List<WorldDelta> deltas
) implements Packet {
    public static final int TYPE_ID = 5;

    public OrderedDeltaBatchPacket {
        Objects.requireNonNull(source, "source");
        deltas = List.copyOf(deltas == null ? List.of() : deltas);
    }

    @Override
    public int typeId() {
        return TYPE_ID;
    }

    @Override
    public void write(DataOutput out) throws IOException {
        out.writeLong(logicalTick);
        out.writeUTF(source.name());
        out.writeLong(firstSequence);
        out.writeInt(deltas.size());
        for (WorldDelta delta : deltas) {
            out.writeByte(delta.typeId());
            delta.write(out);
        }
    }

    public static OrderedDeltaBatchPacket read(DataInput in) throws IOException {
        long logicalTick = in.readLong();
        HyperCoreRole source = HyperCoreRole.fromSystemProperty(in.readUTF());
        long firstSequence = in.readLong();
        int count = in.readInt();
        if (count < 0 || count > 1_000_000) {
            throw new IOException("Invalid ordered delta batch size: " + count);
        }
        List<WorldDelta> deltas = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            deltas.add(WorldDelta.read(in));
        }
        return new OrderedDeltaBatchPacket(logicalTick, source, firstSequence, deltas);
    }
}
