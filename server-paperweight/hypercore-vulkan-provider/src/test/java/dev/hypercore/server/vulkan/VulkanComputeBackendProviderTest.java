package dev.hypercore.server.vulkan;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hypercore.server.compute.ComputeRequest;
import dev.hypercore.server.compute.ComputeBackendProvider;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

class VulkanComputeBackendProviderTest {
    @Test
    void convertsTheExplicitF32OperationIntoIndependentCoordinateBuffers() {
        VulkanSquaredLengthKernel.FloatTriples triples = VulkanSquaredLengthKernel.FloatTriples.from(new double[] {3, 4, 0, -1, 2, 2});
        assertArrayEquals(new float[] {3, -1}, triples.x());
        assertArrayEquals(new float[] {4, 2}, triples.y());
        assertArrayEquals(new float[] {0, 2}, triples.z());
    }

    @Test
    void rejectsInputsThatCannotHaveDefinedGpuF32Semantics() {
        assertThrows(IllegalArgumentException.class, () -> VulkanSquaredLengthKernel.FloatTriples.from(new double[] {1, 2}));
        assertThrows(IllegalArgumentException.class, () -> VulkanSquaredLengthKernel.FloatTriples.from(new double[] {Double.NaN, 0, 0}));
        assertThrows(IllegalArgumentException.class, () -> VulkanSquaredLengthKernel.FloatTriples.from(new double[] {Double.MAX_VALUE, 0, 0}));
    }

    @Test
    void providerConstructionDoesNotRequireVulkanUntilAvailabilityIsRequested() {
        assertDoesNotThrow(VulkanComputeBackendProvider::new);
    }

    @Test
    void publishesTheProviderThroughServiceLoader() {
        boolean found = ServiceLoader.load(ComputeBackendProvider.class).stream()
            .map(ServiceLoader.Provider::get)
            .anyMatch(provider -> provider.id().equals(VulkanComputeBackendProvider.ID));
        assertTrue(found);
    }

    @Test
    void availabilityProbeIsSafeAndExecutesThePipelineWhenThisHostHasVulkan() {
        try (VulkanComputeBackendProvider provider = new VulkanComputeBackendProvider()) {
            boolean available = provider.available();
            System.out.println("Vulkan provider available: " + available);
            if (!available) {
                Throwable failure = provider.initializationFailure();
                System.out.println("Vulkan provider initialization: " + (failure == null ? "not attempted" : failure.getClass().getSimpleName() + ": " + failure.getMessage()));
                return;
            }
            double[] output = provider.compute(new ComputeRequest("squared-length-3d-f32", new double[] {3, 4, 0, 1, 2, 2})).values();
            assertEquals(25.0, output[0]);
            assertEquals(9.0, output[1]);
        }
    }
}
