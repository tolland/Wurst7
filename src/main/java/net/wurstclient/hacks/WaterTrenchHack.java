/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.Comparator;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.DontSaveState;
import net.wurstclient.hack.Hack;
import net.wurstclient.mixinterface.IKeyMapping;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.InteractionSimulator;
import net.wurstclient.util.InventoryUtils;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.RotationUtils;

@DontSaveState
@SearchTags({"water trench", "AutoTrench", "auto trench", "TrenchFiller",
	"trench filler", "water source", "infinite water", "farm water"})
public final class WaterTrenchHack extends Hack
	implements UpdateListener, RenderListener
{
	private static final int TARGET_COLOR = 0x8000FF00;
	private static final int GAP_COLOR = 0x80FFFF00;
	private static final int SCOOP_COLOR = 0x8000FFFF;
	
	/**
	 * How close the player has to be before auto-walk gives up on improving
	 * the angle by walking even closer.
	 */
	private static final double MIN_WALK_DISTANCE = 2;
	
	private final SliderSetting range = new SliderSetting("Range",
		"How far away the existing water sources can be when WaterTrench"
			+ " searches for a trench to continue.",
		6, 1, 10, 0.5, ValueDisplay.DECIMAL);
	
	private final SliderSetting limit = new SliderSetting("Limit",
		"Automatically stops once this many new water sources have been"
			+ " placed.\n\n" + "0 = no limit",
		0, 0, 256, 1, ValueDisplay.INTEGER.withSuffix(" sources")
			.withLabel(1, "1 source").withLabel(0, "disabled"));
	
	private final CheckboxSetting autoWalk = new CheckboxSetting("Auto-walk",
		"Walks along the trench when the next spot is out of reach.\n\n"
			+ "Stand next to the trench, not in it, before enabling this.",
		true);
	
	private final SliderSetting delay = new SliderSetting("Delay",
		"How long to wait between bucket uses.", 4, 0, 20, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks").withLabel(1, "1 tick"));
	
	private final SliderSetting timeout = new SliderSetting("Timeout",
		"How long WaterTrench will keep trying before it gives up and turns"
			+ " itself off.",
		60, 10, 200, 5, ValueDisplay.INTEGER.withSuffix(" ticks"));
	
	private Direction direction;
	private BlockPos frontSource;
	private BlockPos scoopPos;
	private int placed;
	private int timer;
	private int stuckTicks;
	
	public WaterTrenchHack()
	{
		super("WaterTrench");
		setCategory(Category.BLOCKS);
		addSetting(range);
		addSetting(limit);
		addSetting(autoWalk);
		addSetting(delay);
		addSetting(timeout);
	}
	
	@Override
	public String getRenderName()
	{
		if(direction == null)
			return getName() + " [searching]";
		
		if(limit.getValueI() == 0)
			return getName() + " [" + placed + "]";
		
		return getName() + " [" + placed + "/" + limit.getValueI() + "]";
	}
	
	@Override
	protected void onEnable()
	{
		direction = null;
		frontSource = null;
		scoopPos = null;
		placed = 0;
		timer = 0;
		stuckTicks = 0;
		
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		
		stopWalking();
		
		direction = null;
		frontSource = null;
		scoopPos = null;
	}
	
	@Override
	public void onUpdate()
	{
		scoopPos = null;
		
		// look for a trench to continue
		if(direction == null && !findTrench())
		{
			stopWalking();
			return;
		}
		
		// the trench may have changed since it was found
		if(!isWaterSource(frontSource))
		{
			ChatUtils.error(getName() + " lost track of the water sources.");
			setEnabled(false);
			return;
		}
		
		if(limit.getValueI() > 0 && placed >= limit.getValueI())
		{
			ChatUtils.message(getName() + " has placed " + placed
				+ " water sources. Stopping.");
			setEnabled(false);
			return;
		}
		
		Direction.Axis axis = direction.getAxis();
		BlockPos gap = frontSource.relative(direction);
		BlockPos target = gap.relative(direction);
		
		if(!isTrenchCell(gap, axis) || !isTrenchCell(target, axis))
		{
			ChatUtils.message(getName() + " has reached the end of the trench"
				+ " after placing " + placed + " water sources.");
			setEnabled(false);
			return;
		}
		
		// the last source has been placed, wait for the gap to fill in
		if(isWaterSource(target))
		{
			stopWalking();
			
			if(isWaterSource(gap))
			{
				frontSource = target;
				placed++;
				stuckTicks = 0;
				return;
			}
			
			stall("is waiting for the gap at " + gap.toShortString()
				+ " to turn into a source block. Is the trench sealed and"
				+ " level?");
			return;
		}
		
		// walk along the trench until the next spot can be reached, then
		// place a water source two blocks past the last one
		Vec3 targetVec = Vec3.atBottomCenterOf(target);
		if(!canUseBucketAt(targetVec, ClipContext.Fluid.NONE, target.below(),
			Direction.UP))
		{
			walkTowards(targetVec);
			return;
		}
		stopWalking();
		
		if(timer > 0)
		{
			timer--;
			return;
		}
		
		if(MC.player.getMainHandItem().is(Items.WATER_BUCKET))
			useBucket(targetVec);
		else
			refillBucket();
	}
	
	/**
	 * Looks for a line of water source blocks that is one block wide, one
	 * block deep, closed off at one end and continues into an empty trench at
	 * the other end.
	 *
	 * @return true if a trench was found
	 */
	private boolean findTrench()
	{
		Vec3 eyes = RotationUtils.getEyesPos();
		BlockPos eyesBlock = BlockPos.containing(eyes);
		double maxDist = range.getValue();
		
		List<BlockPos> sources =
			BlockUtils.getAllInBoxStream(eyesBlock, range.getValueCeil())
				.filter(this::isWaterSource)
				.filter(pos -> eyes.distanceTo(Vec3.atCenterOf(pos)) <= maxDist)
				.filter(pos -> BlockUtils.hasLineOfSight(Vec3.atCenterOf(pos)))
				.sorted(Comparator.comparingDouble(
					pos -> eyes.distanceToSqr(Vec3.atCenterOf(pos))))
				.toList();
		
		for(BlockPos pos : sources)
			for(Direction dir : Direction.Plane.HORIZONTAL)
				if(tryStartAt(pos, dir))
					return true;
				
		return false;
	}
	
	/**
	 * Checks if the given water source block is part of a trench that can be
	 * extended in the given direction, and starts working on it if it is.
	 *
	 * @return true if the trench was accepted
	 */
	private boolean tryStartAt(BlockPos pos, Direction dir)
	{
		Direction.Axis axis = dir.getAxis();
		Direction back = dir.getOpposite();
		
		if(!isTrenchCell(pos, axis))
			return false;
		
		// find both ends of the line of source blocks
		BlockPos backEnd = pos;
		while(isWaterSource(backEnd.relative(back))
			&& isTrenchCell(backEnd.relative(back), axis))
			backEnd = backEnd.relative(back);
		
		BlockPos front = pos;
		while(isWaterSource(front.relative(dir))
			&& isTrenchCell(front.relative(dir), axis))
			front = front.relative(dir);
		
		// the closed end must be capped off by a solid block
		if(!BlockUtils.isOpaqueFullCube(backEnd.relative(back)))
			return false;
			
		// the pattern only works if the number of sources is odd, since the
		// next source always goes two blocks past the last one
		int length = 1 + backEnd.distManhattan(front);
		if(length < 3 || length % 2 == 0)
			return false;
		
		// the trench must continue past the last source
		BlockPos gap = front.relative(dir);
		if(!isTrenchCell(gap, axis) || !isTrenchCell(gap.relative(dir), axis))
			return false;
		
		direction = dir;
		frontSource = front;
		placed = 0;
		stuckTicks = 0;
		ChatUtils.message(getName() + " found a trench of " + length
			+ " water sources going " + dir.getName() + ".");
		return true;
	}
	
	/**
	 * Selects a water bucket if there is one, otherwise refills an empty
	 * bucket from a source block that can regenerate itself.
	 */
	private void refillBucket()
	{
		// a water bucket from the inventory is quicker than scooping
		if(InventoryUtils.selectItem(Items.WATER_BUCKET))
			return;
		
		if(!MC.player.getMainHandItem().is(Items.BUCKET))
		{
			if(InventoryUtils.selectItem(Items.BUCKET))
				return;
			
			ChatUtils.error(getName()
				+ " needs a water bucket or an empty bucket in your inventory.");
			setEnabled(false);
			return;
		}
		
		BlockPos scoop = findRenewableSource();
		if(scoop == null)
		{
			ChatUtils.error(getName() + " can't reach a water source that"
				+ " refills itself. Move closer to the trench.");
			setEnabled(false);
			return;
		}
		
		scoopPos = scoop;
		Vec3 scoopVec = Vec3.atBottomCenterOf(scoop);
		
		// an empty bucket raycasts against fluids, so it has to hit the
		// source block rather than the floor below it
		if(!canUseBucketAt(scoopVec, ClipContext.Fluid.SOURCE_ONLY, scoop,
			null))
		{
			stall("can't get a clear view of the water source at "
				+ scoop.toShortString() + ".");
			return;
		}
		
		useBucket(scoopVec);
	}
	
	/**
	 * Finds the water source closest to the front of the trench that has
	 * another source on both sides, meaning it will instantly refill itself
	 * when scooped up.
	 *
	 * @return the source to scoop from, or null if none is within reach
	 */
	private BlockPos findRenewableSource()
	{
		Direction back = direction.getOpposite();
		Vec3 eyes = RotationUtils.getEyesPos();
		double reach = MC.player.blockInteractionRange();
		
		for(BlockPos pos = frontSource.relative(back); isWaterSource(pos); pos =
			pos.relative(back))
		{
			if(!isWaterSource(pos.relative(direction))
				|| !isWaterSource(pos.relative(back)))
				continue;
			
			if(eyes.distanceTo(Vec3.atBottomCenterOf(pos)) <= reach)
				return pos;
		}
		
		return null;
	}
	
	/**
	 * Checks that the given point is within reach and that the raycast the
	 * bucket does when it's used would hit the expected block, so that the
	 * hack never uses a bucket at the wrong spot.
	 *
	 * @param face
	 *            the side that must be hit, or null if it doesn't matter
	 */
	private boolean canUseBucketAt(Vec3 aimVec, ClipContext.Fluid fluidHandling,
		BlockPos expectedPos, Direction face)
	{
		if(RotationUtils.getEyesPos().distanceTo(aimVec) > MC.player
			.blockInteractionRange())
			return false;
		
		BlockHitResult hitResult = BlockUtils.raycast(
			RotationUtils.getEyesPos(), toReachEnd(aimVec), fluidHandling);
		
		return hitResult.getType() == HitResult.Type.BLOCK
			&& hitResult.getBlockPos().equals(expectedPos)
			&& (face == null || hitResult.getDirection() == face);
	}
	
	/**
	 * Faces the given point and right-clicks, just like vanilla would when the
	 * player aims at it and presses the use key.
	 */
	private void useBucket(Vec3 aimVec)
	{
		WURST.getRotationFaker().faceVectorClient(aimVec);
		
		// buckets do their own raycast, so vanilla still sends the block
		// interaction for whatever the crosshair is pointing at
		BlockHitResult crosshair =
			BlockUtils.raycast(RotationUtils.getEyesPos(), toReachEnd(aimVec));
		
		InteractionSimulator.rightClickBlock(crosshair);
		timer = delay.getValueI();
		stuckTicks = 0;
	}
	
	/**
	 * Extends the line from the player's eyes through the given point to the
	 * player's full reach, which is where the game's own raycasts end.
	 */
	private Vec3 toReachEnd(Vec3 aimVec)
	{
		Vec3 eyes = RotationUtils.getEyesPos();
		return eyes.add(aimVec.subtract(eyes).normalize()
			.scale(MC.player.blockInteractionRange()));
	}
	
	/**
	 * Walks along the trench to get closer to the given point. Does nothing if
	 * auto-walk is disabled or the player is already right next to it.
	 */
	private void walkTowards(Vec3 aimVec)
	{
		if(!autoWalk.isChecked())
		{
			stopWalking();
			return;
		}
		
		// walking even closer won't help at this point
		if(RotationUtils.getEyesPos().distanceTo(aimVec) <= MIN_WALK_DISTANCE)
		{
			stopWalking();
			stall("can't get a clear view of the next spot in the trench.");
			return;
		}
		
		// walk along the trench instead of into it
		Vec3 dirVec = Vec3.atLowerCornerOf(direction.getUnitVec3i());
		WURST.getRotationFaker()
			.faceVectorClientIgnorePitch(MC.player.position().add(dirVec));
		
		IKeyMapping.get(MC.options.keyUp).setDown(true);
		IKeyMapping.get(MC.options.keyJump)
			.setDown(MC.player.horizontalCollision);
		stuckTicks = 0;
	}
	
	private void stopWalking()
	{
		IKeyMapping.get(MC.options.keyUp).resetPressedState();
		IKeyMapping.get(MC.options.keyJump).resetPressedState();
	}
	
	/**
	 * Keeps track of how long the hack has been unable to make any progress
	 * and turns it off if that takes too long.
	 */
	private void stall(String reason)
	{
		if(++stuckTicks <= timeout.getValueI())
			return;
		
		ChatUtils.error(getName() + " " + reason);
		setEnabled(false);
	}
	
	private boolean isWaterSource(BlockPos pos)
	{
		FluidState fluidState = BlockUtils.getState(pos).getFluidState();
		return fluidState.isSource()
			&& fluidState.getType().isSame(Fluids.WATER);
	}
	
	/**
	 * Checks that the given position is part of a trench that is one block
	 * wide and one block deep, with solid walls and a solid floor, so that
	 * water placed there can't spill out.
	 */
	private boolean isTrenchCell(BlockPos pos, Direction.Axis axis)
	{
		BlockState state = BlockUtils.getState(pos);
		if(!state.isAir()
			&& !state.getFluidState().getType().isSame(Fluids.WATER))
			return false;
		
		// one block deep
		if(BlockUtils.getState(pos.above()).getFluidState().getType()
			.isSame(Fluids.WATER))
			return false;
		
		if(!BlockUtils.isOpaqueFullCube(pos.below()))
			return false;
		
		// one block wide
		for(Direction side : Direction.Plane.HORIZONTAL)
			if(side.getAxis() != axis
				&& !BlockUtils.isOpaqueFullCube(pos.relative(side)))
				return false;
			
		return true;
	}
	
	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		if(direction == null || frontSource == null)
			return;
		
		BlockPos gap = frontSource.relative(direction);
		RenderUtils.drawOutlinedBox(matrixStack, new AABB(gap), GAP_COLOR,
			false);
		RenderUtils.drawOutlinedBox(matrixStack,
			new AABB(gap.relative(direction)), TARGET_COLOR, false);
		
		if(scoopPos != null)
			RenderUtils.drawOutlinedBox(matrixStack, new AABB(scoopPos),
				SCOOP_COLOR, false);
	}
}
