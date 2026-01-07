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
import net.wurstclient.gametest.MiniTestContext;
import net.wurstclient.gametest.WurstTest;

import java.util.HashMap;
import java.util.Map;

import static net.wurstclient.gametest.WurstClientTestHelper.*;

@SuppressWarnings("UnstableApiUsage")
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

    public static void testAutoFarmPlace(ClientGameTestContext context,
                                         TestSingleplayerContext spContext) {
        testAutoFarmPlace(context, spContext, "minecraft:chest",
                SupportDirection.NORTH);
    }

    // Parameterized test: specify the interactable block id (minecraft:chest,
    // minecraft:furnace, ...)
    // and the relative support direction.
    // This is a generic test that reproduced the simple case of problems
    // where AutoFarm failed to place a block when there was an interactable
    // block
    public static void testAutoFarmPlace(ClientGameTestContext context,
                                         TestSingleplayerContext spContext, String interactBlock,
                                         SupportDirection dir) {
        TestServerContext server = spContext.getServer();

        WurstTest.LOGGER.info("Testing AutoFarm place with support {} -> {}",
                interactBlock, dir);

        // Use MiniTestContext for isolated setup/teardown
        try (MiniTestContext testCtx = new MiniTestContext(context, server)) {
            // Set up base platform for farmland (3x1x3 area at Y=0, matching
            // original fill command)
            for (int x = -1; x <= 1; x++) {
                for (int z = 1; z <= 3; z++) {
                    testCtx.setBlock(x, 0, z, "minecraft:smooth_stone");
                }
            }

            // Set up farmland & fully-grown crop (farmland replaces one of the
            // platform blocks)
            testCtx.setBlock(0, 0, 2, "minecraft:farmland");
            testCtx.setBlockWithState(0, 1, 2, "minecraft:carrots[age=7]");
            debugBlock(0, 1, 2);
            context.waitTicks(20);

            // Place the interactable support block at the chosen relative offset
            testCtx.setBlock(dir.dx, dir.dy, dir.dz, interactBlock);

            // In case the farmed block doesn't go into inventory
            runWurstCommand(context, "give carrot");
            runCommand(server, "gamemode survival");
            context.waitTicks(10);

            // Ensure the test environment and activate AutoFarm
            debugBlock(0, 1, 2);
            waitForCropAge(context, 0, 1, 2, 7);
            runWurstCommand(context, "t AutoFarm on");
            waitForCropAge(context, 0, 1, 2, 0);
            runWurstCommand(context, "t AutoFarm off");

            // Prep for evaluation
            clearToasts(context);
            context.waitTick();

            // Snapshot result for verification
            context.takeScreenshot("place_block_result_"
                    + interactBlock.replace(':', '_') + "_" + dir.name());
        }
        // MiniTestContext automatically handles cleanup via close()
    }

    public static void testAutoFarmPlaceAtFootLevel(
            ClientGameTestContext context, TestSingleplayerContext spContext,
            String interactBlock) {
        TestInput input = context.getInput();
        TestServerContext server = spContext.getServer();

        WurstTest.LOGGER.info("Testing AutoFarm place at foot level with {}",
                interactBlock);

        // Use MiniTestContext for isolated setup/teardown
        try (MiniTestContext testCtx = new MiniTestContext(context, server)) {
            // Create block dictionary for easier test definition
            Map<String, String> blocks = new HashMap<>();
            blocks.put("stone", "minecraft:stone");
            blocks.put("farm", "minecraft:farmland");
            blocks.put("carrot", "minecraft:carrots[age=7]");
            blocks.put("interact", interactBlock);

            // Set up layer at Y=-1 (ground level)
            // 5x3 array: center (2,1) is farmland (under player)
            // Original: Z=-2 (stone), Z=-1 (farmland), Z=0 (farmland)
            testCtx.setBlockLayer(-1, blocks, new String[][]{
                    {null, "stone", null},     // Z=-2: stone under interactable
                    {null, "farm", null},      // Z=-1: farmland in front
                    {null, "stone", null},      // Z=0: farmland under player (center)
                    {null, null, null},        // Z=1: empty
                    {null, null, null}         // Z=2: empty
            });

            // Set up layer at Y=0 (player level)
            // 5x3 array: center (2,1) is player position
            // Original: Z=-2 (interactable), Z=-1 (carrot), Z=0 (player)
            testCtx.setBlockLayer(0, blocks, new String[][]{
                    {null, "interact", null},  // Z=-2: interactable block
                    {null, "carrot", null},    // Z=-1: crop
                    {null, "player", null},    // Z=0: player position (center)
                    {null, null, null},        // Z=1: empty
                    {null, null, null}         // Z=2: empty
            });

            // Teleport to test location with specific rotation
            testCtx.teleportPlayer(0.3F, 0, 0.4f, -175.8f, 33.5f);

            // In case the farmed block doesn't go into inventory
            runWurstCommand(context, "give carrot");

            runCommand(server, "gamemode survival");
            waitForCropAge(context, 0, 0, -1, 7);
            runWurstCommand(context, "t AutoFarm on");
            context.waitTick();
            waitForCropAge(context, 0, 0, -1, 0);
            debugBlock(0, 0, -1);
            context.waitTick();
            context.takeScreenshot("farm_test5");
            runWurstCommand(context, "t AutoFarm off");
        }
        // MiniTestContext automatically handles cleanup via close()
    }
}
