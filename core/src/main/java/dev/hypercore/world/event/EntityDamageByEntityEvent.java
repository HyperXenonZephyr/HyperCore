package dev.hypercore.world.event;

import dev.hypercore.plugin.PluginEventBus;

import org.bukkit.entity.Entity;

import java.util.Objects;

/**
 * Internal HyperCore event fired when an entity takes damage caused by another
 * entity.
 *
 * <p>If cancelled, the damage is aborted. Bukkit plugins observe the same
 * cancellation through {@link org.bukkit.event.entity.EntityDamageByEntityEvent}.
 */
public final class EntityDamageByEntityEvent implements PluginEventBus.CancellableEvent {
    private final Entity entity;
    private final Entity damager;
    private final double damage;
    private boolean cancelled;

    public EntityDamageByEntityEvent(Entity entity, Entity damager, double damage) {
        this.entity = Objects.requireNonNull(entity, "entity");
        this.damager = damager;
        this.damage = damage;
    }

    /**
     * Returns the entity taking damage.
     */
    public Entity getEntity() {
        return entity;
    }

    /**
     * Returns the entity dealing the damage, or {@code null} if none.
     */
    public Entity getDamager() {
        return damager;
    }

    /**
     * Returns the amount of damage.
     */
    public double getDamage() {
        return damage;
    }

    @Override
    public boolean cancelled() {
        return cancelled;
    }

    @Override
    public void cancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}