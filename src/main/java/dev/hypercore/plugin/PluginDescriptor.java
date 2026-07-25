package dev.hypercore.plugin;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record PluginDescriptor(String id, String name, String version) {
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z][a-z0-9_-]{1,63}");

    public PluginDescriptor {
        id = Objects.requireNonNull(id, "id").trim().toLowerCase(Locale.ROOT);
        name = requireText(name, "name");
        version = requireText(version, "version");
        if (!ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid plugin id: " + id);
        }
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return normalized;
    }
}
