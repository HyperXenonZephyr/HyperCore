package dev.hypercore.server.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PluginRuntimeFeaturesTest {
    @Test
    void permissionsResolveThroughWildcardsAndParentsAndRollBackOnFailure() throws Exception {
        try (PluginRuntime runtime = new PluginRuntime(Thread.currentThread(), Files.createTempDirectory("plugins"))) {
            runtime.register(new PluginDescriptor("perm_plugin", "Perm", "1", "test.Main", List.of()), new ServerPlugin() {
                @Override public void onEnable(PluginContext context) {
                    context.registerPermission("myplugin.build", PermissionRegistry.Default.TRUE);
                    context.registerPermission("myplugin.admin.*", PermissionRegistry.Default.OP);
                }
            });
            assertTrue(runtime.permissions().check("myplugin.build", false));
            assertTrue(runtime.permissions().check("myplugin.admin.zone", true));
            assertFalse(runtime.permissions().check("myplugin.admin.zone", false));
            assertFalse(runtime.permissions().check("myplugin.other", false));

            AtomicInteger events = new AtomicInteger();
            try {
                runtime.register(new PluginDescriptor("broken_perm", "Broken", "1", "test.Main", List.of()), new ServerPlugin() {
                    @Override public void onEnable(PluginContext context) {
                        context.registerPermission("leaked.node", PermissionRegistry.Default.TRUE);
                        throw new IllegalStateException("expected");
                    }
                });
            } catch (IllegalStateException expected) {
                events.incrementAndGet();
            }
            assertEquals(1, events.get());
            assertFalse(runtime.permissions().check("leaked.node", false));
        }
    }

    @Test
    void delayedAndRepeatingTasksFireOnTheirSchedule() throws Exception {
        try (PluginRuntime runtime = new PluginRuntime(Thread.currentThread(), Files.createTempDirectory("plugins"))) {
            AtomicInteger delayed = new AtomicInteger();
            AtomicInteger repeats = new AtomicInteger();
            runtime.register(new PluginDescriptor("sched_plugin", "Sched", "1", "test.Main", List.of()), new ServerPlugin() {
                @Override public void onEnable(PluginContext context) {
                    context.runTaskLater(delayed::incrementAndGet, 3);
                    context.runTaskTimer(repeats::incrementAndGet, 0, 2);
                }
            });
            runtime.tick();
            assertEquals(1, repeats.get());
            assertEquals(0, delayed.get());
            runtime.tick();
            assertEquals(2, repeats.get());
            assertEquals(0, delayed.get());
            runtime.tick();
            assertEquals(1, delayed.get());
            assertEquals(2, repeats.get());
            runtime.tick();
            assertEquals(3, repeats.get());
        }
    }

    @Test
    void disableCancelsPendingOwnedTasks() throws Exception {
        AtomicInteger fired = new AtomicInteger();
        try (PluginRuntime runtime = new PluginRuntime(Thread.currentThread(), Files.createTempDirectory("plugins"))) {
            runtime.register(new PluginDescriptor("cleanup_plugin", "Cleanup", "1", "test.Main", List.of()), new ServerPlugin() {
                @Override public void onEnable(PluginContext context) {
                    context.runTaskLater(fired::incrementAndGet, 50);
                }
            });
        }
        assertEquals(0, fired.get());
    }

    @Test
    void failingTimerIsRemovedWithoutBlockingOtherTasks() throws Exception {
        try (PluginRuntime runtime = new PluginRuntime(Thread.currentThread(), Files.createTempDirectory("plugins"))) {
            AtomicInteger survivor = new AtomicInteger();
            runtime.register(new PluginDescriptor("boom_plugin", "Boom", "1", "test.Main", List.of()), new ServerPlugin() {
                @Override public void onEnable(PluginContext context) {
                    context.runTaskTimer(() -> { throw new IllegalStateException("boom"); }, 0, 1);
                    context.runTaskTimer(survivor::incrementAndGet, 0, 1);
                }
            });
            runtime.tick();
            runtime.tick();
            assertEquals(2, survivor.get());
        }
    }

    @Test
    void externalCancelOfDelayedTaskDefersToNextTick() throws Exception {
        try (PluginRuntime runtime = new PluginRuntime(Thread.currentThread(), Files.createTempDirectory("plugins"))) {
            AtomicInteger fired = new AtomicInteger();
            PluginRuntime.TaskHandle[] handle = new PluginRuntime.TaskHandle[1];
            runtime.register(new PluginDescriptor("cancel_plugin", "Cancel", "1", "test.Main", List.of()), new ServerPlugin() {
                @Override public void onEnable(PluginContext context) {
                    handle[0] = context.runTaskLater(fired::incrementAndGet, 2);
                }
            });
            Thread canceller = new Thread(handle[0]::cancel);
            canceller.start();
            canceller.join();
            runtime.tick();
            runtime.tick();
            assertEquals(0, fired.get());
        }
    }
}
