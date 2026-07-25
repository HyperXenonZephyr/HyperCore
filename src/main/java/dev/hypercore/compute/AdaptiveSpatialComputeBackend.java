package dev.hypercore.compute;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public final class AdaptiveSpatialComputeBackend implements SpatialComputeBackend, AutoCloseable {
    public static final String VULKAN_ID = "adaptive-vulkan";

    private static final Logger LOGGER = LogUtils.getLogger();

    private final ScalarSpatialComputeBackend cpu = new ScalarSpatialComputeBackend();
    private final GpuOffloadPolicy policy;
    private final AtomicLong cpuBatches = new AtomicLong();
    private final AtomicLong gpuBatches = new AtomicLong();
    private final AtomicLong gpuFailures = new AtomicLong();
    private volatile VulkanSpatialComputeBackend gpu;
    private volatile String unavailableReason;

    private AdaptiveSpatialComputeBackend(
        GpuOffloadPolicy policy,
        VulkanSpatialComputeBackend gpu,
        String unavailableReason
    ) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.gpu = gpu;
        this.unavailableReason = Objects.requireNonNullElse(unavailableReason, "");
    }

    public static AdaptiveSpatialComputeBackend create(GpuOffloadPolicy policy, boolean enabled) {
        if (!enabled) {
            return new AdaptiveSpatialComputeBackend(policy, null, "disabled by configuration");
        }
        VulkanSpatialComputeBackend gpu = null;
        try {
            gpu = VulkanSpatialComputeBackend.create();
            verify(gpu);
            return new AdaptiveSpatialComputeBackend(policy, gpu, "");
        } catch (RuntimeException | LinkageError error) {
            if (gpu != null) {
                try {
                    gpu.close();
                } catch (RuntimeException closeError) {
                    error.addSuppressed(closeError);
                }
            }
            String reason = error.getClass().getSimpleName() + ": " + normalize(error.getMessage());
            LOGGER.warn("Vulkan compute initialization failed; using cpu-scalar: {}", reason);
            return new AdaptiveSpatialComputeBackend(policy, null, reason);
        }
    }

    public static AdaptiveSpatialComputeBackend unavailable(GpuOffloadPolicy policy, String reason) {
        return new AdaptiveSpatialComputeBackend(policy, null, reason);
    }

    @Override
    public String id() {
        return gpu == null ? ScalarSpatialComputeBackend.ID : VULKAN_ID;
    }

    @Override
    public ComputeDeviceType deviceType() {
        return gpu == null ? ComputeDeviceType.CPU : ComputeDeviceType.GPU;
    }

    @Override
    public void squaredDistances(
        float originX,
        float originY,
        float originZ,
        float[] positionsX,
        float[] positionsY,
        float[] positionsZ,
        float[] output
    ) {
        int size = validate(positionsX, positionsY, positionsZ, output);
        VulkanSpatialComputeBackend currentGpu = gpu;
        GpuOffloadPolicy.Decision decision = policy.evaluate(size, currentGpu != null, currentGpu != null);
        if (decision.offload()) {
            try {
                currentGpu.squaredDistances(originX, originY, originZ, positionsX, positionsY, positionsZ, output);
                gpuBatches.incrementAndGet();
                return;
            } catch (VulkanSpatialComputeBackend.BatchNotSupportedException unsupported) {
                // This batch can still run correctly on the scalar fallback.
            } catch (RuntimeException | LinkageError error) {
                disableGpu(currentGpu, error);
            }
        }

        cpu.squaredDistances(originX, originY, originZ, positionsX, positionsY, positionsZ, output);
        cpuBatches.incrementAndGet();
    }

    public Status status() {
        VulkanSpatialComputeBackend currentGpu = gpu;
        return new Status(
            currentGpu != null,
            currentGpu == null ? "" : currentGpu.deviceName(),
            policy.minimumBatchSize(),
            cpuBatches.get(),
            gpuBatches.get(),
            gpuFailures.get(),
            unavailableReason
        );
    }

    @Override
    public synchronized void close() {
        VulkanSpatialComputeBackend currentGpu = gpu;
        gpu = null;
        if (currentGpu != null) {
            currentGpu.close();
        }
    }

    private synchronized void disableGpu(VulkanSpatialComputeBackend failedGpu, Throwable error) {
        if (gpu != failedGpu) {
            return;
        }
        gpu = null;
        gpuFailures.incrementAndGet();
        unavailableReason = error.getClass().getSimpleName() + ": " + normalize(error.getMessage());
        LOGGER.error("Vulkan compute failed and has been disabled; using cpu-scalar", error);
        try {
            failedGpu.close();
        } catch (RuntimeException closeError) {
            LOGGER.warn("Vulkan compute cleanup also failed", closeError);
        }
    }

    private static void verify(VulkanSpatialComputeBackend gpu) {
        int size = 1_024;
        float[] x = new float[size];
        float[] y = new float[size];
        float[] z = new float[size];
        float[] expected = new float[size];
        float[] actual = new float[size];
        for (int index = 0; index < size; index++) {
            x[index] = index * 0.25f - 30.0f;
            y[index] = index % 17 - 8.0f;
            z[index] = index % 31 * 0.5f;
        }
        new ScalarSpatialComputeBackend().squaredDistances(1.25f, -2.5f, 4.0f, x, y, z, expected);
        gpu.squaredDistances(1.25f, -2.5f, 4.0f, x, y, z, actual);
        for (int index = 0; index < size; index++) {
            float tolerance = Math.max(1.0e-4f, Math.abs(expected[index]) * 1.0e-5f);
            if (Math.abs(expected[index] - actual[index]) > tolerance) {
                throw new IllegalStateException(
                    "Vulkan self-test mismatch at index " + index
                        + ": expected=" + expected[index]
                        + ", actual=" + actual[index]
                );
            }
        }
    }

    private static String normalize(String message) {
        return message == null || message.isBlank() ? "unknown" : message.trim();
    }

    private static int validate(float[] x, float[] y, float[] z, float[] output) {
        Objects.requireNonNull(x, "positionsX");
        Objects.requireNonNull(y, "positionsY");
        Objects.requireNonNull(z, "positionsZ");
        Objects.requireNonNull(output, "output");
        int size = x.length;
        if (y.length != size || z.length != size || output.length < size) {
            throw new IllegalArgumentException("Position arrays must have equal lengths and fit in output");
        }
        return size;
    }

    public record Status(
        boolean gpuAvailable,
        String deviceName,
        int minimumBatchSize,
        long cpuBatches,
        long gpuBatches,
        long gpuFailures,
        String unavailableReason
    ) {
    }
}
