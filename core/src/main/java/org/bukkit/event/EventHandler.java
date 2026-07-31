package org.bukkit.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method inside a {@link Listener} class as an event handler. The
 * method must be public, non-static, and declare exactly one parameter whose
 * type is a subclass of {@link Event}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EventHandler {

    /**
     * The priority at which this handler is invoked. Higher-priority handlers
     * run later and see the cancellation state set by lower-priority handlers.
     */
    EventPriority priority() default EventPriority.NORMAL;

    /**
     * If {@code true}, this handler is skipped when the event has already been
     * cancelled.
     */
    boolean ignoreCancelled() default false;
}
