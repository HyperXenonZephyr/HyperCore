package dev.hypercore;

import com.mojang.logging.LogUtils;
import dev.hypercore.command.HyperCoreCommands;
import dev.hypercore.concurrent.HyperCoreExecutor;
import dev.hypercore.metrics.TickMetrics;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(HyperCore.MOD_ID)
public final class HyperCore {
    public static final String MOD_ID = "hypercore";
    public static final String VERSION = "0.1.0-SNAPSHOT";

    private static final Logger LOGGER = LogUtils.getLogger();
    private final HyperCoreExecutor executor = HyperCoreExecutor.createDefault();
    private final TickMetrics tickMetrics = new TickMetrics();

    public HyperCore() {
        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("HyperCore {} initialized with {} worker threads", VERSION, executor.parallelism());
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        HyperCoreCommands.register(event.getDispatcher(), executor, tickMetrics);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        LOGGER.info("HyperCore is active on Minecraft {}", event.getServer().getServerVersion());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            tickMetrics.beginTick();
        } else {
            tickMetrics.endTick();
        }
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        executor.close();
        LOGGER.info("HyperCore worker pool stopped");
    }
}
