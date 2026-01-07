/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestClientWorldContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldBuilder;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.wurstclient.gametest.tests.AltManagerTest;
import net.wurstclient.gametest.tests.AutoFarmTest;
import net.wurstclient.gametest.tests.BuildRandomTest;
import net.wurstclient.gametest.tests.ButtonAuraTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static net.wurstclient.gametest.WurstClientTestHelper.*;

@SuppressWarnings("UnstableApiUsage")
public class WurstTest implements FabricClientGameTest
{
	public static final Logger LOGGER = LoggerFactory.getLogger("Wurst Test");
	
	public static final boolean IS_MOD_COMPAT_TEST =
		System.getProperty("wurst.test.withMods") != null;
	
	@Override
	public void runTest(ClientGameTestContext context)
	{
		LOGGER.info("Starting Wurst Client GameTest");
		hideSplashTexts(context);
		waitForTitleScreenFade(context);
		
		LOGGER.info("Reached title screen");
		assertScreenshotEquals(context, "title_screen",
			"https://i.imgur.com/xSAHDXr.png");
		
		AltManagerTest.testAltManagerButton(context);
		
		LOGGER.info("Creating test world");
		TestWorldBuilder worldBuilder = context.worldBuilder();
		worldBuilder.adjustSettings(creator -> {
			String mcVersion = SharedConstants.getCurrentVersion().name();
			creator.setName("E2E Test " + mcVersion);
			creator.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
			creator.getGameRules().getRule(GameRules.RULE_SENDCOMMANDFEEDBACK)
				.set(false, null);
			applyFlatPresetWithSmoothStone(creator);
		});
		
		try(TestSingleplayerContext spContext = worldBuilder.create())
		{
			testInWorld(context, spContext);
			LOGGER.info("Exiting test world");
		}
		
		LOGGER.info("Test complete");
	}
	
	private void testInWorld(ClientGameTestContext context,
		TestSingleplayerContext spContext) {
		TestInput input = context.getInput();
		TestClientWorldContext world = spContext.getClientWorld();
		TestServerContext server = spContext.getServer();

		runCommand(server, "time set noon");
		runCommand(server, "tp 0 -57 0");
		runCommand(server, "fill ~ ~-3 ~ ~ ~-1 ~ smooth_stone");
		runCommand(server, "fill ~-12 ~-3 ~10 ~12 ~9 ~10 smooth_stone");

		LOGGER.info("Loading chunks");
		context.waitTicks(2);
		world.waitForChunksRender();

		// assertScreenshotEquals(context, "in_game",
		// IS_MOD_COMPAT_TEST ? "https://i.imgur.com/VxbGFrb.png"
		// : "https://i.imgur.com/2UvurYl.png");

		// LOGGER.info("Recording debug menu");
		// input.pressKey(GLFW.GLFW_KEY_F3);
		// context.takeScreenshot("debug_menu");
		// input.pressKey(GLFW.GLFW_KEY_F3);
		// // input.pressKey(GLFW.GLFW_KEY_F5);
		//
		// LOGGER.info("Checking for broken mixins");
		// MixinEnvironment.getCurrentEnvironment().audit();

		// LOGGER.info("Opening inventory");
		// input.pressKey(GLFW.GLFW_KEY_E);
		// assertScreenshotEquals(context, "inventory", "LyQ5FSD.png");
		// input.pressKey(GLFW.GLFW_KEY_ESCAPE);
		//
		// LOGGER.info("Opening game menu");
		// input.pressKey(GLFW.GLFW_KEY_ESCAPE);
		// assertScreenshotEquals(context, "game_menu", "L58HCGj.png");
		// input.pressKey(GLFW.GLFW_KEY_ESCAPE);

		runWurstCommand(context,
				"setmode WurstLogo visibility only_when_outdated");
		runWurstCommand(context, "setcheckbox HackList animations off");

		// TODO: Open ClickGUI and Navigator

		// Test Wurst hacks
		// AutoMineHackTest.testAutoMineHack(context, spContext);
		// FreecamHackTest.testFreecamHack(context, spContext);
		// NoFallHackTest.testNoFallHack(context, spContext);
		// XRayHackTest.testXRayHack(context, spContext);

		// Test Wurst commands
		// CopyItemCmdTest.testCopyItemCmd(context, spContext);
		// GiveCmdTest.testGiveCmd(context, spContext);
		// ModifyCmdTest.testModifyCmd(context, spContext);

		// TODO: Test more Wurst features

		// Test special cases
		// PistonTest.testPistonDoesntCrash(context, spContext);
		for(String block : List.of("minecraft:comparator"))
		{

			AutoFarmTest.testAutoFarmPlaceAtFootLevel(context, spContext,
					block);

		}
		//BuildRandomTest.testBuildRandomPlaceBlock(context, spContext);

		// stuff that can be placed anywhere with any support
		 for(String block : List.of("minecraft:comparator"))
		 {

		 AutoFarmTest.testAutoFarmPlaceAtFootLevel(context, spContext,
		 block);

		 }
		
		// stuff that can be placed anywhere with any support
		for(String block : List.of("minecraft:stone_button[face=floor]"
		
		))
		{
			
			ButtonAuraTest.tesButtonAuraPlace(context, spContext, block);
		}
		
		// stuff that can be placed anywhere with any support
		for(String block : List.of("minecraft:lectern",
			// "minecraft:smooth_stone",
			"minecraft:carved_pumpkin", "minecraft:white_bed",
			"minecraft:shulker_box",
			// "minecraft:chest",
			// "minecraft:crafting_table",
			// "minecraft:beacon",
			// "minecraft:ender_chest",
			// "minecraft:loom",
			"minecraft:stone_button[face=floor]",
			
			// Sound/interaction blocks
			"minecraft:note_block", "minecraft:bell", "minecraft:jukebox",
			
			// Redstone/display blocks
			"minecraft:comparator", "minecraft:repeater",
			"minecraft:tripwire_hook", "minecraft:observer",
			
			// Amethyst & crystal variants
			// these appear to be farmable
			// "minecraft:amethyst_cluster",
			// "minecraft:large_amethyst_bud",
			
			// Sculk variants
			"minecraft:sculk_sensor", "minecraft:calibrated_sculk_sensor",
			"minecraft:sculk_shrieker",
			
			// Cauldron (can be filled/emptied)
			"minecraft:cauldron",
			
			// Cartography table, smithing table (like lectern)
			"minecraft:cartography_table", "minecraft:smithing_table",
			
			// Barrel (interactive storage)
			"minecraft:barrel",
			
			// Decorated pot (can interact)
			"minecraft:decorated_pot",
			
			// Campfire (can cook, add logs)
			"minecraft:campfire",
			
			// Grindstone (interactive)
			"minecraft:grindstone",
			
			// Composters (can add items)
			"minecraft:composter"))
		{
			for(AutoFarmTest.SupportDirection dir : AutoFarmTest.SupportDirection
				.values())
			{
				AutoFarmTest.testAutoFarmPlace(context, spContext, block, dir);
			}
		}
		
		// TODO: Check Wurst Options
	}
	
	// because the grass texture is randomized and smooth stone isn't
	private void applyFlatPresetWithSmoothStone(WorldCreationUiState creator)
	{
		FlatLevelGeneratorSettings config = ((FlatLevelSource)creator
			.getSettings().selectedDimensions().overworld()).settings();
		
		List<FlatLayerInfo> layers =
			List.of(new FlatLayerInfo(1, Blocks.BEDROCK),
				new FlatLayerInfo(2, Blocks.DIRT),
				new FlatLayerInfo(1, Blocks.SMOOTH_STONE));
		
		creator.updateDimensions(
			(drm, dorHolder) -> dorHolder.replaceOverworldGenerator(drm,
				new FlatLevelSource(config.withBiomeAndLayers(layers,
					config.structureOverrides(), config.getBiome()))));
	}
}
