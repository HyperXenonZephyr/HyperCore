package dev.hypercore.runtime;

import dev.hypercore.bridge.BridgeCoordinator;
import dev.hypercore.compute.AdaptiveSpatialComputeBackend;
import dev.hypercore.compute.CpuBackendSelector;
import dev.hypercore.compute.GpuOffloadPolicy;
import dev.hypercore.compute.SpatialComputeBackend;
import dev.hypercore.compute.SpatialQueryEngine;
import dev.hypercore.bukkit.BukkitServerAccess;
import dev.hypercore.concurrent.HyperCoreExecutor;
import dev.hypercore.config.HyperCoreSettings;
import dev.hypercore.hardware.RuntimeCapabilities;
import dev.hypercore.hardware.VulkanRuntimeProbe;
import dev.hypercore.metrics.TickMetrics;
import dev.hypercore.orchestrator.HyperCoreRole;
import dev.hypercore.orchestrator.OrchestratorRuntime;
import dev.hypercore.region.RegionTaskCoordinator;
import dev.hypercore.world.NoOpWorldAccessFactory;
import dev.hypercore.world.RegionExecutionService;
import dev.hypercore.world.WorldAccessFactory;
import dev.hypercore.plugin.PluginManager;
import dev.hypercore.plugin.ExternalPluginLoader;

import java.nio.file.Path;

public final class HyperCoreRuntime implements AutoCloseable {
    private final PluginManager plugins = new PluginManager();
    private volatile State state;

    public synchronized void start(HyperCoreSettings settings) {
        start(settings, null);
    }

    public synchronized void start(HyperCoreSettings settings, Path pluginDirectory) {
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
        RegionTaskCoordinator regionTasks = new RegionTaskCoordinator(executor, workers);
        OrchestratorRuntime orchestrator = null;
        BridgeCoordinator bridge = null;
        if (HyperCoreRole.current() == HyperCoreRole.ORCHESTRATOR) {
            orchestrator = new OrchestratorRuntime(
                settings.orchestrator(),
                Path.of(System.getProperty("hypercore.orchestrator.root", "."))
            );
            orchestrator.start();
            bridge = new BridgeCoordinator(
                settings.orchestrator().orchestratorPort(),
                settings.orchestrator().hostPort(HyperCoreRole.FABRIC_HOST),
                settings.orchestrator().bridgeTickMillis()
            );
            bridge.start();
        }
        state = new State(
            executor,
            new TickMetrics(settings.tickSampleWindow()),
            capabilities,
            regionTasks,
            new RegionExecutionService(new NoOpWorldAccessFactory(), regionTasks, plugins.events()),
            vulkan,
            gpuOffloadPolicy,
            computeBackend,
            new SpatialQueryEngine(computeBackend),
            externalPlugins,
            orchestrator,
            bridge
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

    public RegionExecutionService regionExecution() {
        return requireState().regionExecution();
    }

    /**
     * Registers the real world access factory provided by the loader adapter.
     * Must be called after {@link #start(HyperCoreSettings, Path)} and before
     * any Bukkit plugin tries to access worlds.
     */
    public synchronized void registerWorldAccessFactory(WorldAccessFactory factory) {
        State current = requireState();
        RegionExecutionService execution = new RegionExecutionService(factory, current.regionTasks(), plugins.events());
        execution.setDeltaSink(current.regionExecution().deltaSink());
        BukkitServerAccess.installRegionExecution(execution);
        state = new State(
            current.executor(),
            current.tickMetrics(),
            current.capabilities(),
            current.regionTasks(),
            execution,
            current.vulkan(),
            current.gpuOffloadPolicy(),
            current.computeBackend(),
            current.spatialQueries(),
            current.externalPlugins(),
            current.orchestrator(),
            current.bridge()
        );
    }

    public PluginManager plugins() {
        return plugins;
    }

    public ExternalPluginLoader externalPlugins() {
        return requireState().externalPlugins();
    }

    /**
     * Returns the orchestrator runtime when this process runs in
     * {@code ORCHESTRATOR} role, or {@code null} otherwise.
     */
    public OrchestratorRuntime orchestrator() {
        return requireState().orchestrator();
    }

    /**
     * Returns the orchestrator bridge coordinator when this process runs in
     * {@code ORCHESTRATOR} role, or {@code null} otherwise.
     */
    public BridgeCoordinator bridge() {
        return requireState().bridge();
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
            // Stop the bridge first so any remaining deltas are flushed or
            // dropped cleanly before the orchestrator terminates the hosts.
            if (current.bridge() != null) {
                current.bridge().close();
            }
            if (current.orchestrator() != null) {
                current.orchestrator().close();
            }
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
        RegionExecutionService regionExecution,
        VulkanRuntimeProbe.Result vulkan,
        GpuOffloadPolicy gpuOffloadPolicy,
        AdaptiveSpatialComputeBackend computeBackend,
        SpatialQueryEngine spatialQueries,
        ExternalPluginLoader externalPlugins,
        OrchestratorRuntime orchestrator,
        BridgeCoordinator bridge
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
