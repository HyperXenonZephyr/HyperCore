package org.bukkit.event;

/**
 * Execution priority for event handlers. Handlers run from
 * {@link #LOWEST} to {@link #MONITOR}. MONITOR handlers should observe but not
 * modify events.
 */
public enum EventPriority {
    LOWEST,
    LOW,
    NORMAL,
    HIGH,
    HIGHEST,
    MONITOR
}
