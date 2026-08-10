package org.bukkit.event.entity;

import org.bukkit.entity.Entity;
import org.bukkit.event.HandlerList;

/**
 * Bukkit event fired when an entity takes damage caused by another entity.
 *
 * <p>Extends {@link EntityDamageEvent} and adds the source (damager) entity.
 * This is the event PvP and combat plugins listen to in order to react to or
 * cancel damage dealt by one entity to another.
 */
public class EntityDamageByEntityEvent extends EntityDamageEvent {
    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Entity damager;

    /**
     * Creates an event for the given victim, damager, and damage amount.
     *
     * @param victim  the entity taking damage
     * @param damager the entity dealing the damage, or {@code null}
     * @param damage  the amount of damage
     */
    public EntityDamageByEntityEvent(Entity victim, Entity damager, double damage) {
        super(victim, damage);
        this.damager = damager;
    }

    /**
     * Returns the entity that dealt the damage, or {@code null} if none.
     */
    public Entity getDamager() {
        return damager;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}