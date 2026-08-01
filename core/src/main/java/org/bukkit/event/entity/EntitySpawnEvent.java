package org.bukkit.event.entity;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * Bukkit event fired when an entity is spawned into the world.
 *
 * <p>This is a hand-written event with real fields so that the HyperCore event
 * bridge can populate it and plugins can cancel the spawn.
 */
public class EntitySpawnEvent extends EntityEvent implements Cancellable {
    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Location location;
    private final EntityType type;
    private boolean cancelled;

    public EntitySpawnEvent(Entity entity, Location location, EntityType type) {
        super(entity);
        this.location = location;
        this.type = type;
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
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}
