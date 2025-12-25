/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks.autoshopgui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.wurstclient.hacks.autoshopgui.QuantitySequencePlanner.ButtonConfig;
import net.wurstclient.hacks.autoshopgui.QuantitySequencePlanner.SellPlan;

class QuantitySequencePlannerTest
{
	private QuantitySequencePlanner planner;
	private ButtonConfig config;

	@BeforeEach
	void setUp()
	{
		// Use default ShopGUIPlus configuration
		// 28=set to 1, 29=sub 10, 30=sub 1, 32=add 1, 33=add 10, 34=set to
		// 64, 50=confirm
		config = ButtonConfig.defaultConfig();
		planner = new QuantitySequencePlanner(config);
	}

	@Test
	void testPlanSequence_Quantity1()
	{
		// Quantity 1: already at default, just confirm
		List<Integer> sequence = planner.planSequence(1);
		assertEquals(List.of(50), sequence, "Qty 1 should just confirm");
	}

	@Test
	void testPlanSequence_Quantity64()
	{
		// Quantity 64: set to 64, then confirm
		List<Integer> sequence = planner.planSequence(64);
		assertEquals(List.of(34, 50), sequence,
			"Qty 64 should set to 64 then confirm");
	}

	@Test
	void testPlanSequence_Quantity17()
	{
		// Quantity 17: from 1, add 10 once, add 1 six times, confirm
		// Expected: 33, 32, 32, 32, 32, 32, 32, 50
		List<Integer> sequence = planner.planSequence(17);
		List<Integer> expected =
			List.of(33, 32, 32, 32, 32, 32, 32, 50);
		assertEquals(expected, sequence,
			"Qty 17 should be: add 10, add 1 x6, confirm");
	}

	@Test
	void testPlanSequence_Quantity11()
	{
		// Quantity 11: from 1, add 10 once, confirm
		List<Integer> sequence = planner.planSequence(11);
		assertEquals(List.of(33, 50), sequence,
			"Qty 11 should add 10 then confirm");
	}

	@Test
	void testPlanSequence_Quantity10()
	{
		// Quantity 10: from 1, add 1 nine times, confirm
		List<Integer> sequence = planner.planSequence(10);
		List<Integer> expected =
			List.of(32, 32, 32, 32, 32, 32, 32, 32, 32, 50);
		assertEquals(expected, sequence,
			"Qty 10 should add 1 nine times then confirm");
	}

	@Test
	void testPlanSequence_Quantity2()
	{
		// Quantity 2: from 1, add 1 once, confirm
		List<Integer> sequence = planner.planSequence(2);
		assertEquals(List.of(32, 50), sequence,
			"Qty 2 should add 1 then confirm");
	}

	@Test
	void testPlanSequence_Quantity63()
	{
		// Quantity 63: from 1, add 10 six times, add 1 twice, confirm
		List<Integer> sequence = planner.planSequence(63);
		List<Integer> expected =
			List.of(33, 33, 33, 33, 33, 33, 32, 32, 50);
		assertEquals(expected, sequence,
			"Qty 63 should add 10 x6, add 1 x2, confirm");
	}

	@Test
	void testPlanSequence_InvalidQuantity_Zero()
	{
		assertThrows(IllegalArgumentException.class,
			() -> planner.planSequence(0),
			"Should throw for quantity 0");
	}

	@Test
	void testPlanSequence_InvalidQuantity_Negative()
	{
		assertThrows(IllegalArgumentException.class,
			() -> planner.planSequence(-5),
			"Should throw for negative quantity");
	}

	@Test
	void testPlanSequence_InvalidQuantity_TooLarge()
	{
		assertThrows(IllegalArgumentException.class,
			() -> planner.planSequence(65),
			"Should throw for quantity > 64");
	}

	@Test
	void testPlanFullSellSequence_OnePartialStack()
	{
		// 17 items: one partial stack of 17
		List<List<Integer>> sequences = planner.planFullSellSequence(17);
		assertEquals(1, sequences.size(),
			"17 items should produce 1 sequence");
		assertEquals(List.of(33, 32, 32, 32, 32, 32, 32, 50),
			sequences.get(0), "Should be the sequence for qty 17");
	}

	@Test
	void testPlanFullSellSequence_OneFullStack()
	{
		// 64 items: one full stack
		List<List<Integer>> sequences = planner.planFullSellSequence(64);
		assertEquals(1, sequences.size(),
			"64 items should produce 1 sequence");
		assertEquals(List.of(34, 50), sequences.get(0),
			"Should be the sequence for qty 64");
	}

	@Test
	void testPlanFullSellSequence_PartialPlusFullStacks()
	{
		// 401 items (6 stacks + 17): one partial (17), then six full (64)
		List<List<Integer>> sequences = planner.planFullSellSequence(401);
		assertEquals(7, sequences.size(),
			"401 items should produce 7 sequences");

		// First sequence should be partial stack of 17
		assertEquals(List.of(33, 32, 32, 32, 32, 32, 32, 50),
			sequences.get(0), "First should be partial stack of 17");

		// Next 6 sequences should be full stacks of 64
		for(int i = 1; i < 7; i++)
		{
			assertEquals(List.of(34, 50), sequences.get(i),
				"Sequence " + i + " should be full stack of 64");
		}
	}

	@Test
	void testPlanFullSellSequence_OnlyFullStacks()
	{
		// 192 items (3 full stacks): no partial
		List<List<Integer>> sequences = planner.planFullSellSequence(192);
		assertEquals(3, sequences.size(),
			"192 items should produce 3 sequences");

		for(int i = 0; i < 3; i++)
		{
			assertEquals(List.of(34, 50), sequences.get(i),
				"All sequences should be full stacks of 64");
		}
	}

	@Test
	void testAnalyzeSellPlan_PartialOnly()
	{
		SellPlan plan = planner.analyzeSellPlan(17);
		assertEquals(17, plan.partialStackQty, "Partial should be 17");
		assertEquals(0, plan.fullStackCount, "No full stacks");
		assertEquals(1, plan.totalSequences, "Should be 1 sequence total");
	}

	@Test
	void testAnalyzeSellPlan_FullStackOnly()
	{
		SellPlan plan = planner.analyzeSellPlan(64);
		assertEquals(0, plan.partialStackQty, "No partial stack");
		assertEquals(1, plan.fullStackCount, "One full stack");
		assertEquals(1, plan.totalSequences, "Should be 1 sequence total");
	}

	@Test
	void testAnalyzeSellPlan_PartialPlusFull()
	{
		SellPlan plan = planner.analyzeSellPlan(401);
		assertEquals(17, plan.partialStackQty, "Partial should be 17");
		assertEquals(6, plan.fullStackCount, "Six full stacks");
		assertEquals(7, plan.totalSequences,
			"Should be 7 sequences total (1 partial + 6 full)");
	}

	@Test
	void testAnalyzeSellPlan_MultipleFullStacks()
	{
		SellPlan plan = planner.analyzeSellPlan(192);
		assertEquals(0, plan.partialStackQty, "No partial stack");
		assertEquals(3, plan.fullStackCount, "Three full stacks");
		assertEquals(3, plan.totalSequences, "Should be 3 sequences total");
	}

	@Test
	void testAnalyzeSellPlan_SingleItem()
	{
		SellPlan plan = planner.analyzeSellPlan(1);
		assertEquals(1, plan.partialStackQty, "Partial should be 1");
		assertEquals(0, plan.fullStackCount, "No full stacks");
		assertEquals(1, plan.totalSequences, "Should be 1 sequence total");
	}

	@Test
	void testCustomButtonConfig()
	{
		// Test with custom button configuration
		ButtonConfig customConfig = new ButtonConfig(10, 11, 12, 13, 14, 15, 16);
		QuantitySequencePlanner customPlanner =
			new QuantitySequencePlanner(customConfig);

		List<Integer> sequence = customPlanner.planSequence(17);
		// Should use custom slot numbers: add10=14, add1=13, confirm=16
		List<Integer> expected = List.of(14, 13, 13, 13, 13, 13, 13, 16);
		assertEquals(expected, sequence,
			"Should use custom button configuration");
	}

	@Test
	void testEdgeCases_MaxQuantity()
	{
		// Edge case: exactly 64 items
		List<Integer> sequence = planner.planSequence(64);
		assertEquals(2, sequence.size(),
			"Max quantity should have 2 clicks");
		assertEquals(34, sequence.get(0), "Should set to 64");
		assertEquals(50, sequence.get(1), "Should confirm");
	}

	@Test
	void testEdgeCases_MinQuantity()
	{
		// Edge case: exactly 1 item
		List<Integer> sequence = planner.planSequence(1);
		assertEquals(1, sequence.size(), "Min quantity should have 1 click");
		assertEquals(50, sequence.get(0), "Should just confirm");
	}

	@Test
	void testSequenceLengthsAreOptimal()
	{
		// Verify we're using the minimum number of clicks for various
		// quantities

		// Qty 20: should be add10 twice = 3 clicks (add10, add10, confirm)
		assertEquals(3, planner.planSequence(21).size(),
			"Qty 21 should use 3 clicks");

		// Qty 30: should be add10 twice, add1 nine times = 12 clicks
		assertEquals(12, planner.planSequence(30).size(),
			"Qty 30 should use 12 clicks");

		// Qty 50: should be add10 four times, add1 nine times = 14 clicks
		assertEquals(14, planner.planSequence(50).size(),
			"Qty 50 should use 14 clicks");
	}
}
