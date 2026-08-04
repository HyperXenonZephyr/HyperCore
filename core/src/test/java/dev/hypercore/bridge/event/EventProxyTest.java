package dev.hypercore.bridge.event;

import dev.hypercore.bridge.ipc.Packet;
import dev.hypercore.bridge.ipc.packet.EventPacket;
import dev.hypercore.bridge.world.BridgeLink;
import dev.hypercore.plugin.PluginEventBus;
import dev.hypercore.world.event.BlockBreakEvent;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies cross-host event forwarding and cancellation propagation.
 */
class EventProxyTest {

    private static final class FakeLink implements BridgeLink {
        boolean connected = true;
        final List<Packet> sent = new ArrayList<>();

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public boolean send(Packet packet) {
            sent.add(packet);
            return true;
        }
    }

    private static Block blockIn(String world) {
        return new org.bukkit.block.Block() {
            @Override
            public org.bukkit.World getWorld() {
                return new org.bukkit.World() {
                    @Override
                    public String getName() {
                        return world;
                    }

                    @Override
                    public Block getBlockAt(int x, int y, int z) {
                        return null;
                    }

                    @Override
                    public List<org.bukkit.entity.Entity> getEntities() {
                        return List.of();
                    }

                    @Override
                    public org.bukkit.entity.Entity spawnEntity(org.bukkit.Location location, org.bukkit.entity.EntityType type) {
                        return null;
                    }
                };
            }

            @Override
            public int getX() {
                return 3;
            }

            @Override
            public int getY() {
                return 4;
            }

            @Override
            public int getZ() {
                return 5;
            }

            @Override
            public Material getType() {
                return Material.STONE;
            }

            @Override
            public void setType(Material type) {
            }

            @Override
            public BlockState getState() {
                return null;
            }
        };
    }

    @Test
    void forwardsCancelledBlockBreak() {
        FakeLink link = new FakeLink();
        EventProxy proxy = new EventProxy(new PluginEventBus(), link);
        BlockBreakEvent event = new BlockBreakEvent(blockIn("minecraft:overworld"), null, Material.STONE);
        event.cancelled(true);

        proxy.forward(event);

        assertEquals(1, link.sent.size());
        EventPacket packet = (EventPacket) link.sent.get(0);
        assertEquals("BlockBreakEvent", packet.eventName());
        assertTrue(packet.cancelled());
        assertEquals("minecraft:overworld;3;4;5", packet.payload());
    }

    @Test
    void nonForwardedEventsAreNotSent() {
        FakeLink link = new FakeLink();
        EventProxy proxy = new EventProxy(new PluginEventBus(), link);
        // An event outside the forwarded set must not be sent.
        PluginEventBus.CancellableEvent other = new PluginEventBus.CancellableEvent() {
            private boolean cancelled;

            @Override
            public boolean cancelled() {
                return cancelled;
            }

            @Override
            public void cancelled(boolean cancelled) {
                this.cancelled = cancelled;
            }
        };
        proxy.forward(other);
        assertTrue(link.sent.isEmpty());
    }

    @Test
    void cancelledRemoteEventSuppressesMirrorApplication() {
        FakeLink link = new FakeLink();
        EventProxy proxy = new EventProxy(new PluginEventBus(), link);
        EventPacket packet = new EventPacket("BlockBreakEvent", true, "minecraft:overworld;3;4;5");

        proxy.handle(packet);

        assertTrue(proxy.consumeBlockSuppression("minecraft:overworld", 3, 4, 5));
        assertFalse(proxy.consumeBlockSuppression("minecraft:overworld", 3, 4, 5), "suppression is single-use");
    }

    @Test
    void uncancelledRemoteEventDoesNotSuppress() {
        FakeLink link = new FakeLink();
        EventProxy proxy = new EventProxy(new PluginEventBus(), link);
        proxy.handle(new EventPacket("BlockPlaceEvent", false, "w;0;0;0"));
        assertFalse(proxy.consumeBlockSuppression("w", 0, 0, 0));
    }

    @Test
    void localPosterRepostsRemoteEvent() {
        FakeLink link = new FakeLink();
        EventProxy proxy = new EventProxy(new PluginEventBus(), link);
        List<EventPacket> posted = new ArrayList<>();
        proxy.setLocalPoster(packet -> {
            posted.add(packet);
            return packet.cancelled();
        });

        proxy.handle(new EventPacket("BlockBreakEvent", true, "w;1;2;3"));
        assertEquals(1, posted.size());
        assertEquals("BlockBreakEvent", posted.get(0).eventName());
    }

    @Test
    void changedCancellationIsSentBackToOriginHost() {
        FakeLink link = new FakeLink();
        EventProxy proxy = new EventProxy(new PluginEventBus(), link);
        // The remote host sent an uncancelled event; the local listener cancels it.
        proxy.setLocalPoster(packet -> Boolean.TRUE);

        proxy.handle(new EventPacket("BlockBreakEvent", false, "w;1;2;3"));

        assertEquals(1, link.sent.size());
        EventPacket feedback = (EventPacket) link.sent.get(0);
        assertTrue(feedback.cancelled());
        assertEquals("w;1;2;3", feedback.payload());
    }
}
