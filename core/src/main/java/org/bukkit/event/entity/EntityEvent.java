package org.bukkit.event.entity;

import org.bukkit.entity.Entity;
import org.bukkit.event.Event;

import java.util.Objects;

/**
 * Base class for entity-related events.
 */
public abstract class EntityEvent extends Event {
    private final Entity entity;

    protected EntityEvent() {
        this(null);
    }

    protected EntityEvent(Entity entity) {
        this.entity = entity;
    }

    /**
     * Returns the entity involved in this event.
     */
    public Entity getEntity() {
        return entity;
    }
}
