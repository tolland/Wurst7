/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
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
	private final ButtonConfig config;

	/**
	 * Configuration for quantity adjustment buttons in ShopGUI.
	 */
	public static class ButtonConfig
	{
		public final int setTo1Slot;
		public final int sub10Slot;
		public final int sub1Slot;
		public final int add1Slot;
		public final int add10Slot;
		public final int setTo64Slot;
		public final int confirmSlot;

		public ButtonConfig(int setTo1Slot, int sub10Slot, int sub1Slot,
			int add1Slot, int add10Slot, int setTo64Slot, int confirmSlot)
		{
			this.setTo1Slot = setTo1Slot;
			this.sub10Slot = sub10Slot;
			this.sub1Slot = sub1Slot;
			this.add1Slot = add1Slot;
			this.add10Slot = add10Slot;
			this.setTo64Slot = setTo64Slot;
			this.confirmSlot = confirmSlot;
		}

		/**
		 * Default ShopGUIPlus configuration.
		 * Slots: 28=set to 1, 29=sub 10, 30=sub 1, 32=add 1, 33=add 10,
		 * 34=set to 64, 50=confirm
		 */
		public static ButtonConfig defaultConfig()
		{
			return new ButtonConfig(28, 29, 30, 32, 33, 34, 50);
		}
	}

	public QuantitySequencePlanner(ButtonConfig config)
	{
		this.config = config;
	}

	/**
	 * Generate the optimal click sequence to reach a target quantity.
	 *
	 * Assumes the starting quantity is always 1 (ShopGUI default).
	 *
	 * @param targetQuantity The desired quantity to reach (1-64)
	 * @return List of slot indices to click in order (including confirm)
	 */
	public List<Integer> planSequence(int targetQuantity)
	{
		if(targetQuantity < 1 || targetQuantity > 64)
			throw new IllegalArgumentException(
				"Target quantity must be between 1 and 64, got: "
					+ targetQuantity);

		List<Integer> clicks = new ArrayList<>();

		if(targetQuantity == 64)
		{
			// Just set to 64
			clicks.add(config.setTo64Slot);
		}else if(targetQuantity > 1)
		{
			// Calculate how to reach target from initial value of 1
			int needed = targetQuantity - 1;

			// Add 10s
			while(needed >= 10)
			{
				clicks.add(config.add10Slot);
				needed -= 10;
			}

			// Add 1s
			while(needed > 0)
			{
				clicks.add(config.add1Slot);
				needed--;
			}
		}
		// If targetQuantity == 1, no clicks needed (already starts at 1)

		// Add confirm click
		clicks.add(config.confirmSlot);

		return clicks;
	}

	/**
	 * Plan the full selling sequence for a given total quantity.
	 *
	 * Returns a list of sequences: first the partial stack (if any), then full
	 * stacks.
	 *
	 * @param totalQuantity Total items to sell
	 * @return List of sequences, each representing one transaction
	 */
	public List<List<Integer>> planFullSellSequence(int totalQuantity)
	{
		if(totalQuantity < 1)
			throw new IllegalArgumentException(
				"Total quantity must be at least 1, got: " + totalQuantity);

		List<List<Integer>> sequences = new ArrayList<>();

		int partialStack = totalQuantity % 64;
		int fullStacks = totalQuantity / 64;

		// Sell partial stack first (if it exists)
		if(partialStack > 0)
		{
			sequences.add(planSequence(partialStack));
		}

		// Then sell each full stack
		for(int i = 0; i < fullStacks; i++)
		{
			sequences.add(planSequence(64));
		}

		return sequences;
	}

	/**
	 * Calculate how many items will be sold given the planned sequences.
	 *
	 * @param totalQuantity Total items to sell
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
		int totalSequences =
			(partialStack > 0 ? 1 : 0) + fullStacks;

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
