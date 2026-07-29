package dev.hypercore.plugin;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static dev.hypercore.plugin.PluginPermissionService.PermissionDefault.OP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PluginCommandRegistryTest {
    @Test
    void dispatchesAliasesAndArgumentsThroughPermissionChecks() {
        PluginPermissionService permissions = new PluginPermissionService();
        permissions.register("test", "test.run", "", OP);
        PluginCommandRegistry registry = new PluginCommandRegistry(permissions);
        List<String> received = new ArrayList<>();
        registry.register("test", new PluginCommandRegistry.CommandDefinition(
            "run",
            List.of("r"),
            "test.run",
            "",
            "",
            (sender, label, arguments) -> {
                received.add(label + arguments);
                return true;
            }
        ));
        TestSender sender = new TestSender();

        assertEquals(
            PluginCommandRegistry.DispatchStatus.NO_PERMISSION,
            registry.dispatch("r", List.of("one"), sender).status()
        );
        sender.operator = true;
        PluginCommandRegistry.DispatchResult result = registry.dispatch("r", List.of("one"), sender);

        assertEquals(PluginCommandRegistry.DispatchStatus.EXECUTED, result.status());
        assertEquals(List.of("r[one]"), received);
    }

    @Test
    void rejectsCommandLabelCollisionsAndCleansPluginOwnership() {
        PluginCommandRegistry registry = new PluginCommandRegistry(new PluginPermissionService());
        PluginCommandRegistry.CommandDefinition definition = definition("status");
        registry.register("one", definition);
        assertThrows(IllegalArgumentException.class, () -> registry.register("two", definition));

        registry.unregisterPlugin("one");
        assertEquals(0, registry.registeredCommands());
    }

    private static PluginCommandRegistry.CommandDefinition definition(String name) {
        return new PluginCommandRegistry.CommandDefinition(name, List.of(), "", "", "", (s, l, a) -> true);
    }

    private static final class TestSender implements PluginCommandSender {
        private boolean operator;

        @Override
        public String name() {
            return "sender";
        }

        @Override
        public boolean operator() {
            return operator;
        }

        @Override
        public void sendMessage(String message) {
        }
    }
}
