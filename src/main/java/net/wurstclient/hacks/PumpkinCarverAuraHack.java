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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.CameraTransformViewBobbingListener;
import net.wurstclient.events.HandleInputListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.*;
import net.wurstclient.settings.FaceTargetSetting.FaceTarget;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.util.*;
import net.wurstclient.util.BlockBreaker.BlockBreakingParams;

import java.util.Comparator;
import java.util.List;

@SearchTags({"pumpkin carver aura", "auto pumpkin", "pumpkin carver",
	"carve pumpkin"})
public final class PumpkinCarverAuraHack extends Hack implements
	HandleInputListener, RenderListener, CameraTransformViewBobbingListener
{
	private final SliderSetting range =
		new SliderSetting("Range", 4.5, 1, 6, 0.05, ValueDisplay.DECIMAL);
	
	private final FaceTargetSetting faceTarget =
		FaceTargetSetting.withoutPacketSpam(this, FaceTarget.SERVER);
	
	private final SwingHandSetting swingHand =
		new SwingHandSetting(this, SwingHand.SERVER);
	
	private final SliderSetting delay = new SliderSetting("Delay",
		"Delay between carvings in ticks.\n" + "20 ticks = 1 second", 2, 0, 20,
		1, ValueDisplay.INTEGER.withSuffix(" ticks").withLabel(0, "none"));
	
	private final CheckboxSetting checkLOS =
		new CheckboxSetting("checkLOS", "Only carve pumpkins you can see.\n"
			+ "Recommended for servers with anti-cheat.", true);
	
	private int timer;
	private BlockPos currentBlock;
	private boolean isSneakingForCarving;
	
	public PumpkinCarverAuraHack()
	{
		super("PumpkinCarverAura");
		setCategory(Category.BLOCKS);
		addSetting(range);
		addSetting(faceTarget);
		addSetting(swingHand);
		addSetting(delay);
		addSetting(checkLOS);
	}
	
	@Override
	protected void onEnable()
	{
		timer = 0;
		currentBlock = null;
		isSneakingForCarving = false;
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
		isSneakingForCarving = false;
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
		
		// get pumpkin blocks
		Vec3 eyesVec = RotationUtils.getEyesPos();
		BlockPos eyesBlock = BlockPos.containing(eyesVec);
		double rangeSq = range.getValueSq();
		int blockRange = range.getValueCeil();
		
		List<BlockPos> pumpkinBlocks = BlockUtils
			.getAllInBoxStream(eyesBlock, blockRange)
			.filter(pos -> pos.distToCenterSqr(eyesVec) <= rangeSq)
			.filter(this::isPumpkin)
			.sorted(
				Comparator.comparingDouble(pos -> pos.distToCenterSqr(eyesVec)))
			.toList();
		
		if(pumpkinBlocks.isEmpty())
		{
			currentBlock = null;
			return;
		}
		
		// check if holding shears
		if(!isHoldingShears())
		{
			selectShears();
			return;
		}
		
		// get the hand that is holding the shears
		InteractionHand hand = getHandWithShears();
		if(hand == null)
		{
			currentBlock = null;
			return;
		}
		
		// try to carve first valid pumpkin
		for(BlockPos pos : pumpkinBlocks)
		{
			// get block breaking params for interaction
			BlockBreakingParams params =
				BlockBreaker.getBlockBreakingParams(pos);
			if(params == null)
				continue;
			
			// check line of sight if enabled
			if(checkLOS.isChecked() && !params.lineOfSight())
				continue;
			
			// face pumpkin
			faceTarget.face(params.hitVec());
			
			// interact with pumpkin (carve it)
			if(MC.rightClickDelay > 0)
				return;
			
			MC.rightClickDelay = 4;
			InteractionSimulator.rightClickBlock(params.toHitResult(), hand,
				swingHand.getSelected());
			
			// set current block for rendering
			currentBlock = pos;
			
			// reset timer
			timer = delay.getValueI();
			
			return;
		}
		
		currentBlock = null;
	}
	
	private boolean isPumpkin(BlockPos pos)
	{
		return BlockUtils.getBlock(pos) == Blocks.PUMPKIN;
	}
	
	private boolean isHoldingShears()
	{
		LocalPlayer player = MC.player;
		return isShears(player.getMainHandItem().getItem())
			|| isShears(player.getOffhandItem().getItem());
	}
	
	private InteractionHand getHandWithShears()
	{
		LocalPlayer player = MC.player;
		if(isShears(player.getMainHandItem().getItem()))
			return InteractionHand.MAIN_HAND;
		if(isShears(player.getOffhandItem().getItem()))
			return InteractionHand.OFF_HAND;
		return null;
	}
	
	private boolean isShears(Item item)
	{
		return item == Items.SHEARS;
	}
	
	private void selectShears()
	{
		InventoryUtils.selectItem(stack -> isShears(stack.getItem()), 36);
	}
	
	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		// render current target block
		if(currentBlock != null)
		{
			int orange = 0xC0FF8000;
			RenderUtils.drawOutlinedBox(matrixStack, new AABB(currentBlock),
				orange, false);
		}
	}
	
	@Override
	public void onCameraTransformViewBobbing(
		CameraTransformViewBobbingEvent event)
	{
		// cancel view bobbing when sneaking for pumpkin carving
		if(isSneakingForCarving)
			event.cancel();
	}
}
