package dev.hypercore.bridge.world;

import dev.hypercore.orchestrator.HyperCoreRole;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the logical timeline: batching, sequence stamping, and outbound
 * ordering.
 */
class WorldStateBridgeTest {

    @Test
    void stampsOrderedSequencesAndBroadcastsOnce() {
        WorldStateBridge bridge = new WorldStateBridge();
        List<WorldStateBridge.OrderedBatch> batches = new ArrayList<>();
        bridge.setOutbound(batches::add);

        UUID id = UUID.randomUUID();
        bridge.submit(HyperCoreRole.FORGE_HOST, List.of(new BlockDelta("w", 1, 2, 3, "STONE")));
        bridge.submit(HyperCoreRole.FABRIC_HOST, List.of(
            new EntitySpawnDelta("w", id, "ZOMBIE", 1, 2, 3),
            new BlockDelta("w", 4, 5, 6, "DIRT")
        ));

        assertEquals(3, bridge.pendingCount());
        bridge.flush();
        assertEquals(0, bridge.pendingCount());
        assertEquals(1, batches.size());

        WorldStateBridge.OrderedBatch batch = batches.get(0);
        assertEquals(1L, batch.logicalTick());
        assertEquals(3, batch.deltas().size());
        // Sequences are contiguous and start at 0.
        assertEquals(0L, batch.deltas().get(0).sequence());
        assertEquals(1L, batch.deltas().get(1).sequence());
        assertEquals(2L, batch.deltas().get(2).sequence());
        // Entity deltas keep arrival order; block winners are appended after
        // them, and sources are preserved per delta.
        assertTrue(batch.deltas().get(0).delta() instanceof EntitySpawnDelta);
        assertEquals(HyperCoreRole.FABRIC_HOST, batch.deltas().get(0).source());
        assertEquals("STONE", ((BlockDelta) batch.deltas().get(1).delta()).blockState());
        assertEquals(HyperCoreRole.FORGE_HOST, batch.deltas().get(1).source());
        assertEquals("DIRT", ((BlockDelta) batch.deltas().get(2).delta()).blockState());
        assertEquals(HyperCoreRole.FABRIC_HOST, batch.deltas().get(2).source());
    }

    @Test
    void emptyFlushIsANoOp() {
        WorldStateBridge bridge = new WorldStateBridge();
        List<WorldStateBridge.OrderedBatch> batches = new ArrayList<>();
        bridge.setOutbound(batches::add);

        bridge.flush();
        assertEquals(0, batches.size());
        assertEquals(0L, bridge.logicalTick());
    }

    @Test
    void rejectsDeltasFromNonHostSources() {
        WorldStateBridge bridge = new WorldStateBridge();
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> bridge.submit(HyperCoreRole.STANDALONE, List.of(new BlockDelta("w", 1, 2, 3, "STONE")))
        );
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> bridge.submit(HyperCoreRole.ORCHESTRATOR, List.of(new BlockDelta("w", 1, 2, 3, "STONE")))
        );
    }
}
