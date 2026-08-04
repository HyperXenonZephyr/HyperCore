package dev.hypercore.bridge.world;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Base type for ordered world mutations exchanged across the bridge.
 *
 * <p>Deltas are loader-agnostic: they carry raw coordinates, ids, and registry
 * names instead of Minecraft objects, so the same byte stream can be decoded by
 * both the Forge and the Fabric host. Each host's applier translates a delta
 * into native operations. The byte layout is versioned by the type id.
 */
public sealed interface WorldDelta permits BlockDelta, EntitySpawnDelta, EntityMoveDelta, EntityRemoveDelta, PlayerStateDelta, PlayerInventoryDelta {

    /**
     * Stable numeric type id used by the delta codec. Must be unique across all
     * delta types and never change once released.
     */
    int typeId();

    /**
     * Returns the name of the world this delta targets.
     */
    String worldName();

    /**
     * Serializes this delta to the given output stream.
     */
    void write(DataOutput out) throws IOException;

    /**
     * Reads a single delta from the given input stream. The stream must be
     * positioned at the delta type id byte.
     */
    static WorldDelta read(DataInput in) throws IOException {
        int typeId = in.readUnsignedByte();
        return switch (typeId) {
            case BlockDelta.TYPE_ID -> BlockDelta.read(in);
            case EntitySpawnDelta.TYPE_ID -> EntitySpawnDelta.read(in);
            case EntityMoveDelta.TYPE_ID -> EntityMoveDelta.read(in);
            case EntityRemoveDelta.TYPE_ID -> EntityRemoveDelta.read(in);
            case PlayerStateDelta.TYPE_ID -> PlayerStateDelta.read(in);
            case PlayerInventoryDelta.TYPE_ID -> PlayerInventoryDelta.read(in);
            default -> throw new IOException("Unknown world delta type id: " + typeId);
        };
    }

    /**
     * Utility methods shared by all delta codecs.
     */
    final class Io {
        private Io() {
        }

        static void writeUuid(DataOutput out, java.util.UUID id) throws IOException {
            out.writeLong(id.getMostSignificantBits());
            out.writeLong(id.getLeastSignificantBits());
        }

        static java.util.UUID readUuid(DataInput in) throws IOException {
            return new java.util.UUID(in.readLong(), in.readLong());
        }

        static void writeNullableString(DataOutput out, String value) throws IOException {
            if (value == null) {
                out.writeBoolean(false);
            } else {
                out.writeBoolean(true);
                out.writeUTF(value);
            }
        }

        static String readNullableString(DataInput in) throws IOException {
            return in.readBoolean() ? in.readUTF() : null;
        }
    }
}
