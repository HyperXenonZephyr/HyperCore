package dev.hypercore.world;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.Inventory;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * No-op factory used when no loader adapter has registered a real world access
 * implementation. All operations return empty defaults.
 */
public final class NoOpWorldAccessFactory implements WorldAccessFactory {

    @Override
    public WorldAccess access(String worldName) {
        return new NoOpWorldAccess(worldName);
    }

    @Override
    public Collection<String> worldNames() {
        return List.of();
    }

    private record NoOpWorldAccess(String worldName) implements WorldAccess {
        @Override
        public Material getBlockType(int x, int y, int z) {
            return Material.AIR;
        }

        @Override
        public void setBlockType(int x, int y, int z, Material type) {
            // No-op.
        }

        @Override
        public Inventory getBlockInventory(int x, int y, int z) {
            return null;
        }

        @Override
        public UUID spawnEntity(EntityType type, Position position) {
            return null;
        }

        @Override
        public Position getEntityPosition(UUID entityId) {
            return null;
        }

        @Override
        public boolean teleportEntity(UUID entityId, Position position) {
            return false;
        }

        @Override
        public Inventory getPlayerInventory(UUID playerId) {
            return null;
        }

        @Override
        public Collection<UUID> entityIds() {
            return Collections.emptyList();
        }

        @Override
        public long getTime() {
            return 0;
        }

        @Override
        public void setTime(long time) {
            // No-op.
        }

        @Override
        public String getPlayerName(UUID playerId) {
            return null;
        }

        @Override
        public boolean hasStorm() {
            return false;
        }

        @Override
        public void setStorm(boolean storm) {
            // No-op.
        }

        @Override
        public boolean isThundering() {
            return false;
        }

        @Override
        public void setThundering(boolean thundering) {
            // No-op.
        }

        @Override
        public Position getSpawnLocation() {
            return null;
        }

        @Override
        public void setSpawnLocation(Position position) {
            // No-op.
        }

        @Override
        public String getBiome(int x, int y, int z) {
            return null;
        }

        @Override
        public void setBiome(int x, int y, int z, String biomeKey) {
            // No-op.
        }

        @Override
        public String getBlockDataAsString(int x, int y, int z) {
            return null;
        }

        @Override
        public void setBlockData(int x, int y, int z, String blockData) {
            // No-op.
        }

        @Override
        public int getBlockLight(int x, int y, int z) {
            return 0;
        }

        @Override
        public int getSkyLight(int x, int y, int z) {
            return 0;
        }

        @Override
        public boolean isBlockPowered(int x, int y, int z) {
            return false;
        }

        @Override
        public boolean isBlockIndirectlyPowered(int x, int y, int z) {
            return false;
        }

        @Override
        public int getBlockPower(int x, int y, int z, String faceName) {
            return 0;
        }

        @Override
        public Collection<UUID> playerIds() {
            return Collections.emptyList();
        }

        @Override
        public EntityType getEntityType(UUID entityId) {
            return null;
        }

        @Override
        public String getEntityCustomName(UUID entityId) {
            return null;
        }

        @Override
        public boolean setEntityCustomName(UUID entityId, String name) {
            return false;
        }

        @Override
        public boolean removeEntity(UUID entityId) {
            return false;
        }

        @Override
        public org.bukkit.GameMode getPlayerGameMode(UUID playerId) {
            return null;
        }

        @Override
        public boolean setPlayerGameMode(UUID playerId, org.bukkit.GameMode gameMode) {
            return false;
        }

        @Override
        public void kickPlayer(UUID playerId, String message) {
            // No-op.
        }

        @Override
        public void sendTitle(UUID playerId, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
            // No-op.
        }

        @Override
        public void resetTitle(UUID playerId) {
            // No-op.
        }

        @Override
        public boolean performCommand(UUID playerId, String command) {
            return false;
        }

        @Override
        public void updateInventory(UUID playerId) {
            // No-op.
        }

        @Override
        public boolean openInventory(UUID playerId, Inventory inventory) {
            return false;
        }

        @Override
        public void setResourcePack(UUID playerId, String url) {
            // No-op.
        }

        @Override
        public boolean isSneaking(UUID playerId) {
            return false;
        }

        @Override
        public void setSneaking(UUID playerId, boolean sneaking) {
            // No-op.
        }

        @Override
        public boolean isSprinting(UUID playerId) {
            return false;
        }

        @Override
        public void setSprinting(UUID playerId, boolean sprinting) {
            // No-op.
        }

        @Override
        public Vector3 getEntityVelocity(UUID entityId) {
            return null;
        }

        @Override
        public boolean setEntityVelocity(UUID entityId, Vector3 velocity) {
            return false;
        }

        @Override
        public float getEntityFallDistance(UUID entityId) {
            return 0.0f;
        }

        @Override
        public boolean setEntityFallDistance(UUID entityId, float distance) {
            return false;
        }

        @Override
        public int getEntityFireTicks(UUID entityId) {
            return 0;
        }

        @Override
        public boolean setEntityFireTicks(UUID entityId, int ticks) {
            return false;
        }

        @Override
        public Collection<UUID> getEntityPassengers(UUID entityId) {
            return Collections.emptyList();
        }

        @Override
        public boolean addEntityPassenger(UUID entityId, UUID passengerId) {
            return false;
        }

        @Override
        public boolean removeEntityPassenger(UUID entityId, UUID passengerId) {
            return false;
        }

        @Override
        public boolean isEntityInsideVehicle(UUID entityId) {
            return false;
        }

        @Override
        public UUID getEntityVehicle(UUID entityId) {
            return null;
        }

        @Override
        public boolean leaveVehicle(UUID entityId) {
            return false;
        }

        @Override
        public double getEntityHealth(UUID entityId) {
            return 0.0;
        }

        @Override
        public boolean setEntityHealth(UUID entityId, double health) {
            return false;
        }

        @Override
        public double getEntityMaxHealth(UUID entityId) {
            return 0.0;
        }

        @Override
        public boolean setEntityMaxHealth(UUID entityId, double maxHealth) {
            return false;
        }

        @Override
        public void damageEntity(UUID entityId, double amount) {
            // No-op.
        }

        @Override
        public boolean isEntityAiEnabled(UUID entityId) {
            return false;
        }

        @Override
        public boolean setEntityAiEnabled(UUID entityId, boolean ai) {
            return false;
        }

        @Override
        public boolean isEntityCollidable(UUID entityId) {
            return false;
        }

        @Override
        public boolean setEntityCollidable(UUID entityId, boolean collidable) {
            return false;
        }
    }
}
