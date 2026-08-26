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
import net.minecraft.world.level.block.Blocks;
import net.wurstclient.gametest.WurstTest;

import static net.wurstclient.gametest.WurstClientTestHelper.*;

public enum ButtonAuraTest
{
	;
	
	public enum SupportDirection
	{
		NORTH(0, 1, 3),
		SOUTH(0, 1, 1),
		EAST(1, 1, 2),
		WEST(-1, 1, 2),
		ABOVE(0, 2, 2);
		
		final int dx, dy, dz;
		
		SupportDirection(int dx, int dy, int dz)
		{
			this.dx = dx;
			this.dy = dy;
			this.dz = dz;
		}
	}
	
	// Parameterized test: specify the interactable block id (minecraft:chest,
	// minecraft:furnace, ...)
	// and the relative support direction.
	public static void testButtonAuraPlace(ClientGameTestContext context,
		TestSingleplayerContext spContext, String buttonBlock)
	{
		TestInput input = context.getInput();
		TestClientWorldContext world = spContext.getClientWorld();
		TestServerContext server = spContext.getServer();
		
		WurstTest.LOGGER.info("Testing ButtonAura place with button {}",
			buttonBlock);
		
		// Teleport to blank area
		runCommand(server, "tp 40 -60 0");
		
		runWurstCommand(context, "give stone_button 64");
		runCommand(server, "gamemode survival");
		context.waitTick();
		
		// Ensure the test environment and activate AutoFarm.
		runWurstCommand(context, "setcheckbox ButtonAura checkLightLevel off");
		runWurstCommand(context, "t ButtonAura on");
		// see that we harvested the crop
		waitForBlock(context, 0, 0, 1, Blocks.STONE_BUTTON);
		waitForBlock(context, 1, 0, 0, Blocks.STONE_BUTTON);
		
		// prep for evaluation
		clearToasts(context);
		context.waitTick();
		
		// Snapshot result for verification.
		context.takeScreenshot(
			"place_block_result_" + buttonBlock.replace(':', '_'));
		
		cleanupAfterTest(context, server);
		
		// Return player to baseline position for following tests.
		runCommand(server, "tp 0 -57 0");
	}
	
	// Format a relative coordinate token for commands: "~" if zero, otherwise
	// "~<n>" (e.g. "~1", "~-1").
	private static String rel(int offset)
	{
		return offset == 0 ? "~" : "~" + offset;
	}
	
	// Reset the placed blocks and clear player state without restarting the
	// server.
	private static void cleanupAfterTest(ClientGameTestContext context,
		TestServerContext server)
	{
		// Turn AutoFarm off to avoid any concurrent placements during cleanup.
		runWurstCommand(context, "t ButtonAura off");
		context.waitTick();
		
		runCommand(server, "gamemode creative");
		// Clear client-side state to avoid cross-test contamination.
		clearInventory(context);
		clearChat(context);
		clearToasts(context);
		clearNearbyItems(server);
		
		// Give the world a short moment to process the removals.
		context.waitTicks(3);
	}
}
