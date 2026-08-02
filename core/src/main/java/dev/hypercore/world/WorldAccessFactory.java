package dev.hypercore.world;

import dev.hypercore.region.RegionKey;

import java.util.Collection;
import java.util.List;

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
     * Creates or loads a world from the given creator configuration.
     *
     * @return the name of the created/loaded world, or {@code null} on failure
     */
    default String createWorld(org.bukkit.WorldCreator creator) {
        return null;
    }

    /**
     * Returns the names of all currently loaded worlds.
     */
    Collection<String> worldNames();

    /**
     * Returns the region keys that are currently loaded. The default
     * implementation returns an empty list; loader-specific factories can
     * override it to report loaded chunks.
     */
    default Collection<RegionKey> loadedRegions(int regionSizeChunks) {
        return List.of();
    }
}
