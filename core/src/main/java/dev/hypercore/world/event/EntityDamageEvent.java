package dev.hypercore.world.event;

import dev.hypercore.plugin.PluginEventBus;

import org.bukkit.entity.Entity;

import java.util.Objects;

/**
 * Internal HyperCore event fired when an entity takes damage.
 */
public final class EntityDamageEvent implements PluginEventBus.CancellableEvent {
    private final Entity entity;
    private final double damage;
    private boolean cancelled;

    public EntityDamageEvent(Entity entity, double damage) {
        this.entity = Objects.requireNonNull(entity, "entity");
        this.damage = damage;
    }

    public Entity getEntity() {
        return entity;
    }

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
