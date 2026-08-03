package dev.hypercore.bukkit;

import dev.hypercore.world.RegionExecutionService;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;



/**
 * Bukkit {@link Entity} implementation backed by HyperCore's
 * {@link RegionExecutionService}.
 *
 * <p>The adapter stores the entity's unique id and resolves its world and
 * location through the region-locked execution service. Teleports and entity
 * attribute mutations are delegated back to the same service so that region
 * ownership is respected.
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
        String customName = getCustomName();
        if (customName != null && !customName.isEmpty()) {
            return customName;
        }
        EntityType type = getType();
        return type == null ? "Entity" : type.name().toLowerCase();
    }

    @Override
    public EntityType getType() {
        return execution.getEntityType(entityId);
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
    public org.bukkit.util.Vector getVelocity() {
        return execution.getEntityVelocity(entityId);
    }

    @Override
    public void setVelocity(org.bukkit.util.Vector velocity) {
        execution.setEntityVelocity(entityId, velocity);
    }

    @Override
    public float getFallDistance() {
        return execution.getEntityFallDistance(entityId);
    }

    @Override
    public void setFallDistance(float distance) {
        execution.setEntityFallDistance(entityId, distance);
    }

    @Override
    public int getFireTicks() {
        return execution.getEntityFireTicks(entityId);
    }

    @Override
    public void setFireTicks(int ticks) {
        execution.setEntityFireTicks(entityId, ticks);
    }

    @Override
    public List<Entity> getPassengers() {
        return execution.getEntityPassengers(entityId);
    }

    @Override
    public boolean addPassenger(Entity passenger) {
        return execution.addEntityPassenger(entityId, passenger);
    }

    @Override
    public void removePassenger(Entity passenger) {
        execution.removeEntityPassenger(entityId, passenger);
    }

    @Override
    public boolean isInsideVehicle() {
        return execution.isEntityInsideVehicle(entityId);
    }

    @Override
    public void leaveVehicle() {
        execution.leaveVehicle(entityId);
    }

    @Override
    public Entity getVehicle() {
        return execution.getEntityVehicle(entityId);
    }

    @Override
    public String getCustomName() {
        return execution.getEntityCustomName(entityId);
    }

    @Override
    public void setCustomName(String name) {
        execution.setEntityCustomName(entityId, name);
    }

    @Override
    public boolean isDead() {
        return !execution.isEntityAlive(entityId);
    }

    @Override
    public boolean isValid() {
        return execution.isEntityAlive(entityId);
    }

    @Override
    public void remove() {
        execution.removeEntity(entityId);
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
