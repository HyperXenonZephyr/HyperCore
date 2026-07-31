package org.bukkit.event.player;

import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityEvent;

/**
 * Base class for player-related events.
 */
public abstract class PlayerEvent extends EntityEvent {

    protected PlayerEvent() {
        this(null);
    }

    protected PlayerEvent(Player player) {
        super(player);
    }

    /**
     * Returns the player involved in this event.
     */
    public Player getPlayer() {
        return (Player) super.getEntity();
    }
}
