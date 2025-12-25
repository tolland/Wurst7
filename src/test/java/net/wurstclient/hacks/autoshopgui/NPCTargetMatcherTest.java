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
import java.util.Optional;

import org.junit.jupiter.api.Test;

import net.wurstclient.hacks.autoshopgui.NPCTargetMatcher.Match;
import net.wurstclient.hacks.autoshopgui.NPCTargetMatcher.NPCInfo;

class NPCTargetMatcherTest
{
	// Helper method to create a ShopTarget
	private ShopTarget createTarget(String npcName, String itemName,
		boolean enabled)
	{
		List<NavigationStep> nav =
			List.of(new NavigationStep(10, NavigationStep.ClickType.LEFT));
		return new ShopTarget(npcName, itemName, "sell", 1000, 64, nav,
			enabled);
	}
	
	@Test
	void testFindFirstMatch_SimpleMatch()
	{
		// Single NPC, single target, should match
		List<NPCInfo> npcs = List.of(new NPCInfo("Farm Shop", 10.0));
		List<ShopTarget> targets =
			List.of(createTarget("Farm Shop", "Wheat", true));
		
		Optional<Match> result = NPCTargetMatcher.findFirstMatch(npcs, targets);
		
		assertTrue(result.isPresent(), "Should find a match");
		assertEquals("Farm Shop", result.get().getNpc().getName());
		assertEquals("Farm Shop", result.get().getTarget().getNpcName());
	}
	
	@Test
	void testFindFirstMatch_PartialNameMatch()
	{
		// NPC name contains target name (case-insensitive)
		List<NPCInfo> npcs = List.of(new NPCInfo("The Farming Shop", 10.0));
		List<ShopTarget> targets =
			List.of(createTarget("farming", "Wheat", true));
		
		Optional<Match> result = NPCTargetMatcher.findFirstMatch(npcs, targets);
		
		assertTrue(result.isPresent(),
			"Should match partial name (case-insensitive)");
		assertEquals("The Farming Shop", result.get().getNpc().getName());
	}
	
	@Test
	void testFindFirstMatch_CaseInsensitive()
	{
		// Different cases should still match
		List<NPCInfo> npcs = List.of(new NPCInfo("FARM SHOP", 10.0));
		List<ShopTarget> targets =
			List.of(createTarget("farm shop", "Wheat", true));
		
		Optional<Match> result = NPCTargetMatcher.findFirstMatch(npcs, targets);
		
		assertTrue(result.isPresent(), "Should match case-insensitively");
	}
	
	@Test
	void testFindFirstMatch_MultipleNPCs_FirstMatch()
	{
		// Multiple NPCs in range, should return first match for first enabled
		// target
		List<NPCInfo> npcs = List.of(new NPCInfo("Wood Shop", 5.0),
			new NPCInfo("Farm Shop", 10.0), new NPCInfo("Mining Shop", 15.0));
		List<ShopTarget> targets =
			List.of(createTarget("Farm Shop", "Wheat", true));
		
		Optional<Match> result = NPCTargetMatcher.findFirstMatch(npcs, targets);
		
		assertTrue(result.isPresent(), "Should find a match");
		assertEquals("Farm Shop", result.get().getNpc().getName());
	}
	
	@Test
	void testFindFirstMatch_MultipleTargets_FirstEnabledMatches()
	{
		// Multiple enabled targets, should match first target that has an NPC
		List<NPCInfo> npcs = List.of(new NPCInfo("Farm Shop", 10.0),
			new NPCInfo("Mining Shop", 15.0));
		List<ShopTarget> targets =
			List.of(createTarget("Wood Shop", "Oak Log", true), // No NPC for
																// this
				createTarget("Farm Shop", "Wheat", true), // This should match
				createTarget("Mining Shop", "Iron", true) // Has NPC but second
			);
			
		Optional<Match> result = NPCTargetMatcher.findFirstMatch(npcs, targets);
		
		assertTrue(result.isPresent(), "Should find a match");
		assertEquals("Farm Shop", result.get().getNpc().getName());
		assertEquals("Wheat", result.get().getTarget().getItemName());
	}
	
	@Test
	void testFindFirstMatch_DisabledTargetsIgnored()
	{
		// Disabled targets should be ignored
		List<NPCInfo> npcs = List.of(new NPCInfo("Farm Shop", 10.0));
		List<ShopTarget> targets =
			List.of(createTarget("Farm Shop", "Wheat", false), // Disabled
				createTarget("Farm Shop", "Sugar Cane", true) // Enabled
			);
			
		Optional<Match> result = NPCTargetMatcher.findFirstMatch(npcs, targets);
		
		assertTrue(result.isPresent(), "Should find a match");
		assertEquals("Sugar Cane", result.get().getTarget().getItemName());
	}
	
	@Test
	void testFindFirstMatch_AllTargetsDisabled()
	{
		// If all targets are disabled, should return empty
		List<NPCInfo> npcs = List.of(new NPCInfo("Farm Shop", 10.0));
		List<ShopTarget> targets =
			List.of(createTarget("Farm Shop", "Wheat", false),
				createTarget("Farm Shop", "Sugar Cane", false));
		
		Optional<Match> result = NPCTargetMatcher.findFirstMatch(npcs, targets);
		
		assertFalse(result.isPresent(),
			"Should not find a match when all targets disabled");
	}
	
	@Test
	void testFindFirstMatch_NoMatchingNPC()
	{
		// No NPC matches any target
		List<NPCInfo> npcs = List.of(new NPCInfo("Wood Shop", 10.0));
		List<ShopTarget> targets =
			List.of(createTarget("Farm Shop", "Wheat", true));
		
		Optional<Match> result = NPCTargetMatcher.findFirstMatch(npcs, targets);
		
		assertFalse(result.isPresent(), "Should not find a match");
	}
	
	@Test
	void testFindFirstMatch_EmptyNPCList()
	{
		// No NPCs in range
		List<NPCInfo> npcs = List.of();
		List<ShopTarget> targets =
			List.of(createTarget("Farm Shop", "Wheat", true));
		
		Optional<Match> result = NPCTargetMatcher.findFirstMatch(npcs, targets);
		
		assertFalse(result.isPresent(), "Should not find a match with no NPCs");
	}
	
	@Test
	void testFindFirstMatch_EmptyTargetList()
	{
		// No targets configured
		List<NPCInfo> npcs = List.of(new NPCInfo("Farm Shop", 10.0));
		List<ShopTarget> targets = List.of();
		
		Optional<Match> result = NPCTargetMatcher.findFirstMatch(npcs, targets);
		
		assertFalse(result.isPresent(),
			"Should not find a match with no targets");
	}
	
	@Test
	void testFindFirstMatch_NullNPCList()
	{
		// Null NPC list should return empty
		List<ShopTarget> targets =
			List.of(createTarget("Farm Shop", "Wheat", true));
		
		Optional<Match> result = NPCTargetMatcher.findFirstMatch(null, targets);
		
		assertFalse(result.isPresent(), "Should handle null NPC list");
	}
	
	@Test
	void testFindFirstMatch_NullTargetList()
	{
		// Null target list should return empty
		List<NPCInfo> npcs = List.of(new NPCInfo("Farm Shop", 10.0));
		
		Optional<Match> result = NPCTargetMatcher.findFirstMatch(npcs, null);
		
		assertFalse(result.isPresent(), "Should handle null target list");
	}
	
	@Test
	void testFindFirstMatch_PriorityOrder()
	{
		// Test that target priority is respected (first enabled target wins)
		List<NPCInfo> npcs = List.of(new NPCInfo("Farm Shop", 10.0),
			new NPCInfo("Mining Shop", 15.0));
		List<ShopTarget> targets =
			List.of(createTarget("Mining Shop", "Iron", true), // First enabled
				createTarget("Farm Shop", "Wheat", true) // Second enabled
			);
			
		Optional<Match> result = NPCTargetMatcher.findFirstMatch(npcs, targets);
		
		assertTrue(result.isPresent(), "Should find a match");
		assertEquals("Mining Shop", result.get().getNpc().getName());
		assertEquals("Iron", result.get().getTarget().getItemName());
	}
	
	@Test
	void testFindFirstMatch_MultipleNPCsSameTarget()
	{
		// Two NPCs match the same target name - should return first NPC
		List<NPCInfo> npcs = List.of(new NPCInfo("Farming Shop 1", 10.0),
			new NPCInfo("Farming Shop 2", 20.0));
		List<ShopTarget> targets =
			List.of(createTarget("Farming", "Wheat", true));
		
		Optional<Match> result = NPCTargetMatcher.findFirstMatch(npcs, targets);
		
		assertTrue(result.isPresent(), "Should find a match");
		assertEquals("Farming Shop 1", result.get().getNpc().getName(),
			"Should return first matching NPC");
	}
	
	@Test
	void testFindFirstMatch_ComplexScenario()
	{
		// Complex scenario from user's example:
		// - Two enabled targets for different NPCs selling same item
		// - One NPC present that matches second target
		List<NPCInfo> npcs =
			List.of(new NPCInfo("Farmer Bob - farming specialist", 10.0));
		List<ShopTarget> targets =
			List.of(createTarget("Farm Shop", "Sugar Cane", true), // Not
																	// present
				createTarget("farming", "Sugar Cane", true) // Matches!
			);
			
		Optional<Match> result = NPCTargetMatcher.findFirstMatch(npcs, targets);
		
		assertTrue(result.isPresent(), "Should find the farming NPC");
		assertEquals("Farmer Bob - farming specialist",
			result.get().getNpc().getName());
		assertEquals("farming", result.get().getTarget().getNpcName());
		assertEquals("Sugar Cane", result.get().getTarget().getItemName());
	}
	
	@Test
	void testFindFirstMatch_PartialNameEdgeCases()
	{
		// Test various partial name matching scenarios
		List<NPCInfo> npcs = List.of(new NPCInfo("ShopGUI Farmer", 10.0));
		
		// Should match
		assertTrue(
			NPCTargetMatcher
				.findFirstMatch(npcs,
					List.of(createTarget("Farmer", "Wheat", true)))
				.isPresent());
		assertTrue(
			NPCTargetMatcher
				.findFirstMatch(npcs,
					List.of(createTarget("ShopGUI", "Wheat", true)))
				.isPresent());
		assertTrue(
			NPCTargetMatcher
				.findFirstMatch(npcs,
					List.of(createTarget("GUI Far", "Wheat", true)))
				.isPresent());
		
		// Should not match
		assertFalse(NPCTargetMatcher
			.findFirstMatch(npcs, List.of(createTarget("Mining", "Iron", true)))
			.isPresent());
	}
	
	@Test
	void testNPCInfo_Constructor()
	{
		NPCInfo npc = new NPCInfo("Test NPC", 25.5);
		assertEquals("Test NPC", npc.getName());
		assertEquals(25.5, npc.getDistanceSq(), 0.001);
	}
	
	@Test
	void testMatch_Constructor()
	{
		NPCInfo npc = new NPCInfo("Farm Shop", 10.0);
		ShopTarget target = createTarget("Farm Shop", "Wheat", true);
		Match match = new Match(npc, target);
		
		assertEquals(npc, match.getNpc());
		assertEquals(target, match.getTarget());
	}
}
