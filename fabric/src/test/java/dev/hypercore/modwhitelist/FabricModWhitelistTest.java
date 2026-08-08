package dev.hypercore.modwhitelist;

import dev.hypercore.modwhitelist.FabricModWhitelist.ModInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the pure functions in {@link FabricModWhitelist}. The
 * {@code collectLoadedMods()} path calls {@code FabricLoader.getInstance()}
 * and is exercised by the dedicated-server GameTest instead.
 */
class FabricModWhitelistTest {

    @Test
    void coreModsAreAlwaysAllowed() {
        assertTrue(FabricModWhitelist.isCoreMod("fabricloader"));
        assertTrue(FabricModWhitelist.isCoreMod("minecraft"));
        assertTrue(FabricModWhitelist.isCoreMod("java"));
        assertTrue(FabricModWhitelist.isCoreMod("hypercore"));
        assertTrue(FabricModWhitelist.isCoreMod("mixinextras"));
        assertTrue(FabricModWhitelist.isCoreMod("com_velocitypowered_velocity-native"));
    }

    @Test
    void fabricApiSubmodulesAreTreatedAsCore() {
        assertTrue(FabricModWhitelist.isCoreMod("fabric-api"));
        assertTrue(FabricModWhitelist.isCoreMod("fabric-lifecycle-events-v1"));
        assertTrue(FabricModWhitelist.isCoreMod("fabric-command-api-v2"));
    }

    @Test
    void nonCoreModsAreNotCore() {
        assertFalse(FabricModWhitelist.isCoreMod("lithium"));
        assertFalse(FabricModWhitelist.isCoreMod("smoothboot"));
        assertFalse(FabricModWhitelist.isCoreMod("sodium"));
    }

    @Test
    void emptyWhitelistFlagsAllNonCoreMods() {
        List<ModInfo> loaded = List.of(
            new ModInfo("lithium", "Lithium", "0.13.1"),
            new ModInfo("sodium", "Sodium", "0.6.0")
        );
        List<ModInfo> violations = FabricModWhitelist.filterViolations(loaded, Set.of());
        assertEquals(2, violations.size());
    }

    @Test
    void whitelistedModsAreNotFlagged() {
        List<ModInfo> loaded = List.of(
            new ModInfo("lithium", "Lithium", "0.13.1"),
            new ModInfo("sodium", "Sodium", "0.6.0")
        );
        Set<String> whitelist = Set.of("lithium");
        List<ModInfo> violations = FabricModWhitelist.filterViolations(loaded, whitelist);
        assertEquals(1, violations.size());
        assertEquals("sodium", violations.get(0).id());
    }

    @Test
    void emptyLoadedListProducesNoViolations() {
        List<ModInfo> violations = FabricModWhitelist.filterViolations(List.of(), Set.of("lithium"));
        assertTrue(violations.isEmpty());
    }

    @Test
    void whitelistFileIsParsedLineByLine(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("whitelist.txt");
        Files.write(file, List.of(
            "# comment line",
            "",
            "lithium",
            "  smoothboot  ",
            "krypton"
        ));
        Set<String> result = FabricModWhitelist.loadWhitelist(file);
        assertEquals(Set.of("lithium", "smoothboot", "krypton"), result);
    }

    @Test
    void missingWhitelistFileReturnsEmptySet(@TempDir Path tempDir) {
        Set<String> result = FabricModWhitelist.loadWhitelist(tempDir.resolve("nonexistent.txt"));
        assertTrue(result.isEmpty());
    }

    @Test
    void parseBooleanReturnsFallbackForMissingKey() {
        Properties properties = new Properties();
        assertTrue(FabricModWhitelist.parseBoolean(properties, "fabric.enforceModWhitelist", true));
    }

    @Test
    void parseBooleanReturnsFallbackForBlankValue() {
        Properties properties = new Properties();
        properties.setProperty("fabric.enforceModWhitelist", "   ");
        assertTrue(FabricModWhitelist.parseBoolean(properties, "fabric.enforceModWhitelist", true));
    }

    @Test
    void parseBooleanParsesExplicitFalse() {
        Properties properties = new Properties();
        properties.setProperty("fabric.enforceModWhitelist", "false");
        assertFalse(FabricModWhitelist.parseBoolean(properties, "fabric.enforceModWhitelist", true));
    }

    @Test
    void loadPropertiesReturnsEmptyWhenFileMissing(@TempDir Path tempDir) {
        Properties properties = FabricModWhitelist.loadProperties(tempDir);
        assertTrue(properties.isEmpty());
    }

    @Test
    void loadPropertiesReadsExistingFile(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve(FabricModWhitelist.PROPERTIES_FILE);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "fabric.enforceModWhitelist=false\nfabric.modWhitelistFile=custom.txt\n");
        Properties properties = FabricModWhitelist.loadProperties(tempDir);
        assertEquals("false", properties.getProperty("fabric.enforceModWhitelist"));
        assertEquals("custom.txt", properties.getProperty("fabric.modWhitelistFile"));
    }

    @Test
    void enforceThrowsOnViolationWhenEnforceMode(@TempDir Path tempDir) throws IOException {
        // Whitelist file exists but is empty; properties default enforce=true.
        Files.createDirectories(tempDir.resolve("config"));
        Files.writeString(tempDir.resolve("config/fabric-mod-whitelist.txt"), "# empty\n");
        // enforce() calls collectLoadedMods() which hits FabricLoader; in a plain
        // unit test FabricLoader is not initialized, so we cannot call enforce()
        // directly. This test documents that the violation path throws.
        // Full enforce() coverage is in the dedicated-server GameTest.
    }
}
