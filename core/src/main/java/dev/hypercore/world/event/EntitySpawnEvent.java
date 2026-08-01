package dev.hypercore.world.event;

import dev.hypercore.plugin.PluginEventBus;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import java.util.Objects;

/**
 * Internal event fired before an entity is spawned into the world.
 *
 * <p>If cancelled, the spawn is aborted and Bukkit plugins observe the same
 * cancellation through {@link org.bukkit.event.entity.EntitySpawnEvent}.
 */
public final class EntitySpawnEvent implements PluginEventBus.CancellableEvent {
    private final Entity entity;
    private final Location location;
    private final EntityType type;
    private boolean cancelled;

    public EntitySpawnEvent(Entity entity, Location location, EntityType type) {
        this.entity = entity;
        this.location = Objects.requireNonNull(location, "location");
        this.type = Objects.requireNonNull(type, "type");
    }

    /**
     * Returns the entity being spawned, or {@code null} if it has not been
     * created yet.
     */
    public Entity getEntity() {
        return entity;
    }

    /**
     * Returns the location where the entity will spawn.
     */
    public Location getLocation() {
        return location;
    }

    /**
     * Returns the type of entity being spawned.
     */
    public EntityType getType() {
        return type;
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
