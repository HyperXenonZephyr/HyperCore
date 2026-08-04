package dev.hypercore.bridge.world;

import dev.hypercore.orchestrator.HyperCoreRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the initial conflict rules: Forge priority on equal block targets,
 * spawn ownership, and player authority.
 */
class ConflictResolverTest {
    private final Map<UUID, HyperCoreRole> entityOwners = new ConcurrentHashMap<>();
    private final Map<UUID, HyperCoreRole> playerOwners = new ConcurrentHashMap<>();
    private ConflictResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ConflictResolver(entityOwners, playerOwners);
    }

    private static ConflictResolver.SourcedDelta sourced(HyperCoreRole role, WorldDelta delta) {
        return new ConflictResolver.SourcedDelta(role, delta);
    }

    @Test
    void forgeWinsSameBlockConflict() {
        List<WorldDelta> resolved = resolver.resolve(List.of(
            sourced(HyperCoreRole.FABRIC_HOST, new BlockDelta("w", 1, 2, 3, "DIRT")),
            sourced(HyperCoreRole.FORGE_HOST, new BlockDelta("w", 1, 2, 3, "STONE"))
        )).stream().map(ConflictResolver.SourcedDelta::delta).toList();

        assertEquals(1, resolved.size());
        assertEquals("STONE", ((BlockDelta) resolved.get(0)).blockState());
    }

    @Test
    void fabricWinsWhenForgeHasNoContender() {
        List<WorldDelta> resolved = resolver.resolve(List.of(
            sourced(HyperCoreRole.FABRIC_HOST, new BlockDelta("w", 5, 6, 7, "SAND"))
        )).stream().map(ConflictResolver.SourcedDelta::delta).toList();

        assertEquals(1, resolved.size());
        assertEquals("SAND", ((BlockDelta) resolved.get(0)).blockState());
    }

    @Test
    void duplicateEntitySpawnsKeepFirstAndOwnership() {
        UUID id = UUID.randomUUID();
        List<WorldDelta> resolved = resolver.resolve(List.of(
            sourced(HyperCoreRole.FORGE_HOST, new EntitySpawnDelta("w", id, "ZOMBIE", 1, 2, 3)),
            sourced(HyperCoreRole.FABRIC_HOST, new EntitySpawnDelta("w", id, "ZOMBIE", 9, 9, 9))
        )).stream().map(ConflictResolver.SourcedDelta::delta).toList();

        assertEquals(1, resolved.size());
        assertEquals(HyperCoreRole.FORGE_HOST, entityOwners.get(id));
    }

    @Test
    void movesFromNonOwnerAreDropped() {
        UUID id = UUID.randomUUID();
        // Forge spawns the entity first and becomes its owner.
        resolver.resolve(List.of(
            sourced(HyperCoreRole.FORGE_HOST, new EntitySpawnDelta("w", id, "ZOMBIE", 1, 2, 3))
        ));
        // Fabric attempts to move the Forge-owned entity; the move is dropped.
        List<WorldDelta> resolved = resolver.resolve(List.of(
            sourced(HyperCoreRole.FABRIC_HOST, new EntityMoveDelta("w", id, 50, 50, 50)),
            sourced(HyperCoreRole.FORGE_HOST, new EntityMoveDelta("w", id, 10, 10, 10))
        )).stream().map(ConflictResolver.SourcedDelta::delta).toList();

        assertEquals(1, resolved.size());
        assertEquals(10.0, ((EntityMoveDelta) resolved.get(0)).x());
    }

    @Test
    void playerStateOnlyAcceptedFromOwnerHost() {
        UUID player = UUID.randomUUID();
        List<WorldDelta> resolved = resolver.resolve(List.of(
            sourced(HyperCoreRole.FORGE_HOST, new PlayerStateDelta("w", player, 20.0, 1, 2, 3)),
            sourced(HyperCoreRole.FABRIC_HOST, new PlayerStateDelta("w", player, 5.0, 9, 9, 9))
        )).stream().map(ConflictResolver.SourcedDelta::delta).toList();

        // The first reporter owns the player; the fabric delta is dropped.
        assertEquals(1, resolved.size());
        assertEquals(20.0, ((PlayerStateDelta) resolved.get(0)).health());
        assertEquals(HyperCoreRole.FORGE_HOST, playerOwners.get(player));
    }

    @Test
    void entityRemovalFromOwnerIsAccepted() {
        UUID id = UUID.randomUUID();
        resolver.resolve(List.of(
            sourced(HyperCoreRole.FABRIC_HOST, new EntitySpawnDelta("w", id, "COW", 1, 2, 3))
        ));
        List<WorldDelta> resolved = resolver.resolve(List.of(
            sourced(HyperCoreRole.FABRIC_HOST, new EntityRemoveDelta("w", id))
        )).stream().map(ConflictResolver.SourcedDelta::delta).toList();

        assertEquals(1, resolved.size());
        assertTrue(resolved.get(0) instanceof EntityRemoveDelta);
        assertTrue(entityOwners.isEmpty());
    }
}
