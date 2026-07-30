package dev.hypercore.compute;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.Configuration;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkBufferMemoryBarrier;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkComputePipelineCreateInfo;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkDeviceQueueCreateInfo;
import org.lwjgl.vulkan.VkFenceCreateInfo;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPushConstantRange;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkQueueFamilyProperties;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkSubmitInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.system.MemoryUtil.memByteBuffer;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_SHADER_READ_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_API_VERSION_1_0;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_BUFFER_RESET_RELEASE_RESOURCES_BIT;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
import static org.lwjgl.vulkan.VK10.VK_FENCE_CREATE_SIGNALED_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT;
import static org.lwjgl.vulkan.VK10.VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU;
import static org.lwjgl.vulkan.VK10.VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_BIND_POINT_COMPUTE;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_TRANSFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_QUEUE_COMPUTE_BIT;
import static org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_COMPUTE_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHARING_MODE_EXCLUSIVE;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.VK_WHOLE_SIZE;
import static org.lwjgl.vulkan.VK10.vkAllocateCommandBuffers;
import static org.lwjgl.vulkan.VK10.vkAllocateDescriptorSets;
import static org.lwjgl.vulkan.VK10.vkAllocateMemory;
import static org.lwjgl.vulkan.VK10.vkBeginCommandBuffer;
import static org.lwjgl.vulkan.VK10.vkBindBufferMemory;
import static org.lwjgl.vulkan.VK10.vkCmdBindDescriptorSets;
import static org.lwjgl.vulkan.VK10.vkCmdBindPipeline;
import static org.lwjgl.vulkan.VK10.vkCmdCopyBuffer;
import static org.lwjgl.vulkan.VK10.vkCmdDispatch;
import static org.lwjgl.vulkan.VK10.vkCmdPipelineBarrier;
import static org.lwjgl.vulkan.VK10.vkCmdPushConstants;
import static org.lwjgl.vulkan.VK10.vkCreateBuffer;
import static org.lwjgl.vulkan.VK10.vkCreateCommandPool;
import static org.lwjgl.vulkan.VK10.vkCreateComputePipelines;
import static org.lwjgl.vulkan.VK10.vkCreateDescriptorPool;
import static org.lwjgl.vulkan.VK10.vkCreateDescriptorSetLayout;
import static org.lwjgl.vulkan.VK10.vkCreateDevice;
import static org.lwjgl.vulkan.VK10.vkCreateFence;
import static org.lwjgl.vulkan.VK10.vkCreateInstance;
import static org.lwjgl.vulkan.VK10.vkCreatePipelineLayout;
import static org.lwjgl.vulkan.VK10.vkCreateShaderModule;
import static org.lwjgl.vulkan.VK10.vkDestroyBuffer;
import static org.lwjgl.vulkan.VK10.vkDestroyCommandPool;
import static org.lwjgl.vulkan.VK10.vkDestroyDescriptorPool;
import static org.lwjgl.vulkan.VK10.vkDestroyDescriptorSetLayout;
import static org.lwjgl.vulkan.VK10.vkDestroyDevice;
import static org.lwjgl.vulkan.VK10.vkDestroyFence;
import static org.lwjgl.vulkan.VK10.vkDestroyInstance;
import static org.lwjgl.vulkan.VK10.vkDestroyPipeline;
import static org.lwjgl.vulkan.VK10.vkDestroyPipelineLayout;
import static org.lwjgl.vulkan.VK10.vkDestroyShaderModule;
import static org.lwjgl.vulkan.VK10.vkDeviceWaitIdle;
import static org.lwjgl.vulkan.VK10.vkEndCommandBuffer;
import static org.lwjgl.vulkan.VK10.vkEnumeratePhysicalDevices;
import static org.lwjgl.vulkan.VK10.vkFreeMemory;
import static org.lwjgl.vulkan.VK10.vkGetBufferMemoryRequirements;
import static org.lwjgl.vulkan.VK10.vkGetDeviceQueue;
import static org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceMemoryProperties;
import static org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceProperties;
import static org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceQueueFamilyProperties;
import static org.lwjgl.vulkan.VK10.vkMapMemory;
import static org.lwjgl.vulkan.VK10.vkQueueSubmit;
import static org.lwjgl.vulkan.VK10.vkResetCommandBuffer;
import static org.lwjgl.vulkan.VK10.vkResetFences;
import static org.lwjgl.vulkan.VK10.vkUnmapMemory;
import static org.lwjgl.vulkan.VK10.vkUpdateDescriptorSets;
import static org.lwjgl.vulkan.VK10.vkWaitForFences;

public final class VulkanSpatialComputeBackend implements ManagedSpatialComputeBackend {
    public static final String ID = "gpu-vulkan";

    private static final String DISTANCE_SHADER_RESOURCE = "/assets/hypercore/shaders/squared_distances.spv";
    private static final String RADIUS_MASK_SHADER_RESOURCE = "/assets/hypercore/shaders/radius_mask.spv";
    private static final int LOCAL_SIZE = 256;
    private static final int MAX_QUERIES_PER_SUBMISSION = 32;
    private static final long FENCE_TIMEOUT_NANOS = 30_000_000_000L;

    private final VkInstance instance;
    private final VkPhysicalDevice physicalDevice;
    private final VkDevice device;
    private final VkQueue queue;
    private final int queueFamilyIndex;
    private final String deviceName;
    private final int maximumWorkgroupsX;
    private final long maximumStorageBufferBytes;
    private final long descriptorSetLayout;
    private final long pipelineLayout;
    private final long shaderModule;
    private final long pipeline;
    private final long radiusMaskShaderModule;
    private final long radiusMaskPipeline;
    private final long descriptorPool;
    private final long descriptorSet;
    private final long commandPool;
    private final VkCommandBuffer commandBuffer;
    private final long fence;

    private BufferSet buffers;
    private long positionDataGeneration;
    // True when staging buffers hold position data newer than the device-local
    // buffers. Set by uploadPositions; cleared by the first record* that copies
    // staging into device-local. Resident queries that skip uploadPositions keep
    // this false and dispatch reads device-local VRAM directly.
    private boolean positionsDirty;
    private boolean closed;

    private VulkanSpatialComputeBackend() {
        VkInstance createdInstance = null;
        VkDevice createdDevice = null;
        long createdDescriptorSetLayout = NULL;
        long createdPipelineLayout = NULL;
        long createdShaderModule = NULL;
        long createdPipeline = NULL;
        long createdRadiusMaskShaderModule = NULL;
        long createdRadiusMaskPipeline = NULL;
        long createdDescriptorPool = NULL;
        long createdCommandPool = NULL;
        long createdFence = NULL;
        try {
            createdInstance = createInstance();
            DeviceCandidate selected = selectPhysicalDevice(createdInstance);
            physicalDevice = selected.device();
            queueFamilyIndex = selected.queueFamilyIndex();
            deviceName = selected.name();
            maximumWorkgroupsX = selected.maximumWorkgroupsX();
            maximumStorageBufferBytes = selected.maximumStorageBufferBytes();
            createdDevice = createDevice(physicalDevice, queueFamilyIndex);

            instance = createdInstance;
            device = createdDevice;
            queue = getQueue(device, queueFamilyIndex);
            createdDescriptorSetLayout = createDescriptorSetLayout(device);
            descriptorSetLayout = createdDescriptorSetLayout;
            createdPipelineLayout = createPipelineLayout(device, descriptorSetLayout);
            pipelineLayout = createdPipelineLayout;
            createdShaderModule = createShaderModule(device, DISTANCE_SHADER_RESOURCE);
            shaderModule = createdShaderModule;
            createdPipeline = createPipeline(device, pipelineLayout, shaderModule);
            pipeline = createdPipeline;
            createdRadiusMaskShaderModule = createShaderModule(device, RADIUS_MASK_SHADER_RESOURCE);
            radiusMaskShaderModule = createdRadiusMaskShaderModule;
            createdRadiusMaskPipeline = createPipeline(device, pipelineLayout, radiusMaskShaderModule);
            radiusMaskPipeline = createdRadiusMaskPipeline;
            createdDescriptorPool = createDescriptorPool(device);
            descriptorPool = createdDescriptorPool;
            descriptorSet = allocateDescriptorSet(device, descriptorPool, descriptorSetLayout);
            createdCommandPool = createCommandPool(device, queueFamilyIndex);
            commandPool = createdCommandPool;
            commandBuffer = allocateCommandBuffer(device, commandPool);
            createdFence = createFence(device);
            fence = createdFence;
        } catch (RuntimeException | LinkageError error) {
            destroyPartial(
                createdInstance,
                createdDevice,
                createdDescriptorSetLayout,
                createdPipelineLayout,
                createdShaderModule,
                createdPipeline,
                createdRadiusMaskShaderModule,
                createdRadiusMaskPipeline,
                createdDescriptorPool,
                createdCommandPool,
                createdFence
            );
            throw error;
        }
    }

    public static VulkanSpatialComputeBackend create() {
        int configuredStackSize = Configuration.STACK_SIZE.get(64 * 1_024);
        if (configuredStackSize < 512 * 1_024) {
            Configuration.STACK_SIZE.set(512 * 1_024);
        }
        return new VulkanSpatialComputeBackend();
    }

    public String deviceName() {
        return deviceName;
    }

    @Override
    public String transferMode() {
        return "device-local-staged";
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public ComputeDeviceType deviceType() {
        return ComputeDeviceType.GPU;
    }

    @Override
    public synchronized SpatialComputeBackend.PositionSnapshot prepareSnapshot(
        float[] positionsX,
        float[] positionsY,
        float[] positionsZ
    ) {
        ensureOpen();
        validatePositions(positionsX, positionsY, positionsZ);
        return prepareOwnedSnapshot(
            positionsX.clone(),
            positionsY.clone(),
            positionsZ.clone()
        );
    }

    synchronized SpatialComputeBackend.PositionSnapshot prepareOwnedSnapshot(
        float[] positionsX,
        float[] positionsY,
        float[] positionsZ
    ) {
        ensureOpen();
        int size = validatePositions(positionsX, positionsY, positionsZ);
        validateRadiusBatchSize(size);
        if (size != 0) {
            ensureCapacity(size);
            uploadPositions(positionsX, positionsY, positionsZ, size);
        }
        return new ResidentPositionSnapshot(
            this,
            positionsX,
            positionsY,
            positionsZ,
            positionDataGeneration
        );
    }

    @Override
    public synchronized void squaredDistances(
        float originX,
        float originY,
        float originZ,
        float[] positionsX,
        float[] positionsY,
        float[] positionsZ,
        float[] output
    ) {
        ensureOpen();
        int size = validate(positionsX, positionsY, positionsZ, output);
        if (size == 0) {
            return;
        }

        int workgroups = Math.ceilDiv(size, LOCAL_SIZE);
        long byteSize = (long) size * Float.BYTES;
        if (workgroups > maximumWorkgroupsX || byteSize > maximumStorageBufferBytes) {
            throw new BatchNotSupportedException("Batch exceeds the selected Vulkan device limits");
        }

        ensureCapacity(size);
        uploadPositions(positionsX, positionsY, positionsZ, size);
        record(originX, originY, originZ, size, workgroups);
        submitAndWait();
        download(buffers.output(), output, size);
    }

    @Override
    public synchronized void radiusMask(
        float originX,
        float originY,
        float originZ,
        float squaredRadius,
        float[] positionsX,
        float[] positionsY,
        float[] positionsZ,
        int[] outputWords
    ) {
        ensureOpen();
        int size = validateMask(positionsX, positionsY, positionsZ, outputWords);
        if (Float.isNaN(squaredRadius) || squaredRadius < 0.0f) {
            throw new IllegalArgumentException("squaredRadius must be non-negative");
        }
        if (size == 0) {
            return;
        }

        validateRadiusBatchSize(size);
        int wordCount = SpatialComputeBackend.maskWordCount(size);
        int workgroups = Math.ceilDiv(wordCount, LOCAL_SIZE);

        ensureCapacity(size);
        uploadPositions(positionsX, positionsY, positionsZ, size);
        recordRadiusMask(originX, originY, originZ, squaredRadius, size, workgroups);
        submitAndWait();
        downloadMask(buffers.output(), outputWords, wordCount);
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        vkDeviceWaitIdle(device);
        if (buffers != null) {
            buffers.close(device);
            buffers = null;
        }
        vkDestroyFence(device, fence, null);
        vkDestroyCommandPool(device, commandPool, null);
        vkDestroyDescriptorPool(device, descriptorPool, null);
        vkDestroyPipeline(device, radiusMaskPipeline, null);
        vkDestroyShaderModule(device, radiusMaskShaderModule, null);
        vkDestroyPipeline(device, pipeline, null);
        vkDestroyShaderModule(device, shaderModule, null);
        vkDestroyPipelineLayout(device, pipelineLayout, null);
        vkDestroyDescriptorSetLayout(device, descriptorSetLayout, null);
        vkDestroyDevice(device, null);
        vkDestroyInstance(instance, null);
    }

    private void ensureCapacity(int size) {
        if (buffers != null && buffers.capacity() >= size) {
            return;
        }
        int newCapacity = Math.max(1_024, Integer.highestOneBit(size - 1) << 1);
        if (newCapacity < size) {
            newCapacity = size;
        }
        vkDeviceWaitIdle(device);
        BufferSet replacement = BufferSet.create(device, physicalDevice, newCapacity);
        BufferSet previous = buffers;
        try {
            updateDescriptorSet(replacement);
        } catch (RuntimeException error) {
            replacement.close(device);
            throw error;
        }
        buffers = replacement;
        positionDataGeneration++;
        // Fresh device-local buffers hold no valid positions; the next upload
        // repopulates staging and the next record copies it into device-local.
        positionsDirty = false;
        if (previous != null) {
            previous.close(device);
        }
    }

    private void updateDescriptorSet(BufferSet bufferSet) {
        GpuResource[] resources = {
            bufferSet.positionsX(),
            bufferSet.positionsY(),
            bufferSet.positionsZ(),
            bufferSet.output()
        };
        try (MemoryStack stack = stackPush()) {
            for (int binding = 0; binding < resources.length; binding++) {
                GpuResource resource = resources[binding];
                VkDescriptorBufferInfo.Buffer info = VkDescriptorBufferInfo.calloc(1, stack)
                    .buffer(resource.buffer())
                    .offset(0)
                    .range(resource.sizeBytes());
                VkWriteDescriptorSet.Buffer write = VkWriteDescriptorSet.calloc(1, stack)
                    .sType$Default()
                    .dstSet(descriptorSet)
                    .dstBinding(binding)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1)
                    .pBufferInfo(info);
                vkUpdateDescriptorSets(device, write, null);
            }
        }
    }

    private void record(float originX, float originY, float originZ, int size, int workgroups) {
        check(vkResetCommandBuffer(commandBuffer, VK_COMMAND_BUFFER_RESET_RELEASE_RESOURCES_BIT), "reset command buffer");
        try (MemoryStack stack = stackPush()) {
            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack).sType$Default();
            check(vkBeginCommandBuffer(commandBuffer, beginInfo), "begin command buffer");
            if (positionsDirty) {
                recordPositionUpload(stack);
                positionsDirty = false;
            }
            vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);
            vkCmdBindDescriptorSets(
                commandBuffer,
                VK_PIPELINE_BIND_POINT_COMPUTE,
                pipelineLayout,
                0,
                stack.longs(descriptorSet),
                null
            );
            ByteBuffer parameters = stack.malloc(4 * Float.BYTES)
                .order(ByteOrder.nativeOrder());
            parameters.putFloat(originX).putFloat(originY).putFloat(originZ).putInt(size).flip();
            vkCmdPushConstants(commandBuffer, pipelineLayout, VK_SHADER_STAGE_COMPUTE_BIT, 0, parameters);
            vkCmdDispatch(commandBuffer, workgroups, 1, 1);
            check(vkEndCommandBuffer(commandBuffer), "end command buffer");
        }
    }

    private void recordRadiusMask(
        float originX,
        float originY,
        float originZ,
        float squaredRadius,
        int size,
        int workgroups
    ) {
        check(vkResetCommandBuffer(commandBuffer, VK_COMMAND_BUFFER_RESET_RELEASE_RESOURCES_BIT), "reset command buffer");
        try (MemoryStack stack = stackPush()) {
            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack).sType$Default();
            check(vkBeginCommandBuffer(commandBuffer, beginInfo), "begin command buffer");
            if (positionsDirty) {
                recordPositionUpload(stack);
                positionsDirty = false;
            }
            vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, radiusMaskPipeline);
            vkCmdBindDescriptorSets(
                commandBuffer,
                VK_PIPELINE_BIND_POINT_COMPUTE,
                pipelineLayout,
                0,
                stack.longs(descriptorSet),
                null
            );
            ByteBuffer parameters = stack.malloc(6 * Float.BYTES).order(ByteOrder.nativeOrder());
            parameters.putFloat(originX)
                .putFloat(originY)
                .putFloat(originZ)
                .putFloat(squaredRadius)
                .putInt(size)
                .putInt(0)
                .flip();
            vkCmdPushConstants(commandBuffer, pipelineLayout, VK_SHADER_STAGE_COMPUTE_BIT, 0, parameters);
            vkCmdDispatch(commandBuffer, workgroups, 1, 1);
            check(vkEndCommandBuffer(commandBuffer), "end command buffer");
        }
    }

    private void recordRadiusMasks(
        SpatialComputeBackend.RadiusMaskQuery[] queries,
        int queryOffset,
        int queryCount,
        int size,
        int workgroups,
        int wordCount
    ) {
        check(vkResetCommandBuffer(commandBuffer, VK_COMMAND_BUFFER_RESET_RELEASE_RESOURCES_BIT), "reset command buffer");
        try (MemoryStack stack = stackPush()) {
            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack).sType$Default();
            check(vkBeginCommandBuffer(commandBuffer, beginInfo), "begin command buffer");
            if (positionsDirty) {
                recordPositionUpload(stack);
                positionsDirty = false;
            }
            vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, radiusMaskPipeline);
            vkCmdBindDescriptorSets(
                commandBuffer,
                VK_PIPELINE_BIND_POINT_COMPUTE,
                pipelineLayout,
                0,
                stack.longs(descriptorSet),
                null
            );
            ByteBuffer parameters = stack.malloc(6 * Float.BYTES).order(ByteOrder.nativeOrder());
            for (int localQuery = 0; localQuery < queryCount; localQuery++) {
                SpatialComputeBackend.RadiusMaskQuery query = queries[queryOffset + localQuery];
                parameters.clear();
                parameters.putFloat(query.originX())
                    .putFloat(query.originY())
                    .putFloat(query.originZ())
                    .putFloat(query.squaredRadius())
                    .putInt(size)
                    .putInt(localQuery * wordCount)
                    .flip();
                vkCmdPushConstants(commandBuffer, pipelineLayout, VK_SHADER_STAGE_COMPUTE_BIT, 0, parameters);
                vkCmdDispatch(commandBuffer, workgroups, 1, 1);
            }
            check(vkEndCommandBuffer(commandBuffer), "end command buffer");
        }
    }

    // Copies the three position staging buffers into their device-local
    // counterparts and inserts a transfer->compute barrier so the subsequent
    // dispatch reads committed VRAM. Runs inside the caller's command buffer
    // (after begin, before bind/dispatch); the single-fence submitAndWait that
    // follows the dispatch covers the copy as well.
    private void recordPositionUpload(MemoryStack stack) {
        long sizeBytes = (long) buffers.capacity() * Float.BYTES;
        long[] sources = {
            buffers.positionsStagingX().buffer(),
            buffers.positionsStagingY().buffer(),
            buffers.positionsStagingZ().buffer()
        };
        long[] destinations = {
            buffers.positionsX().buffer(),
            buffers.positionsY().buffer(),
            buffers.positionsZ().buffer()
        };
        for (int axis = 0; axis < 3; axis++) {
            VkBufferCopy.Buffer region = VkBufferCopy.calloc(1, stack)
                .srcOffset(0)
                .dstOffset(0)
                .size(sizeBytes);
            vkCmdCopyBuffer(commandBuffer, sources[axis], destinations[axis], region);
        }
        VkBufferMemoryBarrier.Buffer barriers = VkBufferMemoryBarrier.calloc(3, stack);
        for (int axis = 0; axis < 3; axis++) {
            barriers.get(axis)
                .sType$Default()
                .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .buffer(destinations[axis])
                .offset(0)
                .size(VK_WHOLE_SIZE);
        }
        vkCmdPipelineBarrier(
            commandBuffer,
            VK_PIPELINE_STAGE_TRANSFER_BIT,
            VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
            0,
            null,
            barriers,
            null
        );
    }

    private void submitAndWait() {
        try (MemoryStack stack = stackPush()) {
            check(vkResetFences(device, stack.longs(fence)), "reset fence");
            VkSubmitInfo.Buffer submitInfo = VkSubmitInfo.calloc(1, stack)
                .sType$Default()
                .pCommandBuffers(stack.pointers(commandBuffer.address()));
            check(vkQueueSubmit(queue, submitInfo, fence), "submit compute work");
            check(
                vkWaitForFences(device, stack.longs(fence), true, FENCE_TIMEOUT_NANOS),
                "wait for compute fence"
            );
        }
    }

    private void upload(GpuBuffer target, float[] values, int size) {
        FloatBuffer destination = target.mapped().duplicate()
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer();
        destination.put(values, 0, size);
    }

    private void download(GpuBuffer source, float[] output, int size) {
        FloatBuffer values = source.mapped().duplicate()
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer();
        values.get(output, 0, size);
    }

    private void downloadMask(GpuBuffer source, int[] output, int wordCount) {
        downloadMask(source, output, 0, wordCount);
    }

    private void downloadMask(GpuBuffer source, int[] output, int outputOffset, int wordCount) {
        IntBuffer values = source.mapped().duplicate()
            .order(ByteOrder.nativeOrder())
            .asIntBuffer();
        values.get(output, outputOffset, wordCount);
    }

    private synchronized void radiusMaskFromSnapshot(
        ResidentPositionSnapshot snapshot,
        float originX,
        float originY,
        float originZ,
        float squaredRadius,
        int[] outputWords
    ) {
        ensureOpen();
        if (snapshot.closed()) {
            throw new IllegalStateException("Position snapshot is closed");
        }
        int size = validateMask(snapshot.positionsX(), snapshot.positionsY(), snapshot.positionsZ(), outputWords);
        if (Float.isNaN(squaredRadius) || squaredRadius < 0.0f) {
            throw new IllegalArgumentException("squaredRadius must be non-negative");
        }
        if (size == 0) {
            return;
        }

        validateRadiusBatchSize(size);
        int wordCount = SpatialComputeBackend.maskWordCount(size);
        int workgroups = Math.ceilDiv(wordCount, LOCAL_SIZE);
        ensureCapacity(size);
        if (snapshot.positionDataGeneration() != positionDataGeneration) {
            uploadPositions(
                snapshot.positionsX(), snapshot.positionsY(), snapshot.positionsZ(), size
            );
            snapshot.positionDataGeneration(positionDataGeneration);
        }
        recordRadiusMask(originX, originY, originZ, squaredRadius, size, workgroups);
        submitAndWait();
        downloadMask(buffers.output(), outputWords, wordCount);
    }

    private synchronized void radiusMasksFromSnapshot(
        ResidentPositionSnapshot snapshot,
        SpatialComputeBackend.RadiusMaskQuery[] queries,
        int[] outputWords
    ) {
        ensureOpen();
        if (snapshot.closed()) {
            throw new IllegalStateException("Position snapshot is closed");
        }
        Objects.requireNonNull(queries, "queries");
        Objects.requireNonNull(outputWords, "outputWords");
        int size = snapshot.positionsX().length;
        int wordCount = SpatialComputeBackend.maskWordCount(size);
        long requiredWords = (long) wordCount * queries.length;
        if (requiredWords > outputWords.length) {
            throw new IllegalArgumentException("Output mask cannot fit every radius query");
        }
        for (int queryIndex = 0; queryIndex < queries.length; queryIndex++) {
            Objects.requireNonNull(queries[queryIndex], "queries[" + queryIndex + "]");
        }
        if (size == 0 || queries.length == 0) {
            return;
        }

        validateRadiusBatchSize(size);
        int workgroups = Math.ceilDiv(wordCount, LOCAL_SIZE);
        ensureCapacity(size);
        if (snapshot.positionDataGeneration() != positionDataGeneration) {
            uploadPositions(
                snapshot.positionsX(), snapshot.positionsY(), snapshot.positionsZ(), size
            );
            snapshot.positionDataGeneration(positionDataGeneration);
        }
        for (int queryOffset = 0; queryOffset < queries.length; queryOffset += MAX_QUERIES_PER_SUBMISSION) {
            int queryCount = Math.min(MAX_QUERIES_PER_SUBMISSION, queries.length - queryOffset);
            long chunkWords = (long) queryCount * wordCount;
            if (chunkWords * Integer.BYTES > buffers.output().sizeBytes()) {
                throw new BatchNotSupportedException("Batched radius-mask output exceeds buffer capacity");
            }
            recordRadiusMasks(queries, queryOffset, queryCount, size, workgroups, wordCount);
            submitAndWait();
            downloadMask(buffers.output(), outputWords, queryOffset * wordCount, (int) chunkWords);
        }
    }

    private void uploadPositions(float[] positionsX, float[] positionsY, float[] positionsZ, int size) {
        upload(buffers.positionsStagingX(), positionsX, size);
        upload(buffers.positionsStagingY(), positionsY, size);
        upload(buffers.positionsStagingZ(), positionsZ, size);
        positionDataGeneration++;
        positionsDirty = true;
    }

    private void validateRadiusBatchSize(int size) {
        int wordCount = SpatialComputeBackend.maskWordCount(size);
        int workgroups = Math.ceilDiv(wordCount, LOCAL_SIZE);
        long inputByteSize = (long) size * Float.BYTES;
        if (workgroups > maximumWorkgroupsX || inputByteSize > maximumStorageBufferBytes) {
            throw new BatchNotSupportedException("Batch exceeds the selected Vulkan device limits");
        }
    }

    private static VkInstance createInstance() {
        try (MemoryStack stack = stackPush()) {
            VkApplicationInfo applicationInfo = VkApplicationInfo.calloc(stack)
                .sType$Default()
                .pApplicationName(stack.UTF8("HyperCore"))
                .applicationVersion(1)
                .pEngineName(stack.UTF8("HyperCore"))
                .engineVersion(1)
                .apiVersion(VK_API_VERSION_1_0);
            VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.calloc(stack)
                .sType$Default()
                .pApplicationInfo(applicationInfo);
            PointerBuffer handle = stack.mallocPointer(1);
            check(vkCreateInstance(createInfo, null, handle), "create instance");
            return new VkInstance(handle.get(0), createInfo);
        }
    }

    private static DeviceCandidate selectPhysicalDevice(VkInstance instance) {
        try (MemoryStack stack = stackPush()) {
            IntBuffer count = stack.ints(0);
            check(vkEnumeratePhysicalDevices(instance, count, null), "enumerate physical device count");
            if (count.get(0) == 0) {
                throw new IllegalStateException("No Vulkan physical devices were found");
            }
            PointerBuffer handles = stack.mallocPointer(count.get(0));
            check(vkEnumeratePhysicalDevices(instance, count, handles), "enumerate physical devices");
            List<DeviceCandidate> candidates = new ArrayList<>();
            for (int index = 0; index < handles.remaining(); index++) {
                VkPhysicalDevice physicalDevice = new VkPhysicalDevice(handles.get(index), instance);
                int queueFamily = findComputeQueue(physicalDevice, stack);
                if (queueFamily < 0) {
                    continue;
                }
                VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.calloc(stack);
                vkGetPhysicalDeviceProperties(physicalDevice, properties);
                candidates.add(new DeviceCandidate(
                    physicalDevice,
                    queueFamily,
                    properties.deviceNameString(),
                    score(properties.deviceType()),
                    properties.limits().maxComputeWorkGroupCount(0),
                    Integer.toUnsignedLong(properties.limits().maxStorageBufferRange())
                ));
            }
            return candidates.stream()
                .max(Comparator.comparingInt(DeviceCandidate::score))
                .orElseThrow(() -> new IllegalStateException("No Vulkan compute queue is available"));
        }
    }

    private static int findComputeQueue(VkPhysicalDevice physicalDevice, MemoryStack stack) {
        IntBuffer count = stack.ints(0);
        vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, count, null);
        VkQueueFamilyProperties.Buffer properties = VkQueueFamilyProperties.calloc(count.get(0), stack);
        vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, count, properties);
        int fallback = -1;
        for (int index = 0; index < properties.capacity(); index++) {
            int flags = properties.get(index).queueFlags();
            if ((flags & VK_QUEUE_COMPUTE_BIT) == 0) {
                continue;
            }
            if ((flags & ~VK_QUEUE_COMPUTE_BIT) == 0) {
                return index;
            }
            fallback = index;
        }
        return fallback;
    }

    private static VkDevice createDevice(VkPhysicalDevice physicalDevice, int queueFamilyIndex) {
        try (MemoryStack stack = stackPush()) {
            VkDeviceQueueCreateInfo.Buffer queueInfo = VkDeviceQueueCreateInfo.calloc(1, stack)
                .sType$Default()
                .queueFamilyIndex(queueFamilyIndex)
                .pQueuePriorities(stack.floats(1.0f));
            VkDeviceCreateInfo createInfo = VkDeviceCreateInfo.calloc(stack)
                .sType$Default()
                .pQueueCreateInfos(queueInfo);
            PointerBuffer handle = stack.mallocPointer(1);
            check(vkCreateDevice(physicalDevice, createInfo, null, handle), "create logical device");
            return new VkDevice(handle.get(0), physicalDevice, createInfo);
        }
    }

    private static VkQueue getQueue(VkDevice device, int queueFamilyIndex) {
        try (MemoryStack stack = stackPush()) {
            PointerBuffer handle = stack.mallocPointer(1);
            vkGetDeviceQueue(device, queueFamilyIndex, 0, handle);
            return new VkQueue(handle.get(0), device);
        }
    }

    private static long createDescriptorSetLayout(VkDevice device) {
        try (MemoryStack stack = stackPush()) {
            VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(4, stack);
            for (int index = 0; index < bindings.capacity(); index++) {
                bindings.get(index)
                    .binding(index)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT);
            }
            VkDescriptorSetLayoutCreateInfo createInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                .sType$Default()
                .pBindings(bindings);
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreateDescriptorSetLayout(device, createInfo, null, handle), "create descriptor set layout");
            return handle.get(0);
        }
    }

    private static long createPipelineLayout(VkDevice device, long descriptorSetLayout) {
        try (MemoryStack stack = stackPush()) {
            VkPushConstantRange.Buffer pushConstants = VkPushConstantRange.calloc(1, stack)
                .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT)
                .offset(0)
                .size(24);
            VkPipelineLayoutCreateInfo createInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                .sType$Default()
                .pSetLayouts(stack.longs(descriptorSetLayout))
                .pPushConstantRanges(pushConstants);
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreatePipelineLayout(device, createInfo, null, handle), "create pipeline layout");
            return handle.get(0);
        }
    }

    private static long createShaderModule(VkDevice device, String resource) {
        byte[] bytecode = readShader(resource);
        ByteBuffer code = ByteBuffer.allocateDirect(bytecode.length).order(ByteOrder.nativeOrder());
        code.put(bytecode).flip();
        try (MemoryStack stack = stackPush()) {
            VkShaderModuleCreateInfo createInfo = VkShaderModuleCreateInfo.calloc(stack)
                .sType$Default()
                .pCode(code);
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreateShaderModule(device, createInfo, null, handle), "create shader module");
            return handle.get(0);
        }
    }

    private static long createPipeline(VkDevice device, long pipelineLayout, long shaderModule) {
        try (MemoryStack stack = stackPush()) {
            VkPipelineShaderStageCreateInfo stage = VkPipelineShaderStageCreateInfo.calloc(stack)
                .sType$Default()
                .stage(VK_SHADER_STAGE_COMPUTE_BIT)
                .module(shaderModule)
                .pName(stack.UTF8("main"));
            VkComputePipelineCreateInfo.Buffer createInfo = VkComputePipelineCreateInfo.calloc(1, stack)
                .sType$Default()
                .stage(stage)
                .layout(pipelineLayout);
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreateComputePipelines(device, NULL, createInfo, null, handle), "create compute pipeline");
            return handle.get(0);
        }
    }

    private static long createDescriptorPool(VkDevice device) {
        try (MemoryStack stack = stackPush()) {
            VkDescriptorPoolSize.Buffer poolSize = VkDescriptorPoolSize.calloc(1, stack)
                .type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                .descriptorCount(4);
            VkDescriptorPoolCreateInfo createInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                .sType$Default()
                .pPoolSizes(poolSize)
                .maxSets(1);
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreateDescriptorPool(device, createInfo, null, handle), "create descriptor pool");
            return handle.get(0);
        }
    }

    private static long allocateDescriptorSet(VkDevice device, long pool, long layout) {
        try (MemoryStack stack = stackPush()) {
            VkDescriptorSetAllocateInfo allocateInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                .sType$Default()
                .descriptorPool(pool)
                .pSetLayouts(stack.longs(layout));
            LongBuffer handle = stack.mallocLong(1);
            check(vkAllocateDescriptorSets(device, allocateInfo, handle), "allocate descriptor set");
            return handle.get(0);
        }
    }

    private static long createCommandPool(VkDevice device, int queueFamilyIndex) {
        try (MemoryStack stack = stackPush()) {
            VkCommandPoolCreateInfo createInfo = VkCommandPoolCreateInfo.calloc(stack)
                .sType$Default()
                .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
                .queueFamilyIndex(queueFamilyIndex);
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreateCommandPool(device, createInfo, null, handle), "create command pool");
            return handle.get(0);
        }
    }

    private static VkCommandBuffer allocateCommandBuffer(VkDevice device, long commandPool) {
        try (MemoryStack stack = stackPush()) {
            VkCommandBufferAllocateInfo allocateInfo = VkCommandBufferAllocateInfo.calloc(stack)
                .sType$Default()
                .commandPool(commandPool)
                .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                .commandBufferCount(1);
            PointerBuffer handle = stack.mallocPointer(1);
            check(vkAllocateCommandBuffers(device, allocateInfo, handle), "allocate command buffer");
            return new VkCommandBuffer(handle.get(0), device);
        }
    }

    private static long createFence(VkDevice device) {
        try (MemoryStack stack = stackPush()) {
            VkFenceCreateInfo createInfo = VkFenceCreateInfo.calloc(stack)
                .sType$Default()
                .flags(VK_FENCE_CREATE_SIGNALED_BIT);
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreateFence(device, createInfo, null, handle), "create fence");
            return handle.get(0);
        }
    }

    private static byte[] readShader(String resource) {
        try (InputStream input = VulkanSpatialComputeBackend.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing compute shader: " + resource);
            }
            return input.readAllBytes();
        } catch (IOException error) {
            throw new IllegalStateException("Failed to read compute shader", error);
        }
    }

    private static int validatePositions(float[] x, float[] y, float[] z) {
        Objects.requireNonNull(x, "positionsX");
        Objects.requireNonNull(y, "positionsY");
        Objects.requireNonNull(z, "positionsZ");
        int size = x.length;
        if (y.length != size || z.length != size) {
            throw new IllegalArgumentException("Position arrays must have equal lengths");
        }
        return size;
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

    private static int validateMask(float[] x, float[] y, float[] z, int[] output) {
        Objects.requireNonNull(x, "positionsX");
        Objects.requireNonNull(y, "positionsY");
        Objects.requireNonNull(z, "positionsZ");
        Objects.requireNonNull(output, "outputWords");
        int size = x.length;
        if (y.length != size || z.length != size || output.length < SpatialComputeBackend.maskWordCount(size)) {
            throw new IllegalArgumentException("Position arrays must have equal lengths and fit in output mask");
        }
        return size;
    }

    private static int score(int deviceType) {
        return switch (deviceType) {
            case VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU -> 2;
            case VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU -> 1;
            default -> 0;
        };
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Vulkan compute backend is closed");
        }
    }

    private static void check(int result, String operation) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException("Failed to " + operation + ": Vulkan result " + result);
        }
    }

    private static void destroyPartial(
        VkInstance instance,
        VkDevice device,
        long descriptorSetLayout,
        long pipelineLayout,
        long shaderModule,
        long pipeline,
        long radiusMaskShaderModule,
        long radiusMaskPipeline,
        long descriptorPool,
        long commandPool,
        long fence
    ) {
        if (device != null) {
            if (fence != NULL) {
                vkDestroyFence(device, fence, null);
            }
            if (commandPool != NULL) {
                vkDestroyCommandPool(device, commandPool, null);
            }
            if (descriptorPool != NULL) {
                vkDestroyDescriptorPool(device, descriptorPool, null);
            }
            if (radiusMaskPipeline != NULL) {
                vkDestroyPipeline(device, radiusMaskPipeline, null);
            }
            if (radiusMaskShaderModule != NULL) {
                vkDestroyShaderModule(device, radiusMaskShaderModule, null);
            }
            if (pipeline != NULL) {
                vkDestroyPipeline(device, pipeline, null);
            }
            if (shaderModule != NULL) {
                vkDestroyShaderModule(device, shaderModule, null);
            }
            if (pipelineLayout != NULL) {
                vkDestroyPipelineLayout(device, pipelineLayout, null);
            }
            if (descriptorSetLayout != NULL) {
                vkDestroyDescriptorSetLayout(device, descriptorSetLayout, null);
            }
            vkDestroyDevice(device, null);
        }
        if (instance != null) {
            vkDestroyInstance(instance, null);
        }
    }

    private record DeviceCandidate(
        VkPhysicalDevice device,
        int queueFamilyIndex,
        String name,
        int score,
        int maximumWorkgroupsX,
        long maximumStorageBufferBytes
    ) {
    }

    private static final class ResidentPositionSnapshot implements SpatialComputeBackend.PositionSnapshot {
        private final VulkanSpatialComputeBackend owner;
        private final float[] positionsX;
        private final float[] positionsY;
        private final float[] positionsZ;
        private long positionDataGeneration;
        private boolean closed;

        private ResidentPositionSnapshot(
            VulkanSpatialComputeBackend owner,
            float[] positionsX,
            float[] positionsY,
            float[] positionsZ,
            long positionDataGeneration
        ) {
            this.owner = owner;
            this.positionsX = positionsX;
            this.positionsY = positionsY;
            this.positionsZ = positionsZ;
            this.positionDataGeneration = positionDataGeneration;
        }

        @Override
        public int size() {
            return positionsX.length;
        }

        @Override
        public void radiusMask(
            float originX,
            float originY,
            float originZ,
            float squaredRadius,
            int[] outputWords
        ) {
            owner.radiusMaskFromSnapshot(
                this, originX, originY, originZ, squaredRadius, outputWords
            );
        }

        @Override
        public void radiusMasks(
            SpatialComputeBackend.RadiusMaskQuery[] queries,
            int[] outputWords
        ) {
            owner.radiusMasksFromSnapshot(this, queries, outputWords);
        }

        @Override
        public void close() {
            synchronized (owner) {
                closed = true;
            }
        }

        private float[] positionsX() {
            return positionsX;
        }

        private float[] positionsY() {
            return positionsY;
        }

        private float[] positionsZ() {
            return positionsZ;
        }

        private long positionDataGeneration() {
            return positionDataGeneration;
        }

        private void positionDataGeneration(long value) {
            positionDataGeneration = value;
        }

        private boolean closed() {
            return closed;
        }
    }

    static final class BatchNotSupportedException extends RuntimeException {
        private BatchNotSupportedException(String message) {
            super(message);
        }
    }

    // A buffer bound into the descriptor set. Device-local position buffers and
    // the host-visible output buffer both expose handle + size for descriptor
    // writes without leaking mapping-specific accessors.
    private sealed interface GpuResource permits GpuBuffer, DeviceLocalBuffer {
        long buffer();

        long sizeBytes();
    }

    // Host-visible, host-coherent, persistently mapped. Used for position
    // staging (CPU writes here, vkCmdCopyBuffer moves it to device-local) and
    // for the output buffer (GPU writes, CPU reads back).
    private record GpuBuffer(long buffer, long memory, long sizeBytes, ByteBuffer mapped) implements GpuResource {
        private static GpuBuffer create(VkDevice device, VkPhysicalDevice physicalDevice, long sizeBytes, int usage) {
            if (sizeBytes > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Host-visible buffer exceeds Java buffer capacity");
            }
            try (MemoryStack stack = stackPush()) {
                VkBufferCreateInfo createInfo = VkBufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .size(sizeBytes)
                    .usage(usage)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
                LongBuffer bufferHandle = stack.mallocLong(1);
                check(vkCreateBuffer(device, createInfo, null, bufferHandle), "create storage buffer");
                long buffer = bufferHandle.get(0);
                VkMemoryRequirements requirements = VkMemoryRequirements.calloc(stack);
                vkGetBufferMemoryRequirements(device, buffer, requirements);
                int memoryType = findMemoryType(
                    physicalDevice,
                    requirements.memoryTypeBits(),
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                    stack
                );
                VkMemoryAllocateInfo allocation = VkMemoryAllocateInfo.calloc(stack)
                    .sType$Default()
                    .allocationSize(requirements.size())
                    .memoryTypeIndex(memoryType);
                LongBuffer memoryHandle = stack.mallocLong(1);
                long memory = NULL;
                boolean mapped = false;
                try {
                    check(vkAllocateMemory(device, allocation, null, memoryHandle), "allocate storage buffer memory");
                    memory = memoryHandle.get(0);
                    check(vkBindBufferMemory(device, buffer, memory, 0), "bind storage buffer memory");
                    PointerBuffer mappedAddress = stack.mallocPointer(1);
                    check(vkMapMemory(device, memory, 0, sizeBytes, 0, mappedAddress), "map persistent storage buffer");
                    mapped = true;
                    ByteBuffer mappedBytes = memByteBuffer(mappedAddress.get(0), (int) sizeBytes)
                        .order(ByteOrder.nativeOrder());
                    return new GpuBuffer(buffer, memory, sizeBytes, mappedBytes);
                } catch (RuntimeException error) {
                    if (mapped) {
                        vkUnmapMemory(device, memory);
                    }
                    if (memory != NULL) {
                        vkFreeMemory(device, memory, null);
                    }
                    vkDestroyBuffer(device, buffer, null);
                    throw error;
                }
            }
        }

        private void close(VkDevice device) {
            vkUnmapMemory(device, memory);
            vkDestroyBuffer(device, buffer, null);
            vkFreeMemory(device, memory, null);
        }
    }

    // Device-local, not mapped. Holds positions for GPU reads; the compute
    // shader reads them as readonly storage buffers. On a discrete GPU this is
    // VRAM (no PCIe traversal during dispatch); on integrated GPUs device-local
    // is typically system RAM, so this degrades to a no-op without harm.
    private record DeviceLocalBuffer(long buffer, long memory, long sizeBytes) implements GpuResource {
        private static DeviceLocalBuffer create(VkDevice device, VkPhysicalDevice physicalDevice, long sizeBytes) {
            try (MemoryStack stack = stackPush()) {
                VkBufferCreateInfo createInfo = VkBufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .size(sizeBytes)
                    .usage(VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
                LongBuffer bufferHandle = stack.mallocLong(1);
                check(vkCreateBuffer(device, createInfo, null, bufferHandle), "create device-local buffer");
                long buffer = bufferHandle.get(0);
                VkMemoryRequirements requirements = VkMemoryRequirements.calloc(stack);
                vkGetBufferMemoryRequirements(device, buffer, requirements);
                int memoryType = findMemoryType(
                    physicalDevice,
                    requirements.memoryTypeBits(),
                    VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                    stack
                );
                VkMemoryAllocateInfo allocation = VkMemoryAllocateInfo.calloc(stack)
                    .sType$Default()
                    .allocationSize(requirements.size())
                    .memoryTypeIndex(memoryType);
                LongBuffer memoryHandle = stack.mallocLong(1);
                long memory = NULL;
                try {
                    check(vkAllocateMemory(device, allocation, null, memoryHandle), "allocate device-local buffer memory");
                    memory = memoryHandle.get(0);
                    check(vkBindBufferMemory(device, buffer, memory, 0), "bind device-local buffer memory");
                    return new DeviceLocalBuffer(buffer, memory, sizeBytes);
                } catch (RuntimeException error) {
                    if (memory != NULL) {
                        vkFreeMemory(device, memory, null);
                    }
                    vkDestroyBuffer(device, buffer, null);
                    throw error;
                }
            }
        }

        private void close(VkDevice device) {
            vkDestroyBuffer(device, buffer, null);
            vkFreeMemory(device, memory, null);
        }
    }

    private static int findMemoryType(
        VkPhysicalDevice physicalDevice,
        int supportedTypes,
        int requiredProperties,
        MemoryStack stack
    ) {
        VkPhysicalDeviceMemoryProperties memory = VkPhysicalDeviceMemoryProperties.calloc(stack);
        vkGetPhysicalDeviceMemoryProperties(physicalDevice, memory);
        for (int index = 0; index < memory.memoryTypeCount(); index++) {
            boolean supported = (supportedTypes & (1 << index)) != 0;
            int flags = memory.memoryTypes(index).propertyFlags();
            if (supported && (flags & requiredProperties) == requiredProperties) {
                return index;
            }
        }
        throw new IllegalStateException(
            "No Vulkan memory type satisfying " + Integer.toBinaryString(requiredProperties) + " is available"
        );
    }

    private record BufferSet(
        int capacity,
        DeviceLocalBuffer positionsX,
        DeviceLocalBuffer positionsY,
        DeviceLocalBuffer positionsZ,
        GpuBuffer positionsStagingX,
        GpuBuffer positionsStagingY,
        GpuBuffer positionsStagingZ,
        GpuBuffer output
    ) {
        private static BufferSet create(VkDevice device, VkPhysicalDevice physicalDevice, int capacity) {
            long sizeBytes = (long) capacity * Float.BYTES;
            DeviceLocalBuffer posX = null;
            DeviceLocalBuffer posY = null;
            DeviceLocalBuffer posZ = null;
            GpuBuffer stagingX = null;
            GpuBuffer stagingY = null;
            GpuBuffer stagingZ = null;
            GpuBuffer output = null;
            try {
                posX = DeviceLocalBuffer.create(device, physicalDevice, sizeBytes);
                posY = DeviceLocalBuffer.create(device, physicalDevice, sizeBytes);
                posZ = DeviceLocalBuffer.create(device, physicalDevice, sizeBytes);
                stagingX = GpuBuffer.create(device, physicalDevice, sizeBytes, VK_BUFFER_USAGE_TRANSFER_SRC_BIT);
                stagingY = GpuBuffer.create(device, physicalDevice, sizeBytes, VK_BUFFER_USAGE_TRANSFER_SRC_BIT);
                stagingZ = GpuBuffer.create(device, physicalDevice, sizeBytes, VK_BUFFER_USAGE_TRANSFER_SRC_BIT);
                output = GpuBuffer.create(device, physicalDevice, sizeBytes, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
                return new BufferSet(
                    capacity,
                    posX,
                    posY,
                    posZ,
                    stagingX,
                    stagingY,
                    stagingZ,
                    output
                );
            } catch (RuntimeException error) {
                if (posX != null) {
                    posX.close(device);
                }
                if (posY != null) {
                    posY.close(device);
                }
                if (posZ != null) {
                    posZ.close(device);
                }
                if (stagingX != null) {
                    stagingX.close(device);
                }
                if (stagingY != null) {
                    stagingY.close(device);
                }
                if (stagingZ != null) {
                    stagingZ.close(device);
                }
                if (output != null) {
                    output.close(device);
                }
                throw error;
            }
        }

        private void close(VkDevice device) {
            positionsX.close(device);
            positionsY.close(device);
            positionsZ.close(device);
            positionsStagingX.close(device);
            positionsStagingY.close(device);
            positionsStagingZ.close(device);
            output.close(device);
        }
    }
}
