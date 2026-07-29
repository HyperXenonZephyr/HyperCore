package dev.hypercore.plugin;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record PluginDescriptor(String id, String name, String version) {
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z][a-z0-9_-]{1,63}");

    public PluginDescriptor {
        id = normalizeId(id);
        name = requireText(name, "name");
        version = requireText(version, "version");
    }

    static String normalizeId(String id) {
        String normalized = Objects.requireNonNull(id, "id").trim().toLowerCase(Locale.ROOT);
        if (!ID_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid plugin id: " + normalized);
        }
        return normalized;
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return normalized;
    }
}
