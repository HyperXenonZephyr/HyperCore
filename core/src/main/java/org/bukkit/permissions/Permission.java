package org.bukkit.permissions;

import java.util.Objects;

/**
 * Minimal stub of the Bukkit {@code Permission} class.
 */
public class Permission {
    private final String name;
    private final PermissionDefault defaultLevel;

    public Permission(String name) {
        this(name, PermissionDefault.OP);
    }

    public Permission(String name, PermissionDefault defaultLevel) {
        this.name = Objects.requireNonNull(name, "name");
        this.defaultLevel = Objects.requireNonNullElse(defaultLevel, PermissionDefault.OP);
    }

    public String getName() {
        return name;
    }

    public PermissionDefault getDefault() {
        return defaultLevel;
    }
}
