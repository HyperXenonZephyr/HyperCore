package fixture.gametest;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Standalone Bukkit plugin used by Forge/Fabric dedicated-server GameTests to
 * verify end-to-end external plugin loading. It records lifecycle events and
 * exposes a {@code /hypercore-gametest} command that echoes a success message.
 *
 * <p>The plugin is packaged into its own JAR (see {@code :core:gametestPluginJar})
 * and copied into {@code run/plugins} before the game test server starts.
 */
public final class GametestBukkitPlugin extends JavaPlugin {
    public static final List<String> LIFECYCLE = new ArrayList<>();

    @Override
    protected void onLoad() {
        LIFECYCLE.add("load");
        if (getServer() == null) {
            throw new IllegalStateException("Bukkit server was not injected by the adapter");
        }
        if (getLogger() == null) {
            throw new IllegalStateException("Logger was not injected by the adapter");
        }
        if (getName() == null || getName().isBlank()) {
            throw new IllegalStateException("Plugin name was not injected by the adapter");
        }
        if (getDataFolder() == null) {
            throw new IllegalStateException("Data folder was not injected by the adapter");
        }
    }

    @Override
    protected void onEnable() {
        LIFECYCLE.add("enable");

        PluginCommand command = getCommand("hypercore-gametest");
        if (command == null) {
            throw new IllegalStateException("hypercore-gametest command was not registered from plugin.yml");
        }
        command.setExecutor((sender, cmd, label, args) -> {
            sender.sendMessage("HyperCore Bukkit GameTest OK");
            return true;
        });
    }

    @Override
    protected void onDisable() {
        LIFECYCLE.add("disable");
    }
}
