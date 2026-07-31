package org.bukkit.event.hanging;

import org.bukkit.entity.Hanging;
import org.bukkit.event.entity.EntityEvent;

/**
 * Base class for hanging-entity-related events.
 */
public abstract class HangingEvent extends EntityEvent {

    protected HangingEvent() {
        this(null);
    }

    protected HangingEvent(Hanging hanging) {
        super(hanging);
    }

    /**
     * Returns the hanging entity involved in this event.
     */
    public Hanging getEntity() {
        return (Hanging) super.getEntity();
    }
}
