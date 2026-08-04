package dev.hypercore.plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public final class PluginCommandRegistry {
    private static final Pattern LABEL_PATTERN = Pattern.compile("[a-z][a-z0-9_-]{0,63}");

    private final PluginPermissionService permissions;
    private final Map<String, RegisteredCommand> commandsByLabel = new HashMap<>();
    private final Map<String, Set<String>> labelsByPlugin = new HashMap<>();
    private final Map<String, RegisteredCommand> primaryCommands = new HashMap<>();

    public PluginCommandRegistry(PluginPermissionService permissions) {
        this.permissions = Objects.requireNonNull(permissions, "permissions");
    }

    public synchronized void register(String pluginId, CommandDefinition definition) {
        String normalizedPluginId = PluginPermissionService.normalizePluginId(pluginId);
        Objects.requireNonNull(definition, "definition");
        String primaryLabel = normalizeLabel(definition.name());
        LinkedHashSet<String> labels = new LinkedHashSet<>();
        labels.add(primaryLabel);
        definition.aliases().stream().map(PluginCommandRegistry::normalizeLabel).forEach(labels::add);

        for (String label : labels) {
            if (commandsByLabel.containsKey(label)) {
                throw new IllegalArgumentException("Command label is already registered: " + label);
            }
        }

        CommandDefinition normalizedDefinition = new CommandDefinition(
            primaryLabel,
            labels.stream().filter(label -> !label.equals(primaryLabel)).toList(),
            definition.permission().isBlank()
                ? ""
                : PluginPermissionService.normalizeNode(definition.permission()),
            definition.description().trim(),
            definition.usage().trim(),
            definition.executor(),
            definition.tabCompleter()
        );
        RegisteredCommand command = new RegisteredCommand(normalizedPluginId, normalizedDefinition);
        labels.forEach(label -> commandsByLabel.put(label, command));
        labelsByPlugin.computeIfAbsent(normalizedPluginId, ignored -> new HashSet<>()).addAll(labels);
        primaryCommands.put(primaryLabel, command);
    }

    public DispatchResult dispatch(
        String label,
        List<String> arguments,
        PluginCommandSender sender
    ) {
        String normalizedLabel = normalizeLabel(label);
        RegisteredCommand command;
        synchronized (this) {
            command = commandsByLabel.get(normalizedLabel);
        }
        if (command == null) {
            return new DispatchResult(DispatchStatus.UNKNOWN_COMMAND, false);
        }
        Objects.requireNonNull(sender, "sender");
        String permission = command.definition().permission();
        if (!permission.isEmpty() && !permissions.test(sender, permission)) {
            sender.sendMessage("You do not have permission to use this command.");
            return new DispatchResult(DispatchStatus.NO_PERMISSION, false);
        }

        boolean success = command.definition().executor().execute(
            sender,
            normalizedLabel,
            List.copyOf(arguments == null ? List.of() : arguments)
        );
        return new DispatchResult(DispatchStatus.EXECUTED, success);
    }

    public synchronized List<RegisteredCommand> commands() {
        return primaryCommands.values().stream()
            .sorted((left, right) -> left.definition().name().compareTo(right.definition().name()))
            .toList();
    }

    /**
     * Returns a serializable snapshot of all primary commands so the bridge can
     * mirror this registry on the remote host. Executors and tab completers are
     * intentionally excluded; they stay on the owning host.
     */
    public synchronized List<dev.hypercore.bridge.ipc.packet.CommandRegistrySnapshotPacket.CommandDescriptor> snapshot() {
        return primaryCommands.values().stream()
            .map(command -> new dev.hypercore.bridge.ipc.packet.CommandRegistrySnapshotPacket.CommandDescriptor(
                command.definition().name(),
                command.definition().aliases(),
                command.definition().permission(),
                command.definition().description(),
                command.definition().usage(),
                command.pluginId()
            ))
            .toList();
    }

    public synchronized int registeredCommands() {
        return primaryCommands.size();
    }

    /**
     * Returns tab-completion suggestions for the given command label and partial
     * arguments. If the registered command has no tab completer, an empty list is
     * returned.
     */
    public List<String> suggest(String label, List<String> arguments, PluginCommandSender sender) {
        String normalizedLabel = normalizeLabel(label);
        RegisteredCommand command;
        synchronized (this) {
            command = commandsByLabel.get(normalizedLabel);
        }
        if (command == null || command.definition().tabCompleter() == null) {
            return List.of();
        }
        Objects.requireNonNull(sender, "sender");
        return command.definition().tabCompleter().complete(sender, normalizedLabel, List.copyOf(arguments == null ? List.of() : arguments));
    }

    public synchronized void unregisterPlugin(String pluginId) {
        String normalizedPluginId = PluginPermissionService.normalizePluginId(pluginId);
        Set<String> labels = labelsByPlugin.remove(normalizedPluginId);
        if (labels == null) {
            return;
        }
        for (String label : labels) {
            RegisteredCommand removed = commandsByLabel.remove(label);
            if (removed != null && removed.definition().name().equals(label)) {
                primaryCommands.remove(label);
            }
        }
    }

    private static String normalizeLabel(String label) {
        String normalized = Objects.requireNonNull(label, "label").trim().toLowerCase(Locale.ROOT);
        if (!LABEL_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid command label: " + label);
        }
        return normalized;
    }

    @FunctionalInterface
    public interface CommandExecutor {
        boolean execute(PluginCommandSender sender, String label, List<String> arguments);
    }

    @FunctionalInterface
    public interface TabCompleter {
        List<String> complete(PluginCommandSender sender, String label, List<String> arguments);
    }

    public record CommandDefinition(
        String name,
        List<String> aliases,
        String permission,
        String description,
        String usage,
        CommandExecutor executor,
        TabCompleter tabCompleter
    ) {
        public CommandDefinition {
            name = Objects.requireNonNull(name, "name");
            aliases = List.copyOf(aliases == null ? List.of() : aliases);
            permission = Objects.requireNonNullElse(permission, "");
            description = Objects.requireNonNullElse(description, "");
            usage = Objects.requireNonNullElse(usage, "");
            executor = Objects.requireNonNull(executor, "executor");
            tabCompleter = tabCompleter;
        }

        /**
         * Backward-compatible constructor for commands that do not provide tab
         * completions.
         */
        public CommandDefinition(
            String name,
            List<String> aliases,
            String permission,
            String description,
            String usage,
            CommandExecutor executor
        ) {
            this(name, aliases, permission, description, usage, executor, null);
        }
    }

    public record RegisteredCommand(String pluginId, CommandDefinition definition) {
    }

    public enum DispatchStatus {
        EXECUTED,
        NO_PERMISSION,
        UNKNOWN_COMMAND
    }

    public record DispatchResult(DispatchStatus status, boolean success) {
    }
}
