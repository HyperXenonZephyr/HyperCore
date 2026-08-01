package dev.hypercore.world.event;

import dev.hypercore.plugin.PluginEventBus;

import org.bukkit.entity.Player;

import java.util.Objects;

/**
 * Internal event fired when a player joins the server.
 *
 * <p>This is bridged to {@link org.bukkit.event.player.PlayerJoinEvent} so that
 * Bukkit plugins can display join messages and initialize per-player state.
 */
public final class PlayerJoinEvent implements PluginEventBus.PluginEvent {
    private final Player player;
    private String joinMessage;

    public PlayerJoinEvent(Player player, String joinMessage) {
        this.player = Objects.requireNonNull(player, "player");
        this.joinMessage = joinMessage;
    }

    /**
     * Returns the player that joined.
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * Returns the join message that will be broadcast, or {@code null}.
     */
    public String getJoinMessage() {
        return joinMessage;
    }

    /**
     * Sets the join message that will be broadcast.
     */
    public void setJoinMessage(String joinMessage) {
        this.joinMessage = joinMessage;
    }
}
