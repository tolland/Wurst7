/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BeetrootBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarrotBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.PotatoBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hacks.autofarm.AutoFarmRenderer;
import net.wurstclient.settings.BlockSetting;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.FaceTargetSetting;
import net.wurstclient.settings.FaceTargetSetting.FaceTarget;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.util.BlockBreaker;
import net.wurstclient.util.BlockBreaker.BlockBreakingParams;
import net.wurstclient.util.BlockBreakingCache;
import net.wurstclient.util.BlockPlacer;
import net.wurstclient.util.BlockPlacer.BlockPlacingParams;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.InteractionSimulator;
import net.wurstclient.util.InventoryUtils;
import net.wurstclient.util.OverlayRenderer;
import net.wurstclient.util.RotationUtils;
import net.wurstclient.util.text.WText;

@SearchTags({"replanter", "crop converter", "crop changer"})
public final class ReplanterHack extends Hack
	implements UpdateListener, RenderListener
{
	private static final Map<Block, Item> TARGET_CROPS =
		Map.of(Blocks.WHEAT, Items.WHEAT_SEEDS, Blocks.CARROTS, Items.CARROT,
			Blocks.POTATOES, Items.POTATO, Blocks.BEETROOTS,
			Items.BEETROOT_SEEDS, Blocks.MELON_STEM, Items.MELON_SEEDS,
			Blocks.PUMPKIN_STEM, Items.PUMPKIN_SEEDS, Blocks.TORCHFLOWER_CROP,
			Items.TORCHFLOWER_SEEDS, Blocks.PITCHER_CROP, Items.PITCHER_POD);
	
	private final SliderSetting range =
		new SliderSetting("Range", 5, 1, 6, 0.05, ValueDisplay.DECIMAL);
	
	private final CheckboxSetting checkLOS =
		new CheckboxSetting("Check line of sight",
			"description.wurst.setting.autofarm.check_line_of_sight", false);
	
	private final FaceTargetSetting faceTarget =
		FaceTargetSetting.withoutPacketSpam(this, FaceTarget.SERVER);
	
	private final SwingHandSetting swingHand =
		new SwingHandSetting(this, SwingHand.SERVER);
	
	private final BlockSetting target = new BlockSetting("Target",
		WText.literal("The crop to plant after harvesting the selected crops."),
		"minecraft:wheat", false, ReplanterHack::isTargetCrop);
	
	private final CheckboxSetting plantEmptyFarmland =
		new CheckboxSetting("Plant empty farmland",
			"Plant the target crop on any empty tilled farmland.", false);
	
	private final CheckboxSetting wheatSeeds =
		new CheckboxSetting("Wheat seeds", true);
	private final CheckboxSetting carrots =
		new CheckboxSetting("Carrots", true);
	private final CheckboxSetting potatoes =
		new CheckboxSetting("Potatoes", true);
	private final CheckboxSetting beetrootSeeds =
		new CheckboxSetting("Beetroot seeds", true);
	private final CheckboxSetting melonSeeds =
		new CheckboxSetting("Melon seeds", true);
	private final CheckboxSetting pumpkinSeeds =
		new CheckboxSetting("Pumpkin seeds", true);
	private final CheckboxSetting torchflowerSeeds =
		new CheckboxSetting("Torchflower seeds", true);
	private final CheckboxSetting pitcherPods =
		new CheckboxSetting("Pitcher pods", true);
	
	private final Set<BlockPos> replantingSpots = new HashSet<>();
	private final BlockBreakingCache cache = new BlockBreakingCache();
	private BlockPos currentlyMining;
	
	private final AutoFarmRenderer renderer = new AutoFarmRenderer();
	private final OverlayRenderer overlay = new OverlayRenderer();
	
	public ReplanterHack()
	{
		super("Replanter");
		setCategory(Category.BLOCKS);
		addSetting(range);
		addSetting(checkLOS);
		addSetting(faceTarget);
		addSetting(swingHand);
		addSetting(target);
		addSetting(plantEmptyFarmland);
		addSetting(wheatSeeds);
		addSetting(carrots);
		addSetting(potatoes);
		addSetting(beetrootSeeds);
		addSetting(melonSeeds);
		addSetting(pumpkinSeeds);
		addSetting(torchflowerSeeds);
		addSetting(pitcherPods);
		renderer.getSettings().forEach(this::addSetting);
	}
	
	@Override
	protected void onEnable()
	{
		WURST.getHax().autoMineHack.setEnabled(false);
		WURST.getHax().autoFarmHack.setEnabled(false);
		replantingSpots.clear();
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		
		if(currentlyMining != null)
		{
			MC.gameMode.isDestroying = true;
			MC.gameMode.stopDestroyBlock();
			currentlyMining = null;
		}
		
		cache.reset();
		overlay.resetProgress();
	}
	
	@Override
	public void onUpdate()
	{
		currentlyMining = null;
		Vec3 eyesVec = RotationUtils.getEyesPos();
		BlockPos eyesBlock = BlockPos.containing(eyesVec);
		double rangeSq = range.getValueSq();
		int blockRange = range.getValueCeil();
		
		List<BlockPos> nonEmptyBlocks =
			BlockUtils.getAllInBoxStream(eyesBlock, blockRange)
				.filter(pos -> pos.distToCenterSqr(eyesVec) <= rangeSq)
				.filter(BlockUtils::canBeClicked).toList();
		
		for(BlockPos pos : nonEmptyBlocks)
			getReplantingSpot(pos).ifPresent(replantingSpots::add);
		
		Comparator<BlockPos> distanceComparator =
			Comparator.comparingDouble(pos -> pos.distToCenterSqr(eyesVec));
		
		List<BlockPos> blocksToHarvest =
			nonEmptyBlocks.stream().filter(this::shouldHarvest)
				.filter(this::canHarvestWithoutStrandingTarget)
				.sorted(distanceComparator).toList();
		
		List<BlockPos> blocksToClear =
			nonEmptyBlocks.stream().filter(this::shouldClearOldPlant)
				.filter(this::canClearWithoutStrandingTarget)
				.sorted(distanceComparator).toList();
		
		List<BlockPos> blocksToReplant =
			BlockUtils.getAllInBoxStream(eyesBlock, blockRange)
				.filter(pos -> pos.distToCenterSqr(eyesVec) <= rangeSq)
				.filter(pos -> BlockUtils.getState(pos).canBeReplaced())
				.filter(this::shouldReplantAt).filter(this::canPlantTargetAt)
				.sorted(distanceComparator).toList();
		
		harvestByMining(blocksToHarvest);
		if(currentlyMining == null)
			harvestByMining(blocksToClear);
		
		if(currentlyMining == null)
			replant(blocksToReplant);
		
		List<BlockPos> blocksToMine = Stream.of(blocksToHarvest, blocksToClear)
			.flatMap(List::stream).toList();
		renderer.update(replantingSpots, blocksToMine, blocksToReplant);
	}
	
	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		renderer.render(matrixStack);
		
		if(renderer.drawBlocksToHarvest.isChecked())
			overlay.render(matrixStack, partialTicks, currentlyMining);
	}
	
	private Optional<BlockPos> getReplantingSpot(BlockPos pos)
	{
		BlockState state = BlockUtils.getState(pos);
		
		if(wheatSeeds.isChecked() && isMatureCrop(state, Blocks.WHEAT))
			return Optional.of(pos);
		
		if(carrots.isChecked() && isMatureCarrots(state))
			return Optional.of(pos);
		
		if(potatoes.isChecked() && isMaturePotatoes(state))
			return Optional.of(pos);
		
		if(beetrootSeeds.isChecked() && isMatureBeetroots(state))
			return Optional.of(pos);
		
		if(torchflowerSeeds.isChecked() && state.is(Blocks.TORCHFLOWER))
			return Optional.of(pos);
		
		if(pitcherPods.isChecked() && isMaturePitcherCrop(state))
			return Optional.of(pos);
		
		if(melonSeeds.isChecked() && state.is(Blocks.MELON))
			return findAdjacentStem(pos, Blocks.ATTACHED_MELON_STEM);
		
		if(pumpkinSeeds.isChecked() && state.is(Blocks.PUMPKIN))
			return findAdjacentStem(pos, Blocks.ATTACHED_PUMPKIN_STEM);
		
		return Optional.empty();
	}
	
	private boolean shouldHarvest(BlockPos pos)
	{
		BlockState state = BlockUtils.getState(pos);
		
		return wheatSeeds.isChecked() && isMatureCrop(state, Blocks.WHEAT)
			|| carrots.isChecked() && isMatureCarrots(state)
			|| potatoes.isChecked() && isMaturePotatoes(state)
			|| beetrootSeeds.isChecked() && isMatureBeetroots(state)
			|| melonSeeds.isChecked() && state.is(Blocks.MELON)
			|| pumpkinSeeds.isChecked() && state.is(Blocks.PUMPKIN)
			|| torchflowerSeeds.isChecked() && state.is(Blocks.TORCHFLOWER)
			|| pitcherPods.isChecked() && isMaturePitcherCrop(state);
	}
	
	private boolean shouldClearOldPlant(BlockPos pos)
	{
		if(!replantingSpots.contains(pos))
			return false;
		
		BlockState state = BlockUtils.getState(pos);
		Block targetBlock = target.getBlock();
		
		if(melonSeeds.isChecked() && (state.is(Blocks.MELON_STEM)
			|| state.is(Blocks.ATTACHED_MELON_STEM)))
			return !targetBlock.equals(Blocks.MELON_STEM);
		
		if(pumpkinSeeds.isChecked() && (state.is(Blocks.PUMPKIN_STEM)
			|| state.is(Blocks.ATTACHED_PUMPKIN_STEM)))
			return !targetBlock.equals(Blocks.PUMPKIN_STEM);
		
		return false;
	}
	
	private boolean canPlantTargetAt(BlockPos pos)
	{
		if(!BlockUtils.getState(pos.below()).is(Blocks.FARMLAND))
			return false;
		
		if(target.getBlock().equals(Blocks.PITCHER_CROP))
			return BlockUtils.getState(pos.above()).canBeReplaced();
		
		return true;
	}
	
	private boolean canPlantTargetAtAfterHarvest(BlockPos pos,
		BlockPos harvestPos)
	{
		if(!BlockUtils.getState(pos.below()).is(Blocks.FARMLAND))
			return false;
		
		if(!target.getBlock().equals(Blocks.PITCHER_CROP))
			return true;
		
		if(BlockUtils.getState(pos.above()).canBeReplaced())
			return true;
		
		return pos.equals(harvestPos)
			&& isMaturePitcherCrop(BlockUtils.getState(harvestPos));
	}
	
	private boolean shouldReplantAt(BlockPos pos)
	{
		return replantingSpots.contains(pos) || plantEmptyFarmland.isChecked();
	}
	
	private boolean canHarvestWithoutStrandingTarget(BlockPos pos)
	{
		Optional<BlockPos> replantingSpot = getReplantingSpot(pos);
		if(replantingSpot.isEmpty())
			return false;
		
		BlockPos spot = replantingSpot.get();
		return canPlantTargetAtAfterHarvest(spot, pos)
			&& canReachReplantingSpot(spot);
	}
	
	private boolean canClearWithoutStrandingTarget(BlockPos pos)
	{
		return canPlantTargetAtAfterHarvest(pos, pos)
			&& canReachReplantingSpot(pos);
	}
	
	private boolean canReachReplantingSpot(BlockPos pos)
	{
		BlockPlacingParams params = BlockPlacer.getBlockPlacingParams(pos);
		if(params == null || params.distanceSq() > range.getValueSq()
			|| params.requiresSneaking())
			return false;
		
		return !checkLOS.isChecked() || params.lineOfSight();
	}
	
	private boolean replant(List<BlockPos> blocksToReplant)
	{
		if(MC.rightClickDelay > 0)
			return false;
		
		if(MC.gameMode.isDestroying() || MC.player.isHandsBusy())
			return false;
		
		Item seedItem = TARGET_CROPS.get(target.getBlock());
		if(seedItem == null)
			return false;
		
		if(MC.player.isHolding(seedItem))
		{
			InteractionHand hand = MC.player.getMainHandItem().is(seedItem)
				? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
			
			for(BlockPos pos : blocksToReplant)
			{
				BlockPlacingParams params =
					BlockPlacer.getBlockPlacingParams(pos);
				if(params == null || params.distanceSq() > range.getValueSq()
					|| params.requiresSneaking())
					continue;
				
				if(checkLOS.isChecked() && !params.lineOfSight())
					continue;
				
				MC.rightClickDelay = 4;
				faceTarget.face(params.hitVec());
				InteractionSimulator.rightClickBlock(params.toHitResult(), hand,
					swingHand.getSelected());
				return true;
			}
		}
		
		for(BlockPos pos : blocksToReplant)
		{
			BlockPlacingParams params = BlockPlacer.getBlockPlacingParams(pos);
			if(params == null || params.distanceSq() > range.getValueSq()
				|| params.requiresSneaking())
				continue;
			
			if(InventoryUtils.selectItem(seedItem)
				|| InventoryUtils.giveCreativeItem(seedItem))
				return true;
		}
		
		return false;
	}
	
	private void harvestByMining(List<BlockPos> blocksToMine)
	{
		double rangeSq = range.getValueSq();
		Stream<BlockBreakingParams> stream = blocksToMine.stream()
			.map(BlockBreaker::getBlockBreakingParams).filter(Objects::nonNull)
			.filter(params -> params.distanceSq() <= rangeSq);
		
		if(checkLOS.isChecked())
			stream = stream.filter(BlockBreakingParams::lineOfSight);
		
		stream = stream.sorted(BlockBreaker.comparingParams());
		
		if(MC.player.getAbilities().instabuild
			&& faceTarget.getSelected() == FaceTarget.OFF)
		{
			MC.gameMode.stopDestroyBlock();
			overlay.resetProgress();
			
			List<BlockPos> blocks = cache
				.filterOutRecentBlocks(stream.map(BlockBreakingParams::pos));
			if(blocks.isEmpty())
				return;
			
			currentlyMining = blocks.get(0);
			BlockBreaker.breakBlocksWithPacketSpam(blocks);
			swingHand.swing(InteractionHand.MAIN_HAND);
			return;
		}
		
		currentlyMining = stream.filter(this::breakOneBlock)
			.map(BlockBreakingParams::pos).findFirst().orElse(null);
		
		if(currentlyMining == null)
		{
			MC.gameMode.stopDestroyBlock();
			overlay.resetProgress();
			return;
		}
		
		overlay.updateProgress();
	}
	
	private boolean breakOneBlock(BlockBreakingParams params)
	{
		faceTarget.face(params.hitVec());
		
		if(!MC.gameMode.continueDestroyBlock(params.pos(), params.side()))
			return false;
		
		swingHand.swing(InteractionHand.MAIN_HAND);
		return true;
	}
	
	private Optional<BlockPos> findAdjacentStem(BlockPos fruitPos,
		Block stemBlock)
	{
		for(Direction direction : Direction.Plane.HORIZONTAL)
		{
			BlockPos stemPos = fruitPos.relative(direction);
			if(BlockUtils.getState(stemPos).is(stemBlock))
				return Optional.of(stemPos);
		}
		
		return Optional.empty();
	}
	
	private boolean isMatureCrop(BlockState state, Block block)
	{
		if(!(state.getBlock() instanceof CropBlock crop))
			return false;
		
		return state.is(block) && crop.isMaxAge(state);
	}
	
	private boolean isMatureCarrots(BlockState state)
	{
		if(!(state.getBlock() instanceof CarrotBlock carrots))
			return false;
		
		return carrots.isMaxAge(state);
	}
	
	private boolean isMaturePotatoes(BlockState state)
	{
		if(!(state.getBlock() instanceof PotatoBlock potatoes))
			return false;
		
		return potatoes.isMaxAge(state);
	}
	
	private boolean isMatureBeetroots(BlockState state)
	{
		if(!(state.getBlock() instanceof BeetrootBlock beetroots))
			return false;
		
		return beetroots.isMaxAge(state);
	}
	
	private boolean isMaturePitcherCrop(BlockState state)
	{
		return state.is(Blocks.PITCHER_CROP)
			&& state.getValue(PitcherCropBlock.HALF) == DoubleBlockHalf.LOWER
			&& state.getValue(PitcherCropBlock.AGE) >= PitcherCropBlock.MAX_AGE;
	}
	
	private static boolean isTargetCrop(Block block)
	{
		return TARGET_CROPS.containsKey(block);
	}
}
