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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
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
		BlockState state = MC.level.getBlockState(pos);
		Block block = state.getBlock();
		
		if(block instanceof BaseEntityBlock
			|| block instanceof CraftingTableBlock)
		{
			return true;
		}
		
		// 1) block entity that provides menus -> almost certainly interactive
		BlockEntity be = MC.level.getBlockEntity(pos);
		if(be instanceof MenuProvider)
			return true;
		
		// 2) class-based cache to avoid repeated reflection overhead
		Class<?> cls = block.getClass();
		Boolean cached = LIKELY_INTERACTABLE_CACHE.get(cls);
		if(cached != null)
			return cached;
		
		boolean result = detectInteractableOnClass(cls);
		LIKELY_INTERACTABLE_CACHE.put(cls, result);
		return result;
	}
	
	/*
	 * Reflection-based detection: look for declared/overridden methods that
	 * usually
	 * indicate right-click handling or menu provision. This only checks for
	 * method
	 * declarations (no invocation), so it's side-effect free.
	 */
	private static boolean detectInteractableOnClass(Class<?> cls)
	{
		Class<?> current = cls;
		// stop at net.minecraft.world.level.block.Block to avoid scanning
		// unrelated ancestors
		while(current != null && current != Block.class)
		{
			try
			{
				// common mapped signatures in decompiled code:
				// InteractionResult use(BlockState, Level, BlockPos, Player,
				// InteractionHand, BlockHitResult)
				current.getDeclaredMethod("use",
					net.minecraft.world.level.block.state.BlockState.class,
					Level.class, BlockPos.class, Player.class,
					InteractionHand.class, BlockHitResult.class);
				return true;
			}catch(NoSuchMethodException ignored)
			{}
			
			try
			{
				// chest/decor patterns: protected InteractionResult
				// useWithoutItem(...)
				current.getDeclaredMethod("useWithoutItem",
					net.minecraft.world.level.block.state.BlockState.class,
					Level.class, BlockPos.class, Player.class,
					BlockHitResult.class);
				return true;
			}catch(NoSuchMethodException ignored)
			{}
			
			current = current.getSuperclass();
		}
		
		// fallback: no obvious interactive methods declared
		return false;
	}

	// static block check

	public static boolean isInteractiveBlock(BlockPos pos)
	{
		// Check if the neighbor block is interactive and would be activated
		// instead of allowing placement if we don't sneak
		var block = BlockUtils.getBlock(pos);

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
				|| block instanceof CartographyTableBlock
				|| block instanceof SmithingTableBlock
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
