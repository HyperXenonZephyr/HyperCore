package dev.hypercore.bridge;

import dev.hypercore.orchestrator.HyperCoreRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies role parsing and host bridge configuration from system properties.
 */
class BridgeHostConfigTest {

    @Test
    void parsesRoleNames() {
        assertEquals(HyperCoreRole.FORGE_HOST, HyperCoreRole.fromSystemProperty("FORGE_HOST"));
        assertEquals(HyperCoreRole.FABRIC_HOST, HyperCoreRole.fromSystemProperty(" FABRIC_HOST ".toUpperCase()));
        assertEquals(HyperCoreRole.STANDALONE, HyperCoreRole.fromSystemProperty("  "));
        assertEquals(HyperCoreRole.STANDALONE, HyperCoreRole.fromSystemProperty("bogus"));
        assertEquals(HyperCoreRole.STANDALONE, HyperCoreRole.fromSystemProperty(null));
    }

    @Test
    void hostRolesBridgeWorldState() {
        assertTrue(HyperCoreRole.FORGE_HOST.isHost());
        assertTrue(HyperCoreRole.FABRIC_HOST.isHost());
        assertFalse(HyperCoreRole.STANDALONE.isHost());
        assertFalse(HyperCoreRole.ORCHESTRATOR.isHost());
    }

    @Test
    void readsHostConfigFromSystemProperties() {
        String previousRole = System.setProperty("hypercore.role", "FORGE_HOST");
        String previousPort = System.setProperty("hypercore.orchestrator.port", "35123");
        try {
            BridgeHostConfig config = BridgeHostConfig.fromSystemProperties();
            assertEquals(HyperCoreRole.FORGE_HOST, config.role());
            assertEquals(35_123, config.orchestratorPort());
            assertTrue(config.bridgeTickMillis() > 0);
            assertFalse(config.readyMarker().isBlank());
        } finally {
            restore(previousRole, "hypercore.role");
            restore(previousPort, "hypercore.orchestrator.port");
        }
    }

    @Test
    void returnsNullForStandaloneProcesses() {
        String previousRole = System.setProperty("hypercore.role", "STANDALONE");
        try {
            assertNull(BridgeHostConfig.fromSystemProperties());
        } finally {
            restore(previousRole, "hypercore.role");
        }
    }

    private static void restore(String previous, String key) {
        if (previous == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previous);
        }
    }
}
