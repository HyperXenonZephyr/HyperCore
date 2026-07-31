package org.bukkit.inventory;

import org.bukkit.entity.HumanEntity;

import java.util.Collections;
import java.util.List;

/**
 * Minimal stub of the Bukkit {@code Inventory} interface.
 */
public interface Inventory {

    /**
     * Returns the size of this inventory.
     */
    default int getSize() {
        return 0;
    }

    /**
     * Returns the viewers of this inventory.
     */
    default List<HumanEntity> getViewers() {
        return Collections.emptyList();
    }
}
