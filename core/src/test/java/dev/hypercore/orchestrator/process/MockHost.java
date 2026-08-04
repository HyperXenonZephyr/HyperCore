package dev.hypercore.orchestrator.process;

/**
 * Mock host process used by orchestrator tests.
 *
 * <p>Prints the configured readiness marker to stdout and then idles until
 * terminated, mimicking a dedicated server that signals the orchestrator when
 * its bridge endpoint is up.
 */
public final class MockHost {
    private MockHost() {
    }

    public static void main(String[] args) throws Exception {
        System.out.println(args.length == 0 ? "MOCK READY" : args[0]);
        System.out.flush();
        while (true) {
            Thread.sleep(1_000);
        }
    }
}
