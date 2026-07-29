package dev.hypercore.hardware;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeCapabilitiesTest {
    @Test
    void reportsJvmAndCpuWithoutForcingGpuProbe() {
        RuntimeCapabilities capabilities = RuntimeCapabilities.detect(false);

        assertTrue(capabilities.logicalProcessors() >= 1);
        assertNotEquals("unknown", capabilities.operatingSystem());
        assertNotEquals("unknown", capabilities.architecture());
        assertNotEquals("unknown", capabilities.javaVersion());
        assertFalse(capabilities.gpu().attempted());
        assertTrue(capabilities.gpu().devices().isEmpty());
    }
}
