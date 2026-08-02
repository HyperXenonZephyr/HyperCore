package org.bukkit.event.entity;

import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * Bukkit event fired when an entity takes damage.
 *
 * <p>This is a hand-written event with real fields so that the HyperCore event
 * bridge can populate it and plugins can cancel or modify the damage.
 */
public class EntityDamageEvent extends EntityEvent implements Cancellable {
    private static final HandlerList HANDLER_LIST = new HandlerList();

    private double damage;
    private boolean cancelled;

    protected EntityDamageEvent() {
        super();
        this.damage = 0.0;
    }

    public EntityDamageEvent(Entity entity, double damage) {
        super(entity);
        this.damage = damage;
    }

    /**
     * Returns the amount of damage being dealt.
     */
    public double getDamage() {
        return damage;
    }

    /**
     * Sets the amount of damage being dealt.
     */
    public void setDamage(double damage) {
        this.damage = damage;
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
