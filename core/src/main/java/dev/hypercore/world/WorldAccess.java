package dev.hypercore.world;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.Inventory;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Loader-agnostic access to a single Minecraft world.
 *
 * <p>Implementations live in the loader-specific adapter projects ({@code :forge},
 * {@code :fabric}) and delegate to the running Minecraft server. The contract is
 * intentionally narrow and uses raw coordinates instead of Bukkit {@link
 * org.bukkit.Location} so that the Bukkit adapter in {@code :core} can attach
 * the correct {@link org.bukkit.World} view to results.
 */
public interface WorldAccess {

    /**
     * Immutable 3D position in a world.
     */
    record Position(double x, double y, double z) {
    }

    /**
     * Returns the name of the world this handle accesses.
     */
    String worldName();

    /**
     * Returns the material of the block at the given block coordinates.
     */
    Material getBlockType(int x, int y, int z);

    /**
     * Sets the material of the block at the given block coordinates.
     */
    void setBlockType(int x, int y, int z, Material type);

    /**
     * Returns the inventory of the block entity at the given block coordinates,
     * or {@code null} if there is no inventory there.
     */
    Inventory getBlockInventory(int x, int y, int z);

    /**
     * Spawns an entity of the given type at the given position.
     *
     * @return the unique id of the spawned entity, or {@code null} on failure
     */
    UUID spawnEntity(EntityType type, Position position);

    /**
     * Returns the current position of the entity with the given unique id.
     */
    Position getEntityPosition(UUID entityId);

    /**
     * Teleports the entity to the given position.
     *
     * @return {@code true} if the entity was found and teleported
     */
    boolean teleportEntity(UUID entityId, Position position);

    /**
     * Returns the inventory of the player with the given unique id.
     */
    Inventory getPlayerInventory(UUID playerId);

    /**
     * Returns the unique ids of all entities currently tracked in this world.
     */
    Collection<UUID> entityIds();

    /**
     * Returns the unique ids of all players currently in this world.
     */
    default Collection<UUID> playerIds() {
        return List.of();
    }

    /**
     * Returns the Bukkit entity type of the entity with the given unique id,
     * or {@code null} if it is not present.
     */
    default EntityType getEntityType(UUID entityId) {
        return null;
    }

    /**
     * Returns the custom name of the entity with the given unique id,
     * or {@code null} if none or the entity is not present.
     */
    default String getEntityCustomName(UUID entityId) {
        return null;
    }

    /**
     * Sets the custom name of the entity with the given unique id.
     *
     * @return {@code true} if the entity was found and updated
     */
    default boolean setEntityCustomName(UUID entityId, String name) {
        return false;
    }

    /**
     * Returns whether the entity with the given unique id is alive and loaded.
     */
    default boolean isEntityAlive(UUID entityId) {
        return getEntityPosition(entityId) != null;
    }

    /**
     * Removes the entity with the given unique id from the world.
     *
     * @return {@code true} if the entity was found and removed
     */
    default boolean removeEntity(UUID entityId) {
        return false;
    }

    /**
     * Returns the game mode of the player with the given unique id,
     * or {@code null} if the player is not present.
     */
    default org.bukkit.GameMode getPlayerGameMode(UUID playerId) {
        return null;
    }

    /**
     * Sets the game mode of the player with the given unique id.
     *
     * @return {@code true} if the player was found and updated
     */
    default boolean setPlayerGameMode(UUID playerId, org.bukkit.GameMode gameMode) {
        return false;
    }
}
