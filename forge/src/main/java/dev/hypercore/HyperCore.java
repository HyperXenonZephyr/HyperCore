package dev.hypercore;

import com.mojang.logging.LogUtils;
import dev.hypercore.bukkit.BukkitEventBridge;
import dev.hypercore.command.HyperCoreCommands;
import dev.hypercore.compute.AdaptiveSpatialComputeBackend;
import dev.hypercore.config.HyperCoreConfig;
import dev.hypercore.hardware.RuntimeCapabilities;
import dev.hypercore.plugin.ForgePluginCommandBridge;
import dev.hypercore.runtime.HyperCoreRuntime;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import java.nio.file.Path;

@Mod(HyperCore.MOD_ID)
public final class HyperCore {
    public static final String MOD_ID = "hypercore";
    public static final String VERSION = "0.1.0-SNAPSHOT";

    private static final Logger LOGGER = LogUtils.getLogger();
    private final HyperCoreRuntime runtime = new HyperCoreRuntime();
    private BukkitEventBridge bukkitEventBridge;

    public HyperCore(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.COMMON, HyperCoreConfig.SPEC);
        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("HyperCore {} initialized", VERSION);
    }

    @SubscribeEvent
    public void onServerAboutToStart(ServerAboutToStartEvent event) {
        runtime.start(HyperCoreConfig.settings(), Path.of("plugins"));
        bukkitEventBridge = new BukkitEventBridge(runtime.plugins());
        bukkitEventBridge.attach();
        // Plugin commands are loaded after RegisterCommandsEvent fires, so re-
        // register them against the live server dispatcher now that plugins are
        // available.
        ForgePluginCommandBridge.register(event.getServer().getCommands().getDispatcher(), runtime.plugins());
        LOGGER.info(
            "HyperCore runtime started with {} workers, queue capacity {}, and compute backend {}",
            runtime.executor().parallelism(),
            runtime.executor().queueCapacity(),
            runtime.computeBackend().id()
        );
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        HyperCoreCommands.register(event.getDispatcher(), runtime);
        ForgePluginCommandBridge.register(event.getDispatcher(), runtime.plugins());
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        if (bukkitEventBridge != null) {
            bukkitEventBridge.fireServerStarted();
        }
        RuntimeCapabilities capabilities = runtime.capabilities();
        LOGGER.info(
            "HyperCore is active on Minecraft {} using Java {} on {} {} with {} detected GPU adapter(s)",
            event.getServer().getServerVersion(),
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

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (!runtime.isStarted()) {
            return;
        }
        if (event.phase == TickEvent.Phase.START) {
            runtime.tickMetrics().beginTick();
            runtime.plugins().scheduler().tick();
        } else {
            runtime.tickMetrics().endTick();
            runtime.regionTasks().dispatchPendingTick().ifPresent(future -> future.thenAccept(result -> {
                if (!result.complete()) {
                    LOGGER.warn(
                        "Region tick {} completed partially: failed={}, requeued={}",
                        result.tickId(),
                        result.failedMessages(),
                        result.requeuedMessages()
                    );
                }
            }));
        }
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
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
