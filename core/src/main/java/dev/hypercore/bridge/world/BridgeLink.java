package dev.hypercore.bridge.world;

import dev.hypercore.bridge.ipc.Packet;

/**
 * Narrow view of the host's bridge connection used by delta senders and other
 * world-bridge components.
 */
public interface BridgeLink {

    /**
     * Returns whether a live, handshaken connection exists.
     */
    boolean isConnected();

    /**
     * Sends a packet to the orchestrator.
     *
     * @return {@code true} if the packet was written
     */
    boolean send(Packet packet);
}
