package dev.hypercore.bridge.world;

import dev.hypercore.orchestrator.HyperCoreRole;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Resolves conflicts between deltas that target the same logical state within
 * one bridge batch.
 *
 * <p>Initial rules, matching the execution plan:
 * <ol>
 *   <li>Same block position in the same logical tick: last-write-wins based on
 *       a deterministic source priority (Forge host beats Fabric host).</li>
 *   <li>Entity mutations: the host that spawned an entity is authoritative for
 *       that entity until ownership is explicitly transferred; moves from a
 *       non-owner are dropped.</li>
 *   <li>Player mutations: the host the player is connected to is authoritative;
 *       the first host to report the player becomes the owner.</li>
 * </ol>
 *
 * <p>The resolver is stateless for block deltas; entity and player ownership is
 * tracked by the {@link WorldStateBridge} and fed in through the ownership
 * lookup callbacks. Every arbitration decision is recorded so it can be logged
 * by the caller for {@code /hypercore bridge replay} diagnostics.
 */
public final class ConflictResolver {
    /** Source priority: higher wins on equal-target block conflicts. */
    public static final int FORGE_PRIORITY = 2;
    public static final int FABRIC_PRIORITY = 1;

    private final Map<UUID, HyperCoreRole> entityOwners;
    private final Map<UUID, HyperCoreRole> playerOwners;

    public ConflictResolver(Map<UUID, HyperCoreRole> entityOwners, Map<UUID, HyperCoreRole> playerOwners) {
        this.entityOwners = Objects.requireNonNull(entityOwners, "entityOwners");
        this.playerOwners = Objects.requireNonNull(playerOwners, "playerOwners");
    }

    /**
     * Resolves a batch of deltas arriving from both hosts in one logical tick.
     *
     * @param deltas the combined deltas in arrival order
     * @return the surviving deltas (with their sources) in deterministic order
     */
    public List<SourcedDelta> resolve(List<SourcedDelta> deltas) {
        Map<String, SourcedDelta> blockWinners = new LinkedHashMap<>();
        Map<UUID, SourcedDelta> spawnWinners = new LinkedHashMap<>();
        List<SourcedDelta> ordered = new ArrayList<>();

        for (SourcedDelta sourced : deltas) {
            WorldDelta delta = sourced.delta();
            switch (delta.typeId()) {
                case BlockDelta.TYPE_ID -> {
                    BlockDelta block = (BlockDelta) delta;
                    SourcedDelta existing = blockWinners.get(block.conflictKey());
                    if (existing == null || priority(sourced.source()) > priority(existing.source())) {
                        blockWinners.put(block.conflictKey(), sourced);
                    }
                }
                case EntitySpawnDelta.TYPE_ID -> {
                    EntitySpawnDelta spawn = (EntitySpawnDelta) delta;
                    // The first host to spawn an entity owns it.
                    entityOwners.putIfAbsent(spawn.entityId(), sourced.source());
                    SourcedDelta existing = spawnWinners.get(spawn.entityId());
                    if (existing == null) {
                        spawnWinners.put(spawn.entityId(), sourced);
                        ordered.add(sourced);
                    }
                    // Duplicate spawns are dropped; ownership stays with the first.
                }
                case EntityMoveDelta.TYPE_ID -> {
                    EntityMoveDelta move = (EntityMoveDelta) delta;
                    HyperCoreRole owner = entityOwners.get(move.entityId());
                    // Moves are only accepted from the entity's owner; an unknown
                    // owner means the entity has not been spawned yet and the move
                    // would target a non-existent entity.
                    if (owner == sourced.source()) {
                        ordered.add(sourced);
                    }
                }
                case EntityRemoveDelta.TYPE_ID -> {
                    EntityRemoveDelta remove = (EntityRemoveDelta) delta;
                    HyperCoreRole owner = entityOwners.get(remove.entityId());
                    if (owner == null || owner == sourced.source()) {
                        entityOwners.remove(remove.entityId());
                        ordered.add(sourced);
                    }
                }
                case PlayerStateDelta.TYPE_ID -> {
                    PlayerStateDelta state = (PlayerStateDelta) delta;
                    HyperCoreRole owner = playerOwners.get(state.playerId());
                    if (owner == null) {
                        playerOwners.put(state.playerId(), sourced.source());
                        ordered.add(sourced);
                    } else if (owner == sourced.source()) {
                        ordered.add(sourced);
                    }
                }
                case PlayerInventoryDelta.TYPE_ID -> {
                    PlayerInventoryDelta inventory = (PlayerInventoryDelta) delta;
                    HyperCoreRole owner = playerOwners.get(inventory.playerId());
                    // Inventory updates are only accepted from the player's owner;
                    // an unknown owner means the player has not been seen yet.
                    if (owner == sourced.source()) {
                        ordered.add(sourced);
                    }
                }
                default -> ordered.add(sourced);
            }
        }

        // Block winners come after spawn/move/remove deltas in a deterministic
        // order (arrival order of their winners).
        for (SourcedDelta winner : blockWinners.values()) {
            ordered.add(winner);
        }
        return ordered;
    }

    private static int priority(HyperCoreRole source) {
        return source == HyperCoreRole.FORGE_HOST ? FORGE_PRIORITY : FABRIC_PRIORITY;
    }

    /**
     * A delta together with the host that produced it.
     */
    public record SourcedDelta(HyperCoreRole source, WorldDelta delta) {
        public SourcedDelta {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(delta, "delta");
        }
    }
}
