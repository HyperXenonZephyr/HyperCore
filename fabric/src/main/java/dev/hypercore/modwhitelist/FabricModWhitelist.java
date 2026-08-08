package dev.hypercore.modwhitelist;

import com.mojang.logging.LogUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Validates loaded Fabric mods against a whitelist at host startup.
 *
 * <p>HyperCore's Fabric host participates in a shared world timeline through the
 * cross-process world-state bridge. An unverified Fabric mod can corrupt that
 * timeline by registering content that has no Forge counterpart, applying
 * conflicting Mixins, or mutating world state outside the captured
 * {@code RegionExecutionService} path. To protect the bridge, only mods that add
 * no content and have been manually verified non-conflicting are permitted.
 *
 * <p>Fabric mods are loaded by the Fabric Loader before HyperCore's entrypoint
 * runs, so this check cannot prevent loading — it detects non-whitelisted mods
 * after the fact and reacts. In enforce mode the check throws and aborts host
 * startup; in non-enforce mode it logs warnings and continues. Build-time
 * gating is enforced separately by the distribution assembler, which only
 * stages whitelisted mod JARs into the Fabric host template.
 *
 * <p>Core infrastructure mods ({@code fabricloader}, {@code fabric-api} and its
 * sub-modules, {@code minecraft}, {@code java}, {@code hypercore}) are always
 * allowed and never need to be listed.
 *
 * @see FABRIC_MOD_WHITELIST.md
 */
public final class FabricModWhitelist {
    private static final Logger LOGGER = LogUtils.getLogger();

    static final String CONFIG_FILE = "config/fabric-mod-whitelist.txt";
    static final String PROPERTIES_FILE = "config/hypercore.properties";
    static final String KEY_WHITELIST_FILE = "fabric.modWhitelistFile";
    static final String KEY_ENFORCE = "fabric.enforceModWhitelist";
    static final boolean DEFAULT_ENFORCE = true;

    /** Mod ids that are always allowed regardless of the whitelist file. */
    static final Set<String> CORE_MODS = Set.of(
        "fabricloader",
        "minecraft",
        "java",
        "hypercore"
    );

    private FabricModWhitelist() {
    }

    /**
     * Loads the whitelist file and validates currently loaded mods against it.
     *
     * @param baseDir the server run directory (relative paths resolve here)
     * @throws WhitelistViolationException if enforce mode is active and one or
     *     more non-whitelisted mods are loaded
     */
    public static void enforce(Path baseDir) {
        Properties properties = loadProperties(baseDir);
        String whitelistPath = properties.getProperty(KEY_WHITELIST_FILE, CONFIG_FILE);
        boolean enforce = parseBoolean(properties, KEY_ENFORCE, DEFAULT_ENFORCE);

        Set<String> whitelist = loadWhitelist(baseDir.resolve(whitelistPath));
        List<ModInfo> loaded = collectLoadedMods();
        List<ModInfo> violations = filterViolations(loaded, whitelist);

        logSummary(whitelist, loaded, violations);

        if (violations.isEmpty()) {
            LOGGER.info("Fabric mod whitelist check passed: {} mod(s) loaded, all permitted", loaded.size());
            return;
        }

        for (ModInfo violation : violations) {
            LOGGER.warn(
                "Non-whitelisted Fabric mod detected: id={}, name={}, version={}",
                violation.id(), violation.name(), violation.version()
            );
        }

        if (enforce) {
            throw new WhitelistViolationException(
                "Fabric mod whitelist enforcement failed: " + violations.size()
                    + " non-whitelisted mod(s) are loaded. Add them to " + whitelistPath
                    + " after manual verification, or set " + KEY_ENFORCE + "=false to bypass (not recommended)."
            );
        }
        LOGGER.warn(
            "Fabric mod whitelist enforcement is disabled ({}=false); {} non-whitelisted mod(s) will remain loaded",
            KEY_ENFORCE,
            violations.size()
        );
    }

    /**
     * Collects the currently loaded non-core mods. Package-private for testing.
     */
    static List<ModInfo> collectLoadedMods() {
        List<ModInfo> mods = new ArrayList<>();
        for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
            String id = container.getMetadata().getId();
            if (isCoreMod(id)) {
                continue;
            }
            mods.add(new ModInfo(
                id,
                container.getMetadata().getName(),
                String.valueOf(container.getMetadata().getVersion())
            ));
        }
        return mods;
    }

    /**
     * Returns the subset of loaded mods that are not on the whitelist.
     * Package-private for testing.
     */
    static List<ModInfo> filterViolations(List<ModInfo> loaded, Set<String> whitelist) {
        List<ModInfo> violations = new ArrayList<>();
        for (ModInfo mod : loaded) {
            if (!whitelist.contains(mod.id())) {
                violations.add(mod);
            }
        }
        return violations;
    }

    /**
     * A core infrastructure mod is always allowed. Fabric API sub-modules share
     * the {@code fabric-} prefix and are treated as core.
     */
    static boolean isCoreMod(String modId) {
        if (CORE_MODS.contains(modId)) {
            return true;
        }
        return modId.startsWith("fabric-");
    }

    static Set<String> loadWhitelist(Path file) {
        Set<String> entries = new LinkedHashSet<>();
        if (!Files.isRegularFile(file)) {
            LOGGER.info("Fabric mod whitelist file {} not found; whitelist is empty", file);
            return entries;
        }
        try {
            for (String line : Files.readAllLines(file)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                entries.add(trimmed);
            }
        } catch (IOException error) {
            LOGGER.warn("Failed to read fabric mod whitelist {}; treating as empty: {}", file, error.getMessage());
            return entries;
        }
        return entries;
    }

    static Properties loadProperties(Path baseDir) {
        Properties properties = new Properties();
        Path file = baseDir.resolve(PROPERTIES_FILE);
        if (!Files.isRegularFile(file)) {
            return properties;
        }
        try (var reader = Files.newBufferedReader(file)) {
            properties.load(reader);
        } catch (IOException error) {
            LOGGER.warn("Failed to read {}; using whitelist defaults: {}", file, error.getMessage());
        }
        return properties;
    }

    static boolean parseBoolean(Properties properties, String key, boolean fallback) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return Boolean.parseBoolean(value.trim());
    }

    private static void logSummary(Set<String> whitelist, List<ModInfo> loaded, List<ModInfo> violations) {
        LOGGER.info(
            "Fabric mod whitelist: {} permitted id(s) in file, {} non-core mod(s) loaded, {} violation(s)",
            whitelist.size(),
            loaded.size(),
            violations.size()
        );
    }

    /** Minimal mod metadata snapshot for logging and testing. */
    public record ModInfo(String id, String name, String version) {
    }

    /** Thrown when enforce mode is active and non-whitelisted mods are loaded. */
    public static final class WhitelistViolationException extends RuntimeException {
        public WhitelistViolationException(String message) {
            super(message);
        }
    }
}
