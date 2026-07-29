package dev.hypercore.compute;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * Selects the runtime CPU spatial compute backend from the {@code compute.cpuBackend}
 * configuration value.
 *
 * <p>{@code auto} (the default) prefers the Java Vector API backend when the
 * {@code jdk.incubator.vector} module is resolvable and falls back to scalar.
 * {@code scalar} always uses the scalar baseline. {@code vector} forces the vector
 * backend but still falls back to scalar with a warning if the incubator module is
 * unavailable, so a misconfiguration never prevents the server from starting.
 */
public final class CpuBackendSelector {
    public static final String AUTO = "auto";
    public static final String SCALAR = "scalar";
    public static final String VECTOR = "vector";

    private static final Logger LOGGER = LogUtils.getLogger();

    private CpuBackendSelector() {
    }

    public static SpatialComputeBackend select(String preference) {
        if (VECTOR.equalsIgnoreCase(preference)) {
            return VectorBackendFactory.tryLoad().orElseGet(() -> {
                LOGGER.warn(
                    "compute.cpuBackend=vector but jdk.incubator.vector is unavailable; falling back to cpu-scalar"
                );
                return new ScalarSpatialComputeBackend();
            });
        }
        if (SCALAR.equalsIgnoreCase(preference)) {
            return new ScalarSpatialComputeBackend();
        }
        // "auto" (and any unrecognized value): prefer vector when available.
        return VectorBackendFactory.tryLoad().orElseGet(ScalarSpatialComputeBackend::new);
    }
}
