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
import net.wurstclient.hacks.autoshopgui.QuantitySequencePlanner.Operation;
import net.wurstclient.hacks.autoshopgui.QuantitySequencePlanner.SellPlan;

class QuantitySequencePlannerTest
{
	private QuantitySequencePlanner planner;
	
	@BeforeEach
	void setUp()
	{
		// Use default ShopGUIPlus configuration from docs
		ButtonConfig config = ButtonConfig.defaultConfig();
		planner = new QuantitySequencePlanner(config);
	}
	
	@Test
	void testPlanSequence_Quantity1()
	{
		// Quantity 1: already at default, just confirm
		List<Operation> sequence = planner.planSequence(1);
		assertEquals(List.of(Operation.CONFIRM), sequence,
			"Qty 1 should just confirm");
	}
	
	@Test
	void testPlanSequence_Quantity64()
	{
		// Quantity 64: set to 64, then confirm
		List<Operation> sequence = planner.planSequence(64);
		assertEquals(List.of(Operation.SET_64, Operation.CONFIRM), sequence,
			"Qty 64 should set to 64 then confirm");
	}
	
	@Test
	void testPlanSequence_Quantity17_OptimalPath()
	{
		// Quantity 17: optimal is 2×ADD_10 (→21), 4×REMOVE_1 (→17), CONFIRM
		// Total: 7 clicks (vs 1×ADD_10 + 6×ADD_1 = 8 clicks)
		List<Operation> sequence = planner.planSequence(17);
		List<Operation> expected = List.of(Operation.ADD_10, Operation.ADD_10,
			Operation.REMOVE_1, Operation.REMOVE_1, Operation.REMOVE_1,
			Operation.REMOVE_1, Operation.CONFIRM);
		assertEquals(expected, sequence,
			"Qty 17 optimal: 2×ADD_10, 4×REMOVE_1, CONFIRM");
		assertEquals(7, sequence.size(), "Should be 7 clicks total");
	}
	
	@Test
	void testPlanSequence_Quantity40_OptimalPath()
	{
		// Quantity 40: optimal is 4×ADD_10 (→41), 1×REMOVE_1 (→40), CONFIRM
		// Total: 6 clicks (vs 3×ADD_10 + 9×ADD_1 = 13 clicks)
		List<Operation> sequence = planner.planSequence(40);
		List<Operation> expected =
			List.of(Operation.ADD_10, Operation.ADD_10, Operation.ADD_10,
				Operation.ADD_10, Operation.REMOVE_1, Operation.CONFIRM);
		assertEquals(expected, sequence,
			"Qty 40 optimal: 4×ADD_10, 1×REMOVE_1, CONFIRM");
		assertEquals(6, sequence.size(), "Should be 6 clicks total");
	}
	
	@Test
	void testPlanSequence_Quantity15_OptimalPath()
	{
		// Quantity 15: optimal is 1×ADD_10 (→11), 4×ADD_1 (→15), CONFIRM
		// Total: 6 clicks (vs 2×ADD_10 + 6×REMOVE_1 = 9 clicks)
		List<Operation> sequence = planner.planSequence(15);
		List<Operation> expected =
			List.of(Operation.ADD_10, Operation.ADD_1, Operation.ADD_1,
				Operation.ADD_1, Operation.ADD_1, Operation.CONFIRM);
		assertEquals(expected, sequence,
			"Qty 15 optimal: 1×ADD_10, 4×ADD_1, CONFIRM");
		assertEquals(6, sequence.size(), "Should be 6 clicks total");
	}
	
	@Test
	void testPlanSequence_Quantity11()
	{
		// Quantity 11: 1×ADD_10 (→11), CONFIRM
		List<Operation> sequence = planner.planSequence(11);
		assertEquals(List.of(Operation.ADD_10, Operation.CONFIRM), sequence,
			"Qty 11 should add 10 then confirm");
	}
	
	@Test
	void testPlanSequence_Quantity21()
	{
		// Quantity 21: 2×ADD_10 (→21), CONFIRM
		List<Operation> sequence = planner.planSequence(21);
		assertEquals(
			List.of(Operation.ADD_10, Operation.ADD_10, Operation.CONFIRM),
			sequence, "Qty 21 should be 2×ADD_10, CONFIRM");
	}
	
	@Test
	void testPlanSequence_Quantity2()
	{
		// Quantity 2: 1×ADD_1 (→2), CONFIRM
		List<Operation> sequence = planner.planSequence(2);
		assertEquals(List.of(Operation.ADD_1, Operation.CONFIRM), sequence,
			"Qty 2 should add 1 then confirm");
	}
	
	@Test
	void testPlanSequence_Quantity63()
	{
		// Quantity 63: nearest is 61, so 6×ADD_10 (→61), 2×ADD_1 (→63),
		// CONFIRM
		List<Operation> sequence = planner.planSequence(63);
		assertEquals(9, sequence.size(), "Should be 9 clicks total");
		assertTrue(sequence.contains(Operation.ADD_10), "Should use ADD_10");
		assertTrue(sequence.contains(Operation.ADD_1), "Should use ADD_1");
		assertEquals(Operation.CONFIRM, sequence.getLast(),
			"Should end with CONFIRM");
	}
	
	@Test
	void testPlanSequence_Quantity59()
	{
		List<Operation> sequence = planner.planSequence(59);
		assertEquals(9, sequence.size(), "Should be 9 clicks total");
		assertTrue(sequence.contains(Operation.ADD_10), "Should use ADD_10");
		assertTrue(sequence.contains(Operation.REMOVE_1), "Should use ADD_1");
		assertEquals(Operation.CONFIRM, sequence.getLast(),
			"Should end with CONFIRM");
	}
	
	@Test
	void testPlanSequence_InvalidQuantity_Zero()
	{
		assertThrows(IllegalArgumentException.class,
			() -> planner.planSequence(0), "Should throw for quantity 0");
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
			() -> planner.planSequence(65), "Should throw for quantity > 64");
	}
	
	@Test
	void testOperationsToSlots_DefaultConfig()
	{
		List<Operation> ops =
			List.of(Operation.ADD_10, Operation.REMOVE_1, Operation.CONFIRM);
		List<Integer> slots = planner.operationsToSlots(ops);
		
		// Default config: add10=25, remove1=20, confirm=39
		assertEquals(List.of(25, 20, 39), slots,
			"Should map to correct default slots");
	}
	
	@Test
	void testOperationsToSlots_LegacyConfig()
	{
		QuantitySequencePlanner legacyPlanner =
			new QuantitySequencePlanner(ButtonConfig.legacyConfig());
		List<Operation> ops =
			List.of(Operation.ADD_10, Operation.ADD_1, Operation.CONFIRM);
		List<Integer> slots = legacyPlanner.operationsToSlots(ops);
		
		// Legacy config: add10=33, add1=32, confirm=50
		assertEquals(List.of(33, 32, 50), slots,
			"Should map to correct legacy slots");
	}
	
	@Test
	void testPlanFullSellSequence_OnePartialStack()
	{
		// 17 items: one partial stack of 17
		List<List<Operation>> sequences = planner.planFullSellSequence(17);
		assertEquals(1, sequences.size(), "17 items should produce 1 sequence");
		
		List<Operation> firstSeq = sequences.getFirst();
		assertEquals(Operation.CONFIRM, firstSeq.getLast(),
			"Should end with CONFIRM");
	}
	
	@Test
	void testPlanFullSellSequence_OneFullStack()
	{
		// 64 items: one full stack
		List<List<Operation>> sequences = planner.planFullSellSequence(64);
		assertEquals(1, sequences.size(), "64 items should produce 1 sequence");
		assertEquals(List.of(Operation.SET_64, Operation.CONFIRM),
			sequences.get(0), "Should be SET_64, CONFIRM");
	}
	
	@Test
	void testPlanFullSellSequence_104Items()
	{
		// 104 items (1 stack + 40): partial 40, then 1 full stack
		List<List<Operation>> sequences = planner.planFullSellSequence(104);
		assertEquals(2, sequences.size(),
			"104 items should produce 2 sequences");
		
		// First: partial 40 (4×ADD_10, 1×REMOVE_1, CONFIRM)
		List<Operation> partial = sequences.get(0);
		assertEquals(6, partial.size(), "Partial 40 should be 6 operations");
		
		// Second: full stacks optimized (SET_64, CONFIRM)
		List<Operation> fullStacks = sequences.get(1);
		assertEquals(2, fullStacks.size(),
			"1 full stack should be 2 operations");
		assertEquals(Operation.SET_64, fullStacks.get(0),
			"Should start with SET_64");
		assertEquals(Operation.CONFIRM, fullStacks.get(1),
			"Should end with CONFIRM");
	}
	
	@Test
	void testPlanFullSellSequence_401Items()
	{
		// 401 items (6 stacks + 17): one partial (17), then optimized full
		// stacks
		List<List<Operation>> sequences = planner.planFullSellSequence(401);
		assertEquals(2, sequences.size(),
			"401 items should produce 2 sequences (partial + optimized full stacks)");
		
		// First sequence should be partial stack of 17
		List<Operation> partial = sequences.get(0);
		assertEquals(7, partial.size(), "Partial 17 should be 7 operations");
		
		// Second sequence should be optimized full stacks: SET_64, CONFIRM × 6
		List<Operation> fullStacks = sequences.get(1);
		assertEquals(7, fullStacks.size(),
			"6 full stacks should be 7 operations (SET_64 + 6 CONFIRMs)");
		assertEquals(Operation.SET_64, fullStacks.get(0),
			"Should start with SET_64");
		for(int i = 1; i < 7; i++)
		{
			assertEquals(Operation.CONFIRM, fullStacks.get(i),
				"Operations 1-6 should be CONFIRM");
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
	void testAnalyzeSellPlan_104Items()
	{
		SellPlan plan = planner.analyzeSellPlan(104);
		assertEquals(40, plan.partialStackQty, "Partial should be 40");
		assertEquals(1, plan.fullStackCount, "One full stack");
		assertEquals(2, plan.totalSequences,
			"Should be 2 sequences total (1 partial + 1 optimized full)");
	}
	
	@Test
	void testAnalyzeSellPlan_401Items()
	{
		SellPlan plan = planner.analyzeSellPlan(401);
		assertEquals(17, plan.partialStackQty, "Partial should be 17");
		assertEquals(6, plan.fullStackCount, "Six full stacks");
		assertEquals(2, plan.totalSequences,
			"Should be 2 sequences total (1 partial + 1 optimized full stacks)");
	}
	
	@Test
	void testAnalyzeSellPlan_OnlyFullStacks()
	{
		SellPlan plan = planner.analyzeSellPlan(192);
		assertEquals(0, plan.partialStackQty, "No partial stack");
		assertEquals(3, plan.fullStackCount, "Three full stacks");
		assertEquals(1, plan.totalSequences,
			"Should be 1 sequence total (all full stacks optimized)");
	}
	
	@Test
	void testOptimalityComparison()
	{
		// Verify optimal algorithm produces fewer clicks than naive approach
		
		// Qty 40: optimal=6 vs naive=13
		assertEquals(6, planner.planSequence(40).size(),
			"Qty 40 should be 6 clicks");
		
		// Qty 17: optimal=7 vs naive=8
		assertEquals(7, planner.planSequence(17).size(),
			"Qty 17 should be 7 clicks");
		
		// Qty 50: optimal should be better than naive
		List<Operation> seq50 = planner.planSequence(50);
		// 5×ADD_10 (→51), 1×REMOVE_1 (→50), CONFIRM = 7 clicks
		assertEquals(7, seq50.size(), "Qty 50 should be 7 clicks");
	}
	
	@Test
	void testCustomButtonConfig()
	{
		// Test with custom button configuration
		ButtonConfig customConfig =
			new ButtonConfig(10, 11, 12, 13, 14, 15, 16, -1, 17);
		QuantitySequencePlanner customPlanner =
			new QuantitySequencePlanner(customConfig);
		
		List<Operation> ops = customPlanner.planSequence(17);
		List<Integer> slots = customPlanner.operationsToSlots(ops);
		
		// Should use custom slot numbers: add10=14, remove1=12, confirm=16
		assertTrue(slots.contains(14), "Should contain add10 slot");
		assertTrue(slots.contains(12), "Should contain remove1 slot");
		assertEquals(16, slots.get(slots.size() - 1),
			"Should end with confirm slot");
	}
}
