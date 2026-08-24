package dev.hypercore.server.vulkan;

import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memByteBuffer;
import static org.lwjgl.system.MemoryUtil.memFree;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_compilation_status_success;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_compile_into_spv;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_compile_options_initialize;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_compile_options_release;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_compile_options_set_optimization_level;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_compile_options_set_target_env;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_compiler_initialize;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_compiler_release;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_compute_shader;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_env_version_vulkan_1_0;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_optimization_level_performance;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_result_get_bytes;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_result_get_compilation_status;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_result_get_error_message;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_result_release;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_target_env_vulkan;
import static org.lwjgl.vulkan.VK10.VK_API_VERSION_1_0;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_BUFFER_RESET_RELEASE_RESOURCES_BIT;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
import static org.lwjgl.vulkan.VK10.VK_FENCE_CREATE_SIGNALED_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_BIND_POINT_COMPUTE;
import static org.lwjgl.vulkan.VK10.VK_QUEUE_COMPUTE_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_COMPUTE_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHARING_MODE_EXCLUSIVE;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkAllocateCommandBuffers;
import static org.lwjgl.vulkan.VK10.vkAllocateDescriptorSets;
import static org.lwjgl.vulkan.VK10.vkAllocateMemory;
import static org.lwjgl.vulkan.VK10.vkBeginCommandBuffer;
import static org.lwjgl.vulkan.VK10.vkBindBufferMemory;
import static org.lwjgl.vulkan.VK10.vkCmdBindDescriptorSets;
import static org.lwjgl.vulkan.VK10.vkCmdBindPipeline;
import static org.lwjgl.vulkan.VK10.vkCmdDispatch;
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
import static org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceQueueFamilyProperties;
import static org.lwjgl.vulkan.VK10.vkMapMemory;
import static org.lwjgl.vulkan.VK10.vkQueueSubmit;
import static org.lwjgl.vulkan.VK10.vkResetCommandBuffer;
import static org.lwjgl.vulkan.VK10.vkResetFences;
import static org.lwjgl.vulkan.VK10.vkUnmapMemory;
import static org.lwjgl.vulkan.VK10.vkUpdateDescriptorSets;
import static org.lwjgl.vulkan.VK10.vkWaitForFences;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkBufferCreateInfo;
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
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPushConstantRange;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkQueueFamilyProperties;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkSubmitInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

/** A deliberately small Vulkan pipeline for the explicit f32 spatial operation. */
final class VulkanSquaredLengthKernel implements AutoCloseable {
    private static final int LOCAL_SIZE = 256;
    private static final long FENCE_TIMEOUT_NANOS = 30_000_000_000L;

    private VkInstance instance;
    private VkPhysicalDevice physicalDevice;
    private VkDevice device;
    private VkQueue queue;
    private int queueFamilyIndex;
    private long descriptorSetLayout = NULL;
    private long descriptorPool = NULL;
    private long descriptorSet = NULL;
    private long pipelineLayout = NULL;
    private long shaderModule = NULL;
    private long pipeline = NULL;
    private long commandPool = NULL;
    private VkCommandBuffer commandBuffer;
    private long fence = NULL;
    private boolean closed;

    VulkanSquaredLengthKernel() {
        try {
            this.instance = createInstance();
            DeviceSelection selection = selectPhysicalDevice(this.instance);
            this.physicalDevice = selection.device();
            this.queueFamilyIndex = selection.queueFamilyIndex();
            this.device = createDevice(this.physicalDevice, this.queueFamilyIndex);
            this.queue = getQueue(this.device, this.queueFamilyIndex);
            this.descriptorSetLayout = createDescriptorSetLayout(this.device);
            this.descriptorPool = createDescriptorPool(this.device);
            this.descriptorSet = allocateDescriptorSet(this.device, this.descriptorPool, this.descriptorSetLayout);
            this.pipelineLayout = createPipelineLayout(this.device, this.descriptorSetLayout);
            this.shaderModule = createShaderModule(this.device);
            this.pipeline = createPipeline(this.device, this.pipelineLayout, this.shaderModule);
            this.commandPool = createCommandPool(this.device, this.queueFamilyIndex);
            this.commandBuffer = allocateCommandBuffer(this.device, this.commandPool);
            this.fence = createFence(this.device);
        } catch (Throwable failure) {
            this.close();
            throw failure;
        }
    }

    synchronized double[] squaredLengths(final double[] values) {
        this.requireOpen();
        FloatTriples triples = FloatTriples.from(values);
        if (triples.count() == 0) {
            return new double[0];
        }
        long sizeBytes = Math.multiplyExact((long) triples.count(), Float.BYTES);
        MappedBuffer x = null;
        MappedBuffer y = null;
        MappedBuffer z = null;
        MappedBuffer output = null;
        try {
            x = MappedBuffer.create(this.device, this.physicalDevice, sizeBytes);
            y = MappedBuffer.create(this.device, this.physicalDevice, sizeBytes);
            z = MappedBuffer.create(this.device, this.physicalDevice, sizeBytes);
            output = MappedBuffer.create(this.device, this.physicalDevice, sizeBytes);
            x.write(triples.x());
            y.write(triples.y());
            z.write(triples.z());
            this.updateDescriptorSet(x, y, z, output);
            this.recordAndSubmit(triples.count());
            return output.readAsDoubles(triples.count());
        } finally {
            if (output != null) output.close(this.device);
            if (z != null) z.close(this.device);
            if (y != null) y.close(this.device);
            if (x != null) x.close(this.device);
        }
    }

    @Override
    public synchronized void close() {
        if (this.closed) return;
        this.closed = true;
        if (this.device != null) {
            vkDeviceWaitIdle(this.device);
            if (this.fence != NULL) vkDestroyFence(this.device, this.fence, null);
            if (this.commandPool != NULL) vkDestroyCommandPool(this.device, this.commandPool, null);
            if (this.pipeline != NULL) vkDestroyPipeline(this.device, this.pipeline, null);
            if (this.shaderModule != NULL) vkDestroyShaderModule(this.device, this.shaderModule, null);
            if (this.pipelineLayout != NULL) vkDestroyPipelineLayout(this.device, this.pipelineLayout, null);
            if (this.descriptorPool != NULL) vkDestroyDescriptorPool(this.device, this.descriptorPool, null);
            if (this.descriptorSetLayout != NULL) vkDestroyDescriptorSetLayout(this.device, this.descriptorSetLayout, null);
            vkDestroyDevice(this.device, null);
        }
        if (this.instance != null) vkDestroyInstance(this.instance, null);
    }

    private void updateDescriptorSet(final MappedBuffer x, final MappedBuffer y, final MappedBuffer z, final MappedBuffer output) {
        MappedBuffer[] buffers = {x, y, z, output};
        try (MemoryStack stack = stackPush()) {
            for (int binding = 0; binding < buffers.length; binding++) {
                MappedBuffer buffer = buffers[binding];
                VkDescriptorBufferInfo.Buffer info = VkDescriptorBufferInfo.calloc(1, stack)
                    .buffer(buffer.buffer()).offset(0).range(buffer.sizeBytes());
                VkWriteDescriptorSet.Buffer write = VkWriteDescriptorSet.calloc(1, stack)
                    .sType$Default().dstSet(this.descriptorSet).dstBinding(binding)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(1).pBufferInfo(info);
                vkUpdateDescriptorSets(this.device, write, null);
            }
        }
    }

    private void recordAndSubmit(final int count) {
        check(vkResetCommandBuffer(this.commandBuffer, VK_COMMAND_BUFFER_RESET_RELEASE_RESOURCES_BIT), "reset command buffer");
        try (MemoryStack stack = stackPush()) {
            check(vkResetFences(this.device, stack.longs(this.fence)), "reset fence");
            check(vkBeginCommandBuffer(this.commandBuffer, VkCommandBufferBeginInfo.calloc(stack).sType$Default()), "begin command buffer");
            vkCmdBindPipeline(this.commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, this.pipeline);
            vkCmdBindDescriptorSets(this.commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, this.pipelineLayout, 0, stack.longs(this.descriptorSet), null);
            ByteBuffer parameters = stack.malloc(4).order(ByteOrder.nativeOrder());
            parameters.putInt(count).flip();
            vkCmdPushConstants(this.commandBuffer, this.pipelineLayout, VK_SHADER_STAGE_COMPUTE_BIT, 0, parameters);
            vkCmdDispatch(this.commandBuffer, Math.ceilDiv(count, LOCAL_SIZE), 1, 1);
            check(vkEndCommandBuffer(this.commandBuffer), "end command buffer");
            VkSubmitInfo.Buffer submit = VkSubmitInfo.calloc(1, stack).sType$Default().pCommandBuffers(stack.pointers(this.commandBuffer.address()));
            check(vkQueueSubmit(this.queue, submit, this.fence), "submit compute work");
            check(vkWaitForFences(this.device, stack.longs(this.fence), true, FENCE_TIMEOUT_NANOS), "wait for compute fence");
        }
    }

    private static VkInstance createInstance() {
        try (MemoryStack stack = stackPush()) {
            VkApplicationInfo application = VkApplicationInfo.calloc(stack).sType$Default()
                .pApplicationName(stack.UTF8("HyperCore Vulkan Provider")).applicationVersion(1)
                .pEngineName(stack.UTF8("HyperCore")).engineVersion(1).apiVersion(VK_API_VERSION_1_0);
            VkInstanceCreateInfo create = VkInstanceCreateInfo.calloc(stack).sType$Default().pApplicationInfo(application);
            PointerBuffer handle = stack.mallocPointer(1);
            check(vkCreateInstance(create, null, handle), "create Vulkan instance");
            return new VkInstance(handle.get(0), create);
        }
    }

    private static DeviceSelection selectPhysicalDevice(final VkInstance instance) {
        try (MemoryStack stack = stackPush()) {
            IntBuffer count = stack.ints(0);
            check(vkEnumeratePhysicalDevices(instance, count, null), "enumerate physical device count");
            if (count.get(0) == 0) throw new IllegalStateException("No Vulkan physical device is available");
            PointerBuffer handles = stack.mallocPointer(count.get(0));
            check(vkEnumeratePhysicalDevices(instance, count, handles), "enumerate physical devices");
            for (int index = 0; index < handles.remaining(); index++) {
                VkPhysicalDevice candidate = new VkPhysicalDevice(handles.get(index), instance);
                int queueFamily = findComputeQueue(candidate, stack);
                if (queueFamily >= 0) return new DeviceSelection(candidate, queueFamily);
            }
            throw new IllegalStateException("No Vulkan compute queue is available");
        }
    }

    private static int findComputeQueue(final VkPhysicalDevice device, final MemoryStack stack) {
        IntBuffer count = stack.ints(0);
        vkGetPhysicalDeviceQueueFamilyProperties(device, count, null);
        VkQueueFamilyProperties.Buffer properties = VkQueueFamilyProperties.calloc(count.get(0), stack);
        vkGetPhysicalDeviceQueueFamilyProperties(device, count, properties);
        for (int index = 0; index < properties.capacity(); index++) {
            if ((properties.get(index).queueFlags() & VK_QUEUE_COMPUTE_BIT) != 0) return index;
        }
        return -1;
    }

    private static VkDevice createDevice(final VkPhysicalDevice physicalDevice, final int queueFamilyIndex) {
        try (MemoryStack stack = stackPush()) {
            VkDeviceQueueCreateInfo.Buffer queueInfo = VkDeviceQueueCreateInfo.calloc(1, stack).sType$Default()
                .queueFamilyIndex(queueFamilyIndex).pQueuePriorities(stack.floats(1.0f));
            VkDeviceCreateInfo create = VkDeviceCreateInfo.calloc(stack).sType$Default().pQueueCreateInfos(queueInfo);
            PointerBuffer handle = stack.mallocPointer(1);
            check(vkCreateDevice(physicalDevice, create, null, handle), "create logical device");
            return new VkDevice(handle.get(0), physicalDevice, create);
        }
    }

    private static VkQueue getQueue(final VkDevice device, final int queueFamilyIndex) {
        try (MemoryStack stack = stackPush()) {
            PointerBuffer handle = stack.mallocPointer(1);
            vkGetDeviceQueue(device, queueFamilyIndex, 0, handle);
            return new VkQueue(handle.get(0), device);
        }
    }

    private static long createDescriptorSetLayout(final VkDevice device) {
        try (MemoryStack stack = stackPush()) {
            VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(4, stack);
            for (int index = 0; index < bindings.capacity(); index++) {
                bindings.get(index).binding(index).descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(1).stageFlags(VK_SHADER_STAGE_COMPUTE_BIT);
            }
            VkDescriptorSetLayoutCreateInfo create = VkDescriptorSetLayoutCreateInfo.calloc(stack).sType$Default().pBindings(bindings);
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreateDescriptorSetLayout(device, create, null, handle), "create descriptor set layout");
            return handle.get(0);
        }
    }

    private static long createDescriptorPool(final VkDevice device) {
        try (MemoryStack stack = stackPush()) {
            VkDescriptorPoolSize.Buffer poolSize = VkDescriptorPoolSize.calloc(1, stack).type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(4);
            VkDescriptorPoolCreateInfo create = VkDescriptorPoolCreateInfo.calloc(stack).sType$Default().pPoolSizes(poolSize).maxSets(1);
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreateDescriptorPool(device, create, null, handle), "create descriptor pool");
            return handle.get(0);
        }
    }

    private static long allocateDescriptorSet(final VkDevice device, final long pool, final long layout) {
        try (MemoryStack stack = stackPush()) {
            VkDescriptorSetAllocateInfo allocate = VkDescriptorSetAllocateInfo.calloc(stack).sType$Default().descriptorPool(pool).pSetLayouts(stack.longs(layout));
            LongBuffer handle = stack.mallocLong(1);
            check(vkAllocateDescriptorSets(device, allocate, handle), "allocate descriptor set");
            return handle.get(0);
        }
    }

    private static long createPipelineLayout(final VkDevice device, final long descriptorSetLayout) {
        try (MemoryStack stack = stackPush()) {
            VkPushConstantRange.Buffer constants = VkPushConstantRange.calloc(1, stack).stageFlags(VK_SHADER_STAGE_COMPUTE_BIT).offset(0).size(4);
            VkPipelineLayoutCreateInfo create = VkPipelineLayoutCreateInfo.calloc(stack).sType$Default().pSetLayouts(stack.longs(descriptorSetLayout)).pPushConstantRanges(constants);
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreatePipelineLayout(device, create, null, handle), "create pipeline layout");
            return handle.get(0);
        }
    }

    private static long createShaderModule(final VkDevice device) {
        ByteBuffer bytecode = compileShader();
        try (MemoryStack stack = stackPush()) {
            VkShaderModuleCreateInfo create = VkShaderModuleCreateInfo.calloc(stack).sType$Default().pCode(bytecode);
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreateShaderModule(device, create, null, handle), "create shader module");
            return handle.get(0);
        } finally {
            memFree(bytecode);
        }
    }

    private static long createPipeline(final VkDevice device, final long pipelineLayout, final long shaderModule) {
        try (MemoryStack stack = stackPush()) {
            VkPipelineShaderStageCreateInfo stage = VkPipelineShaderStageCreateInfo.calloc(stack).sType$Default().stage(VK_SHADER_STAGE_COMPUTE_BIT).module(shaderModule).pName(stack.UTF8("main"));
            VkComputePipelineCreateInfo.Buffer create = VkComputePipelineCreateInfo.calloc(1, stack).sType$Default().stage(stage).layout(pipelineLayout);
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreateComputePipelines(device, NULL, create, null, handle), "create compute pipeline");
            return handle.get(0);
        }
    }

    private static long createCommandPool(final VkDevice device, final int queueFamilyIndex) {
        try (MemoryStack stack = stackPush()) {
            VkCommandPoolCreateInfo create = VkCommandPoolCreateInfo.calloc(stack).sType$Default().flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT).queueFamilyIndex(queueFamilyIndex);
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreateCommandPool(device, create, null, handle), "create command pool");
            return handle.get(0);
        }
    }

    private static VkCommandBuffer allocateCommandBuffer(final VkDevice device, final long commandPool) {
        try (MemoryStack stack = stackPush()) {
            VkCommandBufferAllocateInfo allocate = VkCommandBufferAllocateInfo.calloc(stack).sType$Default().commandPool(commandPool).level(VK_COMMAND_BUFFER_LEVEL_PRIMARY).commandBufferCount(1);
            PointerBuffer handle = stack.mallocPointer(1);
            check(vkAllocateCommandBuffers(device, allocate, handle), "allocate command buffer");
            return new VkCommandBuffer(handle.get(0), device);
        }
    }

    private static long createFence(final VkDevice device) {
        try (MemoryStack stack = stackPush()) {
            VkFenceCreateInfo create = VkFenceCreateInfo.calloc(stack).sType$Default().flags(VK_FENCE_CREATE_SIGNALED_BIT);
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreateFence(device, create, null, handle), "create fence");
            return handle.get(0);
        }
    }

    private static ByteBuffer compileShader() {
        String source;
        try (InputStream input = VulkanSquaredLengthKernel.class.getResourceAsStream("/dev/hypercore/server/vulkan/squared_lengths.comp")) {
            if (input == null) throw new IllegalStateException("Missing Vulkan squared-length shader");
            source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot read Vulkan squared-length shader", error);
        }
        long compiler = shaderc_compiler_initialize();
        long options = shaderc_compile_options_initialize();
        long result = NULL;
        if (compiler == NULL || options == NULL) throw new IllegalStateException("Cannot initialize shaderc");
        try {
            shaderc_compile_options_set_target_env(options, shaderc_target_env_vulkan, shaderc_env_version_vulkan_1_0);
            shaderc_compile_options_set_optimization_level(options, shaderc_optimization_level_performance);
            result = shaderc_compile_into_spv(compiler, source, shaderc_compute_shader, "squared_lengths.comp", "main", options);
            if (result == NULL) throw new IllegalStateException("shaderc returned no result");
            if (shaderc_result_get_compilation_status(result) != shaderc_compilation_status_success) {
                throw new IllegalStateException(shaderc_result_get_error_message(result));
            }
            ByteBuffer compiled = shaderc_result_get_bytes(result);
            ByteBuffer copy = memAlloc(compiled.remaining());
            copy.put(compiled).flip();
            return copy;
        } finally {
            if (result != NULL) shaderc_result_release(result);
            shaderc_compile_options_release(options);
            shaderc_compiler_release(compiler);
        }
    }

    private static int findMemoryType(final VkPhysicalDevice physicalDevice, final int bits, final int required, final MemoryStack stack) {
        VkPhysicalDeviceMemoryProperties properties = VkPhysicalDeviceMemoryProperties.calloc(stack);
        vkGetPhysicalDeviceMemoryProperties(physicalDevice, properties);
        for (int index = 0; index < properties.memoryTypeCount(); index++) {
            if ((bits & (1 << index)) != 0 && (properties.memoryTypes(index).propertyFlags() & required) == required) return index;
        }
        throw new IllegalStateException("No host-visible coherent Vulkan memory type is available");
    }

    private void requireOpen() {
        if (this.closed) throw new IllegalStateException("Vulkan kernel is closed");
    }

    private static void check(final int result, final String operation) {
        if (result != VK_SUCCESS) throw new IllegalStateException("Cannot " + operation + ": Vulkan result " + result);
    }

    private record DeviceSelection(VkPhysicalDevice device, int queueFamilyIndex) {}

    static record FloatTriples(float[] x, float[] y, float[] z) {
        static FloatTriples from(final double[] values) {
            Objects.requireNonNull(values, "values");
            if (values.length % 3 != 0) throw new IllegalArgumentException("squared-length-3d-f32 requires xyz triples");
            int count = values.length / 3;
            float[] x = new float[count];
            float[] y = new float[count];
            float[] z = new float[count];
            for (int index = 0; index < count; index++) {
                x[index] = narrow(values[index * 3], index, "x");
                y[index] = narrow(values[index * 3 + 1], index, "y");
                z[index] = narrow(values[index * 3 + 2], index, "z");
            }
            return new FloatTriples(x, y, z);
        }

        int count() { return this.x.length; }

        private static float narrow(final double value, final int index, final String axis) {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite " + axis + " coordinate at " + index);
            float narrowed = (float) value;
            if (!Float.isFinite(narrowed)) throw new IllegalArgumentException("Out-of-range " + axis + " coordinate at " + index);
            return narrowed;
        }
    }

    private record MappedBuffer(long buffer, long memory, long sizeBytes, ByteBuffer mapped) {
        static MappedBuffer create(final VkDevice device, final VkPhysicalDevice physicalDevice, final long sizeBytes) {
            if (sizeBytes <= 0 || sizeBytes > Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid Vulkan buffer size: " + sizeBytes);
            try (MemoryStack stack = stackPush()) {
                VkBufferCreateInfo create = VkBufferCreateInfo.calloc(stack).sType$Default().size(sizeBytes).usage(VK_BUFFER_USAGE_STORAGE_BUFFER_BIT).sharingMode(VK_SHARING_MODE_EXCLUSIVE);
                LongBuffer bufferHandle = stack.mallocLong(1);
                check(vkCreateBuffer(device, create, null, bufferHandle), "create storage buffer");
                long buffer = bufferHandle.get(0);
                VkMemoryRequirements requirements = VkMemoryRequirements.calloc(stack);
                vkGetBufferMemoryRequirements(device, buffer, requirements);
                long memory = NULL;
                try {
                    int memoryType = findMemoryType(physicalDevice, requirements.memoryTypeBits(), VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT, stack);
                    VkMemoryAllocateInfo allocate = VkMemoryAllocateInfo.calloc(stack).sType$Default().allocationSize(requirements.size()).memoryTypeIndex(memoryType);
                    LongBuffer memoryHandle = stack.mallocLong(1);
                    check(vkAllocateMemory(device, allocate, null, memoryHandle), "allocate storage memory");
                    memory = memoryHandle.get(0);
                    check(vkBindBufferMemory(device, buffer, memory, 0), "bind storage memory");
                    PointerBuffer address = stack.mallocPointer(1);
                    check(vkMapMemory(device, memory, 0, sizeBytes, 0, address), "map storage memory");
                    return new MappedBuffer(buffer, memory, sizeBytes, memByteBuffer(address.get(0), Math.toIntExact(sizeBytes)).order(ByteOrder.nativeOrder()));
                } catch (Throwable failure) {
                    if (memory != NULL) vkFreeMemory(device, memory, null);
                    vkDestroyBuffer(device, buffer, null);
                    throw failure;
                }
            }
        }

        void write(final float[] values) {
            FloatBuffer target = this.mapped.duplicate().order(ByteOrder.nativeOrder()).asFloatBuffer();
            target.put(values);
        }

        double[] readAsDoubles(final int count) {
            FloatBuffer source = this.mapped.duplicate().order(ByteOrder.nativeOrder()).asFloatBuffer();
            double[] output = new double[count];
            for (int index = 0; index < count; index++) output[index] = source.get(index);
            return output;
        }

        void close(final VkDevice device) {
            vkUnmapMemory(device, this.memory);
            vkDestroyBuffer(device, this.buffer, null);
            vkFreeMemory(device, this.memory, null);
        }
    }
}
