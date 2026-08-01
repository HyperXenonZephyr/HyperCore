package dev.hypercore.bukkit;

import dev.hypercore.world.RegionExecutionService;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
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
    public List<Entity> getEntities() {
        List<Entity> entities = new ArrayList<>();
        for (UUID entityId : execution.entityIds(worldName)) {
            entities.add(new HyperCoreEntity(execution, entityId));
        }
        return entities;
    }

    @Override
    public Entity spawnEntity(Location location, EntityType type) {
        UUID entityId = execution.spawnEntity(worldName, type, location);
        return entityId == null ? null : new HyperCoreEntity(execution, entityId);
    }
}
