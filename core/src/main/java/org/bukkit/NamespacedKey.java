package org.bukkit;

import java.util.Locale;
import java.util.Objects;

/**
 * Minimal stub of the Bukkit {@code NamespacedKey} class.
 *
 * <p>Represents a Minecraft resource location in the form {@code namespace:key}.
 */
public final class NamespacedKey {
    private final String namespace;
    private final String key;

    /**
     * Creates a key in the {@code minecraft} namespace.
     */
    public NamespacedKey(String key) {
        this("minecraft", key);
    }

    /**
     * Creates a key with the given namespace and key.
     */
    public NamespacedKey(String namespace, String key) {
        this.namespace = Objects.requireNonNull(namespace, "namespace").toLowerCase(Locale.ROOT);
        this.key = Objects.requireNonNull(key, "key").toLowerCase(Locale.ROOT);
    }

    /**
     * Returns the namespace of this key.
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Returns the key part of this key.
     */
    public String getKey() {
        return key;
    }

    @Override
    public String toString() {
        return namespace + ":" + key;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof NamespacedKey other)) {
            return false;
        }
        return namespace.equals(other.namespace) && key.equals(other.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, key);
    }
}
