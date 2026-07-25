package dev.hypercore.command;

import com.mojang.brigadier.CommandDispatcher;
import dev.hypercore.HyperCore;
import dev.hypercore.concurrent.HyperCoreExecutor;
import dev.hypercore.metrics.TickMetrics;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import static net.minecraft.commands.Commands.literal;

public final class HyperCoreCommands {
    private HyperCoreCommands() {
    }

    public static void register(
        CommandDispatcher<CommandSourceStack> dispatcher,
        HyperCoreExecutor executor,
        TickMetrics tickMetrics
    ) {
        dispatcher.register(literal("hypercore")
            .requires(source -> source.hasPermission(2))
            .then(literal("status").executes(context -> {
                Runtime runtime = Runtime.getRuntime();
                long usedMemory = runtime.totalMemory() - runtime.freeMemory();
                context.getSource().sendSuccess(() -> Component.literal(
                    "HyperCore " + HyperCore.VERSION
                        + " | workers=" + executor.parallelism()
                        + " | tasks=" + executor.completedTasks() + "/" + executor.submittedTasks()
                        + " | heap=" + toMiB(usedMemory) + "/" + toMiB(runtime.maxMemory()) + " MiB"
                ), false);
                return 1;
            }))
            .then(literal("timings").executes(context -> {
                TickMetrics.Snapshot snapshot = tickMetrics.snapshot();
                context.getSource().sendSuccess(() -> Component.literal(String.format(
                    "Tick window: n=%d avg=%.2f ms p95=%.2f ms max=%.2f ms",
                    snapshot.samples(), snapshot.averageMs(), snapshot.p95Ms(), snapshot.maximumMs()
                )), false);
                return snapshot.samples();
            }))
        );
    }

    private static long toMiB(long bytes) {
        return bytes / (1024L * 1024L);
    }
}

