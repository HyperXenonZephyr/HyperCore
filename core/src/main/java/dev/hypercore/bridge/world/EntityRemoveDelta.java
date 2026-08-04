package dev.hypercore.bridge.world;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

/**
 * Mirrors the removal of an entity from the world to the remote host.
 */
public record EntityRemoveDelta(String worldName, UUID entityId) implements WorldDelta {
    public static final int TYPE_ID = 4;

    public EntityRemoveDelta {
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
    }

    static EntityRemoveDelta read(DataInput in) throws IOException {
        return new EntityRemoveDelta(in.readUTF(), WorldDelta.Io.readUuid(in));
    }
}
