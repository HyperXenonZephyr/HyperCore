package dev.hypercore.bridge.ipc;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.Objects;

/**
 * A versioned, length-prefixed message channel over a TCP socket.
 *
 * <p>Wraps a {@link Socket} with {@link DataInputStream}/{@link DataOutputStream}
 * and {@link PacketCodec} framing. Writes are serialized under an internal lock
 * so multiple threads can send concurrently; reads are blocking and must run on
 * a dedicated thread.
 */
public final class IpcChannel implements AutoCloseable {
    private final Socket socket;
    private final DataInputStream input;
    private final DataOutputStream output;
    private final Object writeLock = new Object();

    private IpcChannel(Socket socket) throws IOException {
        this.socket = Objects.requireNonNull(socket, "socket");
        this.socket.setTcpNoDelay(true);
        this.input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        this.output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
    }

    /**
     * Connects to the given endpoint, blocking until the connection is
     * established or the timeout elapses.
     */
    public static IpcChannel connect(String host, int port, Duration timeout) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), (int) timeout.toMillis());
        return new IpcChannel(socket);
    }

    /**
     * Wraps an already-accepted server-side socket.
     */
    public static IpcChannel accept(Socket socket) throws IOException {
        return new IpcChannel(socket);
    }

    /**
     * Sends a packet. Safe to call from multiple threads.
     */
    public void send(Packet packet) throws IOException {
        Objects.requireNonNull(packet, "packet");
        synchronized (writeLock) {
            PacketCodec.writeFrame(output, packet);
            output.flush();
        }
    }

    /**
     * Blocks until the next packet arrives.
     *
     * @return the packet, or {@code null} on clean EOF at a frame boundary
     */
    public Packet receive() throws IOException {
        try {
            return PacketCodec.readFrame(input);
        } catch (EOFException error) {
            return null;
        }
    }

    /**
     * Returns the remote address of the connected peer.
     */
    public InetSocketAddress remoteAddress() {
        return (InetSocketAddress) socket.getRemoteSocketAddress();
    }

    public boolean isClosed() {
        return socket.isClosed();
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // Closing a socket that is already closed is a no-op on most
            // platforms; nothing to surface here.
        }
    }
}
