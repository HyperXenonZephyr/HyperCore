package dev.hypercore.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hypercore.server.bukkit.BukkitCompatibilityBridge;
import dev.hypercore.server.compat.LoaderCompatibilityProvider;
import dev.hypercore.server.compat.LoaderCompatibilityRegistry;
import dev.hypercore.server.compute.ComputeRequest;
import dev.hypercore.server.compute.ComputeService;
import dev.hypercore.server.plugin.PluginContext;
import dev.hypercore.server.plugin.PluginDescriptor;
import dev.hypercore.server.plugin.PluginRuntime;
import dev.hypercore.server.plugin.ServerPlugin;
import dev.hypercore.server.region.RegionKey;
import dev.hypercore.server.region.RegionMutation;
import dev.hypercore.server.region.ServerMutationQueue;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RuntimeBoundaryTest {
    @Test
    void appliesImmutableMutationsOnlyOnTheServerThread() throws Exception {
        ServerMutationQueue queue = new ServerMutationQueue(Thread.currentThread());
        RegionMutation mutation = RegionMutation.marker(new RegionKey("minecraft:overworld", 0, 0), "test-marker");
        Thread worker = new Thread(() -> queue.submit(mutation));
        worker.start();
        worker.join();
        assertEquals(1, queue.pending());
        java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
        Thread offThread = new Thread(() -> {
            try {
                queue.applyAtTickBoundary(ignored -> { });
            } catch (Throwable error) {
                failure.set(error);
            }
        });
        offThread.start();
        offThread.join();
        assertTrue(failure.get() instanceof IllegalStateException);
        assertEquals(1, queue.applyAtTickBoundary(value -> assertEquals(mutation, value)));
    }

    @Test
    void isolatesCompatibilityProviderFailures() {
        LoaderCompatibilityRegistry registry = new LoaderCompatibilityRegistry();
        registry.register(new LoaderCompatibilityProvider() {
            @Override public String id() { return "broken"; }
            @Override public void onServerStarting() { throw new IllegalStateException("expected"); }
        });
        registry.start();
        assertEquals(List.of("broken"), registry.providerIds());
        assertEquals(1, registry.failures().size());
    }

    @Test
    void executesPluginLifecycleCommandsTasksAndEventsOnServerThread() throws Exception {
        List<String> lifecycle = new ArrayList<>();
        AtomicInteger events = new AtomicInteger();
        try (PluginRuntime runtime = new PluginRuntime(Thread.currentThread(), Files.createTempDirectory("hypercore-plugins"))) {
            runtime.register(new PluginDescriptor("test_plugin", "Test", "1", "test.Main", List.of()), new ServerPlugin() {
                @Override public void onLoad(PluginContext context) { lifecycle.add("load"); }
                @Override public void onEnable(PluginContext context) {
                    lifecycle.add("enable");
                    context.registerCommand("sample", arguments -> arguments.size() == 1);
                    context.runTask(() -> lifecycle.add("task"));
                    context.registerListener(String.class, event -> events.incrementAndGet());
                }
                @Override public void onDisable(PluginContext context) { lifecycle.add("disable"); }
            });
            BukkitCompatibilityBridge bridge = new BukkitCompatibilityBridge(runtime);
            assertTrue(bridge.dispatchCommand("sample", new String[] {"argument"}));
            assertEquals(1, bridge.postEvent("event"));
            runtime.tick();
            assertEquals(1, events.get());
            assertEquals(List.of("load", "enable", "task"), lifecycle);
        }
        assertEquals(List.of("load", "enable", "task", "disable"), lifecycle);
    }

    @Test
    void rollsBackFailedPluginActivationAndDefersExternalCancellation() throws Exception {
        AtomicInteger events = new AtomicInteger();
        AtomicInteger tasks = new AtomicInteger();
        try (PluginRuntime runtime = new PluginRuntime(Thread.currentThread(), Files.createTempDirectory("hypercore-plugins"))) {
            assertThrows(IllegalStateException.class, () -> runtime.register(
                new PluginDescriptor("broken_plugin", "Broken", "1", "test.Broken", List.of()),
                new ServerPlugin() {
                    @Override public void onEnable(PluginContext context) {
                        context.registerCommand("leaked", arguments -> true);
                        context.runTask(tasks::incrementAndGet);
                        context.registerListener(String.class, ignored -> events.incrementAndGet());
                        throw new IllegalStateException("expected");
                    }
                }
            ));
            assertFalse(runtime.dispatchCommand("leaked", new String[0]));
            assertEquals(0, runtime.postEvent("event"));
            runtime.tick();
            assertEquals(0, tasks.get());

            AtomicInteger callbacks = new AtomicInteger();
            PluginRuntime.TaskHandle task = registerHandles(runtime, callbacks);
            Thread cancellation = new Thread(task::cancel);
            cancellation.start();
            cancellation.join();
            runtime.tick();
            assertEquals(0, callbacks.get());
            assertEquals(0, runtime.postEvent("event"));
        }
        assertEquals(0, events.get());
    }

    private static PluginRuntime.TaskHandle registerHandles(PluginRuntime runtime, AtomicInteger callbacks) {
        final PluginRuntime.TaskHandle[] task = new PluginRuntime.TaskHandle[1];
        runtime.register(new PluginDescriptor("handle_plugin", "Handles", "1", "test.Handles", List.of()), new ServerPlugin() {
            @Override public void onEnable(PluginContext context) {
                task[0] = context.runTask(callbacks::incrementAndGet);
                PluginRuntime.Subscription subscription = context.registerListener(String.class, ignored -> callbacks.incrementAndGet());
                Thread unsubscribe = new Thread(subscription::close);
                unsubscribe.start();
                try {
                    unsubscribe.join();
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(error);
                }
            }
        });
        return task[0];
    }

    @Test
    void fallsBackToDeterministicCpuComputeWithoutGpu() throws Exception {
        try (ComputeService compute = new ComputeService()) {
            assertFalse(compute.gpuAvailable());
            var result = compute.submit(new ComputeRequest("squared-length-3d", new double[] {3, 4, 0, 1, 2, 2})).get();
            assertEquals("cpu-scalar", result.backend());
            assertArrayEquals(new double[] {25, 9}, result.values());
        }
    }

    @Test
    void fallsBackToExplicitF32ComputeWithoutChangingTheF64Operation() throws Exception {
        try (ComputeService compute = new ComputeService()) {
            var result = compute.submit(new ComputeRequest("squared-length-3d-f32", new double[] {3, 4, 0, 1, 2, 2})).get();
            assertEquals("cpu-scalar", result.backend());
            assertArrayEquals(new double[] {25, 9}, result.values());
        }
    }
}
