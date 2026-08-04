package dev.hypercore.bridge.player;

import dev.hypercore.bridge.ipc.packet.EventPacket;
import dev.hypercore.bridge.world.BridgeLink;
import dev.hypercore.orchestrator.HyperCoreRole;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routes player state between the host a player is connected to and the other
 * host.
 *
 * <p>The host the player is physically connected to is authoritative for that
 * player. This proxy records local join/quit transitions and announces them to
 * the other host, so both hosts agree on which side owns each player. Ownership
 * is read by the world-state machinery to drop player deltas from the
 * non-authoritative host.
 */
public final class PlayerProxy {
    private final HyperCoreRole localRole;
    private final BridgeLink link;
    private final Map<UUID, HyperCoreRole> owners = new ConcurrentHashMap<>();

    public PlayerProxy(HyperCoreRole localRole, BridgeLink link) {
        this.localRole = Objects.requireNonNull(localRole, "localRole");
        this.link = Objects.requireNonNull(link, "link");
    }

    /**
     * Records a player joining this host and announces the ownership to the
     * other host.
     */
    public void playerJoined(UUID playerId, String worldName) {
        owners.put(playerId, localRole);
        link.send(new EventPacket("PlayerJoinEvent", false, "playerId=" + playerId + ";world=" + worldName));
    }

    /**
     * Records a player leaving this host and announces the departure.
     */
    public void playerQuit(UUID playerId, String worldName) {
        owners.remove(playerId);
        link.send(new EventPacket("PlayerQuitEvent", false, "playerId=" + playerId + ";world=" + worldName));
    }

    /**
     * Handles a remote join/quit announcement.
     */
    public void handle(EventPacket packet) {
        if (!"PlayerJoinEvent".equals(packet.eventName()) && !"PlayerQuitEvent".equals(packet.eventName())) {
            return;
        }
        String[] fields = packet.payload().split(";");
        UUID playerId = null;
        String world = "";
        for (String field : fields) {
            if (field.startsWith("playerId=")) {
                playerId = UUID.fromString(field.substring("playerId=".length()));
            } else if (field.startsWith("world=")) {
                world = field.substring("world=".length());
            }
        }
        if (playerId == null) {
            return;
        }
        if ("PlayerJoinEvent".equals(packet.eventName())) {
            owners.put(playerId, peerRole());
        } else {
            owners.remove(playerId);
        }
    }

    /**
     * Returns the host that owns the given player, or {@code null} if unknown.
     */
    public HyperCoreRole ownerOf(UUID playerId) {
        return owners.get(playerId);
    }

    /**
     * Returns whether the given player is connected to this host.
     */
    public boolean isLocal(UUID playerId) {
        return localRole.equals(owners.get(playerId));
    }

    private HyperCoreRole peerRole() {
        return localRole == HyperCoreRole.FORGE_HOST ? HyperCoreRole.FABRIC_HOST : HyperCoreRole.FORGE_HOST;
    }
}
