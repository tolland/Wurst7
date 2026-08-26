/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.gametest;

import static net.wurstclient.gametest.BlockLists.getInteractiveBlocks;
import static net.wurstclient.gametest.WurstClientTestHelper.*;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldBuilder;
import net.fabricmc.fabric.impl.client.gametest.TestSystemProperties;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.wurstclient.gametest.tests.AutoFarmTest;
import net.wurstclient.gametest.tests.ButtonAuraTest;

/**
 * Downstream-owned client GameTest entry point. Keeping selection and local
 * regression tests here minimizes merge conflicts in upstream's
 * {@link WurstTest}.
 */
@SuppressWarnings("UnstableApiUsage")
public final class DownstreamWurstTest implements FabricClientGameTest
{
	private static final String SELECT_PROPERTY = "wurst.gametest.select";
	private static final Set<String> KNOWN_SELECTORS = Set.of("all", "upstream",
		"downstream", "block-placement", "autofarm", "autofarm-foot-level",
		"button-aura", "autofarm-interactive-supports");
	
	private final Set<String> selectors = readSelectors();
	
	@Override
	public void runTest(ClientGameTestContext context)
	{
		if(!TestSystemProperties.DISABLE_NETWORK_SYNCHRONIZER)
			throw new RuntimeException("Network synchronizer is not disabled");
		
		WurstTest.LOGGER.info("Selected client GameTests: {}", selectors);
		
		if(isSelected("upstream"))
			new WurstTest().runTest(context);
		
		if(!hasDownstreamSelection())
			return;
		
		hideSplashTexts(context);
		waitForTitleScreenFade(context);
		runDownstreamTests(context);
	}
	
	private void runDownstreamTests(ClientGameTestContext context)
	{
		WurstTest.LOGGER.info("Creating downstream regression test world");
		TestWorldBuilder worldBuilder = context.worldBuilder();
		worldBuilder.adjustSettings(creator -> {
			String mcVersion = SharedConstants.getCurrentVersion().name();
			creator.setName("Wurst downstream tests " + mcVersion);
			creator.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
			creator.getGameRules().set(GameRules.SEND_COMMAND_FEEDBACK, false,
				null);
			applyFlatPresetWithSmoothStone(creator);
		});
		
		try(TestSingleplayerContext spContext = worldBuilder.create())
		{
			try
			{
				runSelectedWorldTests(context, spContext);
			}finally
			{
				spContext.getServer().runOnServer(mc -> mc.halt(false));
			}
		}
	}
	
	private void runSelectedWorldTests(ClientGameTestContext context,
		TestSingleplayerContext spContext)
	{
		if(isSelected("autofarm-foot-level", "autofarm", "block-placement",
			"downstream"))
		{
			LinkedHashSet<String> blocks =
				new LinkedHashSet<>(Arrays.asList(getInteractiveBlocks()));
			blocks.add("minecraft:comparator");
			
			for(String block : blocks)
				AutoFarmTest.testAutoFarmPlaceAtFootLevel(context, spContext,
					block);
		}
		
		if(isSelected("button-aura", "block-placement", "downstream"))
			ButtonAuraTest.testButtonAuraPlace(context, spContext,
				"minecraft:stone_button[face=floor]");
		
		if(isSelected("autofarm-interactive-supports", "autofarm",
			"block-placement", "downstream"))
		{
			for(String block : getInteractiveBlocks())
			{
				for(AutoFarmTest.SupportDirection dir : AutoFarmTest.SupportDirection
					.values())
				{
					AutoFarmTest.testAutoFarmPlace(context, spContext, block,
						dir);
				}
			}
		}
	}
	
	private boolean hasDownstreamSelection()
	{
		return selectors.stream()
			.anyMatch(selector -> !selector.equals("upstream"));
	}
	
	private boolean isSelected(String... names)
	{
		if(selectors.contains("all"))
			return true;
		
		return Arrays.stream(names).anyMatch(selectors::contains);
	}
	
	private static Set<String> readSelectors()
	{
		String configured = System.getProperty(SELECT_PROPERTY, "all");
		LinkedHashSet<String> result = new LinkedHashSet<>();
		Arrays.stream(configured.split(",")).map(String::trim)
			.filter(selector -> !selector.isEmpty()).forEach(result::add);
		
		if(result.isEmpty())
			throw new IllegalArgumentException("No client GameTests selected");
		
		LinkedHashSet<String> unknown = new LinkedHashSet<>(result);
		unknown.removeAll(KNOWN_SELECTORS);
		if(!unknown.isEmpty())
			throw new IllegalArgumentException(
				"Unknown client GameTest selectors: " + unknown
					+ ". Known selectors: " + KNOWN_SELECTORS);
		
		return Set.copyOf(result);
	}
	
	private void applyFlatPresetWithSmoothStone(WorldCreationUiState creator)
	{
		FlatLevelGeneratorSettings config = ((FlatLevelSource)creator
			.getSettings().selectedDimensions().overworld()).settings();
		
		creator.updateDimensions(
			(drm, dorHolder) -> dorHolder.replaceOverworldGenerator(drm,
				new FlatLevelSource(config.withBiomeAndLayers(
					List.of(new FlatLayerInfo(1, Blocks.BEDROCK),
						new FlatLayerInfo(2, Blocks.DIRT),
						new FlatLayerInfo(1, Blocks.SMOOTH_STONE)),
					config.structureOverrides(), config.getBiome()))));
	}
}
