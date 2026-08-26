package dev.hypercore.server.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EventsTest {
    @Test
    void blockEventsExposePlayerPositionAndBlock() {
        BlockBreakEvent breakEvent = new BlockBreakEvent("Steve", 1, -2, 3, "minecraft:stone");
        BlockPlaceEvent placeEvent = new BlockPlaceEvent("Alex", -4, 5, 6, "minecraft:oak_planks");

        assertEquals("Steve", breakEvent.playerName());
        assertEquals(1, breakEvent.x());
        assertEquals(-2, breakEvent.y());
        assertEquals(3, breakEvent.z());
        assertEquals("minecraft:stone", breakEvent.blockId());

        assertEquals("Alex", placeEvent.playerName());
        assertEquals(-4, placeEvent.x());
        assertEquals(5, placeEvent.y());
        assertEquals(6, placeEvent.z());
        assertEquals("minecraft:oak_planks", placeEvent.blockId());

        assertTrue(breakEvent instanceof HyperCoreEvent);
        assertTrue(placeEvent instanceof HyperCoreEvent);
    }

    @Test
    void cancellableEventsToggleIndependently() {
        BlockBreakEvent breakEvent = new BlockBreakEvent("Steve", 0, 0, 0, "minecraft:dirt");
        BlockPlaceEvent placeEvent = new BlockPlaceEvent("Alex", 0, 64, 0, "minecraft:grass_block");

        assertFalse(breakEvent.isCancelled());
        assertFalse(placeEvent.isCancelled());

        breakEvent.setCancelled(true);
        assertTrue(breakEvent.isCancelled());
        assertFalse(placeEvent.isCancelled());

        placeEvent.setCancelled(true);
        assertTrue(placeEvent.isCancelled());

        breakEvent.setCancelled(false);
        assertFalse(breakEvent.isCancelled());
        assertTrue(placeEvent.isCancelled());
    }

    @Test
    void joinAndQuitCarryOnlyPlayerName() {
        PlayerJoinEvent joinEvent = new PlayerJoinEvent("Steve");
        PlayerQuitEvent quitEvent = new PlayerQuitEvent("Alex");

        assertEquals("Steve", joinEvent.playerName());
        assertEquals("Alex", quitEvent.playerName());
        assertTrue(joinEvent instanceof HyperCoreEvent);
        assertTrue(quitEvent instanceof HyperCoreEvent);
        assertFalse(joinEvent instanceof Cancellable);
        assertFalse(quitEvent instanceof Cancellable);
    }

    @Test
    void nullPlayerNameIsRejectedEverywhere() {
        assertThrows(NullPointerException.class, () -> new BlockBreakEvent(null, 1, 2, 3, "minecraft:stone"));
        assertThrows(NullPointerException.class, () -> new BlockPlaceEvent(null, 1, 2, 3, "minecraft:stone"));
        assertThrows(NullPointerException.class, () -> new PlayerJoinEvent(null));
        assertThrows(NullPointerException.class, () -> new PlayerQuitEvent(null));
    }

    @Test
    void nullBlockIdIsRejected() {
        assertThrows(NullPointerException.class, () -> new BlockBreakEvent("Steve", 1, 2, 3, null));
        assertThrows(NullPointerException.class, () -> new BlockPlaceEvent("Steve", 1, 2, 3, null));
    }
}
