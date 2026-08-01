package dev.hypercore.bukkit;

import dev.hypercore.world.RegionExecutionService;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.util.Objects;
import java.util.UUID;



/**
 * Bukkit {@link Entity} implementation backed by HyperCore's
 * {@link RegionExecutionService}.
 *
 * <p>The adapter stores the entity's unique id and resolves its world and
 * location through the region-locked execution service. Teleports are delegated
 * back to the same service so that region ownership is respected.
 */
public class HyperCoreEntity implements Entity {
    protected final RegionExecutionService execution;
    private final UUID entityId;

    public HyperCoreEntity(RegionExecutionService execution, UUID entityId) {
        this.execution = Objects.requireNonNull(execution, "execution");
        this.entityId = Objects.requireNonNull(entityId, "entityId");
    }

    @Override
    public UUID getUniqueId() {
        return entityId;
    }

    @Override
    public String getName() {
        // Minimal stub: real Bukkit returns the entity's custom name or its
        // translated type name. The current WorldAccess contract does not expose
        // entity types, so a stable identifier is returned instead.
        return "Entity";
    }

    @Override
    public World getWorld() {
        Location location = getLocation();
        return location == null ? null : location.getWorld();
    }

    @Override
    public Location getLocation() {
        return execution.getEntityLocation(entityId);
    }

    @Override
    public boolean teleport(Location location) {
        return execution.teleportEntity(entityId, location);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof Entity other)) {
            return false;
        }
        return entityId.equals(other.getUniqueId());
    }

    @Override
    public int hashCode() {
        return entityId.hashCode();
    }
}
