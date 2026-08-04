package dev.hypercore.bridge.world;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Objects;

/**
 * Mirrors a single block mutation to the remote host.
 *
 * <p>The block state is transmitted as its block-state string (for example
 * {@code minecraft:stone} or a full state string with properties). Appliers
 * that cannot parse full property strings fall back to the material name.
 */
public record BlockDelta(String worldName, int x, int y, int z, String blockState) implements WorldDelta {
    public static final int TYPE_ID = 1;

    public BlockDelta {
        Objects.requireNonNull(worldName, "worldName");
        Objects.requireNonNull(blockState, "blockState");
    }

    @Override
    public int typeId() {
        return TYPE_ID;
    }

    @Override
    public void write(DataOutput out) throws IOException {
        out.writeUTF(worldName);
        out.writeInt(x);
        out.writeInt(y);
        out.writeInt(z);
        out.writeUTF(blockState);
    }

    static BlockDelta read(DataInput in) throws IOException {
        return new BlockDelta(in.readUTF(), in.readInt(), in.readInt(), in.readInt(), in.readUTF());
    }

    /**
     * Returns a compact key for conflict detection on the same block position.
     */
    public String conflictKey() {
        return worldName + ";" + x + ";" + y + ";" + z;
    }
}
