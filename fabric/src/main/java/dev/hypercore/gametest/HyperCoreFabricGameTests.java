package dev.hypercore.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.hypercore.HyperCoreFabric;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;

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

    @GameTest(template = EMPTY_STRUCTURE)
    public static void bukkitWorldBlockAndEntityApis(GameTestHelper helper) {
        BlockPos blockPos = helper.absolutePos(new BlockPos(1, 1, 1));
        runCommand(helper, "hypercore-gametest block " + blockPos.getX() + " " + blockPos.getY() + " " + blockPos.getZ());

        Vec3 entityPos = helper.absoluteVec(new Vec3(1.5, 2.0, 1.5));
        runCommand(helper, "hypercore-gametest entity " + entityPos.x() + " " + entityPos.y() + " " + entityPos.z());
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public static void bukkitInventoryApi(GameTestHelper helper) {
        BlockPos blockPos = helper.absolutePos(new BlockPos(1, 1, 1));
        runCommand(helper, "hypercore-gametest inventory " + blockPos.getX() + " " + blockPos.getY() + " " + blockPos.getZ());
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public static void bukkitEventBridge(GameTestHelper helper) {
        BlockPos blockPos = helper.absolutePos(new BlockPos(1, 1, 1));
        runCommand(helper, "hypercore-gametest event " + blockPos.getX() + " " + blockPos.getY() + " " + blockPos.getZ());
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public static void regionParallelExecution(GameTestHelper helper) {
        runCommand(helper, "hypercore-gametest parallel 4");
        helper.succeed();
    }

    private static void runCommand(GameTestHelper helper, String command) {
        MinecraftServer server = helper.getLevel().getServer();
        CommandSourceStack source = server.createCommandSourceStack().withPermission(2);
        try {
            server.getCommands().getDispatcher().execute(command, source);
        } catch (CommandSyntaxException error) {
            throw new AssertionError("Command failed: " + command + " - " + error.getMessage(), error);
        }
    }
}
