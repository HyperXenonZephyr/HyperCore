package dev.hypercore.bridge;

import dev.hypercore.bridge.ipc.IpcChannel;
import dev.hypercore.bridge.ipc.Packet;
import dev.hypercore.bridge.ipc.PacketCodec;
import dev.hypercore.bridge.ipc.packet.AckPacket;
import dev.hypercore.bridge.ipc.packet.HandshakePacket;
import dev.hypercore.bridge.ipc.packet.HeartbeatPacket;
import dev.hypercore.bridge.world.BridgeLink;
import dev.hypercore.orchestrator.HyperCoreRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Loader-agnostic host-side bridge endpoint.
 *
 * <p>Connects to the orchestrator, completes the handshake, exchanges
 * heartbeats, and dispatches incoming packets to a handler registered by the
 * loader adapter. Reconnects automatically with a backoff when the orchestrator
 * is unavailable or the connection drops. Once the handshake is acknowledged the
 * endpoint prints the configured ready marker to stdout so the orchestrator's
 * {@code ServerProcess} can detect readiness.
 */
public final class BridgeEndpoint implements BridgeLink, AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(BridgeEndpoint.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final long RECONNECT_DELAY_MILLIS = 1_000;

    private final HyperCoreRole role;
    private final String orchestratorHost;
    private final int orchestratorPort;
    private final long tickMillis;
    private final String minecraftVersion;
    private final String hostName;
    private final String readyMarker;
    private final PacketHandler handler;

    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean handshaken = new AtomicBoolean();
    private final AtomicBoolean markerPrinted = new AtomicBoolean();
    private final AtomicLong nextSequence = new AtomicLong();
    private final AtomicLong handshakeSequence = new AtomicLong();
    private final AtomicLong lastLatencyMillis = new AtomicLong(-1);

    private final Object connectLock = new Object();
    private final CopyOnWriteArrayList<Runnable> connectListeners = new CopyOnWriteArrayList<>();
    private volatile IpcChannel channel;
    private volatile Thread readerThread;
    private volatile ScheduledExecutorService heartbeats;

    /**
     * @param role this host's role
     * @param orchestratorHost orchestrator address
     * @param orchestratorPort orchestrator port for this role
     * @param tickMillis heartbeat and bridge tick interval
     * @param minecraftVersion version reported in the handshake
     * @param hostName human-readable host name reported in the handshake
     * @param readyMarker stdout marker printed once the handshake completes
     * @param handler receives every packet from the orchestrator
     */
    public BridgeEndpoint(
        HyperCoreRole role,
        String orchestratorHost,
        int orchestratorPort,
        long tickMillis,
        String minecraftVersion,
        String hostName,
        String readyMarker,
        PacketHandler handler
    ) {
        this.role = Objects.requireNonNull(role, "role");
        this.orchestratorHost = Objects.requireNonNull(orchestratorHost, "orchestratorHost");
        this.orchestratorPort = orchestratorPort;
        this.tickMillis = tickMillis;
        this.minecraftVersion = Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        this.hostName = Objects.requireNonNullElse(hostName, role.displayName());
        this.readyMarker = Objects.requireNonNull(readyMarker, "readyMarker");
        this.handler = Objects.requireNonNull(handler, "handler");
        if (!role.isHost()) {
            throw new IllegalArgumentException("Bridge endpoints are only opened by host roles: " + role);
        }
    }

    /**
     * Connects to the orchestrator and starts heartbeats and the reader loop.
     * Returns immediately; connection happens on a background thread.
     *
     * @throws IllegalStateException if the endpoint has already been started
     */
    public void start() {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("Bridge endpoint is already started");
        }
        heartbeats = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread heartbeat = new Thread(runnable, "bridge-heartbeat-" + role.displayName());
            heartbeat.setDaemon(true);
            return heartbeat;
        });
        heartbeats.scheduleWithFixedDelay(this::sendHeartbeat, tickMillis, tickMillis, TimeUnit.MILLISECONDS);
        Thread thread = new Thread(this::run, "bridge-endpoint-" + role.displayName());
        thread.setDaemon(true);
        readerThread = thread;
        thread.start();
    }

    /**
     * Registers a listener invoked every time the handshake with the
     * orchestrator completes successfully (including reconnections).
     */
    public void onConnected(Runnable listener) {
        connectListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * Sends a packet to the orchestrator.
     *
     * @return {@code true} if the packet was written, {@code false} if the
     *         endpoint is not connected
     */
    public boolean send(Packet packet) {
        IpcChannel current = channel;
        if (current == null || current.isClosed()) {
            return false;
        }
        try {
            current.send(packet);
            return true;
        } catch (IOException error) {
            LOGGER.debug("Failed to send packet to orchestrator: {}", error.getMessage());
            return false;
        }
    }

    /**
     * Returns whether a live, handshaken connection to the orchestrator exists.
     */
    public boolean isConnected() {
        IpcChannel current = channel;
        return current != null && !current.isClosed() && handshaken.get();
    }

    /**
     * Returns the measured round-trip latency in milliseconds, or {@code -1} if
     * no heartbeat echo has been received yet.
     */
    public long lastLatencyMillis() {
        return lastLatencyMillis.get();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        ScheduledExecutorService heartbeats = this.heartbeats;
        this.heartbeats = null;
        if (heartbeats != null) {
            heartbeats.shutdownNow();
            try {
                if (!heartbeats.awaitTermination(5, TimeUnit.SECONDS)) {
                    LOGGER.warn("Heartbeat executor did not terminate promptly");
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
        }
        IpcChannel current = channel;
        channel = null;
        if (current != null) {
            current.close();
        }
        Thread reader = readerThread;
        readerThread = null;
        if (reader != null) {
            reader.interrupt();
            try {
                reader.join(5_000);
                if (reader.isAlive()) {
                    LOGGER.warn("Bridge reader thread did not terminate promptly");
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void run() {
        while (!closed.get()) {
            if (!connectAndServe()) {
                if (closed.get()) {
                    return;
                }
                sleepQuietly(RECONNECT_DELAY_MILLIS);
            }
        }
    }

    /**
     * Attempts one connection and serves it until it drops.
     *
     * @return {@code true} if a connection was established (regardless of how it
     *         later ended), {@code false} if it could not be established
     */
    private boolean connectAndServe() {
        IpcChannel connected;
        synchronized (connectLock) {
            if (closed.get()) {
                return false;
            }
            try {
                connected = IpcChannel.connect(orchestratorHost, orchestratorPort, CONNECT_TIMEOUT);
            } catch (IOException error) {
                LOGGER.debug(
                    "Cannot reach orchestrator at {}:{}: {}",
                    orchestratorHost,
                    orchestratorPort,
                    error.getMessage()
                );
                return false;
            }
            channel = connected;
            handshaken.set(false);
        }
        LOGGER.info("Connected to orchestrator at {}:{}", orchestratorHost, orchestratorPort);
        long sequence = nextSequence.getAndIncrement();
        handshakeSequence.set(sequence);
        try {
            connected.send(new HandshakePacket(PacketCodec.PROTOCOL_VERSION, role, minecraftVersion, hostName, sequence));
        } catch (IOException error) {
            LOGGER.warn("Handshake send failed: {}", error.getMessage());
            connected.close();
            return true;
        }
        try {
            Packet packet;
            while (!closed.get() && (packet = connected.receive()) != null) {
                handle(connected, packet);
            }
        } catch (IOException error) {
            if (!closed.get()) {
                LOGGER.debug("Bridge connection to orchestrator dropped: {}", error.getMessage());
            }
        } finally {
            connected.close();
            if (channel == connected) {
                channel = null;
                handshaken.set(false);
            }
        }
        return true;
    }

    private void handle(IpcChannel current, Packet packet) {
        if (packet instanceof AckPacket ack && !handshaken.get()) {
            if (ack.sequence() == handshakeSequence.get()) {
                handshaken.set(true);
                if (markerPrinted.compareAndSet(false, true)) {
                    // The orchestrator watches this marker to detect readiness.
                    System.out.println(readyMarker);
                    System.out.flush();
                    LOGGER.info("Bridge handshake acknowledged for {}", role.displayName());
                }
                for (Runnable listener : connectListeners) {
                    try {
                        listener.run();
                    } catch (RuntimeException error) {
                        LOGGER.error("Connect listener failed", error);
                    }
                }
            }
            return;
        }
        if (packet instanceof HeartbeatPacket heartbeat) {
            // Orchestrator echoes heartbeats; measure round-trip latency.
            long latency = (System.nanoTime() - heartbeat.timestampNanos()) / 1_000_000;
            if (latency >= 0) {
                lastLatencyMillis.set(latency);
            }
            return;
        }
        try {
            handler.handle(packet);
        } catch (RuntimeException error) {
            LOGGER.error("Packet handler failed for {}", packet.getClass().getSimpleName(), error);
        }
    }

    private void sendHeartbeat() {
        if (!isConnected()) {
            return;
        }
        send(new HeartbeatPacket(nextSequence.getAndIncrement(), System.nanoTime()));
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Receives packets dispatched by the endpoint.
     */
    @FunctionalInterface
    public interface PacketHandler {
        void handle(Packet packet);
    }
}
