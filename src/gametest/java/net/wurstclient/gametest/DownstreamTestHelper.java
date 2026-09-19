/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.gametest;

import static net.wurstclient.gametest.WurstClientTestHelper.runCommand;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;

/**
 * Downstream-owned utilities for tests using {@link MiniTestContext}.
 * Keep fork-specific helpers here so upstream test refactors do not remove
 * the static utilities used by downstream tests.
 */
public enum DownstreamTestHelper
{
	;
	
	public static void clearChat(ClientGameTestContext context)
	{
		context.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(true));
	}
	
	public static void clearNearbyItems(TestServerContext server)
	{
		runCommand(server, "kill @e[type=item]");
	}
	
	public static void clearInventory(ClientGameTestContext context,
		TestServerContext server)
	{
		runCommand(server, "clear");
		clearChat(context);
	}
	
	public static void clearToasts(ClientGameTestContext context)
	{
		context.runOnClient(mc -> mc.gui.toastManager().clear());
	}
	
	/** Waits for a block at an offset relative to the player. */
	public static void waitForRelativeBlock(ClientGameTestContext context,
		int relX, int relY, int relZ, Block block)
	{
		context.waitFor(mc -> mc.level
			.getBlockState(mc.player.blockPosition().offset(relX, relY, relZ))
			.is(block));
	}
	
	/** Waits for a crop at an offset relative to the player to reach an age. */
	public static void waitForCropAge(ClientGameTestContext context, int relX,
		int relY, int relZ, int age)
	{
		context.waitFor(mc -> {
			var state = mc.level.getBlockState(
				mc.player.blockPosition().offset(relX, relY, relZ));
			return state.getBlock() instanceof CropBlock crop
				&& crop.getAge(state) == age;
		});
	}
}
