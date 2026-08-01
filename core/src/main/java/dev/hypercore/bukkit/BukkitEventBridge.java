package dev.hypercore.bukkit;

import dev.hypercore.plugin.HyperPlugin;
import dev.hypercore.plugin.PluginDescriptor;
import dev.hypercore.plugin.PluginEventBus;
import dev.hypercore.plugin.PluginManager;
import dev.hypercore.plugin.lifecycle.PluginDisabledEvent;
import dev.hypercore.plugin.lifecycle.PluginEnabledEvent;
import dev.hypercore.plugin.lifecycle.ServerStartedEvent;
import dev.hypercore.world.event.BlockBreakEvent;
import dev.hypercore.world.event.BlockPlaceEvent;
import dev.hypercore.world.event.EntitySpawnEvent;
import dev.hypercore.world.event.PlayerJoinEvent;
import dev.hypercore.world.event.PlayerMoveEvent;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Bridges Bukkit and HyperCore lifecycle and world events. It observes HyperCore
 * plugin state changes and internal world mutations, posts matching Bukkit events,
 * and converts selected Bukkit events posted by plugins into HyperCore internal
 * events.
 */
public final class BukkitEventBridge implements PluginManager.LifecycleCallback {
    private final PluginManager plugins;
    private final List<PluginEventBus.Subscription> subscriptions = new ArrayList<>();
    private boolean serverStartedFired;

    public BukkitEventBridge(PluginManager plugins) {
        this.plugins = Objects.requireNonNull(plugins, "plugins");
    }

    /**
     * Attaches this bridge to the HyperCore plugin manager and event bus so that
     * HyperCore lifecycle transitions and world mutations produce Bukkit events.
     */
    public void attach() {
        plugins.setLifecycleCallback(this);
        PluginEventBus events = plugins.events();
        subscriptions.add(events.register("hypercore", BlockPlaceEvent.class, PluginEventBus.EventPriority.NORMAL, false, this::onBlockPlace));
        subscriptions.add(events.register("hypercore", BlockBreakEvent.class, PluginEventBus.EventPriority.NORMAL, false, this::onBlockBreak));
        subscriptions.add(events.register("hypercore", EntitySpawnEvent.class, PluginEventBus.EventPriority.NORMAL, false, this::onEntitySpawn));
        subscriptions.add(events.register("hypercore", PlayerMoveEvent.class, PluginEventBus.EventPriority.NORMAL, false, this::onPlayerMove));
        subscriptions.add(events.register("hypercore", PlayerJoinEvent.class, PluginEventBus.EventPriority.NORMAL, false, this::onPlayerJoin));
    }

    @Override
    public void onLoad(PluginDescriptor descriptor, HyperPlugin plugin) {
        // Bukkit does not define a PluginLoadEvent, so nothing is posted here.
    }

    @Override
    public void onEnable(PluginDescriptor descriptor, HyperPlugin plugin) {
        Plugin bukkitPlugin = toBukkitPlugin(plugin);
        if (bukkitPlugin != null) {
            callBukkit(new PluginEnableEvent(bukkitPlugin));
        }
    }

    @Override
    public void onDisable(PluginDescriptor descriptor, HyperPlugin plugin) {
        Plugin bukkitPlugin = toBukkitPlugin(plugin);
        if (bukkitPlugin != null) {
            callBukkit(new PluginDisableEvent(bukkitPlugin));
        }
    }

    /**
     * Fires the Bukkit {@link ServerLoadEvent} once per server instance. Called
     * by the loader adapter after the server has finished starting.
     */
    public void fireServerStarted() {
        if (serverStartedFired) {
            return;
        }
        serverStartedFired = true;
        callBukkit(new ServerLoadEvent(ServerLoadEvent.LoadType.STARTUP));
        plugins.events().post(new ServerStartedEvent());
    }

    private void onBlockPlace(BlockPlaceEvent internal) {
        org.bukkit.event.block.BlockPlaceEvent bukkit = new org.bukkit.event.block.BlockPlaceEvent(
            internal.getBlock(),
            internal.getPlayer(),
            internal.getType()
        );
        callBukkit(bukkit);
        if (bukkit.isCancelled()) {
            internal.cancelled(true);
        }
    }

    private void onBlockBreak(BlockBreakEvent internal) {
        org.bukkit.event.block.BlockBreakEvent bukkit = new org.bukkit.event.block.BlockBreakEvent(
            internal.getBlock(),
            internal.getPlayer(),
            internal.getType()
        );
        callBukkit(bukkit);
        if (bukkit.isCancelled()) {
            internal.cancelled(true);
        }
    }

    private void onEntitySpawn(EntitySpawnEvent internal) {
        org.bukkit.event.entity.EntitySpawnEvent bukkit = new org.bukkit.event.entity.EntitySpawnEvent(
            internal.getEntity(),
            internal.getLocation(),
            internal.getType()
        );
        callBukkit(bukkit);
        if (bukkit.isCancelled()) {
            internal.cancelled(true);
        }
    }

    private void onPlayerMove(PlayerMoveEvent internal) {
        org.bukkit.event.player.PlayerMoveEvent bukkit = new org.bukkit.event.player.PlayerMoveEvent(
            internal.getPlayer(),
            internal.getFrom(),
            internal.getTo()
        );
        callBukkit(bukkit);
        if (bukkit.isCancelled()) {
            internal.cancelled(true);
        }
    }

    private void onPlayerJoin(PlayerJoinEvent internal) {
        org.bukkit.event.player.PlayerJoinEvent bukkit = new org.bukkit.event.player.PlayerJoinEvent(
            internal.getPlayer(),
            internal.getJoinMessage()
        );
        callBukkit(bukkit);
    }

    /**
     * Converts a Bukkit event into the equivalent HyperCore internal event and
     * posts it on the internal bus. Only events with a well-defined HyperCore
     * counterpart are bridged.
     */
    static void bridgeToHyperCore(Event event, PluginEventBus events) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(events, "events");
        if (event instanceof org.bukkit.event.server.ServerCommandEvent serverCommand) {
            events.post(new dev.hypercore.plugin.lifecycle.ServerCommandEvent(
                serverCommand.getSender() == null ? "server" : serverCommand.getSender().getName(),
                serverCommand.getCommand()
            ));
        }
    }

    private static Plugin toBukkitPlugin(HyperPlugin plugin) {
        if (plugin instanceof BukkitPluginAdapter adapter) {
            return adapter.plugin();
        }
        return null;
    }

    private void callBukkit(Event event) {
        org.bukkit.plugin.PluginManager manager = Bukkit.getPluginManager();
        if (manager != null) {
            manager.callEvent(event);
        }
    }
}
