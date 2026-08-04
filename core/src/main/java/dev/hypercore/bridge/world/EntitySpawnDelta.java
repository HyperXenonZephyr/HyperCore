package dev.hypercore.bridge.world;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

/**
 * Announces that an entity was spawned in a world. The spawning host becomes
 * the entity's owner and stays authoritative for its moves until ownership is
 * explicitly transferred.
 *
 * <p>The entity type is transmitted as the Bukkit entity type name so both
 * loaders can map it through their own entity-type tables.
 */
public record EntitySpawnDelta(String worldName, UUID entityId, String entityType, double x, double y, double z) implements WorldDelta {
    public static final int TYPE_ID = 2;

    public EntitySpawnDelta {
        Objects.requireNonNull(worldName, "worldName");
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(entityType, "entityType");
    }

    @Override
    public int typeId() {
        return TYPE_ID;
    }

    @Override
    public void write(DataOutput out) throws IOException {
        out.writeUTF(worldName);
        WorldDelta.Io.writeUuid(out, entityId);
        out.writeUTF(entityType);
        out.writeDouble(x);
        out.writeDouble(y);
        out.writeDouble(z);
    }

    static EntitySpawnDelta read(DataInput in) throws IOException {
        return new EntitySpawnDelta(
            in.readUTF(),
            WorldDelta.Io.readUuid(in),
            in.readUTF(),
            in.readDouble(),
            in.readDouble(),
            in.readDouble()
        );
    }
}
