/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.gametest.tests;

import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.level.block.Blocks;
import net.wurstclient.gametest.MiniTestContext;
import net.wurstclient.gametest.WurstTest;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

import static net.wurstclient.gametest.WurstClientTestHelper.*;

@SuppressWarnings("UnstableApiUsage")
public enum BuildRandomTest {
    ;

    /**
     * Test BuildRandom placing a block with an interactable support block
     */
    public static void testBuildRandomPlaceBlock(
            ClientGameTestContext context,
            TestSingleplayerContext spContext
    ) {
        TestServerContext server = spContext.getServer();
        TestInput input = context.getInput();

        WurstTest.LOGGER.info("Testing BuildRandom placing {} -> {}",
                "dirt", "minecraft:chest");

        // Use MiniTestContext for isolated setup/teardown
        try (MiniTestContext testCtx = new MiniTestContext(context, server)) {

            // Make a chest to act as the interactable block
            testCtx.setBlock(0, 0, 2, "minecraft:chest");
            runCommand(server, "give @p dirt 16");
            runCommand(server, "gamemode survival");
            context.waitTick();

            // Ensure the test environment and activate AutoFarm

            runWurstCommand(context, "t BuildRandom on");
            context.waitTick();
            waitForBlock(context, 0, 1, 2, Blocks.DIRT);
            runWurstCommand(context, "t BuildRandom off");

            // Prep for evaluation
            clearToasts(context);
            context.waitTick();

        }
    }

}
