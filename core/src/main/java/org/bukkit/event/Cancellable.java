package org.bukkit.event;

/**
 * Marker interface for events that can be cancelled. A cancelled event stops
 * the default action from taking place after the event has been dispatched to
 * all listeners.
 */
public interface Cancellable {

    /**
     * Returns {@code true} if this event has been cancelled.
     */
    boolean isCancelled();

    /**
     * Sets the cancelled state of this event.
     */
    void setCancelled(boolean cancelled);
}
