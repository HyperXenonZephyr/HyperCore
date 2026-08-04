package dev.hypercore.bridge;

import dev.hypercore.orchestrator.HyperCoreRole;
import dev.hypercore.orchestrator.process.ProcessLauncher;

import java.util.Objects;

/**
 * Host-side bridge configuration derived from system properties.
 *
 * <p>The orchestrator injects these properties into each host's JVM command
 * line (see {@link ProcessLauncher}); hosts read them at startup so they need
 * no local configuration file. Standalone processes see no properties and
 * simply never open a bridge.
 */
public record BridgeHostConfig(
    HyperCoreRole role,
    String orchestratorHost,
    int orchestratorPort,
    long bridgeTickMillis,
    String readyMarker
) {
    public static final String DEFAULT_READY_MARKER = "[hypercore] BRIDGE READY";
    public static final long DEFAULT_BRIDGE_TICK_MILLIS = 50;

    public BridgeHostConfig {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(orchestratorHost, "orchestratorHost");
        if (orchestratorPort < 1 || orchestratorPort > 65_535) {
            throw new IllegalArgumentException("orchestratorPort must be within the valid port range");
        }
        if (bridgeTickMillis < 1) {
            throw new IllegalArgumentException("bridgeTickMillis must be positive");
        }
        readyMarker = Objects.requireNonNullElse(readyMarker, DEFAULT_READY_MARKER);
    }

    /**
     * Reads the bridge configuration from the current process's system
     * properties. Returns {@code null} when the process is not running as a
     * host (or the orchestrator port is missing), in which case bridging is
     * disabled.
     */
    public static BridgeHostConfig fromSystemProperties() {
        HyperCoreRole role = HyperCoreRole.current();
        if (!role.isHost()) {
            return null;
        }
        String portValue = System.getProperty(ProcessLauncher.ORCHESTRATOR_PORT_PROPERTY);
        if (portValue == null || portValue.isBlank()) {
            return null;
        }
        int port;
        try {
            port = Integer.parseInt(portValue.trim());
        } catch (NumberFormatException error) {
            return null;
        }
        long tickMillis = parseLong(
            System.getProperty("hypercore.bridge.tickMillis", String.valueOf(DEFAULT_BRIDGE_TICK_MILLIS)),
            DEFAULT_BRIDGE_TICK_MILLIS
        );
        String marker = System.getProperty("hypercore.orchestrator.readyMarker", DEFAULT_READY_MARKER);
        return new BridgeHostConfig(
            role,
            ProcessLauncher.orchestratorHost(),
            port,
            tickMillis,
            marker
        );
    }

    private static long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException error) {
            return fallback;
        }
    }
}
