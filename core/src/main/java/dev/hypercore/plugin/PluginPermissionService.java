package dev.hypercore.plugin;

import org.bukkit.permissions.Permission;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public final class PluginPermissionService {
    private static final Pattern NODE_PATTERN = Pattern.compile("(?:[a-z0-9_-]+\\.)*[a-z0-9_*-]+");

    private final Map<String, RegisteredPermission> permissions = new HashMap<>();
    private final Map<String, Set<String>> permissionsByPlugin = new HashMap<>();

    public synchronized void register(
        String pluginId,
        String node,
        String description,
        PermissionDefault defaultValue
    ) {
        register(pluginId, node, description, defaultValue, Map.of());
    }

    public synchronized void register(
        String pluginId,
        String node,
        String description,
        PermissionDefault defaultValue,
        Map<String, Boolean> children
    ) {
        String normalizedPluginId = normalizePluginId(pluginId);
        String normalizedNode = normalizeNode(node);
        RegisteredPermission permission = new RegisteredPermission(
            normalizedPluginId,
            normalizedNode,
            Objects.requireNonNullElse(description, "").trim(),
            Objects.requireNonNull(defaultValue, "defaultValue"),
            children == null || children.isEmpty() ? Map.of() : Map.copyOf(children)
        );
        if (permissions.putIfAbsent(normalizedNode, permission) != null) {
            throw new IllegalArgumentException("Permission is already registered: " + normalizedNode);
        }
        permissionsByPlugin.computeIfAbsent(normalizedPluginId, ignored -> new HashSet<>())
            .add(normalizedNode);
    }

    /**
     * Registers a Bukkit-style permission object, including its children.
     */
    public synchronized void register(String pluginId, Permission permission) {
        Objects.requireNonNull(permission, "permission");
        String defaultName = permission.getDefault() == null
            ? PermissionDefault.OP.name()
            : permission.getDefault().name();
        PermissionDefault defaultValue = PermissionDefault.valueOf(defaultName.toUpperCase(Locale.ROOT));
        register(
            pluginId,
            permission.getName(),
            permission.getDescription(),
            defaultValue,
            permission.getChildren()
        );
    }

    public synchronized boolean test(PluginCommandSender sender, String node) {
        Objects.requireNonNull(sender, "sender");
        String normalizedNode = normalizeNode(node);
        for (String candidate : overrideCandidates(normalizedNode)) {
            Optional<Boolean> override = sender.permissionOverride(candidate);
            if (override.isPresent()) {
                return override.get();
            }
        }

        RegisteredPermission permission = permissions.get(normalizedNode);
        if (permission == null) {
            return false;
        }
        if (permission.defaultValue().allows(sender.operator())) {
            return true;
        }
        // Child permissions can grant access when a parent is explicitly set.
        for (Map.Entry<String, Boolean> child : permission.children().entrySet()) {
            if (!Boolean.TRUE.equals(child.getValue())) {
                continue;
            }
            Optional<Boolean> childOverride = sender.permissionOverride(child.getKey());
            if (childOverride.isPresent() && childOverride.get()) {
                return true;
            }
            RegisteredPermission childRegistration = permissions.get(normalizeNode(child.getKey()));
            if (childRegistration != null && childRegistration.defaultValue().allows(sender.operator())) {
                return true;
            }
        }
        return false;
    }

    public synchronized int registeredPermissions() {
        return permissions.size();
    }

    /**
     * Returns the Bukkit permission object for the given node, or {@code null}
     * if it is not registered.
     */
    public synchronized org.bukkit.permissions.Permission getPermission(String name) {
        RegisteredPermission registered = permissions.get(normalizeNode(name));
        if (registered == null) {
            return null;
        }
        return new org.bukkit.permissions.Permission(
            registered.node(),
            toBukkitDefault(registered.defaultValue()),
            registered.description(),
            registered.children()
        );
    }

    private static org.bukkit.permissions.PermissionDefault toBukkitDefault(PermissionDefault value) {
        return switch (value) {
            case TRUE -> org.bukkit.permissions.PermissionDefault.TRUE;
            case FALSE -> org.bukkit.permissions.PermissionDefault.FALSE;
            case OP -> org.bukkit.permissions.PermissionDefault.OP;
            case NOT_OP -> org.bukkit.permissions.PermissionDefault.NOT_OP;
        };
    }

    public synchronized void unregisterPlugin(String pluginId) {
        String normalizedPluginId = normalizePluginId(pluginId);
        Set<String> ownedPermissions = permissionsByPlugin.remove(normalizedPluginId);
        if (ownedPermissions != null) {
            ownedPermissions.forEach(permissions::remove);
        }
    }

    private static List<String> overrideCandidates(String node) {
        List<String> candidates = new ArrayList<>();
        candidates.add(node);
        int separator = node.lastIndexOf('.');
        while (separator >= 0) {
            candidates.add(node.substring(0, separator) + ".*");
            separator = node.lastIndexOf('.', separator - 1);
        }
        candidates.add("*");
        return candidates;
    }

    static String normalizePluginId(String pluginId) {
        return Objects.requireNonNull(pluginId, "pluginId").trim().toLowerCase(Locale.ROOT);
    }

    static String normalizeNode(String node) {
        String normalized = Objects.requireNonNull(node, "node").trim().toLowerCase(Locale.ROOT);
        if (!NODE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid permission node: " + node);
        }
        return normalized;
    }

    private record RegisteredPermission(
        String pluginId,
        String node,
        String description,
        PermissionDefault defaultValue,
        Map<String, Boolean> children
    ) {
    }

    public enum PermissionDefault {
        TRUE,
        FALSE,
        OP,
        NOT_OP;

        public boolean allows(boolean operator) {
            return switch (this) {
                case TRUE -> true;
                case FALSE -> false;
                case OP -> operator;
                case NOT_OP -> !operator;
            };
        }
    }
}
