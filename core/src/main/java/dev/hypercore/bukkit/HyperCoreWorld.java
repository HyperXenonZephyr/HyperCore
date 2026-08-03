package dev.hypercore.bukkit;

import dev.hypercore.world.RegionExecutionService;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Bukkit {@link World} implementation backed by HyperCore's
 * {@link RegionExecutionService}.
 */
public final class HyperCoreWorld implements World {
    private final RegionExecutionService execution;
    private final String worldName;

    public HyperCoreWorld(RegionExecutionService execution, String worldName) {
        this.execution = Objects.requireNonNull(execution, "execution");
        this.worldName = Objects.requireNonNull(worldName, "worldName");
    }

    @Override
    public String getName() {
        return worldName;
    }

    @Override
    public Block getBlockAt(int x, int y, int z) {
        return new HyperCoreBlock(execution, worldName, x, y, z);
    }

    @Override
    public long getTime() {
        return execution.getTime(worldName);
    }

    @Override
    public void setTime(long time) {
        execution.setTime(worldName, time);
    }

    @Override
    public long getFullTime() {
        return execution.getFullTime(worldName);
    }

    @Override
    public void setFullTime(long time) {
        execution.setFullTime(worldName, time);
    }

    @Override
    public boolean hasStorm() {
        return execution.hasStorm(worldName);
    }

    @Override
    public void setStorm(boolean hasStorm) {
        execution.setStorm(worldName, hasStorm);
    }

    @Override
    public boolean isThundering() {
        return execution.isThundering(worldName);
    }

    @Override
    public void setThundering(boolean thundering) {
        execution.setThundering(worldName, thundering);
    }

    @Override
    public Location getSpawnLocation() {
        return execution.getSpawnLocation(worldName);
    }

    @Override
    public void setSpawnLocation(Location location) {
        execution.setSpawnLocation(worldName, location);
    }

    @Override
    public Biome getBiome(int x, int y, int z) {
        return execution.getBiome(worldName, x, y, z);
    }

    @Override
    public void setBiome(int x, int y, int z, Biome biome) {
        execution.setBiome(worldName, x, y, z, biome);
    }

    @Override
    public List<Entity> getEntities() {
        List<Entity> entities = new ArrayList<>();
        for (UUID entityId : execution.entityIds(worldName)) {
            Entity entity = execution.resolveEntity(entityId);
            if (entity != null) {
                entities.add(entity);
            }
        }
        return entities;
    }

    @Override
    public <T extends Entity> Collection<T> getEntitiesByClass(Class<T> clazz) {
        List<T> result = new ArrayList<>();
        for (Entity entity : getEntities()) {
            if (clazz.isInstance(entity)) {
                result.add(clazz.cast(entity));
            }
        }
        return result;
    }

    @Override
    public Collection<Entity> getNearbyEntities(Location location, double x, double y, double z) {
        List<Entity> result = new ArrayList<>();
        for (Entity entity : getEntities()) {
            Location entityLocation = entity.getLocation();
            if (entityLocation == null) {
                continue;
            }
            if (Math.abs(entityLocation.getX() - location.getX()) <= x
                && Math.abs(entityLocation.getY() - location.getY()) <= y
                && Math.abs(entityLocation.getZ() - location.getZ()) <= z) {
                result.add(entity);
            }
        }
        return result;
    }

    @Override
    public Entity spawnEntity(Location location, EntityType type) {
        UUID entityId = execution.spawnEntity(worldName, type, location);
        return entityId == null ? null : execution.resolveEntity(entityId);
    }
}
