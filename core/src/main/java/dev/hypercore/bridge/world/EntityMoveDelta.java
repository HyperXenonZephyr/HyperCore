package dev.hypercore.bridge.world;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

/**
 * Mirrors an entity position change to the remote host. Move deltas from a
 * non-owner host are dropped by the conflict resolver unless ownership was
 * transferred.
 */
public record EntityMoveDelta(String worldName, UUID entityId, double x, double y, double z) implements WorldDelta {
    public static final int TYPE_ID = 3;

    public EntityMoveDelta {
        Objects.requireNonNull(worldName, "worldName");
        Objects.requireNonNull(entityId, "entityId");
    }

    @Override
    public int typeId() {
        return TYPE_ID;
    }

    @Override
    public void write(DataOutput out) throws IOException {
        out.writeUTF(worldName);
        WorldDelta.Io.writeUuid(out, entityId);
        out.writeDouble(x);
        out.writeDouble(y);
        out.writeDouble(z);
    }

    static EntityMoveDelta read(DataInput in) throws IOException {
        return new EntityMoveDelta(
            in.readUTF(),
            WorldDelta.Io.readUuid(in),
            in.readDouble(),
            in.readDouble(),
            in.readDouble()
        );
    }
}
