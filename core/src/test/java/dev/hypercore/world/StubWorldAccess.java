package dev.hypercore.world;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Configurable {@link WorldAccess} implementation for unit tests.
 *
 * <p>Each public field is a knob that tests can set before invoking the
 * adapter under test. Mutator methods (setTime, setStorm, etc.) record their
 * arguments so tests can assert that the adapter delegated correctly.
 */
public final class StubWorldAccess implements WorldAccess {

    private final String worldName;

    // --- Configurable return values ---
    public long time = 0L;
    public long fullTime = 0L;
    public boolean storm = false;
    public boolean thundering = false;
    public Position spawnLocation = null;
    public String biomeKey = null;
    public Material blockType = Material.AIR;
    public String blockDataString = null;
    public int blockLight = 0;
    public int skyLight = 0;
    public boolean blockPowered = false;
    public boolean blockIndirectlyPowered = false;
    public int blockPower = 0;
    public String playerName = null;
    public Collection<UUID> entityIds = List.of();
    public Collection<UUID> playerIds = List.of();
    public Map<UUID, Position> entityPositions = new HashMap<>();
    public Map<UUID, EntityType> entityTypes = new HashMap<>();
    public Map<UUID, String> entityCustomNames = new HashMap<>();
    public Map<UUID, Vector3> entityVelocities = new HashMap<>();
    public Map<UUID, Float> entityFallDistances = new HashMap<>();
    public Map<UUID, Integer> entityFireTicks = new HashMap<>();
    public Map<UUID, Collection<UUID>> entityPassengers = new HashMap<>();
    public Map<UUID, UUID> entityVehicles = new HashMap<>();
    public Map<UUID, Double> entityHealth = new HashMap<>();
    public Map<UUID, Double> entityMaxHealth = new HashMap<>();
    public Map<UUID, Boolean> entityAi = new HashMap<>();
    public Map<UUID, Boolean> entityCollidable = new HashMap<>();
    public org.bukkit.GameMode playerGameMode = null;

    // --- Recorded mutations ---
    public Long recordedTime = null;
    public Long recordedFullTime = null;
    public Boolean recordedStorm = null;
    public Boolean recordedThundering = null;
    public Position recordedSpawnLocation = null;
    public String recordedBiomeKey = null;
    public Material recordedBlockType = null;
    public String recordedBlockData = null;
    public Vector3 recordedVelocity = null;
    public Float recordedFallDistance = null;
    public Integer recordedFireTicks = null;
    public UUID recordedPassengerVehicle = null;
    public UUID recordedPassengerRemoved = null;
    public Double recordedHealth = null;
    public Double recordedMaxHealth = null;
    public Double recordedDamage = null;
    public Boolean recordedAi = null;
    public Boolean recordedCollidable = null;
    public String recordedCustomName = null;
    public UUID removedEntity = null;

    public StubWorldAccess(String worldName) {
        this.worldName = worldName;
    }

    @Override
    public String worldName() {
        return worldName;
    }

    @Override
    public long getTime() {
        return time;
    }

    @Override
    public void setTime(long time) {
        this.time = time;
        this.recordedTime = time;
    }

    @Override
    public long getFullTime() {
        return fullTime;
    }

    @Override
    public void setFullTime(long time) {
        this.fullTime = time;
        this.recordedFullTime = time;
    }

    @Override
    public boolean hasStorm() {
        return storm;
    }

    @Override
    public void setStorm(boolean storm) {
        this.storm = storm;
        this.recordedStorm = storm;
    }

    @Override
    public boolean isThundering() {
        return thundering;
    }

    @Override
    public void setThundering(boolean thundering) {
        this.thundering = thundering;
        this.recordedThundering = thundering;
    }

    @Override
    public Position getSpawnLocation() {
        return spawnLocation;
    }

    @Override
    public void setSpawnLocation(Position position) {
        this.spawnLocation = position;
        this.recordedSpawnLocation = position;
    }

    @Override
    public String getBiome(int x, int y, int z) {
        return biomeKey;
    }

    @Override
    public void setBiome(int x, int y, int z, String biomeKey) {
        this.biomeKey = biomeKey;
        this.recordedBiomeKey = biomeKey;
    }

    @Override
    public Material getBlockType(int x, int y, int z) {
        return blockType;
    }

    @Override
    public void setBlockType(int x, int y, int z, Material type) {
        this.blockType = type;
        this.recordedBlockType = type;
    }

    @Override
    public Inventory getBlockInventory(int x, int y, int z) {
        return null;
    }

    @Override
    public UUID spawnEntity(EntityType type, Position position) {
        UUID id = UUID.randomUUID();
        entityIds = new ArrayList<>(entityIds);
        ((List<UUID>) entityIds).add(id);
        entityPositions.put(id, position);
        entityTypes.put(id, type);
        return id;
    }

    @Override
    public Position getEntityPosition(UUID entityId) {
        return entityPositions.get(entityId);
    }

    @Override
    public boolean teleportEntity(UUID entityId, Position position) {
        if (entityPositions.containsKey(entityId)) {
            entityPositions.put(entityId, position);
            return true;
        }
        return false;
    }

    @Override
    public Inventory getPlayerInventory(UUID playerId) {
        return null;
    }

    @Override
    public Collection<UUID> entityIds() {
        return entityIds;
    }

    @Override
    public Collection<UUID> playerIds() {
        return playerIds;
    }

    @Override
    public String getPlayerName(UUID playerId) {
        return playerName;
    }

    @Override
    public EntityType getEntityType(UUID entityId) {
        return entityTypes.get(entityId);
    }

    @Override
    public String getEntityCustomName(UUID entityId) {
        return entityCustomNames.get(entityId);
    }

    @Override
    public boolean setEntityCustomName(UUID entityId, String name) {
        entityCustomNames.put(entityId, name);
        recordedCustomName = name;
        return true;
    }

    @Override
    public boolean isEntityAlive(UUID entityId) {
        return entityPositions.containsKey(entityId);
    }

    @Override
    public boolean removeEntity(UUID entityId) {
        removedEntity = entityId;
        entityPositions.remove(entityId);
        if (entityIds instanceof java.util.List) {
            return ((java.util.List<UUID>) entityIds).remove(entityId);
        }
        return false;
    }

    @Override
    public org.bukkit.GameMode getPlayerGameMode(UUID playerId) {
        return playerGameMode;
    }

    @Override
    public boolean setPlayerGameMode(UUID playerId, org.bukkit.GameMode gameMode) {
        this.playerGameMode = gameMode;
        return true;
    }

    @Override
    public String getBlockDataAsString(int x, int y, int z) {
        return blockDataString;
    }

    @Override
    public void setBlockData(int x, int y, int z, String blockData) {
        this.blockDataString = blockData;
        this.recordedBlockData = blockData;
    }

    @Override
    public int getBlockLight(int x, int y, int z) {
        return blockLight;
    }

    @Override
    public int getSkyLight(int x, int y, int z) {
        return skyLight;
    }

    @Override
    public boolean isBlockPowered(int x, int y, int z) {
        return blockPowered;
    }

    @Override
    public boolean isBlockIndirectlyPowered(int x, int y, int z) {
        return blockIndirectlyPowered;
    }

    @Override
    public int getBlockPower(int x, int y, int z, String faceName) {
        return blockPower;
    }

    @Override
    public Vector3 getEntityVelocity(UUID entityId) {
        return entityVelocities.get(entityId);
    }

    @Override
    public boolean setEntityVelocity(UUID entityId, Vector3 velocity) {
        entityVelocities.put(entityId, velocity);
        recordedVelocity = velocity;
        return true;
    }

    @Override
    public float getEntityFallDistance(UUID entityId) {
        return entityFallDistances.getOrDefault(entityId, 0.0f);
    }

    @Override
    public boolean setEntityFallDistance(UUID entityId, float distance) {
        entityFallDistances.put(entityId, distance);
        recordedFallDistance = distance;
        return true;
    }

    @Override
    public int getEntityFireTicks(UUID entityId) {
        return entityFireTicks.getOrDefault(entityId, 0);
    }

    @Override
    public boolean setEntityFireTicks(UUID entityId, int ticks) {
        entityFireTicks.put(entityId, ticks);
        recordedFireTicks = ticks;
        return true;
    }

    @Override
    public Collection<UUID> getEntityPassengers(UUID entityId) {
        return entityPassengers.getOrDefault(entityId, List.of());
    }

    @Override
    public boolean addEntityPassenger(UUID entityId, UUID passengerId) {
        entityPassengers.computeIfAbsent(entityId, k -> new ArrayList<>()).add(passengerId);
        recordedPassengerVehicle = entityId;
        return true;
    }

    @Override
    public boolean removeEntityPassenger(UUID entityId, UUID passengerId) {
        Collection<UUID> passengers = entityPassengers.get(entityId);
        if (passengers != null) {
            passengers.remove(passengerId);
        }
        recordedPassengerVehicle = entityId;
        recordedPassengerRemoved = passengerId;
        return true;
    }

    @Override
    public boolean isEntityInsideVehicle(UUID entityId) {
        return entityVehicles.containsKey(entityId);
    }

    @Override
    public UUID getEntityVehicle(UUID entityId) {
        return entityVehicles.get(entityId);
    }

    @Override
    public boolean leaveVehicle(UUID entityId) {
        return entityVehicles.remove(entityId) != null;
    }

    @Override
    public double getEntityHealth(UUID entityId) {
        return entityHealth.getOrDefault(entityId, 0.0);
    }

    @Override
    public boolean setEntityHealth(UUID entityId, double health) {
        entityHealth.put(entityId, health);
        recordedHealth = health;
        return true;
    }

    @Override
    public double getEntityMaxHealth(UUID entityId) {
        return entityMaxHealth.getOrDefault(entityId, 0.0);
    }

    @Override
    public boolean setEntityMaxHealth(UUID entityId, double maxHealth) {
        entityMaxHealth.put(entityId, maxHealth);
        recordedMaxHealth = maxHealth;
        return true;
    }

    @Override
    public void damageEntity(UUID entityId, double amount) {
        recordedDamage = amount;
    }

    @Override
    public boolean isEntityAiEnabled(UUID entityId) {
        return entityAi.getOrDefault(entityId, false);
    }

    @Override
    public boolean setEntityAiEnabled(UUID entityId, boolean ai) {
        entityAi.put(entityId, ai);
        recordedAi = ai;
        return true;
    }

    @Override
    public boolean isEntityCollidable(UUID entityId) {
        return entityCollidable.getOrDefault(entityId, false);
    }

    @Override
    public boolean setEntityCollidable(UUID entityId, boolean collidable) {
        entityCollidable.put(entityId, collidable);
        recordedCollidable = collidable;
        return true;
    }
}
