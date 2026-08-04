package dev.hypercore.bridge.world;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

/**
 * Mirrors the state of a player (health and position) to the remote host.
 *
 * <p>Only the host the player is connected to is authoritative for player
 * state; deltas from the other host are dropped by the conflict resolver.
 */
public record PlayerStateDelta(String worldName, UUID playerId, double health, double x, double y, double z) implements WorldDelta {
    public static final int TYPE_ID = 5;

    public PlayerStateDelta {
        Objects.requireNonNull(worldName, "worldName");
        Objects.requireNonNull(playerId, "playerId");
    }

    @Override
    public int typeId() {
        return TYPE_ID;
    }

    @Override
    public void write(DataOutput out) throws IOException {
        out.writeUTF(worldName);
        WorldDelta.Io.writeUuid(out, playerId);
        out.writeDouble(health);
        out.writeDouble(x);
        out.writeDouble(y);
        out.writeDouble(z);
    }

    static PlayerStateDelta read(DataInput in) throws IOException {
        return new PlayerStateDelta(
            in.readUTF(),
            WorldDelta.Io.readUuid(in),
            in.readDouble(),
            in.readDouble(),
            in.readDouble(),
            in.readDouble()
        );
    }
}
