package dev.hypercore.compute;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CpuBackendSelectorTest {
    private static boolean vectorAvailable() {
        return VectorBackendFactory.tryLoad().isPresent();
    }

    @Test
    void scalarPreferenceAlwaysSelectsScalar() {
        SpatialComputeBackend backend = CpuBackendSelector.select("scalar");
        assertEquals(ScalarSpatialComputeBackend.ID, backend.id());
    }

    @Test
    void autoPreferenceSelectsVectorWhenAvailable() {
        Assumptions.assumeTrue(vectorAvailable(), "jdk.incubator.vector not available");
        SpatialComputeBackend backend = CpuBackendSelector.select("auto");
        assertEquals(VectorSpatialComputeBackend.ID, backend.id());
    }

    @Test
    void vectorPreferenceSelectsVectorWhenAvailable() {
        Assumptions.assumeTrue(vectorAvailable(), "jdk.incubator.vector not available");
        SpatialComputeBackend backend = CpuBackendSelector.select("vector");
        assertEquals(VectorSpatialComputeBackend.ID, backend.id());
    }
}
