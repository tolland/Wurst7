/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.gametest.tests;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.wurstclient.gametest.MiniTestContext;
import net.wurstclient.gametest.WurstTest;

import static net.wurstclient.gametest.WurstClientTestHelper.*;

public enum ButtonAuraTest
{
	;
	
	public static void testButtonAuraPlace(ClientGameTestContext context,
		TestSingleplayerContext spContext, String buttonBlock)
	{
		TestServerContext server = spContext.getServer();
		
		WurstTest.LOGGER.info(
			"Testing ButtonAura places beside interactive button {}",
			buttonBlock);
		
		try(MiniTestContext testCtx = new MiniTestContext(context, server))
		{
			// Exclude the player's own position from ButtonAura's targets.
			testCtx.setBlock(0, -1, 0, "minecraft:nether_bricks");
			
			// The only valid target is above this smooth stone block.
			testCtx.setBlock(0, -1, 1, "minecraft:smooth_stone");
			
			// Put an interactive button directly behind the target. The
			// original
			// regression clicked this button instead of placing against the
			// stone.
			testCtx.setBlock(0, -1, 2, "minecraft:smooth_stone");
			String unpoweredButton = buttonBlock.endsWith("]")
				? buttonBlock.substring(0, buttonBlock.length() - 1)
					+ ",powered=false]"
				: buttonBlock + "[powered=false]";
			testCtx.setBlockWithState(0, 0, 2, unpoweredButton);
			testCtx.teleportPlayer(0, 0, 0, 0, 20);
			
			runWurstCommand(context, "give stone_button 64");
			runCommand(server, "gamemode survival");
			runWurstCommand(context,
				"setcheckbox ButtonAura checkLightLevel off");
			runWurstCommand(context, "setcheckbox ButtonAura checkLOS off");
			runWurstCommand(context, "t ButtonAura on");
			try
			{
				waitForBlock(context, 0, 0, 1, Blocks.STONE_BUTTON);
				context.runOnClient(mc -> {
					var state = mc.level.getBlockState(
						mc.player.blockPosition().offset(0, 0, 2));
					if(!(state.getBlock() instanceof ButtonBlock)
						|| state.getValue(BlockStateProperties.POWERED))
						throw new AssertionError(
							"ButtonAura activated the existing support button");
				});
			}finally
			{
				runWurstCommand(context, "t ButtonAura off");
			}
		}
	}
}
