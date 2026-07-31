package org.bukkit.event;

import java.util.Objects;

/**
 * Base class for all Bukkit-style events. Subclasses must provide a static
 * {@link HandlerList} via {@link #getHandlers()} and the conventional
 * {@code getHandlerList()} accessor.
 *
 * <p>Events may be synchronous or asynchronous. Most events in this minimal
 * implementation are synchronous because HyperCore dispatches them on the
 * server tick thread.
 */
public abstract class Event {
    private final boolean async;
    private String name;

    /**
     * Creates a synchronous event.
     */
    public Event() {
        this(false);
    }

    /**
     * Creates an event with the specified sync/async flag.
     *
     * @param async true if the event may be fired asynchronously
     */
    public Event(boolean async) {
        this.async = async;
    }

    /**
     * Returns the name used when logging or describing this event.
     *
     * <p>The default implementation returns the simple class name.
     */
    public String getEventName() {
        if (name == null) {
            name = getClass().getSimpleName();
        }
        return name;
    }

    /**
     * Returns the {@link HandlerList} for this event instance. Subclasses must
     * return the same static handler list every time.
     */
    public abstract HandlerList getHandlers();

    /**
     * Returns {@code true} if this event is asynchronous.
     */
    public final boolean isAsynchronous() {
        return async;
    }

    @Override
    public String toString() {
        return Objects.requireNonNullElse(getEventName(), "Event") + " [" + getClass().getName() + "]";
    }
}
