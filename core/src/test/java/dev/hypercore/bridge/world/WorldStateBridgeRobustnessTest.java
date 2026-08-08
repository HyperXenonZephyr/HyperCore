package dev.hypercore.bridge.world;

import dev.hypercore.orchestrator.HyperCoreRole;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Robustness coverage for {@link WorldStateBridge} beyond the happy-path
 * ordering checks in {@link WorldStateBridgeTest}. Exercises cross-tick
 * ownership persistence, sequence monotonicity, missing/throwing outbound
 * callbacks, outbound replacement, and large-batch stress.
 */
class WorldStateBridgeRobustnessTest {

    @Test
    void sequenceAndTickAdvanceAcrossMultipleFlushes() {
        WorldStateBridge bridge = new WorldStateBridge();
        List<WorldStateBridge.OrderedBatch> batches = new ArrayList<>();
        bridge.setOutbound(batches::add);

        for (int tick = 1; tick <= 3; tick++) {
            bridge.submit(HyperCoreRole.FORGE_HOST, List.of(
                new BlockDelta("w", tick, tick, tick, "STONE")
            ));
            bridge.flush();
        }

        assertEquals(3, batches.size());
        assertEquals(3L, bridge.logicalTick());
        // Sequences are contiguous across flushes: 0, 1, 2.
        assertEquals(0L, batches.get(0).deltas().get(0).sequence());
        assertEquals(1L, batches.get(1).deltas().get(0).sequence());
        assertEquals(2L, batches.get(2).deltas().get(0).sequence());
        // Each batch carries its own logical tick.
        assertEquals(1L, batches.get(0).logicalTick());
        assertEquals(2L, batches.get(1).logicalTick());
        assertEquals(3L, batches.get(2).logicalTick());
        assertEquals(3L, bridge.nextSequence());
    }

    @Test
    void ownershipPersistsAcrossTicks() {
        WorldStateBridge bridge = new WorldStateBridge();
        List<WorldStateBridge.OrderedBatch> batches = new ArrayList<>();
        bridge.setOutbound(batches::add);

        UUID id = UUID.randomUUID();
        // Tick 1: Forge spawns the entity and becomes its owner.
        bridge.submit(HyperCoreRole.FORGE_HOST, List.of(
            new EntitySpawnDelta("w", id, "ZOMBIE", 1, 2, 3)
        ));
        bridge.flush();
        assertEquals(HyperCoreRole.FORGE_HOST, bridge.entityOwners().get(id));

        // Tick 2: a non-owner (Fabric) move is dropped, the owner move survives.
        bridge.submit(HyperCoreRole.FABRIC_HOST, List.of(
            new EntityMoveDelta("w", id, 50, 50, 50)
        ));
        bridge.submit(HyperCoreRole.FORGE_HOST, List.of(
            new EntityMoveDelta("w", id, 10, 10, 10)
        ));
        bridge.flush();

        assertEquals(2, batches.size());
        WorldStateBridge.OrderedBatch tick2 = batches.get(1);
        assertEquals(1, tick2.deltas().size());
        EntityMoveDelta move = (EntityMoveDelta) tick2.deltas().get(0).delta();
        assertEquals(10.0, move.x());
        assertEquals(HyperCoreRole.FORGE_HOST, tick2.deltas().get(0).source());
    }

    @Test
    void preRegisteredPlayerOwnerBlocksNonOwnerStateDelta() {
        WorldStateBridge bridge = new WorldStateBridge();
        List<WorldStateBridge.OrderedBatch> batches = new ArrayList<>();
        bridge.setOutbound(batches::add);

        UUID player = UUID.randomUUID();
        bridge.setPlayerOwner(player, HyperCoreRole.FORGE_HOST);

        // Fabric (non-owner) state delta is dropped; Forge (owner) delta survives.
        bridge.submit(HyperCoreRole.FABRIC_HOST, List.of(
            new PlayerStateDelta("w", player, 5.0, 9, 9, 9)
        ));
        bridge.submit(HyperCoreRole.FORGE_HOST, List.of(
            new PlayerStateDelta("w", player, 20.0, 1, 2, 3)
        ));
        bridge.flush();

        assertEquals(1, batches.size());
        assertEquals(1, batches.get(0).deltas().size());
        PlayerStateDelta state = (PlayerStateDelta) batches.get(0).deltas().get(0).delta();
        assertEquals(20.0, state.health());
        assertEquals(HyperCoreRole.FORGE_HOST, batches.get(0).deltas().get(0).source());

        // Removing ownership reopens the player: the next first-reporter wins.
        bridge.removePlayerOwner(player);
        assertFalse(bridge.playerOwners().containsKey(player));
        bridge.submit(HyperCoreRole.FABRIC_HOST, List.of(
            new PlayerStateDelta("w", player, 1.0, 0, 0, 0)
        ));
        bridge.flush();
        assertEquals(HyperCoreRole.FABRIC_HOST, bridge.playerOwners().get(player));
    }

    @Test
    void flushWithoutOutboundClearsPendingAndAdvancesTickButDoesNotBroadcast() {
        WorldStateBridge bridge = new WorldStateBridge();
        // No outbound callback registered.

        bridge.submit(HyperCoreRole.FORGE_HOST, List.of(
            new BlockDelta("w", 1, 2, 3, "STONE")
        ));
        assertEquals(1, bridge.pendingCount());

        // Flush must not throw even though nothing is wired up; the deltas are
        // resolved (and dropped) and the logical timeline still advances.
        assertDoesNotThrow(bridge::flush);

        assertEquals(0, bridge.pendingCount());
        assertEquals(1L, bridge.logicalTick());
        // Sequence numbers were consumed for the resolved delta.
        assertEquals(1L, bridge.nextSequence());
    }

    @Test
    void replacingOutboundRoutesSubsequentBatchesToNewSink() {
        WorldStateBridge bridge = new WorldStateBridge();
        List<WorldStateBridge.OrderedBatch> first = new ArrayList<>();
        bridge.setOutbound(first::add);

        bridge.submit(HyperCoreRole.FORGE_HOST, List.of(new BlockDelta("w", 1, 1, 1, "STONE")));
        bridge.flush();
        assertEquals(1, first.size());

        List<WorldStateBridge.OrderedBatch> second = new ArrayList<>();
        bridge.setOutbound(second::add);

        bridge.submit(HyperCoreRole.FABRIC_HOST, List.of(new BlockDelta("w", 2, 2, 2, "DIRT")));
        bridge.flush();

        assertEquals(1, first.size());
        assertEquals(1, second.size());
        assertEquals(HyperCoreRole.FABRIC_HOST, second.get(0).deltas().get(0).source());
    }

    @Test
    void outboundThrowingDoesNotCorruptBridgeState() {
        WorldStateBridge bridge = new WorldStateBridge();
        bridge.setOutbound(batch -> {
            throw new IllegalStateException("downstream broadcast failure");
        });

        bridge.submit(HyperCoreRole.FORGE_HOST, List.of(new BlockDelta("w", 1, 1, 1, "STONE")));
        // The throwing outbound propagates, but pending was already cleared and
        // the timeline already advanced, so the bridge stays usable.
        assertThrows(IllegalStateException.class, bridge::flush);
        assertEquals(0, bridge.pendingCount());

        // Rewire a healthy outbound and verify a subsequent flush works and
        // sequence numbers continue from where the failed flush left off.
        List<WorldStateBridge.OrderedBatch> batches = new ArrayList<>();
        bridge.setOutbound(batches::add);
        bridge.submit(HyperCoreRole.FORGE_HOST, List.of(new BlockDelta("w", 2, 2, 2, "DIRT")));
        bridge.flush();

        assertEquals(1, batches.size());
        // First flush consumed sequence 0; this delta continues at 1.
        assertEquals(1L, batches.get(0).deltas().get(0).sequence());
    }

    @Test
    void largeBatchResolvesAndBroadcastsInOneFlush() {
        WorldStateBridge bridge = new WorldStateBridge();
        List<WorldStateBridge.OrderedBatch> batches = new ArrayList<>();
        bridge.setOutbound(batches::add);

        int positions = 1000;
        List<WorldDelta> forge = new ArrayList<>(positions);
        List<WorldDelta> fabric = new ArrayList<>(positions);
        for (int i = 0; i < positions; i++) {
            // Same position from both hosts: Forge wins each conflict.
            forge.add(new BlockDelta("w", i, 0, 0, "STONE"));
            fabric.add(new BlockDelta("w", i, 0, 0, "DIRT"));
        }
        bridge.submit(HyperCoreRole.FABRIC_HOST, fabric);
        bridge.submit(HyperCoreRole.FORGE_HOST, forge);

        assertDoesNotThrow(bridge::flush);

        assertEquals(1, batches.size());
        assertEquals(positions, batches.get(0).deltas().size());
        // Every surviving winner came from Forge and is STONE.
        for (WorldStateBridge.ResolvedDelta resolved : batches.get(0).deltas()) {
            assertEquals(HyperCoreRole.FORGE_HOST, resolved.source());
            assertEquals("STONE", ((BlockDelta) resolved.delta()).blockState());
        }
        assertEquals(positions, bridge.nextSequence());
    }

    @Test
    void submitEmptyListIsNoOp() {
        WorldStateBridge bridge = new WorldStateBridge();
        bridge.setOutbound(batch -> {});

        bridge.submit(HyperCoreRole.FORGE_HOST, List.of());
        assertEquals(0, bridge.pendingCount());
        bridge.flush();
        assertEquals(0L, bridge.logicalTick());
    }

    @Test
    void submitNullDeltasThrows() {
        WorldStateBridge bridge = new WorldStateBridge();
        bridge.setOutbound(batch -> {});
        assertThrows(NullPointerException.class,
            () -> bridge.submit(HyperCoreRole.FORGE_HOST, null));
    }

    @Test
    void blockConflictAtBridgeLevelForgeWins() {
        WorldStateBridge bridge = new WorldStateBridge();
        List<WorldStateBridge.OrderedBatch> batches = new ArrayList<>();
        bridge.setOutbound(batches::add);

        bridge.submit(HyperCoreRole.FABRIC_HOST, List.of(new BlockDelta("w", 7, 8, 9, "DIRT")));
        bridge.submit(HyperCoreRole.FORGE_HOST, List.of(new BlockDelta("w", 7, 8, 9, "STONE")));
        bridge.flush();

        assertEquals(1, batches.size());
        WorldStateBridge.OrderedBatch batch = batches.get(0);
        assertEquals(1, batch.deltas().size());
        assertNotNull(batch.deltas().get(0));
        assertEquals(HyperCoreRole.FORGE_HOST, batch.deltas().get(0).source());
        assertTrue(batch.deltas().get(0).delta() instanceof BlockDelta);
        assertEquals("STONE", ((BlockDelta) batch.deltas().get(0).delta()).blockState());
    }
}
