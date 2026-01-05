/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks.autoshopgui;

import java.util.ArrayList;
import java.util.List;

/**
 * Plans the optimal sequence of button clicks to reach a target quantity in
 * ShopGUIPlus trading interfaces.
 *
 * This class handles the math and planning for buying/selling items, supporting
 * various button configurations that ShopGUIPlus can use.
 */
public class QuantitySequencePlanner
{
	/**
	 * Button operations available in ShopGUIPlus quantity selection GUI.
	 */
	public enum Operation
	{
		SET_1, // Reset to 1
		REMOVE_10, // Subtract 10
		REMOVE_1, // Subtract 1
		ADD_1, // Add 1
		ADD_10, // Add 10
		SET_64, // Set to 64
		CONFIRM, // Confirm transaction
		SELL_ALL, // Sell all (if enabled)
		CANCEL; // Cancel transaction
	}
	
	/**
	 * Configuration for quantity adjustment buttons in ShopGUI.
	 * Maps operations to slot indices.
	 */
	public static class ButtonConfig
	{
		public final int set1Slot;
		public final int remove10Slot;
		public final int remove1Slot;
		public final int add1Slot;
		public final int add10Slot;
		public final int set64Slot;
		public final int confirmSlot;
		public final int sellAllSlot; // -1 if disabled
		public final int cancelSlot;
		
		public ButtonConfig(int set1Slot, int remove10Slot, int remove1Slot,
			int add1Slot, int add10Slot, int set64Slot, int confirmSlot,
			int sellAllSlot, int cancelSlot)
		{
			this.set1Slot = set1Slot;
			this.remove10Slot = remove10Slot;
			this.remove1Slot = remove1Slot;
			this.add1Slot = add1Slot;
			this.add10Slot = add10Slot;
			this.set64Slot = set64Slot;
			this.confirmSlot = confirmSlot;
			this.sellAllSlot = sellAllSlot;
			this.cancelSlot = cancelSlot;
		}
		
		/**
		 * Default ShopGUIPlus configuration from docs.
		 * Slots: 18=set1, 19=remove10, 20=remove1, 24=add1, 25=add10,
		 * 26=set64, 39=confirm, 40=sellAll (disabled), 41=cancel
		 */
		public static ButtonConfig defaultConfig()
		{
			return new ButtonConfig(18, 19, 20, 24, 25, 26, 39, -1, 41);
		}
		
		/**
		 * Legacy configuration (for backward compatibility).
		 * Slots: 28=set1, 29=remove10, 30=remove1, 32=add1, 33=add10,
		 * 34=set64, 50=confirm
		 */
		public static ButtonConfig legacyConfig()
		{
			return new ButtonConfig(28, 29, 30, 32, 33, 34, 50, -1, -1);
		}
		
		public int getSlotForOperation(Operation op)
		{
			return switch(op)
			{
				case SET_1 -> set1Slot;
				case REMOVE_10 -> remove10Slot;
				case REMOVE_1 -> remove1Slot;
				case ADD_1 -> add1Slot;
				case ADD_10 -> add10Slot;
				case SET_64 -> set64Slot;
				case CONFIRM -> confirmSlot;
				case SELL_ALL -> sellAllSlot;
				case CANCEL -> cancelSlot;
			};
		}
	}
	
	private final ButtonConfig config;
	
	public QuantitySequencePlanner(ButtonConfig config)
	{
		this.config = config;
	}
	
	/**
	 * Generate the optimal operation sequence to reach a target quantity.
	 *
	 * Assumes the starting quantity is always 1 (ShopGUI default).
	 *
	 * Uses nearest-multiple-of-10 algorithm for optimal click count:
	 * - Round to nearest (10n + 1)
	 * - Use ADD_10 to get there
	 * - Adjust with REMOVE_1 or ADD_1
	 *
	 * @param targetQuantity
	 *            The desired quantity to reach (1-64)
	 * @return List of operations to execute (including CONFIRM)
	 */
	public List<Operation> planSequence(int targetQuantity)
	{
		if(targetQuantity < 1 || targetQuantity > 64)
			throw new IllegalArgumentException(
				"Target quantity must be between 1 and 64, got: "
					+ targetQuantity);
		
		List<Operation> ops = new ArrayList<>();
		
		if(targetQuantity == 64)
		{
			// Special case: just set to 64
			ops.add(Operation.SET_64);
		}else if(targetQuantity == 1)
		{
			// Already at 1, no clicks needed
		}else
		{
			// Optimal algorithm: nearest multiple of 10
			// Starting from 1, we want to reach targetQuantity
			long n = Math.round((targetQuantity - 1) / 10.0);
			long nearest = 10 * n + 1;
			long remainder = targetQuantity - nearest;
			
			// Add ADD_10 operations to reach nearest
			for(int i = 0; i < n; i++)
			{
				ops.add(Operation.ADD_10);
			}
			
			// Adjust with ADD_1 or REMOVE_1
			if(remainder > 0)
			{
				for(int i = 0; i < remainder; i++)
				{
					ops.add(Operation.ADD_1);
				}
			}else if(remainder < 0)
			{
				for(int i = 0; i < -remainder; i++)
				{
					ops.add(Operation.REMOVE_1);
				}
			}
		}
		
		// Add confirm
		ops.add(Operation.CONFIRM);
		
		return ops;
	}
	
	/**
	 * Convert operations to slot indices for clicking.
	 */
	public List<Integer> operationsToSlots(List<Operation> operations)
	{
		List<Integer> slots = new ArrayList<>();
		for(Operation op : operations)
		{
			int slot = config.getSlotForOperation(op);
			if(slot == -1)
				throw new IllegalArgumentException(
					"Operation " + op + " is not configured (slot is -1)");
			slots.add(slot);
		}
		return slots;
	}
	
	/**
	 * Plan the full selling sequence for a given total quantity.
	 *
	 * Returns a list of operation sequences: first the partial stack (if any),
	 * then a single optimized sequence for all full stacks.
	 *
	 * For full stacks, generates: SET_64, CONFIRM, CONFIRM, CONFIRM...
	 * instead of: [SET_64, CONFIRM], [SET_64, CONFIRM], [SET_64, CONFIRM]...
	 *
	 * @param totalQuantity
	 *            Total items to sell
	 * @return List of operation sequences, each representing one or more
	 *         transactions
	 */
	public List<List<Operation>> planFullSellSequence(int totalQuantity)
	{
		if(totalQuantity < 1)
			throw new IllegalArgumentException(
				"Total quantity must be at least 1, got: " + totalQuantity);
		
		List<List<Operation>> sequences = new ArrayList<>();
		
		int partialStack = totalQuantity % 64;
		int fullStacks = totalQuantity / 64;
		
		// Sell partial stack first (if it exists)
		if(partialStack > 0)
		{
			sequences.add(planSequence(partialStack));
		}
		
		// Then sell all full stacks in a single optimized sequence
		if(fullStacks > 0)
		{
			List<Operation> fullStackSequence = new ArrayList<>();
			
			// Set to 64 once
			fullStackSequence.add(Operation.SET_64);
			
			// Then confirm for each full stack
			for(int i = 0; i < fullStacks; i++)
			{
				fullStackSequence.add(Operation.CONFIRM);
			}
			
			sequences.add(fullStackSequence);
		}
		
		return sequences;
	}
	
	/**
	 * Calculate how many items will be sold given the planned sequences.
	 *
	 * Note: With the optimized full stack handling, totalSequences is now:
	 * - 1 if only partial stack exists
	 * - 1 if only full stacks exist (all in one optimized sequence)
	 * - 2 if both partial and full stacks exist
	 *
	 * @param totalQuantity
	 *            Total items to sell
	 * @return Object containing partial stack qty, full stack count, and total
	 *         sequences
	 */
	public SellPlan analyzeSellPlan(int totalQuantity)
	{
		if(totalQuantity < 1)
			throw new IllegalArgumentException(
				"Total quantity must be at least 1, got: " + totalQuantity);
		
		int partialStack = totalQuantity % 64;
		int fullStacks = totalQuantity / 64;
		
		// With optimized full stacks, we have at most 2 sequences:
		// 1 for partial (if exists) + 1 for all full stacks (if exist)
		int totalSequences = 0;
		if(partialStack > 0)
			totalSequences++;
		if(fullStacks > 0)
			totalSequences++;
		
		return new SellPlan(partialStack, fullStacks, totalSequences);
	}
	
	public static class SellPlan
	{
		public final int partialStackQty;
		public final int fullStackCount;
		public final int totalSequences;
		
		public SellPlan(int partialStackQty, int fullStackCount,
			int totalSequences)
		{
			this.partialStackQty = partialStackQty;
			this.fullStackCount = fullStackCount;
			this.totalSequences = totalSequences;
		}
	}
}
