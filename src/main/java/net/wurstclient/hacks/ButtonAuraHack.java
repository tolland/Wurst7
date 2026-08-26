/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.CameraTransformViewBobbingListener;
import net.wurstclient.events.HandleInputListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.mixinterface.IKeyBinding;
import net.wurstclient.settings.*;
import net.wurstclient.settings.FaceTargetSetting.FaceTarget;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.util.*;
import net.wurstclient.util.BlockPlacer.BlockPlacingParams;

import java.util.ArrayList;

@SearchTags({"button aura", "spawn protect", "spawn protection", "auto button",
	"mob spawn protect"})
public final class ButtonAuraHack extends Hack implements HandleInputListener,
	RenderListener, CameraTransformViewBobbingListener
{
	private final SliderSetting range =
		new SliderSetting("Range", 4.5, 1, 6, 0.05, ValueDisplay.DECIMAL);
	
	private final EnumSetting<Priority> priority = new EnumSetting<>("Priority",
		"Which spawnable blocks to target.\n"
			+ "\u00a7lRed only\u00a7r - Only place buttons on blocks where mobs can spawn both day and night (light level < 8).\n"
			+ "\u00a7lAll\u00a7r - Place buttons on all spawnable blocks.",
		Priority.values(), Priority.RED_ONLY);
	
	private final FaceTargetSetting faceTarget =
		FaceTargetSetting.withoutPacketSpam(this, FaceTarget.SERVER);
	
	private final SwingHandSetting swingHand =
		new SwingHandSetting(this, SwingHand.SERVER);
	
	private final SliderSetting delay = new SliderSetting("Delay",
		"Delay between placements in ticks.\n" + "20 ticks = 1 second", 2, 0,
		20, 1, ValueDisplay.INTEGER.withSuffix(" ticks").withLabel(0, "none"));
	
	private final CheckboxSetting checkLOS = new CheckboxSetting("checkLOS",
		"Only place buttons on blocks you can see.\n"
			+ "Recommended for servers with anti-cheat.",
		true);
	
	private final CheckboxSetting checkLight = new CheckboxSetting(
		"checkLightLevel",
		"Only place buttons on blocks with low light levels.\n"
			+ "Disable this for the Nether, where mobs like Piglins can spawn in any light level.",
		true);
	
	private final BlockListSetting ignoreBlocks = new BlockListSetting(
		"ignoreBlocksList",
		"Blocks that you don't want to place buttons on.\n"
			+ "For example, nether bricks where you want mobs to spawn.",
		"minecraft:nether_bricks", "minecraft:red_nether_bricks",
		"minecraft:cracked_nether_bricks", "minecraft:chiseled_nether_bricks");
	
	private final CheckboxSetting ignoreBlocksEnabled = new CheckboxSetting(
		"ignoreBlocksEnabled",
		"When enabled, blocks in the \"Don't button\" list will be skipped.\n"
			+ "Disable this to place buttons on all spawnable surfaces.",
		true);
	
	private int timer;
	private BlockPos currentBlock;
	private boolean isSneakingForPlacement;
	
	public ButtonAuraHack()
	{
		super("ButtonAura");
		setCategory(Category.BLOCKS);
		addSetting(range);
		addSetting(priority);
		addSetting(faceTarget);
		addSetting(swingHand);
		addSetting(delay);
		addSetting(checkLOS);
		addSetting(checkLight);
		addSetting(ignoreBlocks);
		addSetting(ignoreBlocksEnabled);
	}
	
	@Override
	protected void onEnable()
	{
		timer = 0;
		currentBlock = null;
		isSneakingForPlacement = false;
		EVENTS.add(HandleInputListener.class, this);
		EVENTS.add(RenderListener.class, this);
		EVENTS.add(CameraTransformViewBobbingListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(HandleInputListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		EVENTS.remove(CameraTransformViewBobbingListener.class, this);
		currentBlock = null;
		isSneakingForPlacement = false;
	}
	
	@Override
	public void onHandleInput()
	{
		// update timer
		if(timer > 0)
		{
			timer--;
			return;
		}
		
		// don't interfere with other actions
		if(MC.gameMode.isDestroying() || MC.player.isHandsBusy())
			return;
		
		// get spawnable blocks
		ArrayList<BlockPos> spawnableBlocks = BlockSpawnability
			.getSpawnableBlocks(range.getValue(), this::isSpawnable);
		
		if(spawnableBlocks.isEmpty())
		{
			currentBlock = null;
			return;
		}
		
		// check if holding a button
		if(!isHoldingButton())
		{
			selectButton();
			return;
		}
		
		// get the hand that is holding the button
		InteractionHand hand = getHandWithButton();
		if(hand == null)
		{
			currentBlock = null;
			return;
		}
		
		// try to place button on first valid block
		for(BlockPos pos : spawnableBlocks)
		{
			// check if block is already protected
			BlockState state = BlockUtils.getState(pos);
			if(!state.canBeReplaced())
				continue;
			
			// get block placing params
			BlockPlacingParams params = BlockPlacer.getBlockPlacingParams(pos);
			if(params == null)
				continue;
			
			// check line of sight if enabled
			if(checkLOS.isChecked() && !params.lineOfSight())
				continue;
			
			// check if we need to sneak to place against this neighbor
			boolean needsSneak =
				BlockInteractivity.isLikelyInteractable(params.neighbor());
			IKeyBinding sneakKey = null;
			
			if(needsSneak)
			{
				// sneak-place to avoid activating interactive blocks
				isSneakingForPlacement = true;
				sneakKey = IKeyBinding.get(MC.options.keyShift);
				sneakKey.setDown(true);
				if(!MC.player.isShiftKeyDown())
					return;
			}
			
			// face block
			faceTarget.face(params.hitVec());
			
			// place block
			InteractionResult result =
				MC.gameMode.useItemOn(MC.player, hand, params.toHitResult());
			
			// swing hand
			if(result instanceof InteractionResult.Success success && success
				.swingSource() == InteractionResult.SwingSource.CLIENT)
				swingHand.swing(hand);
			
			// reset sneak if we used it
			if(needsSneak)
			{
				sneakKey.resetPressedState();
				isSneakingForPlacement = false;
			}
			
			// set current block for rendering
			currentBlock = pos;
			
			// reset timer
			timer = delay.getValueI();
			
			return;
		}
		
		currentBlock = null;
	}
	
	private boolean isSpawnable(BlockPos pos)
	{
		// Check for solid blocks, fluids, redstone, prevent_spawning tags, etc.
		// See SpawnLocationTypes.ON_GROUND
		if(!SpawnPlacements.isSpawnPositionOk(EntityType.CREEPER, MC.level,
			pos))
			return false;
		
		// check if not in fluid
		BlockState state = MC.level.getBlockState(pos);
		if(!state.getFluidState().isEmpty())
			return false;
		
		// check if the block below is in the ignore list
		if(ignoreBlocksEnabled.isChecked())
		{
			BlockPos blockBelow = pos.below();
			if(ignoreBlocks.contains(BlockUtils.getBlock(blockBelow)))
				return false;
		}
		
		// if light check is disabled, any spawnable surface is valid
		// (useful for Nether mobs that spawn in any light level)
		if(!checkLight.isChecked())
			return true;
		
		// check light level based on priority setting
		int blockLight = MC.level.getBrightness(LightLayer.BLOCK, pos);
		int skyLight = MC.level.getBrightness(LightLayer.SKY, pos);
		
		if(priority.getSelected() == Priority.RED_ONLY)
		{
			// red only: block light < 1 AND sky light < 8
			return blockLight < 1 && skyLight < 8;
		}else
		{
			// all: block light < 1 (red or yellow from MobSpawnESP)
			return blockLight < 1;
		}
	}
	
	private boolean isHoldingButton()
	{
		LocalPlayer player = MC.player;
		return isButton(player.getMainHandItem().getItem())
			|| isButton(player.getOffhandItem().getItem());
	}
	
	private InteractionHand getHandWithButton()
	{
		LocalPlayer player = MC.player;
		if(isButton(player.getMainHandItem().getItem()))
			return InteractionHand.MAIN_HAND;
		if(isButton(player.getOffhandItem().getItem()))
			return InteractionHand.OFF_HAND;
		return null;
	}
	
	private boolean isButton(Item item)
	{
		return item == Items.OAK_BUTTON || item == Items.STONE_BUTTON
			|| item == Items.SPRUCE_BUTTON || item == Items.BIRCH_BUTTON
			|| item == Items.JUNGLE_BUTTON || item == Items.ACACIA_BUTTON
			|| item == Items.DARK_OAK_BUTTON || item == Items.CRIMSON_BUTTON
			|| item == Items.WARPED_BUTTON || item == Items.MANGROVE_BUTTON
			|| item == Items.CHERRY_BUTTON || item == Items.BAMBOO_BUTTON
			|| item == Items.POLISHED_BLACKSTONE_BUTTON;
	}
	
	private void selectButton()
	{
		InventoryUtils.selectItem(stack -> isButton(stack.getItem()), 36);
	}
	
	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		// render current target block
		if(currentBlock != null)
		{
			int green = 0xC000FF00;
			RenderUtils.drawOutlinedBox(matrixStack, new AABB(currentBlock),
				green, false);
		}
	}
	
	@Override
	public void onCameraTransformViewBobbing(
		CameraTransformViewBobbingEvent event)
	{
		// cancel view bobbing when sneaking for button placement
		if(isSneakingForPlacement)
			event.cancel();
	}
	
	private enum Priority
	{
		RED_ONLY("Red only"),
		ALL("All");
		
		private final String name;
		
		private Priority(String name)
		{
			this.name = name;
		}
		
		@Override
		public String toString()
		{
			return name;
		}
	}
}
