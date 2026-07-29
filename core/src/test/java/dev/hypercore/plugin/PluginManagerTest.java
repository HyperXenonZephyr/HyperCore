package dev.hypercore.plugin;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static dev.hypercore.plugin.PluginEventBus.EventPriority.NORMAL;
import static dev.hypercore.plugin.PluginPermissionService.PermissionDefault.FALSE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PluginManagerTest {
    @Test
    void runsLifecycleAndCleansRegistrationsOnDisable() {
        PluginManager manager = new PluginManager();
        List<String> lifecycle = new ArrayList<>();
        manager.register(new PluginDescriptor("demo", "Demo", "1.0"), new HyperPlugin() {
            @Override
            public void onLoad(PluginContext context) {
                lifecycle.add("load");
                context.registerPermission("demo.use", "", FALSE);
                context.registerCommand(new PluginCommandRegistry.CommandDefinition(
                    "demo", List.of(), "demo.use", "", "", (sender, label, arguments) -> true
                ));
                context.registerListener(TestEvent.class, NORMAL, false, event -> lifecycle.add("event"));
            }

            @Override
            public void onEnable(PluginContext context) {
                lifecycle.add("enable");
            }

            @Override
            public void onDisable(PluginContext context) {
                lifecycle.add("disable");
            }
        });

        manager.enableAll();
        assertEquals(List.of("load", "enable"), lifecycle);
        assertEquals(1, manager.status().enabledPlugins());
        assertEquals(1, manager.status().registeredCommands());
        assertEquals(1, manager.status().registeredPermissions());

        manager.close();
        assertEquals(List.of("load", "enable", "disable"), lifecycle);
        assertEquals(0, manager.status().registeredCommands());
        assertEquals(0, manager.status().registeredPermissions());
        assertEquals(0, manager.status().registeredListeners());
    }

    @Test
    void marksFailedPluginsAndCleansPartialLoad() {
        PluginManager manager = new PluginManager();
        manager.register(new PluginDescriptor("broken", "Broken", "1.0"), new HyperPlugin() {
            @Override
            public void onLoad(PluginContext context) {
                throw new IllegalStateException("expected test failure");
            }
        });

        manager.enableAll();

        assertEquals(1, manager.status().registeredPlugins());
        assertEquals(1, manager.status().failedPlugins());
        assertEquals(0, manager.status().registeredCommands());
        assertEquals(0, manager.status().registeredPermissions());
    }

    @Test
    void rejectsDuplicatePluginIds() {
        PluginManager manager = new PluginManager();
        PluginDescriptor descriptor = new PluginDescriptor("demo", "Demo", "1.0");
        manager.register(descriptor, new HyperPlugin() { });

        assertThrows(IllegalArgumentException.class, () -> manager.register(descriptor, new HyperPlugin() { }));
    }

    private static final class TestEvent implements PluginEventBus.PluginEvent {
    }
}
