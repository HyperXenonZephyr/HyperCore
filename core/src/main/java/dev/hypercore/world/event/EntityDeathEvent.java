package dev.hypercore.world.event;

import dev.hypercore.plugin.PluginEventBus;

import org.bukkit.entity.Entity;

import java.util.Objects;

/**
 * Internal HyperCore event fired when an entity dies.
 *
 * <p>Unlike damage events, death events are informational and cannot be
 * cancelled. Bukkit plugins observe the same event through
 * {@link org.bukkit.event.entity.EntityDeathEvent}.
 */
public final class EntityDeathEvent implements PluginEventBus.PluginEvent {
    private final Entity entity;

    public EntityDeathEvent(Entity entity) {
        this.entity = Objects.requireNonNull(entity, "entity");
    }

    /**
     * Returns the entity that died.
     */
    public Entity getEntity() {
        return entity;
    }
}