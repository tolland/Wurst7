/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.gametest.tests;

import com.google.gson.JsonPrimitive;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.wurstclient.settings.BlockSetting;
import net.wurstclient.util.text.WText;

/** Regression coverage for the downstream crop target filter. */
public final class BlockSettingTest
{
	public static void run(ClientGameTestContext context)
	{
		context.runOnClient(mc -> {
			BlockSetting target = new BlockSetting("Test crop", WText.empty(),
				"minecraft:wheat", false, block -> block instanceof CropBlock);
			target.setBlock(Blocks.CARROTS);
			if(target.getBlock() != Blocks.CARROTS)
				throw new AssertionError("Valid crop target was rejected");
			target.setBlock(Blocks.STONE);
			if(target.getBlock() != Blocks.CARROTS)
				throw new AssertionError("Target filter accepted stone");
			target.fromJson(new JsonPrimitive("minecraft:potatoes"));
			if(target.getBlock() != Blocks.POTATOES)
				throw new AssertionError("Valid saved crop was rejected");
			target.fromJson(new JsonPrimitive("minecraft:stone"));
			if(target.getBlock() != Blocks.WHEAT)
				throw new AssertionError("Invalid saved target did not reset");
			try
			{
				new BlockSetting("Test crop", WText.empty(), "minecraft:stone",
					false, block -> block instanceof CropBlock);
			}catch(IllegalArgumentException expected)
			{
				return;
			}
			throw new AssertionError("Invalid default crop was accepted");
		});
	}
}
