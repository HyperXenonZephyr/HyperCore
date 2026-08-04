package dev.hypercore.bridge;

import dev.hypercore.bridge.ipc.Packet;
import dev.hypercore.bridge.ipc.packet.CommandExecutePacket;
import dev.hypercore.bridge.ipc.packet.CommandExecuteResultPacket;
import dev.hypercore.bridge.ipc.packet.CommandRegistrySnapshotPacket;
import dev.hypercore.bridge.ipc.packet.EventPacket;
import dev.hypercore.bridge.ipc.packet.WorldDeltaBatchPacket;
import dev.hypercore.bridge.world.WorldStateBridge;
import dev.hypercore.orchestrator.HyperCoreRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.UUID;

/**
 * Routes packets between the orchestrator and the two hosts.
 *
 * <p>World delta batches are handed to the {@link WorldStateBridge} for
 * ordering and conflict resolution. Command and event packets are forwarded to
 * the other host, which is the deterministic peer in a two-host deployment.
 */
public final class BridgePacketRouter implements OrchestratorBridgeServer.PacketHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(BridgePacketRouter.class);

    private final OrchestratorBridgeServer server;
    private final WorldStateBridge worldBridge;

    public BridgePacketRouter(OrchestratorBridgeServer server, WorldStateBridge worldBridge) {
        this.server = Objects.requireNonNull(server, "server");
        this.worldBridge = Objects.requireNonNull(worldBridge, "worldBridge");
    }

    @Override
    public void handle(HyperCoreRole source, Packet packet) {
        if (packet instanceof WorldDeltaBatchPacket batch) {
            worldBridge.submit(batch.source(), batch.deltas());
            return;
        }
        if (packet instanceof CommandRegistrySnapshotPacket
            || packet instanceof CommandExecutePacket
            || packet instanceof CommandExecuteResultPacket) {
            forwardToOtherHost(source, packet);
            return;
        }
        if (packet instanceof EventPacket event) {
            // Player join/quit announcements update the orchestrator-side
            // ownership table so world-state conflict resolution is consistent
            // with the host that owns each player.
            updatePlayerOwnership(source, event);
            forwardToOtherHost(source, event);
            return;
        }
        LOGGER.warn("Unhandled packet type from {}: {}", source.displayName(), packet.getClass().getSimpleName());
    }

    private void forwardToOtherHost(HyperCoreRole source, Packet packet) {
        HyperCoreRole target = other(source);
        if (!server.sendTo(target, packet)) {
            LOGGER.debug("Could not forward {} to {} (host not connected)", packet.getClass().getSimpleName(), target.displayName());
        }
    }

    /**
     * Returns the peer host of the given host role.
     */
    public static HyperCoreRole other(HyperCoreRole role) {
        return role == HyperCoreRole.FORGE_HOST ? HyperCoreRole.FABRIC_HOST : HyperCoreRole.FORGE_HOST;
    }

    private void updatePlayerOwnership(HyperCoreRole source, EventPacket event) {
        if (!"PlayerJoinEvent".equals(event.eventName()) && !"PlayerQuitEvent".equals(event.eventName())) {
            return;
        }
        UUID playerId = null;
        for (String field : event.payload().split(";")) {
            if (field.startsWith("playerId=")) {
                try {
                    playerId = UUID.fromString(field.substring("playerId=".length()));
                } catch (IllegalArgumentException ignored) {
                    return;
                }
                break;
            }
        }
        if (playerId == null) {
            return;
        }
        if ("PlayerJoinEvent".equals(event.eventName())) {
            worldBridge.setPlayerOwner(playerId, source);
        } else {
            worldBridge.removePlayerOwner(playerId);
        }
    }
}
