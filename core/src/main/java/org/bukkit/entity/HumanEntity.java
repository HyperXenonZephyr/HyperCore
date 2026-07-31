package org.bukkit.entity;

import org.bukkit.command.CommandSender;

/**
 * Minimal stub of the Bukkit {@code HumanEntity} interface.
 */
public interface HumanEntity extends LivingEntity, CommandSender {

    /**
     * Returns the display name of this human entity.
     */
    default String getDisplayName() {
        return getName();
    }
}
