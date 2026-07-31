package org.bukkit.event;

/**
 * Marker interface implemented by plugin classes that contain
 * {@link EventHandler}-annotated methods. The plugin manager uses reflection to
 * discover those methods when {@code registerEvents(listener, plugin)} is
 * called.
 */
public interface Listener {
}
