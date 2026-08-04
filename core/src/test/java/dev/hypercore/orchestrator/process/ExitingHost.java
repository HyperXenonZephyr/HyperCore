package dev.hypercore.orchestrator.process;

/**
 * Mock host process that exits immediately without printing the readiness
 * marker, used to verify the orchestrator reports hosts that die during startup.
 */
public final class ExitingHost {
    private ExitingHost() {
    }

    public static void main(String[] args) {
        System.exit(2);
    }
}
