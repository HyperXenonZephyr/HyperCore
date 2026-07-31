package org.bukkit.plugin;

/**
 * Wraps an exception thrown by an {@link EventExecutor} or by a reflected
 * listener method invocation.
 */
public class EventException extends Exception {

    private final Throwable cause;

    public EventException(Throwable cause) {
        super(cause);
        this.cause = cause;
    }

    public EventException(String message, Throwable cause) {
        super(message, cause);
        this.cause = cause;
    }

    @Override
    public Throwable getCause() {
        return cause;
    }
}
