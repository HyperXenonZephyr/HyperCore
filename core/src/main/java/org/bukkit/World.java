package org.bukkit;

import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import java.util.Collection;
import java.util.List;

/**
 * Stub of the Bukkit {@code World} interface.
 *
 * <p>Exposes the most common world-query and world-mutation entry points used by
 * Bukkit plugins. Methods that cannot be satisfied by the current HyperCore
 * runtime throw {@link UnsupportedOperationException}.
 */
public interface World {

    /**
     * World environment types matching vanilla dimensions.
     */
    enum Environment {
        NORMAL,
        NETHER,
        THE_END
    }

    /**
     * Returns the name of this world.
     */
    String getName();

    /**
     * Returns the block at the given coordinates.
     */
    Block getBlockAt(int x, int y, int z);

    /**
     * Returns the block at the given location.
     */
    default Block getBlockAt(Location location) {
        if (location == null || !equals(location.getWorld())) {
            throw new IllegalArgumentException("Location does not belong to this world");
        }
        return getBlockAt(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    /**
     * Returns the highest non-air block at the given x/z column.
     */
    default Block getHighestBlockAt(int x, int z) {
        for (int y = 319; y >= -64; y--) {
            Block block = getBlockAt(x, y, z);
            if (block.getType() != Material.AIR) {
                return block;
            }
        }
        return getBlockAt(x, -64, z);
    }

    /**
     * Returns the current time of day in this world.
     *
     * <p>Time wraps between 0 and 24000.
     */
    default long getTime() {
        throw new UnsupportedOperationException("getTime");
    }

    /**
     * Sets the current time of day in this world.
     *
     * @param time the time, wrapped between 0 and 24000
     */
    default void setTime(long time) {
        throw new UnsupportedOperationException("setTime");
    }

    /**
     * Returns the absolute world age in ticks.
     */
    default long getFullTime() {
        throw new UnsupportedOperationException("getFullTime");
    }

    /**
     * Sets the absolute world age in ticks.
     */
    default void setFullTime(long time) {
        throw new UnsupportedOperationException("setFullTime");
    }

    /**
     * Returns whether it is currently raining in this world.
     */
    default boolean hasStorm() {
        throw new UnsupportedOperationException("hasStorm");
    }

    /**
     * Sets whether it is raining in this world.
     */
    default void setStorm(boolean hasStorm) {
        throw new UnsupportedOperationException("setStorm");
    }

    /**
     * Returns whether it is currently thundering in this world.
     */
    default boolean isThundering() {
        throw new UnsupportedOperationException("isThundering");
    }

    /**
     * Sets whether it is thundering in this world.
     */
    default void setThundering(boolean thundering) {
        throw new UnsupportedOperationException("setThundering");
    }

    /**
     * Returns the spawn location of this world.
     */
    default Location getSpawnLocation() {
        throw new UnsupportedOperationException("getSpawnLocation");
    }

    /**
     * Sets the spawn location of this world.
     */
    default void setSpawnLocation(Location location) {
        throw new UnsupportedOperationException("setSpawnLocation");
    }

    /**
     * Returns the biome at the given location.
     */
    default Biome getBiome(Location location) {
        if (location == null || !equals(location.getWorld())) {
            throw new IllegalArgumentException("Location does not belong to this world");
        }
        return getBiome(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    /**
     * Returns the biome at the given block coordinates.
     */
    default Biome getBiome(int x, int y, int z) {
        throw new UnsupportedOperationException("getBiome");
    }

    /**
     * Sets the biome at the given location.
     */
    default void setBiome(Location location, Biome biome) {
        if (location == null || !equals(location.getWorld())) {
            throw new IllegalArgumentException("Location does not belong to this world");
        }
        setBiome(location.getBlockX(), location.getBlockY(), location.getBlockZ(), biome);
    }

    /**
     * Sets the biome at the given block coordinates.
     */
    default void setBiome(int x, int y, int z, Biome biome) {
        throw new UnsupportedOperationException("setBiome");
    }

    /**
     * Returns the entities in this world.
     */
    List<Entity> getEntities();

    /**
     * Returns all entities in this world that are instances of the given class.
     */
    default <T extends Entity> Collection<T> getEntitiesByClass(Class<T> clazz) {
        throw new UnsupportedOperationException("getEntitiesByClass");
    }

    /**
     * Returns all entities within the given bounding box centered on the location.
     */
    default Collection<Entity> getNearbyEntities(Location location, double x, double y, double z) {
        throw new UnsupportedOperationException("getNearbyEntities");
    }

    /**
     * Spawns an entity of the given type at the given location.
     *
     * @return the spawned entity, or {@code null} on failure
     */
    Entity spawnEntity(Location location, EntityType type);
}
