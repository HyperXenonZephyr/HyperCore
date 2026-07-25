package dev.hypercore.plugin;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static dev.hypercore.plugin.PluginPermissionService.PermissionDefault.FALSE;
import static dev.hypercore.plugin.PluginPermissionService.PermissionDefault.OP;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginPermissionServiceTest {
    @Test
    void appliesDefaultsAndExplicitWildcardOverrides() {
        PluginPermissionService permissions = new PluginPermissionService();
        permissions.register("test", "hypercore.admin", "", OP);
        permissions.register("test", "hypercore.admin.reload", "", FALSE);
        TestSender operator = new TestSender("op", true);
        TestSender player = new TestSender("player", false);

        assertTrue(permissions.test(operator, "hypercore.admin"));
        assertFalse(permissions.test(player, "hypercore.admin"));
        assertFalse(permissions.test(operator, "hypercore.admin.reload"));

        player.overrides.put("hypercore.admin.*", true);
        assertTrue(permissions.test(player, "hypercore.admin.reload"));
    }

    @Test
    void unregisterRemovesOnlyOwnedPermissions() {
        PluginPermissionService permissions = new PluginPermissionService();
        permissions.register("one", "one.use", "", FALSE);
        permissions.register("two", "two.use", "", FALSE);

        permissions.unregisterPlugin("one");

        assertEqualsFalse(permissions.test(new TestSender("sender", false), "one.use"));
        assertFalse(permissions.test(new TestSender("sender", false), "two.use"));
        assertThrows(
            IllegalArgumentException.class,
            () -> permissions.register("two", "two.use", "", FALSE)
        );
    }

    private static void assertEqualsFalse(boolean value) {
        assertFalse(value);
    }

    private static final class TestSender implements PluginCommandSender {
        private final String name;
        private final boolean operator;
        private final Map<String, Boolean> overrides = new HashMap<>();

        private TestSender(String name, boolean operator) {
            this.name = name;
            this.operator = operator;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public boolean operator() {
            return operator;
        }

        @Override
        public Optional<Boolean> permissionOverride(String permission) {
            return Optional.ofNullable(overrides.get(permission));
        }

        @Override
        public void sendMessage(String message) {
        }
    }
}
