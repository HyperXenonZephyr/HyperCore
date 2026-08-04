package dev.hypercore.orchestrator;

import java.util.Locale;

/**
 * Runtime role of the current HyperCore process in an orchestrated deployment.
 *
 * <p>The role is read once from the {@code hypercore.role} system property,
 * which the orchestrator injects into each host's JVM command line. In
 * standalone mode (no property) every process behaves exactly as before and
 * role-dependent code paths are inert.
 */
public enum HyperCoreRole {
    /**
     * A plain server process; either a Forge or a Fabric dedicated server
     * running without orchestration. No bridge is opened.
     */
    STANDALONE,
    /**
     * The coordinating process that launches and monitors the two hosts and
     * routes IPC between them.
     */
    ORCHESTRATOR,
    /**
     * A Forge dedicated server hosting Forge mods and the HyperCore-Forge mod.
     */
    FORGE_HOST,
    /**
     * A Fabric dedicated server hosting Fabric mods and the HyperCore-Fabric mod.
     */
    FABRIC_HOST;

    /**
     * Returns the role of the current process from the {@code hypercore.role}
     * system property, defaulting to {@link #STANDALONE}.
     */
    public static HyperCoreRole current() {
        return fromSystemProperty(System.getProperty("hypercore.role"));
    }

    /**
     * Parses a role name, ignoring case and surrounding whitespace. Unknown or
     * blank values map to {@link #STANDALONE}.
     */
    public static HyperCoreRole fromSystemProperty(String value) {
        if (value == null || value.isBlank()) {
            return STANDALONE;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            return STANDALONE;
        }
    }

    /**
     * Returns whether this role runs a Minecraft host that opens a bridge
     * endpoint back to the orchestrator.
     */
    public boolean isHost() {
        return this == FORGE_HOST || this == FABRIC_HOST;
    }

    /**
     * Returns whether this role participates in the world-state bridge.
     */
    public boolean bridgesWorldState() {
        return isHost();
    }

    /**
     * Human-readable label used for logs and command output.
     */
    public String displayName() {
        return switch (this) {
            case STANDALONE -> "standalone";
            case ORCHESTRATOR -> "orchestrator";
            case FORGE_HOST -> "forge-host";
            case FABRIC_HOST -> "fabric-host";
        };
    }
}
