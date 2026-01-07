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
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.wurstclient.WurstClient;
import net.wurstclient.mixinterface.IMinecraftClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public enum BlockInteractivity
{
	;
	private static final WurstClient WURST = WurstClient.INSTANCE;
	private static final Minecraft MC = WurstClient.MC;
	private static final IMinecraftClient IMC = WurstClient.IMC;
	
	/*
	 * Helper: guess whether a block will consume right-click.
	 * - fast path: block entity that is a MenuProvider (chests, furnaces, etc.)
	 * - reflection: detect if the concrete block class (or a superclass under
	 * Block)
	 * declares an interaction-like method (common names mapped in decompiled
	 * code)
	 * - cache results per block class to avoid repeated reflection overhead
	 */
	private static final Map<Class<?>, Boolean> LIKELY_INTERACTABLE_CACHE =
		new ConcurrentHashMap<>();
	
	public static boolean isLikelyInteractable(BlockPos pos)
	{
		assert MC.level != null;
		
		var block = BlockUtils.getBlock(pos);
		
		if(block instanceof BaseEntityBlock
			|| block instanceof CraftingTableBlock)
		{
			return true;
		}
		
		// 1) block entity that provides menus -> almost certainly interactive
		BlockEntity be = MC.level.getBlockEntity(pos);
		if(be instanceof MenuProvider)
			return true;
		
		// Most blocks with tile entities are interactable
		if(block instanceof BaseEntityBlock)
			return true;
		
		// Redstone components
		if(block instanceof ButtonBlock || block instanceof LeverBlock
			|| block instanceof ComparatorBlock
			|| block instanceof RepeaterBlock || block instanceof NoteBlock
			|| block instanceof DaylightDetectorBlock)
			return true;
		
		// Doors and gates
		if(block instanceof DoorBlock || block instanceof TrapDoorBlock
			|| block instanceof FenceGateBlock)
			return true;
		
		// Workstations and utility blocks
		if(block instanceof CraftingTableBlock
			|| block instanceof ComposterBlock
			|| block instanceof CartographyTableBlock
			|| block instanceof GrindstoneBlock || block instanceof LoomBlock
			|| block instanceof StonecutterBlock
			|| block instanceof EnchantingTableBlock)
			return true;
		
		// Storage and special blocks
		return block instanceof AnvilBlock || block instanceof BedBlock
			|| block instanceof CakeBlock || block instanceof FlowerPotBlock
			|| block instanceof JukeboxBlock
			|| block instanceof RespawnAnchorBlock
			|| block instanceof RedStoneOreBlock;
	}
	
}
