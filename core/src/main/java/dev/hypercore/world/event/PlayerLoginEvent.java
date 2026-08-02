package dev.hypercore.world.event;

import dev.hypercore.plugin.PluginEventBus;

import org.bukkit.entity.Player;

import java.util.Objects;

/**
 * Internal HyperCore event fired when a player is logging in.
 */
public final class PlayerLoginEvent implements PluginEventBus.CancellableEvent {
    private final Player player;
    private String kickMessage;
    private boolean cancelled;

    public PlayerLoginEvent(Player player) {
        this.player = Objects.requireNonNull(player, "player");
    }

    public Player getPlayer() {
        return player;
    }

    public String getKickMessage() {
        return kickMessage;
    }

    public void setKickMessage(String kickMessage) {
        this.kickMessage = kickMessage;
    }

    @Override
    public boolean cancelled() {
        return cancelled;
    }

    @Override
    public void cancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
