package dev.hypercore.bukkit;

import dev.hypercore.world.RegionExecutionService;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link HyperCorePlayer} independent of a running Minecraft
 * server. These tests verify the Bukkit-facing adapter behavior that can be
 * exercised with a no-op execution service.
 */
class HyperCorePlayerTest {

    @Test
    void displayNameDefaultsToPlayerName() {
        RegionExecutionService execution = NoOpExecutionService.create();
        Player player = new HyperCorePlayer(execution, UUID.randomUUID(), "Steve");

        assertEquals("Steve", player.getDisplayName());
    }

    @Test
    void displayNameCanBeChanged() {
        RegionExecutionService execution = NoOpExecutionService.create();
        Player player = new HyperCorePlayer(execution, UUID.randomUUID(), "Steve");

        player.setDisplayName("GameTestPlayer");

        assertEquals("GameTestPlayer", player.getDisplayName());
    }

    @Test
    void nullDisplayNameFallsBackToPlayerName() {
        RegionExecutionService execution = NoOpExecutionService.create();
        Player player = new HyperCorePlayer(execution, UUID.randomUUID(), "Steve");

        player.setDisplayName("Custom");
        player.setDisplayName(null);

        assertEquals("Steve", player.getDisplayName());
    }

    @Test
    void gameModeFallsBackToSurvivalWhenExecutionReturnsNull() {
        RegionExecutionService execution = NoOpExecutionService.create();
        Player player = new HyperCorePlayer(execution, UUID.randomUUID(), "Steve");

        assertEquals(GameMode.SURVIVAL, player.getGameMode());
    }

    @Test
    void inventoryIsNullWhenExecutionReturnsNone() {
        RegionExecutionService execution = NoOpExecutionService.create();
        Player player = new HyperCorePlayer(execution, UUID.randomUUID(), "Steve");

        assertNull(player.getInventory());
    }
}
