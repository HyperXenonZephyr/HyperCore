package dev.hypercore.bridge;

/**
 * Read-only snapshot of the host bridge for diagnostics commands.
 *
 * <p>Standalone processes expose {@link #NONE}; the status is a no-op there.
 */
public interface BridgeStatusView {

    /**
     * Returns the display name of this host's role.
     */
    String roleName();

    /**
     * Returns whether a live, handshaken connection to the orchestrator exists.
     */
    boolean connected();

    /**
     * Returns the measured round-trip bridge latency in milliseconds, or
     * {@code -1} if none has been measured yet.
     */
    long latencyMillis();

    /**
     * Returns the number of world deltas shipped to the orchestrator.
     */
    long publishedDeltas();

    /**
     * Returns the number of world deltas dropped because the bridge was down.
     */
    long droppedDeltas();

    /**
     * Returns the number of commands mirrored from the remote host.
     */
    int mirroredCommands();

    /**
     * Returns a short summary of the reachable peers.
     */
    String peerSummary();

    /**
     * A bridge status for processes that do not participate in orchestration.
     */
    BridgeStatusView NONE = new BridgeStatusView() {
        @Override
        public String roleName() {
            return "standalone";
        }

        @Override
        public boolean connected() {
            return false;
        }

        @Override
        public long latencyMillis() {
            return -1;
        }

        @Override
        public long publishedDeltas() {
            return 0;
        }

        @Override
        public long droppedDeltas() {
            return 0;
        }

        @Override
        public int mirroredCommands() {
            return 0;
        }

        @Override
        public String peerSummary() {
            return "none (standalone)";
        }
    };
}
