package dev.hypercore.world.event;

import dev.hypercore.plugin.PluginEventBus;

import org.bukkit.entity.Player;

import java.util.Objects;

/**
 * Internal HyperCore event fired when a player leaves the server.
 */
public final class PlayerQuitEvent implements PluginEventBus.PluginEvent {
    private final Player player;
    private String quitMessage;

    public PlayerQuitEvent(Player player, String quitMessage) {
        this.player = Objects.requireNonNull(player, "player");
        this.quitMessage = quitMessage;
    }

    public Player getPlayer() {
        return player;
    }

    public String getQuitMessage() {
        return quitMessage;
    }

    public void setQuitMessage(String quitMessage) {
        this.quitMessage = quitMessage;
    }
}
