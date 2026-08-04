package dev.hypercore.bridge.event;

import dev.hypercore.bridge.ipc.packet.EventPacket;
import dev.hypercore.bridge.world.BridgeLink;
import dev.hypercore.plugin.PluginEventBus;
import dev.hypercore.world.event.BlockBreakEvent;
import dev.hypercore.world.event.BlockPlaceEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Forwards selected HyperCore internal events across the bridge and propagates
 * their cancellation state.
 *
 * <p>Block events are forwarded at {@code MONITOR} priority, so by the time the
 * proxy sees them all local listeners have run and the cancellation state is
 * final. The remote host records the cancellation and reposts the event on its
 * own event bus (through a loader-configured poster), letting listeners there
 * observe the veto. The recorded suppression is consumed by the remote world
 * applier so cancelled mutations never reach the mirror world.
 */
public final class EventProxy {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventProxy.class);

    private static final Set<String> FORWARDED_EVENTS = Set.of("BlockBreakEvent", "BlockPlaceEvent");

    private final PluginEventBus events;
    private final BridgeLink link;
    private final Set<String> suppressedBlocks = ConcurrentHashMap.newKeySet();
    private volatile Function<EventPacket, Boolean> localPoster;

    public EventProxy(PluginEventBus events, BridgeLink link) {
        this.events = Objects.requireNonNull(events, "events");
        this.link = Objects.requireNonNull(link, "link");
    }

    /**
     * Registers the callback used to repost a received event on the local event
     * bus. Loader adapters configure this so the event can be rebuilt with a
     * real block handle. The callback returns the final cancellation state after
     * local listeners ran; if it differs from the received state, the updated
     * state is sent back to the origin host.
     */
    public void setLocalPoster(Function<EventPacket, Boolean> localPoster) {
        this.localPoster = localPoster;
    }

    /**
     * Subscribes this proxy to the forwarded internal events at MONITOR
     * priority. Returns a combined subscription for teardown.
     */
    public PluginEventBus.Subscription attach() {
        PluginEventBus.Subscription breakSubscription = events.register(
            "hypercore-bridge",
            BlockBreakEvent.class,
            PluginEventBus.EventPriority.MONITOR,
            false,
            this::forward
        );
        PluginEventBus.Subscription placeSubscription = events.register(
            "hypercore-bridge",
            BlockPlaceEvent.class,
            PluginEventBus.EventPriority.MONITOR,
            false,
            this::forward
        );
        return () -> {
            breakSubscription.close();
            placeSubscription.close();
        };
    }

    /**
     * Forwards a local internal event to the remote host with its final
     * cancellation state.
     */
    public void forward(PluginEventBus.CancellableEvent event) {
        String eventName = event.getClass().getSimpleName();
        if (!FORWARDED_EVENTS.contains(eventName)) {
            return;
        }
        String payload = payloadFor(event);
        if (payload == null) {
            return;
        }
        link.send(new EventPacket(eventName, event.cancelled(), payload));
    }

    /**
     * Handles an event received from the remote host: records cancellations and
     * reposts the event on the local bus. If local listeners change the
     * cancellation state, the updated state is sent back to the origin host so
     * both sides agree on the veto.
     */
    public void handle(EventPacket packet) {
        if (packet.cancelled()) {
            if ("BlockBreakEvent".equals(packet.eventName()) || "BlockPlaceEvent".equals(packet.eventName())) {
                suppressedBlocks.add(packet.payload());
            }
        }
        Function<EventPacket, Boolean> poster = localPoster;
        if (poster != null) {
            try {
                Boolean finalCancelled = poster.apply(packet);
                if (finalCancelled != null && finalCancelled != packet.cancelled()) {
                    // Local listeners changed the outcome; notify the origin host.
                    link.send(new EventPacket(packet.eventName(), finalCancelled, packet.payload()));
                }
            } catch (RuntimeException error) {
                LOGGER.error("Failed to repost remote event {}: {}", packet.eventName(), error.getMessage());
            }
        }
    }

    /**
     * Consumes a block suppression recorded from a cancelled remote event.
     *
     * @return {@code true} if the remote host cancelled a mutation for this
     *         block position and the delta should be skipped
     */
    public boolean consumeBlockSuppression(String worldName, int x, int y, int z) {
        return suppressedBlocks.remove(key(worldName, x, y, z));
    }

    private static String payloadFor(PluginEventBus.CancellableEvent event) {
        if (event instanceof BlockBreakEvent blockBreak) {
            org.bukkit.block.Block block = blockBreak.getBlock();
            if (block == null) {
                return null;
            }
            return key(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        }
        if (event instanceof BlockPlaceEvent blockPlace) {
            org.bukkit.block.Block block = blockPlace.getBlock();
            if (block == null) {
                return null;
            }
            return key(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        }
        return null;
    }

    private static String key(String worldName, int x, int y, int z) {
        return worldName + ";" + x + ";" + y + ";" + z;
    }
}
