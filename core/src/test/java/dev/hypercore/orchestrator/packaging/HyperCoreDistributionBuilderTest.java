package dev.hypercore.orchestrator.packaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the Fabric mod whitelist gating logic in
 * {@link HyperCoreDistributionBuilder}.
 */
class HyperCoreDistributionBuilderTest {

    @Test
    void loadWhitelistParsesModIdsAndIgnoresComments(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("whitelist.txt");
        Files.writeString(file, "# comment\n\nlithium\n  ferritecore  \nkrypton\n");
        Set<String> result = HyperCoreDistributionBuilder.loadWhitelist(file);
        assertEquals(Set.of("lithium", "ferritecore", "krypton"), result);
    }

    @Test
    void loadWhitelistReturnsEmptyForMissingFile(@TempDir Path tempDir) throws IOException {
        Set<String> result = HyperCoreDistributionBuilder.loadWhitelist(tempDir.resolve("nonexistent.txt"));
        assertTrue(result.isEmpty());
    }

    @Test
    void isCoreModRecognizesInfrastructureMods() {
        assertTrue(HyperCoreDistributionBuilder.isCoreMod("fabricloader"));
        assertTrue(HyperCoreDistributionBuilder.isCoreMod("minecraft"));
        assertTrue(HyperCoreDistributionBuilder.isCoreMod("java"));
        assertTrue(HyperCoreDistributionBuilder.isCoreMod("hypercore"));
        assertTrue(HyperCoreDistributionBuilder.isCoreMod("mixinextras"));
        assertTrue(HyperCoreDistributionBuilder.isCoreMod("com_velocitypowered_velocity-native"));
        assertTrue(HyperCoreDistributionBuilder.isCoreMod("fabric-api"));
        assertTrue(HyperCoreDistributionBuilder.isCoreMod("fabric-lifecycle-events-v1"));
    }

    @Test
    void isCoreModRejectsThirdPartyMods() {
        assertFalse(HyperCoreDistributionBuilder.isCoreMod("lithium"));
        assertFalse(HyperCoreDistributionBuilder.isCoreMod("sodium"));
    }

    @Test
    void extractJsonStringFieldFindsId() {
        String json = "{\"schemaVersion\":1,\"id\":\"lithium\",\"name\":\"Lithium\"}";
        assertEquals("lithium", HyperCoreDistributionBuilder.extractJsonStringField(json, "id"));
        assertEquals("Lithium", HyperCoreDistributionBuilder.extractJsonStringField(json, "name"));
    }

    @Test
    void extractJsonStringFieldReturnsNullForMissingField() {
        String json = "{\"id\":\"lithium\"}";
        assertNull(HyperCoreDistributionBuilder.extractJsonStringField(json, "version"));
    }

    @Test
    void readFabricModIdFromJar(@TempDir Path tempDir) throws IOException {
        Path jar = tempDir.resolve("test-mod.jar");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(jar))) {
            zos.putNextEntry(new ZipEntry("fabric.mod.json"));
            zos.write("{\"schemaVersion\":1,\"id\":\"lithium\",\"name\":\"Lithium\"}".getBytes());
            zos.closeEntry();
        }
        assertEquals("lithium", HyperCoreDistributionBuilder.readFabricModId(jar));
    }

    @Test
    void readFabricModIdReturnsNullForNonFabricJar(@TempDir Path tempDir) throws IOException {
        Path jar = tempDir.resolve("not-a-mod.jar");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(jar))) {
            zos.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            zos.write("Manifest-Version: 1.0\n".getBytes());
            zos.closeEntry();
        }
        assertNull(HyperCoreDistributionBuilder.readFabricModId(jar));
    }

    @Test
    void filterFabricModsRemovesNonWhitelistedJars(@TempDir Path tempDir) throws IOException {
        Path modsDir = tempDir.resolve("mods");
        Files.createDirectories(modsDir);

        // Whitelisted mod JAR
        createFakeModJar(modsDir.resolve("lithium.jar"), "lithium");
        // Non-whitelisted mod JAR
        createFakeModJar(modsDir.resolve("badmod.jar"), "badmod");
        // HyperCore's own JAR (always kept)
        Files.writeString(modsDir.resolve("hypercore-fabric.jar"), "dummy");

        Path whitelist = tempDir.resolve("whitelist.txt");
        Files.writeString(whitelist, "lithium\n");

        HyperCoreDistributionBuilder.filterFabricMods(modsDir, whitelist);

        assertTrue(Files.exists(modsDir.resolve("lithium.jar")), "whitelisted mod kept");
        assertTrue(Files.exists(modsDir.resolve("hypercore-fabric.jar")), "hypercore jar kept");
        assertFalse(Files.exists(modsDir.resolve("badmod.jar")), "non-whitelisted mod removed");
    }

    @Test
    void filterFabricModsKeepsCoreMods(@TempDir Path tempDir) throws IOException {
        Path modsDir = tempDir.resolve("mods");
        Files.createDirectories(modsDir);

        createFakeModJar(modsDir.resolve("fabric-api.jar"), "fabric-api");
        createFakeModJar(modsDir.resolve("random-mod.jar"), "random-mod");

        Path whitelist = tempDir.resolve("whitelist.txt");
        Files.writeString(whitelist, "# empty\n");

        HyperCoreDistributionBuilder.filterFabricMods(modsDir, whitelist);

        assertTrue(Files.exists(modsDir.resolve("fabric-api.jar")), "core mod kept");
        assertFalse(Files.exists(modsDir.resolve("random-mod.jar")), "non-whitelisted mod removed");
    }

    private static void createFakeModJar(Path path, String modId) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(path))) {
            zos.putNextEntry(new ZipEntry("fabric.mod.json"));
            zos.write(("{\"id\":\"" + modId + "\"}").getBytes());
            zos.closeEntry();
        }
    }
}
