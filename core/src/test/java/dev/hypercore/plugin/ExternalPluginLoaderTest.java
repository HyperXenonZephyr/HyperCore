package dev.hypercore.plugin;

import fixture.external.ValidPlugin;
import fixture.external.FailingPlugin;
import fixture.external.ExampleBukkitPlugin;
import dev.hypercore.plugin.compat.BukkitPluginYmlParser;
import dev.hypercore.bukkit.BukkitServerAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalPluginLoaderTest {
    @TempDir
    Path temporaryDirectory;

    @BeforeEach
    void resetStaticBukkitState() {
        // BukkitPluginAdapter installs a static server singleton; clear it so
        // loader tests do not observe state leaked by BukkitPluginAdapterTest.
        BukkitServerAccess.reset();
        org.bukkit.Bukkit.setServer(null);
    }

    @AfterEach
    void cleanupBukkitDataFolders() {
        // BukkitPluginAdapter creates a best-effort data folder at plugins/<name>
        // (relative to the working directory). Remove BukkitExample debris left by
        // the JavaPlugin integration test so the module tree stays clean.
        deleteRecursively(new File("plugins", "BukkitExample"));
        deleteRecursively(new File("plugins", "ExampleBukkitPlugin"));
        // Remove the parent plugins/ directory if it is now empty.
        File pluginsRoot = new File("plugins");
        if (pluginsRoot.exists() && pluginsRoot.isDirectory() && pluginsRoot.list().length == 0) {
            pluginsRoot.delete();
        }
    }

    @Test
    void loadsHardDependenciesInOrderAndUnregistersOnClose() throws Exception {
        createPluginJar("02-addon.jar", descriptor("addon", "fixture.external.ValidPlugin", List.of("base")));
        createPluginJar("01-base.jar", descriptor("base", "fixture.external.ValidPlugin", List.of()));

        PluginManager manager = new PluginManager();
        try (ExternalPluginLoader loader = new ExternalPluginLoader(manager, temporaryDirectory)) {
            ExternalPluginLoader.LoadReport report = loader.load();

            assertEquals(2, report.discovered());
            assertEquals(2, report.loaded());
            assertEquals(0, report.skipped());
            assertEquals(List.of("base", "addon"), report.loadOrder());
            assertEquals(2, manager.status().externalPlugins());

            manager.enableAll();
            assertEquals(2, manager.status().enabledPlugins());
            manager.scheduler().tick();
            assertEquals(2L, manager.status().completedScheduledTasks());
            assertEquals(0L, manager.status().failedScheduledTasks());
        }

        assertEquals(0, manager.status().registeredPlugins());
        manager.close();
    }

    @Test
    void ordersPresentSoftDependenciesWithoutMakingThemRequired() throws Exception {
        createPluginJar(
            "01-addon.jar",
            descriptor("addon", "fixture.external.ValidPlugin", List.of(), List.of("base"))
        );
        createPluginJar("02-base.jar", descriptor("base", "fixture.external.ValidPlugin", List.of()));
        createPluginJar(
            "03-standalone.jar",
            descriptor("standalone", "fixture.external.ValidPlugin", List.of(), List.of("absent"))
        );

        PluginManager manager = new PluginManager();
        try (ExternalPluginLoader loader = new ExternalPluginLoader(manager, temporaryDirectory)) {
            ExternalPluginLoader.LoadReport report = loader.load();

            assertEquals(3, report.loaded());
            assertTrue(report.loadOrder().indexOf("base") < report.loadOrder().indexOf("addon"));
            assertTrue(report.loadOrder().contains("standalone"));
        }
        manager.close();
    }

    @Test
    void skipsMissingDependenciesAndDependencyCycles() throws Exception {
        createPluginJar("missing.jar", descriptor("missing", "fixture.external.ValidPlugin", List.of("absent")));
        createPluginJar("cycle-a.jar", descriptor("cycle_a", "fixture.external.ValidPlugin", List.of("cycle_b")));
        createPluginJar("cycle-b.jar", descriptor("cycle_b", "fixture.external.ValidPlugin", List.of("cycle_a")));

        PluginManager manager = new PluginManager();
        try (ExternalPluginLoader loader = new ExternalPluginLoader(manager, temporaryDirectory)) {
            ExternalPluginLoader.LoadReport report = loader.load();

            assertEquals(3, report.discovered());
            assertEquals(0, report.loaded());
            assertEquals(3, report.skipped());
            assertTrue(report.errors().stream().anyMatch(error -> error.contains("missing dependency absent")));
            assertEquals(2, report.errors().stream().filter(error -> error.contains("dependency cycle")).count());
        }
        manager.close();
    }

    @Test
    void isolatesAnInvalidMainClassWithoutBlockingValidPlugins() throws Exception {
        createPluginJar("01-invalid.jar", descriptor("invalid", "java.lang.String", List.of()));
        createPluginJar("02-valid.jar", descriptor("valid", "fixture.external.ValidPlugin", List.of()));

        PluginManager manager = new PluginManager();
        try (ExternalPluginLoader loader = new ExternalPluginLoader(manager, temporaryDirectory)) {
            ExternalPluginLoader.LoadReport report = loader.load();

            assertEquals(2, report.discovered());
            assertEquals(1, report.loaded());
            assertEquals(1, report.skipped());
            assertEquals(List.of("valid"), report.loadOrder());
            assertTrue(report.errors().stream().anyMatch(error -> error.contains("implements neither HyperPlugin nor JavaPlugin")));
        }
        manager.close();
    }

    @Test
    void removesLifecycleFailuresAndRejectsDuplicateIds() throws Exception {
        createPluginJar(
            "01-failing.jar",
            descriptor("failing", "fixture.external.FailingPlugin", List.of()),
            FailingPlugin.class
        );
        createPluginJar("02-duplicate-a.jar", descriptor("duplicate", "fixture.external.ValidPlugin", List.of()));
        createPluginJar("03-duplicate-b.jar", descriptor("duplicate", "fixture.external.ValidPlugin", List.of()));

        PluginManager manager = new PluginManager();
        try (ExternalPluginLoader loader = new ExternalPluginLoader(manager, temporaryDirectory)) {
            ExternalPluginLoader.LoadReport report = loader.load();

            assertEquals(1, report.discovered());
            assertEquals(0, report.loaded());
            assertEquals(1, report.skipped());
            assertEquals(0, manager.status().registeredPlugins());
            assertTrue(report.errors().stream().anyMatch(error -> error.contains("duplicate plugin id")));
            assertTrue(report.errors().stream().anyMatch(error -> error.contains("lifecycle ended in failed")));
        }
        manager.close();
    }

    @Test
    void loadsBukkitYmlDescriptor() throws Exception {
        String yml = """
            name: BukkitExample
            version: '1.0.0'
            main: fixture.external.ValidPlugin
            depend: []
            softdepend: []
            """;
        createBukkitPluginJar("01-bukkit.jar", yml);

        PluginManager manager = new PluginManager();
        try (ExternalPluginLoader loader = new ExternalPluginLoader(manager, temporaryDirectory)) {
            ExternalPluginLoader.LoadReport report = loader.load();

            assertEquals(1, report.discovered());
            assertEquals(1, report.loaded());
            assertEquals(0, report.skipped());
            assertEquals(List.of("bukkitexample"), report.loadOrder());
        }
        manager.close();
    }

    @Test
    void loadsJavaPluginFromBukkitJar() throws Exception {
        String yml = """
            name: BukkitExample
            version: '1.0.0'
            main: fixture.external.ExampleBukkitPlugin
            commands:
              greet:
                description: Greet someone
                usage: /greet [name]
            """;
        createBukkitPluginJar("01-bukkit.jar", yml, ExampleBukkitPlugin.class);

        PluginManager manager = new PluginManager();
        List<String> received = new ArrayList<>();
        try (ExternalPluginLoader loader = new ExternalPluginLoader(manager, temporaryDirectory)) {
            ExternalPluginLoader.LoadReport report = loader.load();

            assertEquals(1, report.discovered());
            assertEquals(1, report.loaded());
            assertEquals(0, report.skipped());
            assertEquals(List.of("bukkitexample"), report.loadOrder());

            manager.enableAll();
            assertEquals(1, manager.status().enabledPlugins());
            assertEquals(1, manager.status().registeredCommands());

            PluginCommandSender sender = capturingSender("tester", received);
            PluginCommandRegistry.DispatchResult result =
                manager.commands().dispatch("greet", List.of("HyperCore"), sender);

            assertEquals(PluginCommandRegistry.DispatchStatus.EXECUTED, result.status());
            assertTrue(result.success());
            assertEquals(List.of("Hello, HyperCore!"), received);
        }
        manager.close();
    }

    private static PluginCommandSender capturingSender(String name, List<String> sink) {
        return new PluginCommandSender() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public boolean operator() {
                return true;
            }

            @Override
            public void sendMessage(String message) {
                sink.add(message);
            }
        };
    }

    @Test
    void prefersHyperCoreJsonWhenBothPresent() throws Exception {
        String json = descriptor("fromjson", "fixture.external.ValidPlugin", List.of());
        String yml = """
            name: FromYml
            version: '1.0.0'
            main: fixture.external.ValidPlugin
            """;
        createDualDescriptorJar("01-dual.jar", json, yml);

        PluginManager manager = new PluginManager();
        try (ExternalPluginLoader loader = new ExternalPluginLoader(manager, temporaryDirectory)) {
            ExternalPluginLoader.LoadReport report = loader.load();

            assertEquals(1, report.loaded());
            // hypercore-plugin.json takes precedence, so the id is "fromjson" not "fromyml"
            assertEquals(List.of("fromjson"), report.loadOrder());
        }
        manager.close();
    }

    private void createPluginJar(String fileName, String descriptor) throws IOException {
        createPluginJar(fileName, descriptor, ValidPlugin.class);
    }

    private void createPluginJar(String fileName, String descriptor, Class<?> pluginClass) throws IOException {
        Path jar = temporaryDirectory.resolve(fileName);
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry(ExternalPluginLoader.DESCRIPTOR_ENTRY));
            output.write(descriptor.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();

            String classEntry = pluginClass.getName().replace('.', '/') + ".class";
            output.putNextEntry(new JarEntry(classEntry));
            try (InputStream classBytes = pluginClass.getClassLoader().getResourceAsStream(classEntry)) {
                if (classBytes == null) {
                    throw new IOException("Missing test fixture class: " + classEntry);
                }
                classBytes.transferTo(output);
            }
            output.closeEntry();
        }
    }

    private static String descriptor(String id, String mainClass, List<String> depends) {
        return descriptor(id, mainClass, depends, List.of());
    }

    private static String descriptor(
        String id,
        String mainClass,
        List<String> depends,
        List<String> softDepends
    ) {
        String dependencies = depends.stream()
            .map(dependency -> "\"" + dependency + "\"")
            .collect(java.util.stream.Collectors.joining(", "));
        String optionalDependencies = softDepends.stream()
            .map(dependency -> "\"" + dependency + "\"")
            .collect(java.util.stream.Collectors.joining(", "));
        return """
            {
              "id": "%s",
              "name": "%s",
              "version": "1.0.0",
              "apiVersion": 1,
              "main": "%s",
              "depends": [%s],
              "softDepends": [%s]
            }
            """.formatted(id, id, mainClass, dependencies, optionalDependencies);
    }

    private void createBukkitPluginJar(String fileName, String yml) throws IOException {
        createBukkitPluginJar(fileName, yml, ValidPlugin.class);
    }

    private void createBukkitPluginJar(String fileName, String yml, Class<?> pluginClass) throws IOException {
        Path jar = temporaryDirectory.resolve(fileName);
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry(BukkitPluginYmlParser.DESCRIPTOR_ENTRY));
            output.write(yml.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();

            String classEntry = pluginClass.getName().replace('.', '/') + ".class";
            output.putNextEntry(new JarEntry(classEntry));
            try (InputStream classBytes = pluginClass.getClassLoader().getResourceAsStream(classEntry)) {
                if (classBytes == null) {
                    throw new IOException("Missing test fixture class: " + classEntry);
                }
                classBytes.transferTo(output);
            }
            output.closeEntry();
        }
    }

    private void createDualDescriptorJar(String fileName, String json, String yml) throws IOException {
        createDualDescriptorJar(fileName, json, yml, ValidPlugin.class);
    }

    private void createDualDescriptorJar(String fileName, String json, String yml, Class<?> pluginClass) throws IOException {
        Path jar = temporaryDirectory.resolve(fileName);
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry(ExternalPluginLoader.DESCRIPTOR_ENTRY));
            output.write(json.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();

            output.putNextEntry(new JarEntry(BukkitPluginYmlParser.DESCRIPTOR_ENTRY));
            output.write(yml.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();

            String classEntry = pluginClass.getName().replace('.', '/') + ".class";
            output.putNextEntry(new JarEntry(classEntry));
            try (InputStream classBytes = pluginClass.getClassLoader().getResourceAsStream(classEntry)) {
                if (classBytes == null) {
                    throw new IOException("Missing test fixture class: " + classEntry);
                }
                classBytes.transferTo(output);
            }
            output.closeEntry();
        }
    }

    private static void deleteRecursively(File file) {
        if (!file.exists()) {
            return;
        }
        try (var stream = Files.walk(file.toPath())) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (FileSystemException ignored) {
                    // File may still be locked on Windows; best-effort cleanup.
                } catch (Exception error) {
                    throw new RuntimeException("Could not delete " + path, error);
                }
            });
        } catch (Exception ignored) {
            // Best-effort cleanup; do not fail the test on teardown errors.
        }
    }
}
