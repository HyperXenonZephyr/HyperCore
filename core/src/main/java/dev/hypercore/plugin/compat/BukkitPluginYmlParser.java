package dev.hypercore.plugin.compat;

import dev.hypercore.plugin.ExternalPluginDescriptor;
import dev.hypercore.plugin.PluginDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Parses a Bukkit-format {@code plugin.yml} descriptor and translates it into
 * the loader-agnostic {@link ExternalPluginDescriptor} model. This lets
 * {@link dev.hypercore.plugin.ExternalPluginLoader} discover JARs that ship a
 * Bukkit descriptor alongside the native {@code hypercore-plugin.json} format.
 *
 * <p>Field mapping (Bukkit → HyperCore):
 * <ul>
 *   <li>{@code name} (required) → {@link PluginDescriptor#id()} (lower-cased
 *       by {@link PluginDescriptor#normalizeId}) and {@link PluginDescriptor#name()}</li>
 *   <li>{@code version} (required) → {@link PluginDescriptor#version()}</li>
 *   <li>{@code main} (required) → {@link ExternalPluginDescriptor#mainClass()}</li>
 *   <li>{@code api-version} (optional, e.g. {@code "1.21"}) → ignored; the
 *       HyperCore API version is pinned to
 *       {@link ExternalPluginDescriptor#CURRENT_API_VERSION}</li>
 *   <li>{@code depend} (optional list) → {@link ExternalPluginDescriptor#depends()}</li>
 *   <li>{@code softdepend} (optional list) → {@link ExternalPluginDescriptor#softDepends()}</li>
 * </ul>
 *
 * <p>Unmapped fields ({@code load}, {@code commands}, {@code permissions},
 * {@code author}, {@code website}, etc.) are accepted but ignored at this stage.
 * A minimal Bukkit API shim ({@code org.bukkit.plugin.java.JavaPlugin}) handles
 * lifecycle and command registration separately.
 */
public final class BukkitPluginYmlParser {
    public static final String DESCRIPTOR_ENTRY = "plugin.yml";

    private static final Logger LOGGER = LoggerFactory.getLogger(BukkitPluginYmlParser.class);

    private BukkitPluginYmlParser() {
    }

    /**
     * Parsed plugin.yml content: the translated descriptor plus the raw
     * {@code commands} map (command name → properties) for the Bukkit command
     * bridge to register.
     */
    public record ParsedPluginYml(
        ExternalPluginDescriptor descriptor,
        Map<String, Map<String, Object>> commands
    ) {
        public ParsedPluginYml {
            commands = Objects.requireNonNullElse(commands, Map.of());
        }
    }

    /**
     * Parses a Bukkit {@code plugin.yml} from the given reader.
     *
     * @param source a reader over the YAML content; not closed by this method
     * @return the translated descriptor
     * @throws IllegalArgumentException if a required field is missing or blank
     */
    public static ExternalPluginDescriptor parse(Reader source) {
        return parseWithCommands(source).descriptor();
    }

    /**
     * Parses a Bukkit {@code plugin.yml} and returns both the translated
     * descriptor and the {@code commands} map for command-bridge registration.
     *
     * @param source a reader over the YAML content; not closed by this method
     * @return the parsed descriptor and commands
     * @throws IllegalArgumentException if a required field is missing or blank
     */
    public static ParsedPluginYml parseWithCommands(Reader source) {
        Objects.requireNonNull(source, "source");
        Yaml yaml = new Yaml();
        Object loaded = yaml.load(source);
        if (!(loaded instanceof Map<?, ?> rawMap)) {
            throw new IllegalArgumentException("plugin.yml must contain a YAML mapping at the root");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) rawMap;

        String name = requiredString(root, "name");
        String version = requiredString(root, "version");
        String main = requiredString(root, "main");

        PluginDescriptor plugin = new PluginDescriptor(name, name, version);

        List<String> depends = readStringList(root, "depend");
        List<String> softDepends = readStringList(root, "softdepend");

        if (root.containsKey("api-version")) {
            LOGGER.debug(
                "plugin.yml api-version '{}' ignored for {}; HyperCore API version is {}",
                root.get("api-version"), name, ExternalPluginDescriptor.CURRENT_API_VERSION
            );
        }

        Map<String, Map<String, Object>> commands = readCommandsMap(root);

        return new ParsedPluginYml(
            new ExternalPluginDescriptor(
                plugin,
                ExternalPluginDescriptor.CURRENT_API_VERSION,
                main,
                depends,
                softDepends
            ),
            commands
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> readCommandsMap(Map<String, Object> root) {
        Object value = root.get("commands");
        if (!(value instanceof Map<?, ?> rawCommands) || rawCommands.isEmpty()) {
            return Map.of();
        }
        Map<String, Map<String, Object>> commands = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawCommands.entrySet()) {
            String commandName = entry.getKey().toString().toLowerCase(java.util.Locale.ROOT);
            Map<String, Object> properties = entry.getValue() instanceof Map<?, ?> props
                ? (Map<String, Object>) props
                : Map.of();
            commands.put(commandName, properties);
        }
        return Map.copyOf(commands);
    }

    private static String requiredString(Map<String, Object> root, String field) {
        Object value = root.get(field);
        if (value == null) {
            throw new IllegalArgumentException("Missing plugin.yml field: " + field);
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("plugin.yml field is blank: " + field);
        }
        return text;
    }

    private static List<String> readStringList(Map<String, Object> root, String field) {
        Object value = root.get(field);
        if (value == null) {
            return List.of();
        }
        // SnakeYAML parses flow-style `[a, b]` and block-style `- a\n- b` into
        // a List. A single scalar (rare but valid YAML) becomes a String.
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object element : list) {
                if (element == null) {
                    continue;
                }
                String text = element.toString().trim();
                if (!text.isEmpty()) {
                    result.add(text);
                }
            }
        } else {
            String text = value.toString().trim();
            if (!text.isEmpty()) {
                result.add(text);
            }
        }
        return List.copyOf(result);
    }
}
