/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.gametest.tests;

import static net.wurstclient.gametest.WurstClientTestHelper.runCommand;
import static net.wurstclient.gametest.WurstClientTestHelper.runWurstCommand;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.wurstclient.gametest.MiniTestContext;
import net.wurstclient.WurstClient;
import net.wurstclient.gametest.WurstTest;
import net.wurstclient.hacks.autofarm.AutoFarmPlantTypeManager;
import net.wurstclient.util.BlockPlacer;

/** Downstream regression coverage for crop discovery and replanting. */
public final class AutoFarmCropTest
{
	public static void run(ClientGameTestContext context,
		TestSingleplayerContext spContext)
	{
		context.runOnClient(mc -> {
			var hax = WurstClient.INSTANCE.getHax();
			hax.replanterHack.setEnabled(true);
			try
			{
				hax.autoFarmHack.setEnabled(true);
				if(hax.replanterHack.isEnabled())
					throw new AssertionError("AutoFarm must disable Replanter");
			}finally
			{
				hax.autoFarmHack.setEnabled(false);
				hax.replanterHack.setEnabled(false);
			}
		});
		for(String crop : new String[]{"wheat", "carrots", "potatoes",
			"bamboo"})
		{
			WurstTest.LOGGER.info("Testing AutoFarm crop cycle: {}", crop);
			var server = spContext.getServer();
			try(MiniTestContext test = new MiniTestContext(context, server))
			{
				boolean bamboo = crop.equals("bamboo");
				var expectedBlock = switch(crop)
				{
					case "wheat" -> Blocks.WHEAT;
					case "carrots" -> Blocks.CARROTS;
					case "potatoes" -> Blocks.POTATOES;
					default -> Blocks.BAMBOO;
				};
				test.setBlock(0, -1, 1, bamboo ? "minecraft:dirt"
					: "minecraft:farmland[moisture=7]");
				test.setBlock(0, 0, 1,
					"minecraft:" + crop + (bamboo ? "" : "[age=7]"));
				if(bamboo)
					test.setBlock(0, 1, 1, "minecraft:bamboo");
				test.teleportPlayer(0.5F, 0, 0.5F, 0, 35);
				String item = switch(crop)
				{
					case "wheat" -> "wheat_seeds";
					case "carrots" -> "carrot";
					case "potatoes" -> "potato";
					default -> "bamboo";
				};
				runCommand(server, "give @p " + item + " 16");
				runCommand(server, "time set noon");
				runCommand(server, "gamemode survival");
				BlockPos pos = new BlockPos(test.getLocation().baseX,
					test.getLocation().baseY, test.getLocation().baseZ + 1);
				context
					.waitFor(mc -> mc.level.getBlockState(pos).is(expectedBlock)
						&& (!bamboo || mc.level.getBlockState(pos.above())
							.is(Blocks.BAMBOO)));
				context.runOnClient(mc -> {
					var types = new AutoFarmPlantTypeManager();
					var type = types.getReplantingSpotType(pos);
					if(bamboo && types.shouldHarvestByMining(pos))
						throw new AssertionError(
							"AutoFarm must preserve the bamboo base");
					if(type == null || !type.hasPlantingSurface(pos) || !types
						.shouldHarvestByMining(bamboo ? pos.above() : pos))
						throw new AssertionError("Crop discovery failed for "
							+ crop + ": type=" + type + ", floor="
							+ mc.level.getBlockState(pos.below()));
				});
				runWurstCommand(context, "t AutoFarm on");
				try
				{
					context.waitFor(mc -> {
						var state = mc.level.getBlockState(pos);
						return bamboo
							? mc.level.getBlockState(pos.above()).isAir()
								&& state.is(Blocks.BAMBOO)
							: state.is(expectedBlock)
								&& state.getBlock() instanceof CropBlock plant
								&& plant.getAge(state) == 0;
					});
				}catch(AssertionError e)
				{
					context.runOnClient(mc -> WurstTest.LOGGER.error(
						"Crop cycle failed for {}: state={}, floor={}, hand={}, params={}",
						crop, mc.level.getBlockState(pos),
						mc.level.getBlockState(pos.below()),
						mc.player.getMainHandItem(),
						BlockPlacer.getBlockPlacingParams(pos)));
					throw e;
				}finally
				{
					runWurstCommand(context, "t AutoFarm off");
				}
			}
		}
	}
}
