package dev.hypercore.gametest;

import dev.hypercore.HyperCoreFabric;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;

/**
 * Fabric GameTest that mirrors the Forge {@code HyperCoreGameTests}: verifies
 * that HyperCore loads in a real dedicated-server environment and that the
 * {@code /hypercore} command tree is registered and callable.
 *
 * <p>Registered through the {@code fabric-gametest} entrypoint in
 * {@code fabric.mod.json} — Fabric does not use {@code @GameTestHolder}; the
 * entrypoint class is scanned for {@code @GameTest} methods directly. The
 * {@code gameTestServer} Loom run config already exists in
 * {@code fabric/build.gradle}; this class supplies the tests it runs.
 */
public final class HyperCoreFabricGameTests implements FabricGameTest {
    public HyperCoreFabricGameTests() {
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public static void serverLoadsHyperCore(GameTestHelper helper) {
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public static void hyperCoreStatusCommand(GameTestHelper helper) {
        runCommand(helper, "hypercore status");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public static void hyperCoreCapabilitiesCommand(GameTestHelper helper) {
        runCommand(helper, "hypercore capabilities");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public static void hyperCorePluginsCommand(GameTestHelper helper) {
        runCommand(helper, "hypercore plugins");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public static void bukkitPluginLoadsAndCommandExecutes(GameTestHelper helper) {
        // The gametest Bukkit plugin registers /hypercore-gametest. If the plugin
        // failed to load from run/plugins, this command will not exist and the
        // prefixed execution will throw, failing the GameTest.
        runCommand(helper, "hypercore-gametest");
        helper.succeed();
    }

    private static void runCommand(GameTestHelper helper, String command) {
        MinecraftServer server = helper.getLevel().getServer();
        server.getCommands().performPrefixedCommand(
            server.createCommandSourceStack().withPermission(2),
            command
        );
    }
}
