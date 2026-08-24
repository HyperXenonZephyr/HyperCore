package dev.hypercore.server.vulkan;

import dev.hypercore.server.compute.ComputeBackendProvider;
import dev.hypercore.server.compute.ComputeRequest;
import dev.hypercore.server.compute.ComputeResult;
import java.util.Objects;
import org.lwjgl.system.Configuration;

/**
 * Optional f32 Vulkan implementation. Loading this class requires LWJGL only
 * from the compute-providers directory, never from the base server jar.
 */
public final class VulkanComputeBackendProvider implements ComputeBackendProvider {
    public static final String ID = "gpu-vulkan-f32";
    static final String SQUARED_LENGTH_OPERATION = "squared-length-3d-f32";

    private volatile VulkanSquaredLengthKernel kernel;
    private volatile Throwable initializationFailure;
    private volatile boolean closed;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean available() {
        return this.kernelOrNull() != null;
    }

    @Override
    public ComputeResult compute(final ComputeRequest request) {
        Objects.requireNonNull(request, "request");
        if (!SQUARED_LENGTH_OPERATION.equals(request.operation())) {
            throw new UnsupportedOperationException("Unsupported Vulkan operation: " + request.operation());
        }
        VulkanSquaredLengthKernel active = this.kernelOrNull();
        if (active == null) {
            throw new IllegalStateException("Vulkan compute is unavailable", this.initializationFailure);
        }
        return new ComputeResult(ID, active.squaredLengths(request.values()));
    }

    @Override
    public synchronized void close() {
        this.closed = true;
        VulkanSquaredLengthKernel active = this.kernel;
        this.kernel = null;
        if (active != null) {
            active.close();
        }
    }

    Throwable initializationFailure() {
        return this.initializationFailure;
    }

    private VulkanSquaredLengthKernel kernelOrNull() {
        VulkanSquaredLengthKernel active = this.kernel;
        if (active != null || this.initializationFailure != null || this.closed) {
            return active;
        }
        synchronized (this) {
            active = this.kernel;
            if (active != null || this.initializationFailure != null || this.closed) {
                return active;
            }
            try {
                // STACK_SIZE is configured in KiB. Shader compilation and
                // Vulkan struct setup exceed the default 64 KiB stack.
                if (Configuration.STACK_SIZE.get(64) < 512) {
                    Configuration.STACK_SIZE.set(512);
                }
                active = new VulkanSquaredLengthKernel();
                this.kernel = active;
                return active;
            } catch (Throwable failure) {
                this.initializationFailure = failure;
                return null;
            }
        }
    }
}
