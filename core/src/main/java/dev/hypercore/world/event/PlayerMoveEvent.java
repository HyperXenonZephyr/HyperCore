package dev.hypercore.world.event;

import dev.hypercore.plugin.PluginEventBus;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Objects;

/**
 * Internal event fired before a player is teleported within the same region.
 *
 * <p>If cancelled, the teleport is aborted and Bukkit plugins observe the same
 * cancellation through {@link org.bukkit.event.player.PlayerMoveEvent}.
 */
public final class PlayerMoveEvent implements PluginEventBus.CancellableEvent {
    private final Player player;
    private final Location from;
    private final Location to;
    private boolean cancelled;

    public PlayerMoveEvent(Player player, Location from, Location to) {
        this.player = Objects.requireNonNull(player, "player");
        this.from = Objects.requireNonNull(from, "from");
        this.to = Objects.requireNonNull(to, "to");
    }

    /**
     * Returns the player that is moving.
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * Returns the player's current location.
     */
    public Location getFrom() {
        return from;
    }

    /**
     * Returns the destination location.
     */
    public Location getTo() {
        return to;
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
