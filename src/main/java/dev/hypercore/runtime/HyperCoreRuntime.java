package dev.hypercore.runtime;

import dev.hypercore.compute.AdaptiveSpatialComputeBackend;
import dev.hypercore.compute.CpuBackendSelector;
import dev.hypercore.compute.GpuOffloadPolicy;
import dev.hypercore.compute.SpatialComputeBackend;
import dev.hypercore.compute.SpatialQueryEngine;
import dev.hypercore.concurrent.HyperCoreExecutor;
import dev.hypercore.config.HyperCoreConfig;
import dev.hypercore.hardware.RuntimeCapabilities;
import dev.hypercore.hardware.VulkanRuntimeProbe;
import dev.hypercore.metrics.TickMetrics;
import dev.hypercore.region.RegionTaskCoordinator;
import dev.hypercore.plugin.PluginManager;
import dev.hypercore.plugin.ExternalPluginLoader;

import java.nio.file.Path;

public final class HyperCoreRuntime implements AutoCloseable {
    private final PluginManager plugins = new PluginManager();
    private volatile State state;

    public synchronized void start(HyperCoreConfig.Settings settings) {
        start(settings, null);
    }

    public synchronized void start(HyperCoreConfig.Settings settings, Path pluginDirectory) {
        if (state != null) {
            throw new IllegalStateException("HyperCore runtime is already started");
        }

        RuntimeCapabilities capabilities = RuntimeCapabilities.detect(settings.probeGpu());
        VulkanRuntimeProbe.Result vulkan = settings.probeGpu() || settings.enableGpu()
            ? VulkanRuntimeProbe.detect()
            : VulkanRuntimeProbe.disabled();
        GpuOffloadPolicy gpuOffloadPolicy = new GpuOffloadPolicy(settings.gpuMinimumBatchSize());
        SpatialComputeBackend cpuBackend = CpuBackendSelector.select(settings.cpuBackend());
        AdaptiveSpatialComputeBackend computeBackend = settings.enableGpu() && vulkan.available()
            ? AdaptiveSpatialComputeBackend.create(gpuOffloadPolicy, true, cpuBackend)
            : AdaptiveSpatialComputeBackend.unavailable(
                gpuOffloadPolicy,
                settings.enableGpu() ? vulkan.error() : "disabled by configuration",
                cpuBackend
            );
        int workers = settings.resolveWorkerThreads(capabilities.logicalProcessors());
        int queueCapacity = settings.resolveQueueCapacity(workers);
        HyperCoreExecutor executor = HyperCoreExecutor.create(workers, queueCapacity);
        plugins.scheduler().attachExecutor(executor);
        ExternalPluginLoader externalPlugins = pluginDirectory == null
            ? null
            : new ExternalPluginLoader(plugins, pluginDirectory);
        if (externalPlugins != null) {
            externalPlugins.load();
        }
        state = new State(
            executor,
            new TickMetrics(settings.tickSampleWindow()),
            capabilities,
            new RegionTaskCoordinator(executor, workers),
            vulkan,
            gpuOffloadPolicy,
            computeBackend,
            new SpatialQueryEngine(computeBackend),
            externalPlugins
        );
        plugins.enableAll();
    }

    public boolean isStarted() {
        return state != null;
    }

    public HyperCoreExecutor executor() {
        return requireState().executor();
    }

    public TickMetrics tickMetrics() {
        return requireState().tickMetrics();
    }

    public RuntimeCapabilities capabilities() {
        return requireState().capabilities();
    }

    public RegionTaskCoordinator regionTasks() {
        return requireState().regionTasks();
    }

    public PluginManager plugins() {
        return plugins;
    }

    public ExternalPluginLoader externalPlugins() {
        return requireState().externalPlugins();
    }

    public VulkanRuntimeProbe.Result vulkan() {
        return requireState().vulkan();
    }

    public GpuOffloadPolicy gpuOffloadPolicy() {
        return requireState().gpuOffloadPolicy();
    }

    public SpatialComputeBackend computeBackend() {
        return requireState().computeBackend();
    }

    public AdaptiveSpatialComputeBackend.Status computeStatus() {
        return requireState().computeBackend().status();
    }

    public SpatialQueryEngine spatialQueries() {
        return requireState().spatialQueries();
    }

    public Status status() {
        State current = requireState();
        HyperCoreExecutor executor = current.executor();
        Runtime runtime = Runtime.getRuntime();
        return new Status(
            executor.parallelism(),
            executor.activeTasks(),
            executor.queuedTasks(),
            executor.queueCapacity(),
            executor.submittedTasks(),
            executor.completedTasks(),
            executor.rejectedTasks(),
            runtime.totalMemory() - runtime.freeMemory(),
            runtime.maxMemory(),
            current.computeBackend().id()
        );
    }

    @Override
    public synchronized void close() {
        State current = state;
        state = null;
        if (current != null) {
            plugins.disableAll();
            if (current.externalPlugins() != null) {
                current.externalPlugins().close();
            }
            plugins.scheduler().detachExecutor(current.executor());
            current.spatialQueries().close();
            current.computeBackend().close();
            current.executor().close();
        }
    }

    private State requireState() {
        State current = state;
        if (current == null) {
            throw new IllegalStateException("HyperCore runtime is not started");
        }
        return current;
    }

    private record State(
        HyperCoreExecutor executor,
        TickMetrics tickMetrics,
        RuntimeCapabilities capabilities,
        RegionTaskCoordinator regionTasks,
        VulkanRuntimeProbe.Result vulkan,
        GpuOffloadPolicy gpuOffloadPolicy,
        AdaptiveSpatialComputeBackend computeBackend,
        SpatialQueryEngine spatialQueries,
        ExternalPluginLoader externalPlugins
    ) {
    }

    public record Status(
        int workers,
        int activeTasks,
        int queuedTasks,
        int queueCapacity,
        long submittedTasks,
        long completedTasks,
        long rejectedTasks,
        long usedHeapBytes,
        long maximumHeapBytes,
        String computeBackend
    ) {
    }
}
