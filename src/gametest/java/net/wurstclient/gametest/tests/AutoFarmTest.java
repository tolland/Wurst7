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
import net.fabricmc.fabric.api.client.gametest.v1.context.TestClientWorldContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.wurstclient.gametest.WurstTest;

import static net.wurstclient.gametest.WurstClientTestHelper.*;

public enum AutoFarmTest {
    ;

    public enum SupportDirection {
        NORTH(0, 1, 3),
        SOUTH(0, 1, 1),
        EAST(1, 1, 2),
        WEST(-1, 1, 2),
        ABOVE(0, 2, 2);

        final int dx, dy, dz;

        SupportDirection(int dx, int dy, int dz) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
        }
    }

    // Convenience no-arg method kept for compatibility: places a chest to the default direction (NORTH)
    public static void testAutoFarmPlace(
            ClientGameTestContext context,
            TestSingleplayerContext spContext
    ) {
        testAutoFarmPlace(context, spContext, "minecraft:chest", SupportDirection.NORTH);
    }

    // Parameterized test: specify the interactable block id (minecraft:chest, minecraft:furnace, ...)
    // and the relative support direction.
    public static void testAutoFarmPlace(
            ClientGameTestContext context,
            TestSingleplayerContext spContext,
            String interactBlock,
            SupportDirection dir
    ) {
        TestInput input = context.getInput();
        TestClientWorldContext world = spContext.getClientWorld();
        TestServerContext server = spContext.getServer();

        WurstTest.LOGGER.info("Testing AutoFarm place with support {} -> {}", interactBlock, dir);

        // Teleport and set up farmland & fully-grown crop.
        runCommand(server, "gamemode survival");
        runCommand(server, "tp 20 -60 0");
        runCommand(server, "setblock ~ ~ ~2 minecraft:farmland replace");
        runCommand(server, "setblock ~ ~1 ~2 minecraft:carrots[age=7] replace");
//		runCommand(server, "setblock ~-1 ~1 ~2 minecraft:chest replace");
       runCommand(server, "setblock ~ ~1 ~3 minecraft:chest replace");

        // Place the interactable support block at the chosen relative offset.
        //runCommand(server, String.format("setblock %s %s %s %s replace",
        //        rel(dir.dx), rel(dir.dy), rel(dir.dz), interactBlock));

        // Ensure the test environment and activate AutoFarm.
        runWurstCommand(context, "t AutoFarm on");
//		waitForBlock(context, 0, 0, 2, Blocks.CARROTS);

        // prep for evaluation
        clearToasts(context);
        context.waitTick();

        // Snapshot result for verification.
        context.takeScreenshot("place_block_result_" + interactBlock.replace(':', '_') + "_" + dir.name());

        // Cleanup only the blocks and player state we changed so next test starts clean.
        cleanupAfterTest(context, server, dir);

        // Return player to baseline position for following tests.
        runCommand(server, "tp 0 -57 0");
    }

    // Format a relative coordinate token for commands: "~" if zero, otherwise "~<n>" (e.g. "~1", "~-1").
    private static String rel(int offset) {
        return offset == 0 ? "~" : "~" + offset;
    }

    // Reset the placed blocks and clear player state without restarting the server.
    private static void cleanupAfterTest(ClientGameTestContext context,
                                         TestServerContext server,
                                         SupportDirection dir) {
        // Remove support block and crop, restore base to smooth_stone (same footprint used elsewhere).
        runCommand(server, String.format("setblock %s %s %s minecraft:air replace",
                rel(dir.dx), rel(dir.dy), rel(dir.dz)));
        // Remove crop and farmland used by the test.
        runCommand(server, "setblock ~ ~1 ~2 minecraft:air replace"); // carrots
        runCommand(server, "setblock ~ ~ ~2 minecraft:smooth_stone replace"); // farmland => smooth_stone

        // Clear client-side state to avoid cross-test contamination.
        clearInventory(context);
        clearChat(context);
        clearToasts(context);

        // Give the world a short moment to process the removals.
        context.waitTicks(20);
    }
}
