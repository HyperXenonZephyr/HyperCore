package dev.hypercore.plugin;

import fixture.external.ValidPlugin;
import fixture.external.FailingPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalPluginLoaderTest {
    @TempDir
    Path temporaryDirectory;

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
            assertTrue(report.errors().stream().anyMatch(error -> error.contains("does not implement HyperPlugin")));
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
}
