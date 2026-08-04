package dev.hypercore.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.hypercore.HyperCoreFabric;
import dev.hypercore.bridge.BridgeHostConfig;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;

/**
 * Fabric-side coexistence GameTests. These verify the cross-host bridge when
 * the process runs as an orchestrated {@code FABRIC_HOST}:
 * <ul>
 *   <li>{@code crossProcessBlockSyncOnFabric} observes a block placed on the
 *       Forge host after it crosses the bridge;</li>
 *   <li>{@code crossProcessEntityMoveFromFabric} spawns and moves an entity
 *       whose deltas are mirrored onto the Forge host (verified there);</li>
 *   <li>{@code crossProcessCommandExecutionOnFabric} executes a mirrored Forge
 *       command through the bridge.</li>
 * </ul>
 *
 * <p>All polling uses asynchronous GameTest sequences so the server thread keeps
 * ticking. In standalone mode every test succeeds immediately so existing
 * single-loader GameTest runs stay green.
 */
public final class HyperCoreFabricCoexistenceGameTests implements FabricGameTest {
    /** Fixed logical coordinates shared by both hosts. */
    private static final int BLOCK_X = 10_011;
    private static final int BLOCK_Y = 64;
    private static final int BLOCK_Z = 10_011;
    private static final int ENTITY_X = 10_031;
    private static final int ENTITY_Y = 64;
    private static final int ENTITY_Z = 10_031;
    private static final int ENTITY_MOVE_Z = 10_032;

    public HyperCoreFabricCoexistenceGameTests() {
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public static void crossProcessBlockSyncOnFabric(GameTestHelper helper) {
        if (!bridged()) {
            helper.succeed();
            return;
        }
        // The Forge-side crossProcessBlockSyncFromForge test places this block;
        // poll until its delta has been mirrored here within the bridge budget.
        helper.startSequence()
            .thenWaitUntil(() -> {
                BlockPos pos = new BlockPos(BLOCK_X, BLOCK_Y, BLOCK_Z);
                // Force the chunk to exist before reading the mirrored block.
                helper.getLevel().getChunkAt(pos);
                if (helper.getLevel().getBlockState(pos).getBlock() != Blocks.STONE) {
                    throw new GameTestAssertException("Block placed on the Forge host has not been mirrored here yet");
                }
            })
            .thenSucceed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public static void crossProcessEntityMoveFromFabric(GameTestHelper helper) {
        if (!bridged()) {
            helper.succeed();
            return;
        }
        // Spawn and move an entity; the deltas must be mirrored onto the Forge
        // host, which verifies the position in crossProcessEntityMoveOnForge.
        runCommand(helper, "hypercore-coexistence spawn " + ENTITY_X + " " + ENTITY_Y + " " + ENTITY_Z);
        runCommand(helper, "hypercore-coexistence move " + ENTITY_X + " " + ENTITY_Y + " " + ENTITY_MOVE_Z);
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public static void crossProcessCommandExecutionOnFabric(GameTestHelper helper) {
        if (!bridged()) {
            helper.succeed();
            return;
        }
        // Wait for the Forge host's command snapshot to be mirrored, then invoke
        // the mirrored command so it executes on the Forge host.
        helper.startSequence()
            .thenWaitUntil(() -> {
                if (helper.getLevel().getServer().getCommands().getDispatcher().getRoot().getChild("xforge_hypercore-coexistence") == null) {
                    throw new GameTestAssertException("Waiting for the mirrored Forge command");
                }
            })
            .thenExecute(() -> runCommand(helper, "xforge_hypercore-coexistence forgeonly"))
            .thenSucceed();
    }

    private static boolean bridged() {
        return BridgeHostConfig.fromSystemProperties() != null;
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
