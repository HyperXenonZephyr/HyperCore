package dev.hypercore.bukkit;

import dev.hypercore.plugin.PluginCommandRegistry;
import dev.hypercore.plugin.PluginCommandSender;
import dev.hypercore.plugin.PluginDescriptor;
import dev.hypercore.plugin.PluginManager;
import fixture.external.ExampleBukkitPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Direct unit tests for {@link BukkitPluginAdapter}. Each test constructs an
 * {@link ExampleBukkitPlugin}, wraps it in a {@link BukkitPluginAdapter}, and
 * registers the adapter with a fresh {@link PluginManager}, exercising the full
 * Bukkit → HyperCore bridge: lifecycle, command registration/dispatch, and sync
 * task scheduling through the {@code Bukkit.getScheduler()} shim.
 */
class BukkitPluginAdapterTest {
    private static final String PLUGIN_NAME = "ExampleBukkitPlugin";
    private static final String PLUGIN_ID = "examplebukkitplugin";

    private final List<File> dataFolders = new ArrayList<>();

    @BeforeEach
    void resetStaticBukkitState() {
        // Each test constructs a fresh PluginManager; clear the shared Bukkit
        // server singleton so the next adapter installs a server backed by the
        // correct manager.
        BukkitServerAccess.reset();
        org.bukkit.Bukkit.setServer(null);
    }

    @AfterEach
    void cleanupDataFolders() {
        // The adapter creates a best-effort data folder at plugins/<name>;
        // remove it so tests do not leave debris in the working directory.
        File dataFolder = new File("plugins", PLUGIN_NAME);
        if (dataFolder.exists()) {
            dataFolders.add(dataFolder);
        }
        for (File folder : dataFolders) {
            deleteRecursively(folder);
        }
        dataFolders.clear();
        // Remove the parent plugins/ directory if it is now empty.
        File pluginsRoot = new File("plugins");
        if (pluginsRoot.exists() && pluginsRoot.isDirectory() && pluginsRoot.list().length == 0) {
            pluginsRoot.delete();
        }
    }

    @Test
    void runsBukkitLifecycleThroughAdapter() {
        PluginManager manager = new PluginManager();
        ExampleBukkitPlugin bukkitPlugin = new ExampleBukkitPlugin();
        BukkitPluginAdapter adapter = newAdapter(bukkitPlugin, manager);

        manager.register(new PluginDescriptor(PLUGIN_ID, PLUGIN_NAME, "1.0"), adapter);

        manager.enableAll();
        assertEquals(List.of("load", "enable"), bukkitPlugin.lifecycle);
        assertTrue(bukkitPlugin.isEnabled(), "JavaPlugin should be enabled after enableAll");

        manager.close();
        assertEquals(List.of("load", "enable", "disable"), bukkitPlugin.lifecycle);
        assertFalse(bukkitPlugin.isEnabled(), "JavaPlugin should be disabled after close");
    }

    @Test
    void injectsBukkitApiStubsIntoJavaPlugin() {
        PluginManager manager = new PluginManager();
        ExampleBukkitPlugin bukkitPlugin = new ExampleBukkitPlugin();
        BukkitPluginAdapter adapter = newAdapter(bukkitPlugin, manager);

        manager.register(new PluginDescriptor(PLUGIN_ID, PLUGIN_NAME, "1.0"), adapter);
        manager.enableAll();

        // onLoad already asserted non-null server/logger/name/dataFolder; here we
        // verify the server reports HyperCore and getCommand returns the yml command.
        assertEquals("HyperCore", bukkitPlugin.getServer().getName());
        assertEquals(PLUGIN_NAME, bukkitPlugin.getName());
        assertNotNull(bukkitPlugin.getCommand("greet"), "greet command should be available via getCommand");

        manager.close();
    }

    @Test
    void dispatchesBukkitCommandViaRegistry() {
        PluginManager manager = new PluginManager();
        ExampleBukkitPlugin bukkitPlugin = new ExampleBukkitPlugin();
        BukkitPluginAdapter adapter = newAdapter(bukkitPlugin, manager);

        manager.register(new PluginDescriptor(PLUGIN_ID, PLUGIN_NAME, "1.0"), adapter);
        manager.enableAll();

        List<String> received = new ArrayList<>();
        PluginCommandSender sender = capturingSender("tester", true, received);

        PluginCommandRegistry.DispatchResult result =
            manager.commands().dispatch("greet", List.of("HyperCore"), sender);

        assertEquals(PluginCommandRegistry.DispatchStatus.EXECUTED, result.status());
        assertTrue(result.success());
        assertEquals(List.of("HyperCore"), bukkitPlugin.greetings);
        assertEquals(List.of("Hello, HyperCore!"), received);

        manager.close();
    }

    @Test
    void dispatchesBukkitCommandWithDefaultArgument() {
        PluginManager manager = new PluginManager();
        ExampleBukkitPlugin bukkitPlugin = new ExampleBukkitPlugin();
        BukkitPluginAdapter adapter = newAdapter(bukkitPlugin, manager);

        manager.register(new PluginDescriptor(PLUGIN_ID, PLUGIN_NAME, "1.0"), adapter);
        manager.enableAll();

        List<String> received = new ArrayList<>();
        PluginCommandSender sender = capturingSender("tester", true, received);

        manager.commands().dispatch("greet", List.of(), sender);

        assertEquals(List.of("world"), bukkitPlugin.greetings);
        assertEquals(List.of("Hello, world!"), received);

        manager.close();
    }

    @Test
    void schedulesSyncTaskViaBukkitScheduler() {
        PluginManager manager = new PluginManager();
        ExampleBukkitPlugin bukkitPlugin = new ExampleBukkitPlugin();
        BukkitPluginAdapter adapter = newAdapter(bukkitPlugin, manager);

        manager.register(new PluginDescriptor(PLUGIN_ID, PLUGIN_NAME, "1.0"), adapter);
        manager.enableAll();

        // onEnable scheduled a sync task; it fires on the next scheduler tick.
        assertFalse(bukkitPlugin.taskRan, "task should not run before a tick");
        manager.scheduler().tick();

        assertTrue(bukkitPlugin.taskRan, "sync task should run after one tick");
        assertEquals(List.of("load", "enable", "tick"), bukkitPlugin.lifecycle);
        assertEquals(1L, manager.status().completedScheduledTasks());
        assertEquals(0L, manager.status().failedScheduledTasks());

        manager.close();
    }

    @Test
    void commandRegistrationSurvivesDisableCleanup() {
        PluginManager manager = new PluginManager();
        ExampleBukkitPlugin bukkitPlugin = new ExampleBukkitPlugin();
        BukkitPluginAdapter adapter = newAdapter(bukkitPlugin, manager);

        manager.register(new PluginDescriptor(PLUGIN_ID, PLUGIN_NAME, "1.0"), adapter);
        manager.enableAll();
        assertEquals(1, manager.status().registeredCommands());

        manager.close();
        assertEquals(0, manager.status().registeredCommands());
    }

    @Test
    void exposesPluginForCrossPluginLookup() {
        PluginManager manager = new PluginManager();
        ExampleBukkitPlugin bukkitPlugin = new ExampleBukkitPlugin();
        BukkitPluginAdapter adapter = newAdapter(bukkitPlugin, manager);

        manager.register(new PluginDescriptor(PLUGIN_ID, PLUGIN_NAME, "1.0"), adapter);
        manager.enableAll();

        org.bukkit.plugin.Plugin found = bukkitPlugin.getServer().getPluginManager().getPlugin(PLUGIN_NAME);
        assertEquals(bukkitPlugin, found, "Bukkit PluginManager should return the wrapped JavaPlugin by name");
        assertEquals(bukkitPlugin, org.bukkit.Bukkit.getPluginManager().getPlugin(PLUGIN_NAME));

        manager.close();
    }

    private static BukkitPluginAdapter newAdapter(ExampleBukkitPlugin plugin, PluginManager manager) {
        Map<String, Map<String, Object>> commands = Map.of(
            "greet", Map.of("description", "Greet someone", "usage", "/greet [name]")
        );
        return new BukkitPluginAdapter(plugin, PLUGIN_NAME, commands, manager);
    }

    private static PluginCommandSender capturingSender(
        String name, boolean operator, List<String> sink
    ) {
        return new PluginCommandSender() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public boolean operator() {
                return operator;
            }

            @Override
            public void sendMessage(String message) {
                sink.add(message);
            }
        };
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
                    fail("Could not delete " + path + ": " + error.getMessage());
                }
            });
        } catch (Exception error) {
            fail("Could not walk " + file + " for cleanup: " + error.getMessage());
        }
    }
}
