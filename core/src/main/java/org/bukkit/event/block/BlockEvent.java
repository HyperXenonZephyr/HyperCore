package org.bukkit.event.block;

import org.bukkit.block.Block;
import org.bukkit.event.Event;

import java.util.Objects;

/**
 * Base class for block-related events.
 */
public abstract class BlockEvent extends Event {
    private final Block block;

    protected BlockEvent() {
        this(null);
    }

    protected BlockEvent(Block block) {
        this.block = block;
    }

    /**
     * Returns the block involved in this event.
     */
    public final Block getBlock() {
        return block;
    }
}
