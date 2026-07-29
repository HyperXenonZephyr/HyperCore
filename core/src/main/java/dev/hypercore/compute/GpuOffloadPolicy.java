package dev.hypercore.compute;

public final class GpuOffloadPolicy {
    private final int minimumBatchSize;

    public GpuOffloadPolicy(int minimumBatchSize) {
        if (minimumBatchSize < 1) {
            throw new IllegalArgumentException("minimumBatchSize must be positive");
        }
        this.minimumBatchSize = minimumBatchSize;
    }

    public int minimumBatchSize() {
        return minimumBatchSize;
    }

    public Decision evaluate(int batchSize, boolean vulkanAvailable, boolean gpuBackendAvailable) {
        if (batchSize < 0) {
            throw new IllegalArgumentException("batchSize cannot be negative");
        }
        if (!gpuBackendAvailable) {
            return new Decision(false, Reason.BACKEND_UNAVAILABLE);
        }
        if (!vulkanAvailable) {
            return new Decision(false, Reason.VULKAN_UNAVAILABLE);
        }
        if (batchSize < minimumBatchSize) {
            return new Decision(false, Reason.BELOW_BATCH_THRESHOLD);
        }
        return new Decision(true, Reason.ELIGIBLE);
    }

    public enum Reason {
        ELIGIBLE,
        BACKEND_UNAVAILABLE,
        VULKAN_UNAVAILABLE,
        BELOW_BATCH_THRESHOLD
    }

    public record Decision(boolean offload, Reason reason) {
    }
}
