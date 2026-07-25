package dev.hypercore.command;

import com.mojang.brigadier.CommandDispatcher;
import dev.hypercore.HyperCore;
import dev.hypercore.hardware.RuntimeCapabilities;
import dev.hypercore.metrics.TickMetrics;
import dev.hypercore.runtime.HyperCoreRuntime;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.util.Locale;

import static net.minecraft.commands.Commands.literal;

public final class HyperCoreCommands {
    private HyperCoreCommands() {
    }

    public static void register(
        CommandDispatcher<CommandSourceStack> dispatcher,
        HyperCoreRuntime runtime
    ) {
        dispatcher.register(literal("hypercore")
            .requires(source -> source.hasPermission(2))
            .then(literal("status").executes(context -> showStatus(context.getSource(), runtime)))
            .then(literal("timings").executes(context -> showTimings(context.getSource(), runtime)))
            .then(literal("capabilities").executes(context -> showCapabilities(context.getSource(), runtime)))
        );
    }

    private static int showStatus(CommandSourceStack source, HyperCoreRuntime runtime) {
        HyperCoreRuntime.Status status = runtime.status();
        source.sendSuccess(() -> Component.literal(
            "HyperCore " + HyperCore.VERSION
                + " | workers=" + status.workers()
                + " | active=" + status.activeTasks()
                + " | queue=" + status.queuedTasks() + "/" + status.queueCapacity()
                + " | tasks=" + status.completedTasks() + "/" + status.submittedTasks()
                + " | rejected=" + status.rejectedTasks()
                + " | compute=" + status.computeBackend()
                + " | heap=" + toMiB(status.usedHeapBytes()) + "/" + toMiB(status.maximumHeapBytes()) + " MiB"
        ), false);
        return 1;
    }

    private static int showTimings(CommandSourceStack source, HyperCoreRuntime runtime) {
        TickMetrics.Snapshot snapshot = runtime.tickMetrics().snapshot();
        source.sendSuccess(() -> Component.literal(String.format(
            Locale.ROOT,
            "Tick window: n=%d/%d avg=%.2f ms p95=%.2f ms max=%.2f ms",
            snapshot.samples(),
            runtime.tickMetrics().windowSize(),
            snapshot.averageMs(),
            snapshot.p95Ms(),
            snapshot.maximumMs()
        )), false);
        return snapshot.samples();
    }

    private static int showCapabilities(CommandSourceStack source, HyperCoreRuntime runtime) {
        RuntimeCapabilities capabilities = runtime.capabilities();
        source.sendSuccess(() -> Component.literal(
            "CPU: " + capabilities.logicalProcessors() + " logical processors"
                + " | OS: " + capabilities.operatingSystem() + " " + capabilities.architecture()
                + " | Java: " + capabilities.javaVersion()
        ), false);

        RuntimeCapabilities.GpuProbe gpu = capabilities.gpu();
        if (!gpu.attempted()) {
            source.sendSuccess(() -> Component.literal("GPU probe: disabled"), false);
        } else if (!gpu.succeeded()) {
            source.sendSuccess(() -> Component.literal("GPU probe failed: " + gpu.error()), false);
        } else if (gpu.devices().isEmpty()) {
            source.sendSuccess(() -> Component.literal("GPU probe: no adapters detected"), false);
        } else {
            for (int index = 0; index < gpu.devices().size(); index++) {
                int adapterIndex = index;
                RuntimeCapabilities.GpuDevice device = gpu.devices().get(index);
                source.sendSuccess(() -> Component.literal(
                    "GPU " + adapterIndex + ": " + device.name()
                        + " | vendor=" + device.vendor()
                        + " | VRAM=" + toMiB(device.vramBytes()) + " MiB"
                        + " | id=" + device.deviceId()
                ), false);
            }
        }
        source.sendSuccess(() -> Component.literal(
            "Active compute backend: " + runtime.computeBackend().id()
                + " (" + runtime.computeBackend().deviceType().name().toLowerCase(Locale.ROOT) + ")"
        ), false);
        return gpu.devices().size();
    }

    private static long toMiB(long bytes) {
        return bytes / (1024L * 1024L);
    }
}
