package dev.hypercore.server.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PermissionRegistryTest {

    @Test
    void resolvesExactNodeByOperatorStatus() {
        final PermissionRegistry registry = new PermissionRegistry();
        registry.register("a.b.c", PermissionRegistry.Default.OP);

        assertFalse(registry.check("a.b.c", false));
        assertTrue(registry.check("a.b.c", true));
        assertEquals(PermissionRegistry.Default.OP, registry.defaultOf("a.b.c"));
    }

    @Test
    void explicitWildcardBeatsImplicitParentFallback() {
        final PermissionRegistry registry = new PermissionRegistry();
        registry.register("a.b.*", PermissionRegistry.Default.TRUE);

        assertTrue(registry.check("a.b.c", false));
        assertTrue(registry.check("a.b.c.d", true));
        assertEquals(PermissionRegistry.Default.TRUE, registry.defaultOf("a.b.c"));
    }

    @Test
    void globalWildcardAppliesToAnyNode() {
        final PermissionRegistry registry = new PermissionRegistry();
        registry.register("*", PermissionRegistry.Default.NOT_OP);

        assertTrue(registry.check("x.y", false));
        assertFalse(registry.check("x.y", true));
        assertEquals(PermissionRegistry.Default.NOT_OP, registry.defaultOf("anything"));
    }

    @Test
    void fallsBackThroughImplicitParentSegments() {
        final PermissionRegistry registry = new PermissionRegistry();
        registry.register("a.b", PermissionRegistry.Default.FALSE);

        assertEquals(PermissionRegistry.Default.FALSE, registry.defaultOf("a.b.c"));
        assertFalse(registry.check("a.b.c", true));
    }

    @Test
    void prefersExactThenLongestWildcardThenParent() {
        final PermissionRegistry registry = new PermissionRegistry();
        registry.register("*", PermissionRegistry.Default.FALSE);
        registry.register("a.*", PermissionRegistry.Default.NOT_OP);
        registry.register("a.b.*", PermissionRegistry.Default.TRUE);

        assertTrue(registry.check("a.b.c", false));
        assertFalse(registry.check("a.z", true));
        assertFalse(registry.check("q.r", false));

        registry.register("a.b.c", PermissionRegistry.Default.FALSE);
        assertFalse(registry.check("a.b.c", true));
    }

    @Test
    void unregisteredNodesDenyByDefault() {
        final PermissionRegistry registry = new PermissionRegistry();

        assertEquals(PermissionRegistry.Default.FALSE, registry.defaultOf("nope"));
        assertFalse(registry.check("nope", true));
    }

    @Test
    void normalizesNodesAndRejectsBlankInput() {
        final PermissionRegistry registry = new PermissionRegistry();
        registry.register("  A.B ", PermissionRegistry.Default.TRUE);

        assertTrue(registry.check("a.b", true));
        assertEquals(Set.of("a.b"), registry.nodes());
        assertThrows(IllegalArgumentException.class, () -> registry.register("   ", PermissionRegistry.Default.TRUE));
        assertThrows(IllegalArgumentException.class, () -> registry.check("   ", true));
        assertThrows(NullPointerException.class, () -> registry.register(null, PermissionRegistry.Default.TRUE));
    }

    @Test
    void conflictingRegistrationFailsWhileIdenticalIsIdempotent() {
        final PermissionRegistry registry = new PermissionRegistry();
        registry.register("a.b", PermissionRegistry.Default.OP);

        assertThrows(IllegalArgumentException.class, () -> registry.register("a.b", PermissionRegistry.Default.FALSE));
        registry.register("A.B", PermissionRegistry.Default.OP);
        registry.register("a.b", PermissionRegistry.Default.OP);
        assertEquals(Set.of("a.b"), registry.nodes());
    }

    @Test
    void nodesReturnsSortedSnapshotIsolatedFromLaterChanges() {
        final PermissionRegistry registry = new PermissionRegistry();
        registry.register("b", PermissionRegistry.Default.TRUE);
        registry.register("a.c", PermissionRegistry.Default.FALSE);
        registry.register("a", PermissionRegistry.Default.OP);

        final Set<String> snapshot = registry.nodes();
        assertEquals(List.of("a", "a.c", "b"), List.copyOf(snapshot));
        assertThrows(UnsupportedOperationException.class, snapshot::clear);

        registry.register("z", PermissionRegistry.Default.TRUE);
        assertEquals(List.of("a", "a.c", "b"), List.copyOf(snapshot));
        assertEquals(List.of("a", "a.c", "b", "z"), List.copyOf(registry.nodes()));
    }
}
