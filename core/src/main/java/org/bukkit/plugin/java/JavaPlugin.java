package org.bukkit.plugin.java;

import org.bukkit.Server;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Minimal stub of the Bukkit {@code JavaPlugin} abstract base class. A Bukkit
 * plugin extends this class and overrides {@link #onLoad()}, {@link #onEnable()},
 * and {@link #onDisable()}.
 *
 * <p>The HyperCore adapter ({@code BukkitPluginAdapter}) calls
 * {@link #init(Server, Logger, String, File, Map)} before any lifecycle
 * callback to inject the server, logger, plugin name, data folder, and
 * plugin.yml-defined commands. A plugin obtains its commands via
 * {@link #getCommand(String)} and sets an executor in {@code onEnable}.
 */
public abstract class JavaPlugin implements Plugin {
    private Server server;
    private Logger logger;
    private String name;
    private File dataFolder;
    private FileConfiguration config;
    private Map<String, PluginCommand> commands;
    private boolean enabled;

    protected JavaPlugin() {
    }

    /**
     * Internal: called by the HyperCore adapter before lifecycle callbacks.
     */
    public final void init(
        Server server,
        Logger logger,
        String name,
        File dataFolder,
        Map<String, PluginCommand> commands
    ) {
        this.server = Objects.requireNonNull(server, "server");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.name = Objects.requireNonNull(name, "name");
        this.dataFolder = Objects.requireNonNull(dataFolder, "dataFolder");
        this.commands = Objects.requireNonNullElse(commands, Map.of());
    }

    protected void onLoad() {
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    /**
     * Internal: invoked by the HyperCore adapter (in a different package) to
     * drive the protected lifecycle callbacks without exposing them publicly.
     */
    public final void fireOnLoad() {
        onLoad();
    }

    public final void fireOnEnable() {
        onEnable();
    }

    public final void fireOnDisable() {
        onDisable();
    }

    @Override
    public final String getName() {
        return name;
    }

    @Override
    public final boolean isEnabled() {
        return enabled;
    }

    public final Server getServer() {
        return server;
    }

    public final Logger getLogger() {
        return logger;
    }

    public final File getDataFolder() {
        return dataFolder;
    }

    public FileConfiguration getConfig() {
        if (config == null) {
            config = new FileConfiguration();
        }
        return config;
    }

    /**
     * Returns the {@link PluginCommand} defined in {@code plugin.yml} with the
     * given name (case-insensitive), or {@code null} if not defined.
     */
    public final PluginCommand getCommand(String name) {
        if (name == null || commands == null) {
            return null;
        }
        return commands.get(name.toLowerCase(Locale.ROOT));
    }

    /**
     * Internal: called by the adapter to track enabled state.
     */
    public final void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
