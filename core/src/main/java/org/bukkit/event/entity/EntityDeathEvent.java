package org.bukkit.event.entity;

import org.bukkit.entity.Entity;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Bukkit event fired when an entity dies.
 *
 * <p>This event is not cancellable — death cannot be prevented once it has
 * occurred. Plugins typically use it to react to a death or to alter the
 * dropped items.
 */
public class EntityDeathEvent extends EntityEvent {
    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final List<ItemStack> drops;

    /**
     * Creates an event for the given entity and its drops.
     *
     * @param entity the entity that died
     * @param drops  the items dropped by the entity, or {@code null} for none
     */
    public EntityDeathEvent(Entity entity, List<ItemStack> drops) {
        super(entity);
        this.drops = drops == null ? List.of() : List.copyOf(drops);
    }

    /**
     * No-argument constructor kept for generated subclasses (e.g.
     * {@code PlayerDeathEvent}) that call {@code super()} from a skeleton.
     */
    protected EntityDeathEvent() {
        super();
        this.drops = List.of();
    }

    /**
     * Returns the items dropped by the entity at death.
     */
    public List<ItemStack> getDrops() {
        return drops;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}