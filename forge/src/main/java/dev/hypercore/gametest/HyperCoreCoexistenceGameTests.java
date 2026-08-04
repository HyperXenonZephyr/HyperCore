package dev.hypercore.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.hypercore.HyperCore;
import dev.hypercore.bridge.BridgeHostConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;

/**
 * Forge-side coexistence GameTests. These verify the cross-host bridge when the
 * process runs as an orchestrated {@code FORGE_HOST}:
 * <ul>
 *   <li>{@code crossProcessBlockSyncFromForge} places a block whose delta must
 *       be mirrored onto the Fabric host (verified by the Fabric-side test);</li>
 *   <li>{@code crossProcessEntityMoveOnForge} observes a zombie that was spawned
 *       and moved on the Fabric host after its deltas crossed the bridge;</li>
 *   <li>{@code crossProcessCommandExecution} observes a command that the Fabric
 *       host executed here through the bridge (signalled by a marker block);</li>
 *   <li>{@code crossProcessEventPropagation} observes a block break veto that
 *       originated on the Fabric host and was reflected back here (signalled by
 *       a marker block).</li>
 * </ul>
 *
 * <p>All polling uses asynchronous GameTest sequences ({@code thenWaitUntil}) so
 * the server thread keeps ticking and the bridge keeps flushing. Assertions are
 * world-based (mirrored blocks and marker blocks) so the tests never depend on
 * sharing Java state across processes. In standalone mode (no orchestrator)
 * every test succeeds immediately so existing single-loader GameTest runs stay
 * green.
 */
@GameTestHolder(HyperCore.MOD_ID)
public final class HyperCoreCoexistenceGameTests {
    /** Fixed logical coordinates shared by both hosts. */
    private static final int BLOCK_X = 10_011;
    private static final int BLOCK_Y = 64;
    private static final int BLOCK_Z = 10_011;
    private static final int EVENT_X = 10_021;
    private static final int EVENT_Z = 10_021;
    private static final int ENTITY_X = 10_031;
    private static final int ENTITY_Y = 64;
    private static final int ENTITY_Z = 10_031;
    private static final int ENTITY_MOVE_Z = 10_032;
    private static final int FORGE_MARKER_X = 10_041;
    private static final int FORGE_MARKER_Y = 64;
    private static final int FORGE_MARKER_Z = 10_041;
    private static final int CANCEL_MARKER_X = 10_051;
    private static final int CANCEL_MARKER_Y = 64;
    private static final int CANCEL_MARKER_Z = 10_051;

    private HyperCoreCoexistenceGameTests() {
    }

    @GameTest(template = "forge:empty3x3x3")
    public static void crossProcessBlockSyncFromForge(GameTestHelper helper) {
        if (!bridged()) {
            helper.succeed();
            return;
        }
        runCommand(helper, "hypercore-coexistence set " + BLOCK_X + " " + BLOCK_Y + " " + BLOCK_Z + " STONE");
        // The Fabric-side crossProcessBlockSyncOnFabric test asserts the mirror.
        helper.succeed();
    }

    @GameTest(template = "forge:empty3x3x3")
    public static void crossProcessEntityMoveOnForge(GameTestHelper helper) {
        if (!bridged()) {
            helper.succeed();
            return;
        }
        // The Fabric-side test spawns and moves a zombie; wait until a zombie
        // sits at the mirrored destination position on this host.
        helper.startSequence()
            .thenWaitUntil(() -> {
                BlockPos target = new BlockPos(ENTITY_X, ENTITY_Y, ENTITY_MOVE_Z);
                boolean found = false;
                for (net.minecraft.world.entity.Entity entity : helper.getLevel().getAllEntities()) {
                    if (entity.getType() == net.minecraft.world.entity.EntityType.ZOMBIE
                        && entity.blockPosition().getX() == target.getX()
                        && Math.abs(entity.blockPosition().getY() - target.getY()) <= 2
                        && entity.blockPosition().getZ() == target.getZ()) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    throw new GameTestAssertException("Zombie moved on the Fabric host has not been mirrored here yet");
                }
            })
            .thenSucceed();
    }

    @GameTest(template = "forge:empty3x3x3")
    public static void crossProcessCommandExecution(GameTestHelper helper) {
        if (!bridged()) {
            helper.succeed();
            return;
        }
        // The Fabric-side test invokes the mirrored command, which executes here
        // and places a marker block; wait until the marker appears.
        helper.startSequence()
            .thenWaitUntil(() -> {
                BlockPos marker = new BlockPos(FORGE_MARKER_X, FORGE_MARKER_Y, FORGE_MARKER_Z);
                helper.getLevel().getChunkAt(marker);
                if (helper.getLevel().getBlockState(marker).getBlock() != Blocks.DIAMOND_BLOCK) {
                    throw new GameTestAssertException("Waiting for a bridge command execution from the Fabric host");
                }
            })
            .thenSucceed();
    }

    @GameTest(template = "forge:empty3x3x3")
    public static void crossProcessEventPropagation(GameTestHelper helper) {
        if (!bridged()) {
            helper.succeed();
            return;
        }
        // Ensure a block exists so breaking it fires a BlockBreakEvent.
        runCommand(helper, "hypercore-coexistence set " + EVENT_X + " " + BLOCK_Y + " " + EVENT_Z + " STONE");
        helper.startSequence()
            .thenWaitUntil(() -> {
                if (helper.getLevel().getServer().getCommands().getDispatcher().getRoot().getChild("xfabric_hypercore-coexistence") == null) {
                    throw new GameTestAssertException("Waiting for the mirrored Fabric command");
                }
            })
            .thenExecute(() -> runCommand(
                helper,
                "xfabric_hypercore-coexistence cancelbreak " + EVENT_X + " " + BLOCK_Y + " " + EVENT_Z
            ))
            .thenExecute(() -> runCommand(helper, "hypercore-coexistence break " + EVENT_X + " " + BLOCK_Y + " " + EVENT_Z))
            .thenWaitUntil(() -> {
                BlockPos marker = new BlockPos(CANCEL_MARKER_X, CANCEL_MARKER_Y, CANCEL_MARKER_Z);
                helper.getLevel().getChunkAt(marker);
                if (helper.getLevel().getBlockState(marker).getBlock() != Blocks.GOLD_BLOCK) {
                    throw new GameTestAssertException("BlockBreakEvent cancelled on the Fabric host was not observed here");
                }
            })
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
