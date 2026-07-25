package dev.hypercore.gametest;

import dev.hypercore.HyperCore;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;

@GameTestHolder(HyperCore.MOD_ID)
public final class HyperCoreGameTests {
    private HyperCoreGameTests() {
    }

    @GameTest(template = "forge:empty3x3x3")
    public static void serverLoadsHyperCore(GameTestHelper helper) {
        helper.succeed();
    }
}

