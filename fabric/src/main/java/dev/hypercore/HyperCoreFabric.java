package dev.hypercore;

import com.mojang.logging.LogUtils;
import dev.hypercore.bridge.FabricBridgeEndpoint;
import dev.hypercore.bukkit.BukkitEventBridge;
import dev.hypercore.command.HyperCoreCommands;
import dev.hypercore.compute.AdaptiveSpatialComputeBackend;
import dev.hypercore.config.FabricConfigLoader;
import dev.hypercore.hardware.RuntimeCapabilities;
import dev.hypercore.plugin.FabricPluginCommandBridge;
import dev.hypercore.region.RegionTaskCoordinator;
import dev.hypercore.runtime.HyperCoreRuntime;
import dev.hypercore.world.FabricWorldAccessFactory;
import dev.hypercore.world.WorldRegionTickTask;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.nio.file.Path;

/**
 * Fabric dedicated-server entry point.
 *
 * <p>Mirrors the Forge {@code HyperCore} mod class: starts the loader-agnostic
 * {@link HyperCoreRuntime} when the server is starting, drives tick metrics and
 * region dispatch on the server tick boundary, wires commands, and closes the
 * runtime on server stop. Configuration comes from a properties file read by
 * {@link FabricConfigLoader} instead of a ForgeConfigSpec.
 */
public final class HyperCoreFabric implements DedicatedServerModInitializer {
    public static final String MOD_ID = "hypercore";
    public static final String VERSION = "0.1.0-SNAPSHOT";

    private static final Logger LOGGER = LogUtils.getLogger();
    private final HyperCoreRuntime runtime = new HyperCoreRuntime();
    private BukkitEventBridge bukkitEventBridge;
    private FabricBridgeEndpoint bridgeEndpoint;

    @Override
    public void onInitializeServer() {
        ServerLifecycleEvents.SERVER_STARTING.register(this::onServerStarting);
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);
        ServerTickEvents.START_SERVER_TICK.register(this::onServerTickStart);
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTickEnd);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dev.hypercore.bridge.BridgeStatusView bridgeStatus = bridgeEndpoint == null
                ? dev.hypercore.bridge.BridgeStatusView.NONE
                : bridgeEndpoint.bridge().statusView();
            HyperCoreCommands.register(dispatcher, runtime, bridgeStatus);
            FabricPluginCommandBridge.register(dispatcher, runtime.plugins());
        });

        LOGGER.info("HyperCore {} initialized", VERSION);
    }

    private void onServerStarting(MinecraftServer server) {
        // Dedicated servers run with the working directory set to the run/game
        // directory, so relative paths resolve there — matching the Forge entry
        // point's Path.of("plugins") convention.
        Path configDirectory = Path.of("config");
        runtime.start(FabricConfigLoader.load(configDirectory), Path.of("plugins"));
        runtime.registerWorldAccessFactory(new FabricWorldAccessFactory(server));
        bukkitEventBridge = new BukkitEventBridge(runtime.plugins());
        bukkitEventBridge.attach();
        // Opens the cross-process bridge when running as an orchestrated Fabric
        // host; returns null (and is inert) in standalone mode.
        bridgeEndpoint = FabricBridgeEndpoint.open(runtime, server);
        // Plugin commands are loaded after CommandRegistrationCallback fires, so
        // re-register them against the live server dispatcher now that plugins
        // are available.
        FabricPluginCommandBridge.register(server.getCommands().getDispatcher(), runtime.plugins());
        LOGGER.info(
            "HyperCore runtime started with {} workers, queue capacity {}, and compute backend {}",
            runtime.executor().parallelism(),
            runtime.executor().queueCapacity(),
            runtime.computeBackend().id()
        );
    }

    private void onServerStarted(MinecraftServer server) {
        if (bukkitEventBridge != null) {
            bukkitEventBridge.fireServerStarted();
        }
        RuntimeCapabilities capabilities = runtime.capabilities();
        LOGGER.info(
            "HyperCore is active on Minecraft {} using Java {} on {} {} with {} detected GPU adapter(s)",
            server.getServerVersion(),
            capabilities.javaVersion(),
            capabilities.operatingSystem(),
            capabilities.architecture(),
            capabilities.gpu().devices().size()
        );
        logGpuCapabilities(capabilities.gpu());
        logVulkanCapabilities();
        AdaptiveSpatialComputeBackend.Status computeStatus = runtime.computeStatus();
        if (computeStatus.gpuAvailable()) {
            LOGGER.info(
                "Vulkan compute is enabled on {} with a minimum batch size of {}",
                computeStatus.deviceName(),
                computeStatus.minimumBatchSize()
            );
        } else if (computeStatus.initializationState() == AdaptiveSpatialComputeBackend.InitializationState.INITIALIZING) {
            LOGGER.info("Vulkan compute is initializing asynchronously; CPU fallback is serving batches");
        } else if (computeStatus.initializationState() == AdaptiveSpatialComputeBackend.InitializationState.CLOSED) {
            LOGGER.info("Vulkan compute is closed");
        } else {
            LOGGER.warn("Vulkan compute is unavailable; using cpu-scalar: {}", computeStatus.unavailableReason());
        }
    }

    private void onServerTickStart(MinecraftServer server) {
        if (!runtime.isStarted()) {
            return;
        }
        runtime.tickMetrics().beginTick();
        runtime.plugins().scheduler().tick();
    }

    private void onServerTickEnd(MinecraftServer server) {
        if (!runtime.isStarted()) {
            return;
        }
        runtime.tickMetrics().endTick();
        try {
            RegionTaskCoordinator.TickResult result = runtime.regionExecution()
                .tickRegions(new WorldRegionTickTask())
                .join();
            if (!result.complete()) {
                LOGGER.warn(
                    "Region tick {} completed partially: failed={}, requeued={}",
                    result.tickId(),
                    result.failedMessages(),
                    result.requeuedMessages()
                );
            }
        } catch (RuntimeException error) {
            LOGGER.error("Region tick failed", error);
        }
        // Ship locally-produced world deltas to the orchestrator once per
        // server tick so they arrive within one bridge tick.
        if (bridgeEndpoint != null) {
            bridgeEndpoint.flush();
        }
    }

    private void onServerStopping(MinecraftServer server) {
        if (bridgeEndpoint != null) {
            bridgeEndpoint.close();
            bridgeEndpoint = null;
        }
        runtime.close();
        LOGGER.info("HyperCore runtime stopped");
    }

    private static void logGpuCapabilities(RuntimeCapabilities.GpuProbe gpu) {
        if (!gpu.attempted()) {
            LOGGER.info("GPU capability probe is disabled");
            return;
        }
        if (!gpu.succeeded()) {
            LOGGER.warn("GPU capability probe failed: {}", gpu.error());
            return;
        }
        if (gpu.devices().isEmpty()) {
            LOGGER.info("GPU capability probe completed without finding an adapter");
            return;
        }

        for (int index = 0; index < gpu.devices().size(); index++) {
            RuntimeCapabilities.GpuDevice device = gpu.devices().get(index);
            LOGGER.info(
                "GPU {}: {} | vendor={} | VRAM={} MiB | id={}",
                index,
                device.name(),
                device.vendor(),
                device.vramBytes() / (1024L * 1024L),
                device.deviceId()
            );
        }
    }

    private void logVulkanCapabilities() {
        if (!runtime.vulkan().attempted()) {
            LOGGER.info("Vulkan loader probe is disabled");
        } else if (runtime.vulkan().available()) {
            LOGGER.info(
                "Vulkan loader {} is available with API version {}",
                runtime.vulkan().library(),
                runtime.vulkan().apiVersion()
            );
        } else {
            LOGGER.warn("Vulkan loader probe failed: {}", runtime.vulkan().error());
        }
    }
}
