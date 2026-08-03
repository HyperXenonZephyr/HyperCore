package dev.hypercore.bukkit;

import org.bukkit.entity.LivingEntity;

import java.util.Objects;
import java.util.UUID;

/**
 * Bukkit {@link LivingEntity} implementation backed by HyperCore's
 * {@link dev.hypercore.world.RegionExecutionService}.
 *
 * <p>Health, AI, and collision attribute mutations are delegated back to the
 * region-locked execution service so that region ownership is respected.
 */
public class HyperCoreLivingEntity extends HyperCoreEntity implements LivingEntity {

    public HyperCoreLivingEntity(dev.hypercore.world.RegionExecutionService execution, UUID entityId) {
        super(execution, entityId);
    }

    @Override
    public double getHealth() {
        return execution.getEntityHealth(getUniqueId());
    }

    @Override
    public void setHealth(double health) {
        execution.setEntityHealth(getUniqueId(), health);
    }

    @Override
    public double getMaxHealth() {
        return execution.getEntityMaxHealth(getUniqueId());
    }

    @Override
    public void setMaxHealth(double maxHealth) {
        execution.setEntityMaxHealth(getUniqueId(), maxHealth);
    }

    @Override
    public void damage(double amount) {
        execution.damageEntity(getUniqueId(), amount);
    }

    @Override
    public void damage(double amount, org.bukkit.entity.Entity source) {
        damage(amount);
    }

    @Override
    public boolean hasAI() {
        return execution.isEntityAiEnabled(getUniqueId());
    }

    @Override
    public void setAI(boolean ai) {
        execution.setEntityAiEnabled(getUniqueId(), ai);
    }

    @Override
    public boolean isCollidable() {
        return execution.isEntityCollidable(getUniqueId());
    }

    @Override
    public void setCollidable(boolean collidable) {
        execution.setEntityCollidable(getUniqueId(), collidable);
    }
}
