/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.WurstClient;
import net.wurstclient.mixinterface.IMinecraftClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public enum BlockSpawnability
{
	;
	private static final WurstClient WURST = WurstClient.INSTANCE;
	private static final Minecraft MC = WurstClient.MC;
	private static final IMinecraftClient IMC = WurstClient.IMC;
	
	private ArrayList<BlockPos> getSpawnableBlocks(double range)
	{
		return getSpawnableBlocks(range, BlockSpawnability::isSpawnable);
	}
	
	public static ArrayList<BlockPos> getSpawnableBlocks(double range,
		Predicate<BlockPos> isSpawnable)
	{
		Vec3 eyesVec = RotationUtils.getEyesPos();
		double rangeSq = Math.pow(range, 2);
		int rangeI = (int)Math.ceil(range);
		
		BlockPos center = BlockPos.containing(eyesVec);
		BlockPos min = center.offset(-rangeI, -rangeI, -rangeI);
		BlockPos max = center.offset(rangeI, rangeI, rangeI);
		
		Comparator<BlockPos> c = Comparator.<BlockPos> comparingDouble(
			pos -> eyesVec.distanceToSqr(Vec3.atCenterOf(pos)));
		
		return BlockUtils.getAllInBox(min, max).stream()
			.filter(
				pos -> eyesVec.distanceToSqr(Vec3.atCenterOf(pos)) <= rangeSq)
			.filter(BlockSpawnability::isSpawnable).sorted(c)
			.collect(Collectors.toCollection(ArrayList::new));
	}
	
	private static boolean isSpawnable(BlockPos pos)
	{
		// Check for solid blocks, fluids, redstone, prevent_spawning
		// tags, etc.
		// See SpawnLocationTypes.ON_GROUND
		if(!SpawnPlacements.isSpawnPositionOk(EntityType.CREEPER, MC.level,
			pos))
			return false;
		
		// check if not in fluid
		BlockState state = MC.level.getBlockState(pos);
		if(!state.getFluidState().isEmpty())
			return false;
		
		// Check for hitbox collisions
		if(!unstableHitboxCheck(pos))
			return false;
		
		// Check block light level
		return MC.level.getBrightness(LightLayer.BLOCK, pos) < 1;
	}
	
	// "unstable" because isSpaceEmpty() is not thread-safe
	private static boolean unstableHitboxCheck(BlockPos pos)
	{
		try
		{
			return MC.level.noCollision(EntityType.CREEPER
				.getSpawnAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
		}catch(Exception e)
		{
			return false;
			
		}
	}
	
}
