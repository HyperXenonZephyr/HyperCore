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
     * Immutable 3D vector used for entity velocity and similar quantities.
     */
    record Vector3(double x, double y, double z) {
    }

    /**
     * Returns the name of the world this handle accesses.
     */
    String worldName();

    /**
     * Returns the current time of day in this world.
     */
    default long getTime() {
        throw new UnsupportedOperationException("getTime");
    }

    /**
     * Sets the current time of day in this world.
     */
    default void setTime(long time) {
        throw new UnsupportedOperationException("setTime");
    }

    /**
     * Returns the absolute age of this world in ticks.
     *
     * <p>The default implementation falls back to the time of day so existing
     * implementations keep compiling; loader adapters should override this with
     * the true world age.
     */
    default long getFullTime() {
        return getTime();
    }

    /**
     * Sets the absolute age of this world in ticks.
     *
     * <p>The default implementation falls back to the time of day so existing
     * implementations keep compiling; loader adapters should override this with
     * the true world age.
     */
    default void setFullTime(long time) {
        setTime(time);
    }

    /**
     * Returns the display name of the player with the given unique id,
     * or {@code null} if the player is not present or the name is unknown.
     */
    default String getPlayerName(UUID playerId) {
        throw new UnsupportedOperationException("getPlayerName");
    }

    /**
     * Returns whether it is currently raining in this world.
     */
    default boolean hasStorm() {
        throw new UnsupportedOperationException("hasStorm");
    }

    /**
     * Sets whether it is currently raining in this world.
     */
    default void setStorm(boolean storm) {
        throw new UnsupportedOperationException("setStorm");
    }

    /**
     * Returns whether it is currently thundering in this world.
     */
    default boolean isThundering() {
        throw new UnsupportedOperationException("isThundering");
    }

    /**
     * Sets whether it is currently thundering in this world.
     */
    default void setThundering(boolean thundering) {
        throw new UnsupportedOperationException("setThundering");
    }

    /**
     * Returns the spawn position of this world.
     */
    default Position getSpawnLocation() {
        throw new UnsupportedOperationException("getSpawnLocation");
    }

    /**
     * Sets the spawn position of this world.
     */
    default void setSpawnLocation(Position position) {
        throw new UnsupportedOperationException("setSpawnLocation");
    }

    /**
     * Returns the resource-key string of the biome at the given block coordinates.
     */
    default String getBiome(int x, int y, int z) {
        throw new UnsupportedOperationException("getBiome");
    }

    /**
     * Sets the biome at the given block coordinates from a resource-key string.
     */
    default void setBiome(int x, int y, int z, String biomeKey) {
        throw new UnsupportedOperationException("setBiome");
    }

    /**
     * Returns the block state as a string at the given block coordinates,
     * or {@code null} if it cannot be represented.
     */
    default String getBlockDataAsString(int x, int y, int z) {
        throw new UnsupportedOperationException("getBlockDataAsString");
    }

    /**
     * Sets the block state from a string at the given block coordinates.
     */
    default void setBlockData(int x, int y, int z, String blockData) {
        throw new UnsupportedOperationException("setBlockData");
    }

    /**
     * Returns the block light level at the given block coordinates.
     */
    default int getBlockLight(int x, int y, int z) {
        throw new UnsupportedOperationException("getBlockLight");
    }

    /**
     * Returns the sky light level at the given block coordinates.
     */
    default int getSkyLight(int x, int y, int z) {
        throw new UnsupportedOperationException("getSkyLight");
    }

    /**
     * Returns whether the block at the given coordinates is directly powered.
     */
    default boolean isBlockPowered(int x, int y, int z) {
        throw new UnsupportedOperationException("isBlockPowered");
    }

    /**
     * Returns whether the block at the given coordinates is indirectly powered.
     */
    default boolean isBlockIndirectlyPowered(int x, int y, int z) {
        throw new UnsupportedOperationException("isBlockIndirectlyPowered");
    }

    /**
     * Returns the redstone signal strength from the given face at the given
     * block coordinates.
     */
    default int getBlockPower(int x, int y, int z, String faceName) {
        throw new UnsupportedOperationException("getBlockPower");
    }

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
        throw new UnsupportedOperationException("playerIds");
    }

    /**
     * Returns the Bukkit entity type of the entity with the given unique id,
     * or {@code null} if it is not present.
     */
    default EntityType getEntityType(UUID entityId) {
        throw new UnsupportedOperationException("getEntityType");
    }

    /**
     * Returns the custom name of the entity with the given unique id,
     * or {@code null} if none or the entity is not present.
     */
    default String getEntityCustomName(UUID entityId) {
        throw new UnsupportedOperationException("getEntityCustomName");
    }

    /**
     * Sets the custom name of the entity with the given unique id.
     *
     * @return {@code true} if the entity was found and updated
     */
    default boolean setEntityCustomName(UUID entityId, String name) {
        throw new UnsupportedOperationException("setEntityCustomName");
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
        throw new UnsupportedOperationException("removeEntity");
    }

    /**
     * Returns the game mode of the player with the given unique id,
     * or {@code null} if the player is not present.
     */
    default org.bukkit.GameMode getPlayerGameMode(UUID playerId) {
        throw new UnsupportedOperationException("getPlayerGameMode");
    }

    /**
     * Sets the game mode of the player with the given unique id.
     *
     * @return {@code true} if the player was found and updated
     */
    default boolean setPlayerGameMode(UUID playerId, org.bukkit.GameMode gameMode) {
        throw new UnsupportedOperationException("setPlayerGameMode");
    }

    /**
     * Disconnects the player with the given unique id from the server.
     */
    default void kickPlayer(UUID playerId, String message) {
        throw new UnsupportedOperationException("kickPlayer");
    }

    /**
     * Sends a title to the player with the given unique id.
     */
    default void sendTitle(UUID playerId, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        throw new UnsupportedOperationException("sendTitle");
    }

    /**
     * Resets the title currently displayed to the player with the given unique id.
     */
    default void resetTitle(UUID playerId) {
        throw new UnsupportedOperationException("resetTitle");
    }

    /**
     * Executes a command as the player with the given unique id.
     *
     * @return {@code true} if the command was found and executed
     */
    default boolean performCommand(UUID playerId, String command) {
        throw new UnsupportedOperationException("performCommand");
    }

    /**
     * Sends the current inventory contents to the player with the given unique id.
     */
    default void updateInventory(UUID playerId) {
        throw new UnsupportedOperationException("updateInventory");
    }

    /**
     * Opens the given inventory for the player with the given unique id.
     *
     * @return {@code true} if the inventory was opened
     */
    default boolean openInventory(UUID playerId, org.bukkit.inventory.Inventory inventory) {
        throw new UnsupportedOperationException("openInventory");
    }

    /**
     * Sets the resource pack URL for the player with the given unique id.
     */
    default void setResourcePack(UUID playerId, String url) {
        throw new UnsupportedOperationException("setResourcePack");
    }

    /**
     * Returns whether the player with the given unique id is sneaking.
     */
    default boolean isSneaking(UUID playerId) {
        throw new UnsupportedOperationException("isSneaking");
    }

    /**
     * Sets whether the player with the given unique id is sneaking.
     */
    default void setSneaking(UUID playerId, boolean sneaking) {
        throw new UnsupportedOperationException("setSneaking");
    }

    /**
     * Returns whether the player with the given unique id is sprinting.
     */
    default boolean isSprinting(UUID playerId) {
        throw new UnsupportedOperationException("isSprinting");
    }

    /**
     * Sets whether the player with the given unique id is sprinting.
     */
    default void setSprinting(UUID playerId, boolean sprinting) {
        throw new UnsupportedOperationException("setSprinting");
    }

    /**
     * Returns the armor contents of the player with the given unique id.
     *
     * <p>The returned array has four elements in the order boots, leggings,
     * chestplate, helmet.
     */
    default org.bukkit.inventory.ItemStack[] getPlayerArmor(UUID playerId) {
        org.bukkit.inventory.Inventory inventory = getPlayerInventory(playerId);
        if (!(inventory instanceof org.bukkit.inventory.PlayerInventory playerInventory)) {
            return new org.bukkit.inventory.ItemStack[4];
        }
        return playerInventory.getArmorContents();
    }

    /**
     * Sets the armor contents of the player with the given unique id.
     */
    default void setPlayerArmor(UUID playerId, org.bukkit.inventory.ItemStack[] armor) {
        org.bukkit.inventory.Inventory inventory = getPlayerInventory(playerId);
        if (inventory instanceof org.bukkit.inventory.PlayerInventory playerInventory) {
            playerInventory.setArmorContents(armor);
        }
    }

    /**
     * Returns the off-hand item of the player with the given unique id.
     */
    default org.bukkit.inventory.ItemStack getPlayerOffHand(UUID playerId) {
        org.bukkit.inventory.Inventory inventory = getPlayerInventory(playerId);
        if (!(inventory instanceof org.bukkit.inventory.PlayerInventory playerInventory)) {
            return null;
        }
        return playerInventory.getItemInOffHand();
    }

    /**
     * Sets the off-hand item of the player with the given unique id.
     */
    default void setPlayerOffHand(UUID playerId, org.bukkit.inventory.ItemStack item) {
        org.bukkit.inventory.Inventory inventory = getPlayerInventory(playerId);
        if (inventory instanceof org.bukkit.inventory.PlayerInventory playerInventory) {
            playerInventory.setItemInOffHand(item);
        }
    }

    /**
     * Returns the currently selected hotbar slot of the player with the given
     * unique id.
     */
    default int getPlayerHeldSlot(UUID playerId) {
        org.bukkit.inventory.Inventory inventory = getPlayerInventory(playerId);
        if (!(inventory instanceof org.bukkit.inventory.PlayerInventory playerInventory)) {
            return 0;
        }
        return playerInventory.getHeldItemSlot();
    }

    /**
     * Sets the currently selected hotbar slot of the player with the given
     * unique id.
     */
    default void setPlayerHeldSlot(UUID playerId, int slot) {
        org.bukkit.inventory.Inventory inventory = getPlayerInventory(playerId);
        if (inventory instanceof org.bukkit.inventory.PlayerInventory playerInventory) {
            playerInventory.setHeldItemSlot(slot);
        }
    }

    /**
     * Returns the velocity of the entity with the given unique id,
     * or {@code null} if the entity is not present.
     */
    default Vector3 getEntityVelocity(UUID entityId) {
        throw new UnsupportedOperationException("getEntityVelocity");
    }

    /**
     * Sets the velocity of the entity with the given unique id.
     *
     * @return {@code true} if the entity was found and updated
     */
    default boolean setEntityVelocity(UUID entityId, Vector3 velocity) {
        throw new UnsupportedOperationException("setEntityVelocity");
    }

    /**
     * Returns the distance the entity with the given unique id has fallen,
     * or {@code 0.0f} if the entity is not present.
     */
    default float getEntityFallDistance(UUID entityId) {
        throw new UnsupportedOperationException("getEntityFallDistance");
    }

    /**
     * Sets the distance the entity with the given unique id has fallen.
     *
     * @return {@code true} if the entity was found and updated
     */
    default boolean setEntityFallDistance(UUID entityId, float distance) {
        throw new UnsupportedOperationException("setEntityFallDistance");
    }

    /**
     * Returns the number of ticks the entity with the given unique id is on fire,
     * or {@code 0} if the entity is not present.
     */
    default int getEntityFireTicks(UUID entityId) {
        throw new UnsupportedOperationException("getEntityFireTicks");
    }

    /**
     * Sets the number of ticks the entity with the given unique id is on fire.
     *
     * @return {@code true} if the entity was found and updated
     */
    default boolean setEntityFireTicks(UUID entityId, int ticks) {
        throw new UnsupportedOperationException("setEntityFireTicks");
    }

    /**
     * Returns the unique ids of the passengers riding the entity with the given
     * unique id.
     */
    default Collection<UUID> getEntityPassengers(UUID entityId) {
        throw new UnsupportedOperationException("getEntityPassengers");
    }

    /**
     * Adds a passenger to the entity with the given unique id.
     *
     * @return {@code true} if both entities were found and the passenger was added
     */
    default boolean addEntityPassenger(UUID entityId, UUID passengerId) {
        throw new UnsupportedOperationException("addEntityPassenger");
    }

    /**
     * Removes a passenger from the entity with the given unique id.
     *
     * @return {@code true} if both entities were found and the passenger was removed
     */
    default boolean removeEntityPassenger(UUID entityId, UUID passengerId) {
        throw new UnsupportedOperationException("removeEntityPassenger");
    }

    /**
     * Returns whether the entity with the given unique id is inside a vehicle.
     */
    default boolean isEntityInsideVehicle(UUID entityId) {
        throw new UnsupportedOperationException("isEntityInsideVehicle");
    }

    /**
     * Returns the unique id of the vehicle the entity with the given unique id
     * is riding, or {@code null} if none.
     */
    default UUID getEntityVehicle(UUID entityId) {
        throw new UnsupportedOperationException("getEntityVehicle");
    }

    /**
     * Makes the entity with the given unique id leave its vehicle.
     *
     * @return {@code true} if the entity was found and was inside a vehicle
     */
    default boolean leaveVehicle(UUID entityId) {
        throw new UnsupportedOperationException("leaveVehicle");
    }

    /**
     * Returns the health of the living entity with the given unique id,
     * or {@code 0.0} if the entity is not present or not alive.
     */
    default double getEntityHealth(UUID entityId) {
        throw new UnsupportedOperationException("getEntityHealth");
    }

    /**
     * Sets the health of the living entity with the given unique id.
     *
     * @return {@code true} if the entity was found and updated
     */
    default boolean setEntityHealth(UUID entityId, double health) {
        throw new UnsupportedOperationException("setEntityHealth");
    }

    /**
     * Returns the maximum health of the living entity with the given unique id,
     * or {@code 0.0} if the entity is not present or not alive.
     */
    default double getEntityMaxHealth(UUID entityId) {
        throw new UnsupportedOperationException("getEntityMaxHealth");
    }

    /**
     * Sets the maximum health of the living entity with the given unique id.
     *
     * @return {@code true} if the entity was found and updated
     */
    default boolean setEntityMaxHealth(UUID entityId, double maxHealth) {
        throw new UnsupportedOperationException("setEntityMaxHealth");
    }

    /**
     * Deals the given amount of damage to the living entity with the given
     * unique id. Non-living entities are ignored.
     */
    default void damageEntity(UUID entityId, double amount) {
        throw new UnsupportedOperationException("damageEntity");
    }

    /**
     * Returns whether the living entity with the given unique id has AI,
     * or {@code true} if the entity is not present or not alive.
     */
    default boolean isEntityAiEnabled(UUID entityId) {
        throw new UnsupportedOperationException("isEntityAiEnabled");
    }

    /**
     * Sets whether the living entity with the given unique id has AI.
     *
     * @return {@code true} if the entity was found and updated
     */
    default boolean setEntityAiEnabled(UUID entityId, boolean ai) {
        throw new UnsupportedOperationException("setEntityAiEnabled");
    }

    /**
     * Returns whether the living entity with the given unique id is collidable,
     * or {@code true} if the entity is not present or not alive.
     */
    default boolean isEntityCollidable(UUID entityId) {
        throw new UnsupportedOperationException("isEntityCollidable");
    }

    /**
     * Sets whether the living entity with the given unique id is collidable.
     *
     * @return {@code true} if the entity was found and updated
     */
    default boolean setEntityCollidable(UUID entityId, boolean collidable) {
        throw new UnsupportedOperationException("setEntityCollidable");
    }

    /**
     * Flushes any pending world mutations queued by worker threads. Called on
     * the server thread after region ticks complete. The default implementation
     * is a no-op because the base contract applies mutations synchronously.
     */
    default void flushPendingMutations() {
    }
}
