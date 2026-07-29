package dev.hypercore.compute;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuOffloadPolicyTest {
    @Test
    void keepsCpuFallbackUntilBackendAndVulkanAreAvailable() {
        GpuOffloadPolicy policy = new GpuOffloadPolicy(1_024);

        assertEquals(
            GpuOffloadPolicy.Reason.BACKEND_UNAVAILABLE,
            policy.evaluate(10_000, true, false).reason()
        );
        assertEquals(
            GpuOffloadPolicy.Reason.VULKAN_UNAVAILABLE,
            policy.evaluate(10_000, false, true).reason()
        );
        assertEquals(
            GpuOffloadPolicy.Reason.BELOW_BATCH_THRESHOLD,
            policy.evaluate(512, true, true).reason()
        );
    }

    @Test
    void marksLargeBatchesEligibleOnlyWhenBothCapabilitiesExist() {
        GpuOffloadPolicy.Decision decision = new GpuOffloadPolicy(1_024)
            .evaluate(1_024, true, true);

        assertTrue(decision.offload());
        assertEquals(GpuOffloadPolicy.Reason.ELIGIBLE, decision.reason());
        assertFalse(new GpuOffloadPolicy(1_024).evaluate(0, true, true).offload());
    }
}
