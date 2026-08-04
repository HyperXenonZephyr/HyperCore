package dev.hypercore.bridge;

import dev.hypercore.plugin.PluginEventBus;
import dev.hypercore.runtime.HyperCoreRuntime;
import dev.hypercore.world.ForgeWorldStateApplier;
import dev.hypercore.world.event.BlockBreakEvent;
import dev.hypercore.world.event.BlockPlaceEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import org.bukkit.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Forge-specific bridge wiring.
 *
 * <p>Opens a {@link HostBridge} when the process runs as a {@code FORGE_HOST},
 * attaches the Forge world delta applier, routes local world mutations into the
 * bridge, mirrors block-event cancellations, and announces player join/quit to
 * the other host. Returns {@code null} in standalone mode so existing Forge
 * servers are completely unaffected.
 */
public final class ForgeBridgeEndpoint implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ForgeBridgeEndpoint.class);

    private final HostBridge bridge;
    private final HyperCoreRuntime runtime;
    private final MinecraftServer server;
    private PluginEventBus.Subscription eventProxySubscription;

    private ForgeBridgeEndpoint(HostBridge bridge, HyperCoreRuntime runtime, MinecraftServer server) {
        this.bridge = Objects.requireNonNull(bridge, "bridge");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.server = Objects.requireNonNull(server, "server");
    }

    /**
     * Opens the bridge when this process runs as a Forge host.
     *
     * @return the endpoint, or {@code null} when bridging is disabled
     */
    public static ForgeBridgeEndpoint open(HyperCoreRuntime runtime, MinecraftServer server) {
        BridgeHostConfig config = BridgeHostConfig.fromSystemProperties();
        if (config == null) {
            return null;
        }
        HostBridge bridge = new HostBridge(
            config.role(),
            config.orchestratorHost(),
            config.orchestratorPort(),
            config.bridgeTickMillis(),
            server.getServerVersion(),
            config.readyMarker(),
            runtime.plugins().commands(),
            runtime.plugins().events(),
            "xfabric"
        );
        ForgeBridgeEndpoint endpoint = new ForgeBridgeEndpoint(bridge, runtime, server);
        endpoint.wire();
        bridge.start();
        LOGGER.info(
            "Forge bridge endpoint opened: {} connecting to {}:{} (tick {} ms)",
            config.role().displayName(),
            config.orchestratorHost(),
            config.orchestratorPort(),
            config.bridgeTickMillis()
        );
        return endpoint;
    }

    /**
     * Returns the underlying host bridge.
     */
    public HostBridge bridge() {
        return bridge;
    }

    /**
     * Returns whether a live, handshaken connection exists.
     */
    public boolean isConnected() {
        return bridge.isConnected();
    }

    /**
     * Ships collected world deltas to the orchestrator. Called each server tick.
     */
    public void flush() {
        bridge.flushWorldDeltas();
    }

    @Override
    public void close() {
        if (eventProxySubscription != null) {
            eventProxySubscription.close();
            eventProxySubscription = null;
        }
        bridge.close();
    }

    private void wire() {
        bridge.setApplier(new ForgeWorldStateApplier(server, bridge.eventProxy()));
        bridge.setOnCommandsMirrored(() -> dev.hypercore.plugin.ForgePluginCommandBridge.register(
            server.getCommands().getDispatcher(),
            runtime.plugins()
        ));
        runtime.regionExecution().setDeltaSink(bridge.deltaSender());
        wireEventPoster();
        eventProxySubscription = bridge.eventProxy().attach();
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                bridge.playerProxy().playerJoined(player.getUUID(), player.level().dimension().location().toString());
            }
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                bridge.playerProxy().playerQuit(player.getUUID(), player.level().dimension().location().toString());
            }
        });
    }

    /**
     * Reposts events received from the remote host on the local event bus so
     * local plugins observe remote vetoes. Returns the final cancellation state
     * so changed outcomes can be sent back to the origin host.
     */
    private void wireEventPoster() {
        bridge.eventProxy().setLocalPoster(packet -> {
            String[] parts = packet.payload().split(";");
            if (parts.length < 4) {
                return packet.cancelled();
            }
            String worldName = parts[0];
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            Block block = runtime.regionExecution().world(worldName).getBlockAt(x, y, z);
            PluginEventBus events = runtime.plugins().events();
            if ("BlockBreakEvent".equals(packet.eventName())) {
                BlockBreakEvent internal = new BlockBreakEvent(block, null, block.getType());
                internal.cancelled(packet.cancelled());
                events.post(internal);
                return internal.cancelled();
            }
            if ("BlockPlaceEvent".equals(packet.eventName())) {
                BlockPlaceEvent internal = new BlockPlaceEvent(block, null, block.getType());
                internal.cancelled(packet.cancelled());
                events.post(internal);
                return internal.cancelled();
            }
            return packet.cancelled();
        });
    }
}
