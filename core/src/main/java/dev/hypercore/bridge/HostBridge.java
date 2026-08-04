package dev.hypercore.bridge;

import dev.hypercore.bridge.command.CommandProxy;
import dev.hypercore.bridge.event.EventProxy;
import dev.hypercore.bridge.ipc.Packet;
import dev.hypercore.bridge.ipc.packet.CommandExecutePacket;
import dev.hypercore.bridge.ipc.packet.CommandExecuteResultPacket;
import dev.hypercore.bridge.ipc.packet.CommandRegistrySnapshotPacket;
import dev.hypercore.bridge.ipc.packet.EventPacket;
import dev.hypercore.bridge.ipc.packet.OrderedDeltaBatchPacket;
import dev.hypercore.bridge.player.PlayerProxy;
import dev.hypercore.bridge.world.WorldDeltaApplier;
import dev.hypercore.bridge.world.WorldDeltaSender;
import dev.hypercore.orchestrator.HyperCoreRole;
import dev.hypercore.plugin.PluginCommandRegistry;
import dev.hypercore.plugin.PluginEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Host-side bridge assembly.
 *
 * <p>Wires the {@link BridgeEndpoint} together with the world delta sender, the
 * command proxy, the event proxy, and the player proxy, and dispatches incoming
 * packets. Loader adapters create one {@code HostBridge} in bridge mode, attach
 * the loader-specific {@link WorldDeltaApplier}, install the delta sink on the
 * region execution service, and call {@link #flushWorldDeltas()} each tick.
 */
public final class HostBridge implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(HostBridge.class);

    private final HyperCoreRole role;
    private final BridgeEndpoint endpoint;
    private final WorldDeltaSender deltaSender;
    private final CommandProxy commandProxy;
    private final EventProxy eventProxy;
    private final PlayerProxy playerProxy;
    private volatile WorldDeltaApplier applier;
    private volatile Runnable onCommandsMirrored;

    /**
     * @param role this host's role (must be a host role)
     * @param orchestratorHost orchestrator address
     * @param orchestratorPort orchestrator port for this role
     * @param tickMillis heartbeat and bridge tick interval
     * @param minecraftVersion version reported in the handshake
     * @param readyMarker stdout marker printed once the handshake completes
     * @param commands the local plugin command registry
     * @param events the local plugin event bus
     * @param remoteCommandPrefix prefix for commands mirrored from the remote host
     */
    public HostBridge(
        HyperCoreRole role,
        String orchestratorHost,
        int orchestratorPort,
        long tickMillis,
        String minecraftVersion,
        String readyMarker,
        PluginCommandRegistry commands,
        PluginEventBus events,
        String remoteCommandPrefix
    ) {
        this.role = Objects.requireNonNull(role, "role");
        this.endpoint = new BridgeEndpoint(
            role,
            orchestratorHost,
            orchestratorPort,
            tickMillis,
            minecraftVersion,
            role.displayName(),
            readyMarker,
            this::handlePacket
        );
        this.deltaSender = new WorldDeltaSender(role, endpoint);
        this.commandProxy = new CommandProxy(role, commands, endpoint, remoteCommandPrefix);
        this.eventProxy = new EventProxy(events, endpoint);
        this.playerProxy = new PlayerProxy(role, endpoint);
        this.endpoint.onConnected(commandProxy::publishSnapshot);
    }

    /**
     * Starts connecting to the orchestrator. Returns immediately; connection
     * happens on a background thread.
     */
    public void start() {
        endpoint.start();
    }

    /**
     * Attaches the loader-specific world delta applier. Must be set before the
     * bridge delivers any ordered batch.
     */
    public void setApplier(WorldDeltaApplier applier) {
        this.applier = Objects.requireNonNull(applier, "applier");
    }

    /**
     * Registers a callback invoked after the remote host's commands have been
     * mirrored into the local registry. Adapters use it to re-sync the loader
     * command dispatcher with the new labels.
     */
    public void setOnCommandsMirrored(Runnable onCommandsMirrored) {
        this.onCommandsMirrored = Objects.requireNonNull(onCommandsMirrored, "onCommandsMirrored");
    }

    /**
     * Returns the bridge endpoint.
     */
    public BridgeEndpoint endpoint() {
        return endpoint;
    }

    /**
     * Returns the delta sender; connect it as the region execution service's
     * delta sink.
     */
    public WorldDeltaSender deltaSender() {
        return deltaSender;
    }

    /**
     * Returns the command proxy (for status and diagnostics).
     */
    public CommandProxy commandProxy() {
        return commandProxy;
    }

    /**
     * Returns the event proxy.
     */
    public EventProxy eventProxy() {
        return eventProxy;
    }

    /**
     * Returns the player proxy.
     */
    public PlayerProxy playerProxy() {
        return playerProxy;
    }

    /**
     * Returns whether a live, handshaken connection exists.
     */
    public boolean isConnected() {
        return endpoint.isConnected();
    }

    /**
     * Ships collected world deltas to the orchestrator. Called once per server
     * tick in bridge mode.
     */
    public void flushWorldDeltas() {
        deltaSender.flush();
    }

    /**
     * Returns a diagnostic view of this bridge.
     */
    public BridgeStatusView statusView() {
        return new BridgeStatusView() {
            @Override
            public String roleName() {
                return role.displayName();
            }

            @Override
            public boolean connected() {
                return endpoint.isConnected();
            }

            @Override
            public long latencyMillis() {
                return endpoint.lastLatencyMillis();
            }

            @Override
            public long publishedDeltas() {
                return deltaSender.publishedCount();
            }

            @Override
            public long droppedDeltas() {
                return deltaSender.droppedCount();
            }

            @Override
            public int mirroredCommands() {
                return commandProxy.mirroredCount();
            }

            @Override
            public String peerSummary() {
                return connected()
                    ? "orchestrator (latency " + latencyMillis() + " ms)"
                    : "orchestrator (disconnected)";
            }
        };
    }

    @Override
    public void close() {
        endpoint.close();
    }

    private void handlePacket(Packet packet) {
        if (packet instanceof OrderedDeltaBatchPacket batch) {
            WorldDeltaApplier current = applier;
            if (batch.source() == role) {
                // Locally-originated deltas were applied at production time; the
                // broadcast doubles as an acknowledgement.
                return;
            }
            if (current != null) {
                current.apply(batch.source(), batch.deltas());
            } else {
                LOGGER.warn("Received ordered delta batch but no world delta applier is attached");
            }
            return;
        }
        if (packet instanceof CommandExecutePacket execute) {
            commandProxy.handleExecute(execute);
            return;
        }
        if (packet instanceof CommandExecuteResultPacket result) {
            commandProxy.handleResult(result);
            return;
        }
        if (packet instanceof CommandRegistrySnapshotPacket snapshot) {
            commandProxy.mirrorRemote(snapshot.commands());
            Runnable callback = onCommandsMirrored;
            if (callback != null) {
                try {
                    callback.run();
                } catch (RuntimeException error) {
                    LOGGER.error("Command mirroring callback failed", error);
                }
            }
            return;
        }
        if (packet instanceof EventPacket event) {
            eventProxy.handle(event);
            playerProxy.handle(event);
            return;
        }
        LOGGER.warn("Unhandled packet from orchestrator: {}", packet.getClass().getSimpleName());
    }
}
