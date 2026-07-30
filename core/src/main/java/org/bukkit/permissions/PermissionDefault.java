package org.bukkit.permissions;

/**
 * Minimal stub of the Bukkit {@code PermissionDefault} enum for the HyperCore
 * Bukkit compatibility shim. Only the four canonical values are provided.
 */
public enum PermissionDefault {
    TRUE,
    FALSE,
    OP,
    NOT_OP;

    public boolean getValue(boolean op) {
        return switch (this) {
            case TRUE -> true;
            case FALSE -> false;
            case OP -> op;
            case NOT_OP -> !op;
        };
    }
}
