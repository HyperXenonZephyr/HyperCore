package org.bukkit.permissions;

/**
 * Minimal stub of the Bukkit {@code Permissible} interface.
 */
public interface Permissible {
    boolean hasPermission(String name);

    boolean hasPermission(Permission perm);
}
