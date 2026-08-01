package org.bukkit;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import java.util.List;

/**
 * Minimal stub of the Bukkit {@code World} interface.
 *
 * <p>This version exposes the most common world-mutation entry points used by
 * Bukkit plugins. Methods that cannot be satisfied by the current HyperCore
 * runtime throw {@link UnsupportedOperationException}.
 */
public interface World {

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
     * Returns the entities in this world.
     */
    List<Entity> getEntities();

    /**
     * Spawns an entity of the given type at the given location.
     *
     * @return the spawned entity, or {@code null} on failure
     */
    Entity spawnEntity(Location location, EntityType type);
}
