package dev.hypercore.bridge;

import dev.hypercore.bridge.ipc.IpcChannel;
import dev.hypercore.bridge.ipc.Packet;
import dev.hypercore.bridge.ipc.PacketCodec;
import dev.hypercore.bridge.ipc.packet.AckPacket;
import dev.hypercore.bridge.ipc.packet.HandshakePacket;
import dev.hypercore.bridge.ipc.packet.HeartbeatPacket;
import dev.hypercore.orchestrator.HyperCoreRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Orchestrator-side bridge listener.
 *
 * <p>Listens on the Forge and Fabric host ports, accepts one connection per
 * host, validates the handshake against the expected role and protocol version,
 * and then routes packets between the two hosts. Heartbeats are echoed back to
 * the sender so hosts can measure latency; other packets are handed to the
 * registered router.
 */
public final class OrchestratorBridgeServer implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrchestratorBridgeServer.class);
    private static final Duration HANDSHAKE_TIMEOUT = Duration.ofSeconds(10);

    private final int forgePort;
    private final int fabricPort;
    private final Map<HyperCoreRole, IpcChannel> channels = new ConcurrentHashMap<>();
    private final Map<HyperCoreRole, Peer> peers = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<PacketHandler> handlers = new CopyOnWriteArrayList<>();
    private final ExecutorService readers = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "bridge-reader");
        thread.setDaemon(true);
        return thread;
    });

    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean started = new AtomicBoolean();
    private volatile ServerSocket forgeServerSocket;
    private volatile ServerSocket fabricServerSocket;
    private volatile Thread forgeAcceptThread;
    private volatile Thread fabricAcceptThread;

    /**
     * @param forgePort port the Forge host connects to
     * @param fabricPort port the Fabric host connects to
     */
    public OrchestratorBridgeServer(int forgePort, int fabricPort) {
        this.forgePort = forgePort;
        this.fabricPort = fabricPort;
    }

    /**
     * Binds the listener sockets and starts accepting host connections.
     */
    public synchronized void start() throws IOException {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        if (closed.get()) {
            throw new IllegalStateException("Bridge server is closed");
        }
        forgeServerSocket = new ServerSocket(forgePort, 4);
        fabricServerSocket = new ServerSocket(fabricPort, 4);
        LOGGER.info("Bridge server listening on ports {} (forge) and {} (fabric)", forgePort, fabricPort);
        forgeAcceptThread = new Thread(() -> acceptLoop(forgeServerSocket, HyperCoreRole.FORGE_HOST), "bridge-accept-forge");
        fabricAcceptThread = new Thread(() -> acceptLoop(fabricServerSocket, HyperCoreRole.FABRIC_HOST), "bridge-accept-fabric");
        forgeAcceptThread.setDaemon(true);
        fabricAcceptThread.setDaemon(true);
        forgeAcceptThread.start();
        fabricAcceptThread.start();
    }

    /**
     * Registers a handler that receives every non-control packet together with
     * the host that sent it.
     */
    public void onPacket(PacketHandler handler) {
        handlers.add(Objects.requireNonNull(handler, "handler"));
    }

    /**
     * Sends a packet to the given host.
     *
     * @return {@code true} if the packet was written
     */
    public boolean sendTo(HyperCoreRole role, Packet packet) {
        IpcChannel channel = channels.get(role);
        if (channel == null || channel.isClosed()) {
            return false;
        }
        try {
            channel.send(packet);
            return true;
        } catch (IOException error) {
            LOGGER.debug("Failed to send to {}: {}", role.displayName(), error.getMessage());
            return false;
        }
    }

    /**
     * Broadcasts a packet to every connected host.
     *
     * @return the number of hosts that accepted the packet
     */
    public int broadcast(Packet packet) {
        int delivered = 0;
        for (HyperCoreRole role : new HyperCoreRole[]{HyperCoreRole.FORGE_HOST, HyperCoreRole.FABRIC_HOST}) {
            if (sendTo(role, packet)) {
                delivered++;
            }
        }
        return delivered;
    }

    /**
     * Returns a snapshot of connected peers for diagnostics.
     */
    public Map<HyperCoreRole, Peer> peers() {
        return Map.copyOf(peers);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        closeQuietly(forgeServerSocket);
        closeQuietly(fabricServerSocket);
        forgeServerSocket = null;
        fabricServerSocket = null;
        for (IpcChannel channel : channels.values()) {
            channel.close();
        }
        channels.clear();
        peers.clear();
        readers.shutdownNow();
        try {
            if (!readers.awaitTermination(5, TimeUnit.SECONDS)) {
                LOGGER.warn("Bridge reader pool did not terminate promptly");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
        started.set(false);
        LOGGER.info("Bridge server stopped");
    }

    private void acceptLoop(ServerSocket serverSocket, HyperCoreRole expectedRole) {
        while (!closed.get() && serverSocket != null && !serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                readers.execute(() -> serve(socket, expectedRole));
            } catch (IOException error) {
                if (!closed.get()) {
                    LOGGER.debug("Accept failed on {}: {}", expectedRole.displayName(), error.getMessage());
                }
            }
        }
    }

    private void serve(Socket socket, HyperCoreRole expectedRole) {
        try (IpcChannel channel = IpcChannel.accept(socket)) {
            socket.setSoTimeout((int) HANDSHAKE_TIMEOUT.toMillis());
            Packet first = channel.receive();
            socket.setSoTimeout(0);
            if (!(first instanceof HandshakePacket handshake)) {
                LOGGER.warn("Rejected connection on {}: expected handshake, got {}",
                    expectedRole.displayName(), first == null ? "EOF" : first.getClass().getSimpleName());
                return;
            }
            if (handshake.role() != expectedRole) {
                LOGGER.warn(
                    "Rejected handshake on {} port: role mismatch (expected {}, got {})",
                    expectedRole.displayName(),
                    expectedRole,
                    handshake.role()
                );
                return;
            }
            if (handshake.protocolVersion() != PacketCodec.PROTOCOL_VERSION) {
                LOGGER.warn(
                    "Rejected handshake from {}: protocol version mismatch (local {}, remote {})",
                    expectedRole.displayName(),
                    PacketCodec.PROTOCOL_VERSION,
                    handshake.protocolVersion()
                );
                return;
            }
            channels.put(expectedRole, channel);
            peers.put(expectedRole, new Peer(handshake.minecraftVersion(), handshake.hostName(), channel.remoteAddress().toString()));
            channel.send(new AckPacket(handshake.sequence()));
            LOGGER.info(
                "{} host connected: Minecraft {}, name {}",
                expectedRole.displayName(),
                handshake.minecraftVersion(),
                handshake.hostName()
            );

            Packet packet;
            while (!closed.get() && (packet = channel.receive()) != null) {
                route(expectedRole, packet);
            }
        } catch (IOException error) {
            LOGGER.debug("Connection from {} ended: {}", expectedRole.displayName(), error.getMessage());
        } finally {
            channels.remove(expectedRole);
            peers.remove(expectedRole);
            LOGGER.info("{} host disconnected", expectedRole.displayName());
        }
    }

    private void route(HyperCoreRole source, Packet packet) {
        if (packet instanceof HeartbeatPacket heartbeat) {
            // Echo back so the host can measure round-trip latency.
            sendTo(source, heartbeat);
            return;
        }
        if (packet instanceof AckPacket) {
            // Ack packets are host-issued confirmations; nothing to route.
            return;
        }
        for (PacketHandler handler : handlers) {
            try {
                handler.handle(source, packet);
            } catch (RuntimeException error) {
                LOGGER.error("Bridge handler failed for {}", packet.getClass().getSimpleName(), error);
            }
        }
    }

    private static void closeQuietly(ServerSocket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Nothing to surface when closing a listener.
            }
        }
    }

    /**
     * Receives routed packets together with their source host.
     */
    @FunctionalInterface
    public interface PacketHandler {
        void handle(HyperCoreRole source, Packet packet);
    }

    /**
     * Snapshot of a connected peer.
     */
    public record Peer(String minecraftVersion, String hostName, String address) {
    }
}
