package org.bukkit.event.weather;

import org.bukkit.World;
import org.bukkit.event.Event;

import java.util.Objects;

/**
 * Base class for weather-related events.
 */
public abstract class WeatherEvent extends Event {
    private final World world;

    protected WeatherEvent() {
        this(null);
    }

    protected WeatherEvent(World world) {
        this.world = world;
    }

    /**
     * Returns the world involved in this event.
     */
    public final World getWorld() {
        return world;
    }
}
