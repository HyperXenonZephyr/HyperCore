package org.bukkit.configuration;

import java.util.Set;

/**
 * Minimal stub of the Bukkit {@code ConfigurationSection} interface. Only the
 * methods needed for basic config access are declared; unsupported operations
 * throw {@link UnsupportedOperationException}.
 */
public interface ConfigurationSection {
    Object get(String path);

    String getString(String path);

    boolean getBoolean(String path);

    int getInt(String path);

    boolean contains(String path);

    Set<String> getKeys(boolean deep);
}
