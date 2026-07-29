package dev.hypercore.hardware;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class VulkanRuntimeProbeTest {
    @Test
    void formatsVulkanApiVersions() {
        int version = (1 << 22) | (3 << 12) | 280;
        assertEquals("1.3.280", VulkanRuntimeProbe.formatVersion(version));
    }

    @Test
    void disabledProbeDoesNotClaimAvailability() {
        VulkanRuntimeProbe.Result result = VulkanRuntimeProbe.disabled();

        assertFalse(result.attempted());
        assertFalse(result.available());
        assertEquals("", result.apiVersion());
    }
}
