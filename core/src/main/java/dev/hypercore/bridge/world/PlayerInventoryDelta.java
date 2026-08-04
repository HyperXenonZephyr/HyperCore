package dev.hypercore.bridge.world;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

/**
 * Mirrors a single slot update of a player inventory to the remote host.
 *
 * <p>The item type is transmitted as the Bukkit material name so both loaders
 * can map it through their own item tables. A null item type clears the slot.
 */
public record PlayerInventoryDelta(String worldName, UUID playerId, int slot, String itemType, int amount) implements WorldDelta {
    public static final int TYPE_ID = 6;

    public PlayerInventoryDelta {
        Objects.requireNonNull(worldName, "worldName");
        Objects.requireNonNull(playerId, "playerId");
        if (slot < 0) {
            throw new IllegalArgumentException("slot cannot be negative");
        }
        if (amount < 0) {
            throw new IllegalArgumentException("amount cannot be negative");
        }
    }

    @Override
    public int typeId() {
        return TYPE_ID;
    }

    @Override
    public void write(DataOutput out) throws IOException {
        out.writeUTF(worldName);
        WorldDelta.Io.writeUuid(out, playerId);
        out.writeInt(slot);
        WorldDelta.Io.writeNullableString(out, itemType);
        out.writeInt(amount);
    }

    static PlayerInventoryDelta read(DataInput in) throws IOException {
        return new PlayerInventoryDelta(
            in.readUTF(),
            WorldDelta.Io.readUuid(in),
            in.readInt(),
            WorldDelta.Io.readNullableString(in),
            in.readInt()
        );
    }
}
