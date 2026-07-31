package dev.hypercore.bukkit;

import dev.hypercore.plugin.PluginCommandRegistry.CommandDefinition;
import dev.hypercore.plugin.PluginContext;

import org.bukkit.command.PluginCommand;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Registers plugin.yml-defined commands with HyperCore's
 * {@link dev.hypercore.plugin.PluginCommandRegistry PluginCommandRegistry} via
 * the {@link PluginContext}. Each {@link PluginCommand} created by
 * {@link BukkitPluginAdapter} is wrapped in a {@link CommandDefinition} whose
 * executor delegates back to {@link PluginCommand#execute}, converting the
 * HyperCore sender to a Bukkit {@link org.bukkit.command.CommandSender}.
 *
 * <p>Aliases and permission are extracted from the plugin.yml commands map so
 * that the registered CommandDefinition carries the same metadata Bukkit would.
 */
final class BukkitCommandBridge {
    private BukkitCommandBridge() {
    }

    /**
     * Registers all plugin commands with the HyperCore command registry.
     *
     * @param context        the plugin's HyperCore context
     * @param pluginCommands the PluginCommand objects (created from plugin.yml)
     * @param commandsMap    the raw commands map from plugin.yml (name → props)
     */
    static void registerCommands(
        PluginContext context,
        Map<String, PluginCommand> pluginCommands,
        Map<String, Map<String, Object>> commandsMap
    ) {
        for (Map.Entry<String, PluginCommand> entry : pluginCommands.entrySet()) {
            String name = entry.getKey();
            PluginCommand command = entry.getValue();
            Map<String, Object> props = commandsMap.getOrDefault(name, Map.of());

            List<String> aliases = readStringList(props, "aliases");
            String permission = readString(props, "permission");

            context.registerCommand(new CommandDefinition(
                name,
                aliases,
                permission,
                command.getDescription(),
                command.getUsage(),
                (sender, label, arguments) -> {
                    var bukkitSender = new BukkitCommandSenderAdapter(sender);
                    return command.execute(bukkitSender, label, arguments.toArray(String[]::new));
                },
                (sender, label, arguments) -> {
                    if (command.getTabCompleter() == null) {
                        return List.of();
                    }
                    var bukkitSender = new BukkitCommandSenderAdapter(sender);
                    return command.tabComplete(bukkitSender, label, arguments.toArray(String[]::new));
                }
            ));
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> readStringList(Map<String, Object> props, String key) {
        Object value = props.get(key);
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>(list.size());
            for (Object element : list) {
                if (element != null) {
                    String text = element.toString().trim();
                    if (!text.isEmpty()) {
                        result.add(text);
                    }
                }
            }
            return List.copyOf(result);
        }
        String text = value.toString().trim();
        return text.isEmpty() ? List.of() : List.of(text);
    }

    private static String readString(Map<String, Object> props, String key) {
        Object value = props.get(key);
        if (value == null) {
            return "";
        }
        return value.toString().trim();
    }
}
