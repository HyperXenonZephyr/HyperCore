package dev.hypercore.gametest;

import dev.hypercore.HyperCore;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.gametest.GameTestHolder;

@GameTestHolder(HyperCore.MOD_ID)
public final class HyperCoreGameTests {
    private HyperCoreGameTests() {
    }

    @GameTest(template = "forge:empty3x3x3")
    public static void serverLoadsHyperCore(GameTestHelper helper) {
        helper.succeed();
    }

    @GameTest(template = "forge:empty3x3x3")
    public static void hyperCoreStatusCommand(GameTestHelper helper) {
        runCommand(helper, "hypercore status");
        helper.succeed();
    }

    @GameTest(template = "forge:empty3x3x3")
    public static void hyperCoreCapabilitiesCommand(GameTestHelper helper) {
        runCommand(helper, "hypercore capabilities");
        helper.succeed();
    }

    @GameTest(template = "forge:empty3x3x3")
    public static void hyperCorePluginsCommand(GameTestHelper helper) {
        runCommand(helper, "hypercore plugins");
        helper.succeed();
    }

    @GameTest(template = "forge:empty3x3x3")
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
