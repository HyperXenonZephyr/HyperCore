package dev.hypercore.bukkit;

import dev.hypercore.plugin.HyperPlugin;
import dev.hypercore.plugin.PluginContext;
import dev.hypercore.plugin.PluginManager;
import dev.hypercore.plugin.PluginPermissionService;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Adapts a Bukkit {@link JavaPlugin} instance to the HyperCore
 * {@link HyperPlugin} contract so that the loader-agnostic
 * {@link dev.hypercore.plugin.PluginManager PluginManager} can drive its
 * lifecycle.
 *
 * <p>The adapter is constructed by {@link dev.hypercore.plugin.ExternalPluginLoader}
 * when a plugin.yml-described JAR's main class extends {@code JavaPlugin} but
 * not {@code HyperPlugin}. It:
 * <ol>
 *   <li>{@code onLoad} — creates a {@link HyperCoreBukkitServer} from the
 *       context, builds {@link PluginCommand} objects from the plugin.yml
 *       commands map, calls {@link JavaPlugin#init} to inject them, then calls
 *       {@link JavaPlugin#onLoad()}.</li>
 *   <li>{@code onEnable} — registers the commands with the HyperCore command
 *       registry via {@link BukkitCommandBridge}, then calls
 *       {@link JavaPlugin#onEnable()} where the plugin sets executors.</li>
 *   <li>{@code onDisable} — calls {@link JavaPlugin#onDisable()}.</li>
 * </ol>
 */
public final class BukkitPluginAdapter implements HyperPlugin {
    private final JavaPlugin plugin;
    private final String pluginName;
    private final Map<String, Map<String, Object>> commandsMap;
    private final Map<String, Map<String, Object>> permissionsMap;
    private final PluginManager pluginManager;

    private HyperCoreBukkitServer server;
    private Map<String, PluginCommand> pluginCommands;

    /**
     * @param plugin        the instantiated JavaPlugin (main class from plugin.yml)
     * @param pluginName    the display name (Bukkit {@code name} field)
     * @param commandsMap   the raw commands map from plugin.yml (may be empty)
     * @param pluginManager the HyperCore plugin manager that owns this adapter
     */
    public BukkitPluginAdapter(
        JavaPlugin plugin,
        String pluginName,
        Map<String, Map<String, Object>> commandsMap,
        PluginManager pluginManager
    ) {
        this(plugin, pluginName, commandsMap, Map.of(), pluginManager);
    }

    /**
     * @param plugin          the instantiated JavaPlugin (main class from plugin.yml)
     * @param pluginName      the display name (Bukkit {@code name} field)
     * @param commandsMap     the raw commands map from plugin.yml (may be empty)
     * @param permissionsMap  the raw permissions map from plugin.yml (may be empty)
     * @param pluginManager   the HyperCore plugin manager that owns this adapter
     */
    public BukkitPluginAdapter(
        JavaPlugin plugin,
        String pluginName,
        Map<String, Map<String, Object>> commandsMap,
        Map<String, Map<String, Object>> permissionsMap,
        PluginManager pluginManager
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.pluginName = Objects.requireNonNull(pluginName, "pluginName");
        this.commandsMap = Objects.requireNonNullElse(commandsMap, Map.of());
        this.permissionsMap = Objects.requireNonNullElse(permissionsMap, Map.of());
        this.pluginManager = Objects.requireNonNull(pluginManager, "pluginManager");
    }

    @Override
    public void onLoad(PluginContext context) {
        server = BukkitServerAccess.acquire(pluginManager);
        pluginCommands = createPluginCommands();

        File dataFolder = new File("plugins", pluginName);
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            // Data folder creation is best-effort; the plugin can still load.
        }

        Logger logger = Logger.getLogger(pluginName);
        plugin.init(server, logger, pluginName, dataFolder, pluginCommands);

        // Install the global Bukkit server reference so static Bukkit.getServer()
        // works inside plugin callbacks.
        Bukkit.setServer(server);

        plugin.fireOnLoad();
    }

    @Override
    public void onEnable(PluginContext context) {
        // Register commands before the plugin's onEnable so that the plugin can
        // obtain them via getCommand(name) and set executors, and so the
        // registered CommandDefinition dispatches to the same PluginCommand.
        BukkitCommandBridge.registerCommands(context, pluginCommands, commandsMap);
        registerPermissions(context);

        plugin.setEnabled(true);
        plugin.fireOnEnable();
    }

    private void registerPermissions(PluginContext context) {
        for (Map.Entry<String, Map<String, Object>> entry : permissionsMap.entrySet()) {
            String node = entry.getKey();
            Map<String, Object> props = entry.getValue() == null ? Map.of() : entry.getValue();
            PluginPermissionService.PermissionDefault defaultValue = readPermissionDefault(props.get("default"));
            String description = props.get("description") instanceof String desc ? desc : null;
            Map<String, Boolean> children = readChildren(props.get("children"));
            context.permissions().register(pluginName, node, description, defaultValue, children);
        }
    }

    private static PluginPermissionService.PermissionDefault readPermissionDefault(Object value) {
        if (value == null) {
            return PluginPermissionService.PermissionDefault.OP;
        }
        String text = value.toString().trim().toUpperCase(Locale.ROOT);
        if (text.isEmpty()) {
            return PluginPermissionService.PermissionDefault.OP;
        }
        return switch (text) {
            case "TRUE", "NOT_OP" -> PluginPermissionService.PermissionDefault.TRUE;
            case "FALSE" -> PluginPermissionService.PermissionDefault.FALSE;
            case "OP" -> PluginPermissionService.PermissionDefault.OP;
            default -> PluginPermissionService.PermissionDefault.OP;
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Boolean> readChildren(Object value) {
        if (!(value instanceof Map<?, ?> rawChildren)) {
            return Map.of();
        }
        Map<String, Boolean> children = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawChildren.entrySet()) {
            String childNode = entry.getKey().toString();
            Object childValue = entry.getValue();
            boolean enabled = true;
            if (childValue instanceof Boolean flag) {
                enabled = flag;
            } else if (childValue instanceof Map<?, ?> childProps) {
                Object permission = childProps.get("permission");
                if (permission instanceof String perm && !perm.isBlank()) {
                    childNode = perm;
                }
                Object permissionValue = childProps.get("value");
                if (permissionValue instanceof Boolean flag) {
                    enabled = flag;
                }
            }
            children.put(childNode, enabled);
        }
        return Map.copyOf(children);
    }

    @Override
    public void onDisable(PluginContext context) {
        plugin.fireOnDisable();
        plugin.setEnabled(false);
    }

    /**
     * Returns the wrapped {@link JavaPlugin} instance.
     */
    JavaPlugin plugin() {
        return plugin;
    }

    private Map<String, PluginCommand> createPluginCommands() {
        Map<String, PluginCommand> commands = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : commandsMap.entrySet()) {
            String name = entry.getKey().toLowerCase(Locale.ROOT);
            PluginCommand command = new PluginCommand(name, plugin);
            Map<String, Object> props = entry.getValue();
            if (props != null) {
                if (props.get("description") instanceof String desc) {
                    command.setDescription(desc);
                }
                if (props.get("usage") instanceof String usage) {
                    command.setUsage(usage);
                }
            }
            commands.put(name, command);
        }
        return commands;
    }
}
