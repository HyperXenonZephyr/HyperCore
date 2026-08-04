package dev.hypercore.bridge.ipc;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * A versioned message exchanged over an {@link IpcChannel}.
 *
 * <p>Every packet carries a stable numeric type id and knows how to serialize
 * and deserialize itself. New packet types must be added to the
 * {@link PacketCodec} registry; changing a packet's wire format requires a
 * protocol version bump.
 */
public interface Packet {

    /**
     * Returns the stable numeric type id of this packet.
     */
    int typeId();

    /**
     * Serializes this packet's payload (excluding the type id and frame length)
     * to the given output stream.
     */
    void write(DataOutput out) throws IOException;
}
