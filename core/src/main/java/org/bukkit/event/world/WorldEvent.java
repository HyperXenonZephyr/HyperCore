package org.bukkit.event.world;

import org.bukkit.World;
import org.bukkit.event.Event;

import java.util.Objects;

/**
 * Base class for world-related events.
 */
public abstract class WorldEvent extends Event {
    private final World world;

    protected WorldEvent() {
        this(null);
    }

    protected WorldEvent(World world) {
        this.world = world;
    }

    /**
     * Returns the world involved in this event.
     */
    public final World getWorld() {
        return world;
    }
}
