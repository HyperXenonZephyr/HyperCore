package dev.hypercore.compute;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AdaptiveSpatialComputeBackendTest {
    @Test
    void usesCpuFallbackAndTracksBatchesWhenGpuIsUnavailable() {
        try (AdaptiveSpatialComputeBackend backend = AdaptiveSpatialComputeBackend.unavailable(
            new GpuOffloadPolicy(1),
            "test unavailable"
        )) {
            float[] output = new float[3];
            backend.squaredDistances(
                1.0f,
                2.0f,
                3.0f,
                new float[]{1.0f, 4.0f, -1.0f},
                new float[]{2.0f, 6.0f, 0.0f},
                new float[]{3.0f, 3.0f, 5.0f},
                output
            );

            assertArrayEquals(new float[]{0.0f, 25.0f, 12.0f}, output);
            assertEquals(ScalarSpatialComputeBackend.ID, backend.id());
            assertFalse(backend.status().gpuAvailable());
            assertEquals(1, backend.status().cpuBatches());
            assertEquals(0, backend.status().gpuBatches());
            assertEquals("test unavailable", backend.status().unavailableReason());
        }
    }
}
