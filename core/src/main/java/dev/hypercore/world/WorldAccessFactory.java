package dev.hypercore.world;

import java.util.Collection;

/**
 * Creates {@link WorldAccess} handles for loaded worlds.
 *
 * <p>The factory is registered with {@link dev.hypercore.runtime.HyperCoreRuntime}
 * by the loader adapter ({@code :forge} or {@code :fabric}) after the Minecraft
 * server instance is available. If no factory is registered, HyperCore falls back
 * to a no-op factory that returns empty worlds.
 */
public interface WorldAccessFactory {

    /**
     * Returns a handle for the world with the given name, or {@code null} if
     * the world is not loaded.
     */
    WorldAccess access(String worldName);

    /**
     * Returns the names of all currently loaded worlds.
     */
    Collection<String> worldNames();
}
