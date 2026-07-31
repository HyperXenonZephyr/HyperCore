package org.bukkit.event.server;

import org.bukkit.event.HandlerList;

/**
 * Called when the server has finished loading.
 */
public class ServerLoadEvent extends ServerEvent {
    private static final HandlerList HANDLER_LIST = new HandlerList();
    private final LoadType type;

    public ServerLoadEvent(LoadType type) {
        this.type = type == null ? LoadType.STARTUP : type;
    }

    /**
     * Returns whether this load event is for initial startup or a reload.
     */
    public LoadType getType() {
        return type;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }

    /**
     * The reason the server is loading.
     */
    public enum LoadType {
        STARTUP,
        RELOAD
    }
}
