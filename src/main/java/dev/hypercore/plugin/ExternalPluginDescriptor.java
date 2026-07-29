package dev.hypercore.plugin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record ExternalPluginDescriptor(
    PluginDescriptor plugin,
    int apiVersion,
    String mainClass,
    List<String> depends,
    List<String> softDepends
) {
    public static final int CURRENT_API_VERSION = 1;

    public ExternalPluginDescriptor {
        plugin = Objects.requireNonNull(plugin, "plugin");
        if (apiVersion != CURRENT_API_VERSION) {
            throw new IllegalArgumentException("Unsupported HyperCore plugin API version: " + apiVersion);
        }
        mainClass = requireText(mainClass, "mainClass");
        depends = normalizeDependencies(depends, plugin.id(), "depends");
        softDepends = normalizeDependencies(softDepends, plugin.id(), "softDepends");
    }

    public static ExternalPluginDescriptor parse(Reader source) {
        Objects.requireNonNull(source, "source");
        JsonObject root = JsonParser.parseReader(source).getAsJsonObject();
        String id = requiredString(root, "id");
        PluginDescriptor plugin = new PluginDescriptor(
            id,
            requiredString(root, "name"),
            requiredString(root, "version")
        );
        int apiVersion = root.has("apiVersion") ? root.get("apiVersion").getAsInt() : CURRENT_API_VERSION;
        return new ExternalPluginDescriptor(
            plugin,
            apiVersion,
            requiredString(root, "main"),
            readDependencies(root, "depends"),
            readDependencies(root, "softDepends")
        );
    }

    private static List<String> readDependencies(JsonObject root, String field) {
        if (!root.has(field)) {
            return List.of();
        }
        JsonArray values = root.getAsJsonArray(field);
        List<String> dependencies = new ArrayList<>(values.size());
        for (JsonElement value : values) {
            dependencies.add(value.getAsString());
        }
        return dependencies;
    }

    private static String requiredString(JsonObject root, String field) {
        if (!root.has(field) || root.get(field).isJsonNull()) {
            throw new IllegalArgumentException("Missing plugin descriptor field: " + field);
        }
        return root.get(field).getAsString();
    }

    private static List<String> normalizeDependencies(List<String> values, String pluginId, String field) {
        Objects.requireNonNull(values, field);
        Set<String> unique = new HashSet<>();
        List<String> normalized = new ArrayList<>(values.size());
        for (String value : values) {
            String dependency = PluginDescriptor.normalizeId(value);
            if (dependency.equals(pluginId)) {
                throw new IllegalArgumentException(field + " cannot contain the plugin itself: " + pluginId);
            }
            if (!unique.add(dependency)) {
                throw new IllegalArgumentException("Duplicate " + field + " entry: " + dependency);
            }
            normalized.add(dependency);
        }
        return List.copyOf(normalized);
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return normalized;
    }
}
