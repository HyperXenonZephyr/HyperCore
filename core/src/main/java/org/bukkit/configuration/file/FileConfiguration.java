package org.bukkit.configuration.file;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.Set;

/**
 * Minimal stub of the Bukkit {@code FileConfiguration} class. Returns empty
 * defaults for all config lookups; plugins that need real config loading are
 * not supported by this shim.
 */
public class FileConfiguration implements ConfigurationSection {
    @Override
    public Object get(String path) {
        return null;
    }

    @Override
    public String getString(String path) {
        return null;
    }

    @Override
    public boolean getBoolean(String path) {
        return false;
    }

    @Override
    public int getInt(String path) {
        return 0;
    }

    @Override
    public boolean contains(String path) {
        return false;
    }

    @Override
    public Set<String> getKeys(boolean deep) {
        return Collections.emptySet();
    }
}
