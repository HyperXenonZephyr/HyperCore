package org.bukkit.permissions;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Minimal stub of the Bukkit {@code Permission} class.
 */
public class Permission {
    private final String name;
    private final PermissionDefault defaultLevel;
    private final String description;
    private final Map<String, Boolean> children;

    public Permission(String name) {
        this(name, PermissionDefault.OP);
    }

    public Permission(String name, PermissionDefault defaultLevel) {
        this(name, defaultLevel, null, null);
    }

    public Permission(String name, PermissionDefault defaultLevel, String description) {
        this(name, defaultLevel, description, null);
    }

    public Permission(String name, PermissionDefault defaultLevel, String description, Map<String, Boolean> children) {
        this.name = Objects.requireNonNull(name, "name");
        this.defaultLevel = Objects.requireNonNullElse(defaultLevel, PermissionDefault.OP);
        this.description = description;
        this.children = children == null ? Collections.emptyMap() : Map.copyOf(children);
    }

    public String getName() {
        return name;
    }

    public PermissionDefault getDefault() {
        return defaultLevel;
    }

    /**
     * Returns the human-readable description of this permission, or {@code null}.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the immutable child permission map. Keys are child permission nodes
     * and values indicate whether the child is enabled when this permission is
     * granted.
     */
    public Map<String, Boolean> getChildren() {
        return children;
    }
}
