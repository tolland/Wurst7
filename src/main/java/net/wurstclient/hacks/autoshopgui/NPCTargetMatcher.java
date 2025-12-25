/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks.autoshopgui;

import java.util.List;
import java.util.Optional;

/**
 * Utility class for matching NPCs with shop targets.
 * Handles the logic of finding which NPC (if any) matches any of the enabled
 * targets.
 */
public final class NPCTargetMatcher
{
	/**
	 * Represents a matched NPC and target pair.
	 */
	public static final class Match
	{
		private final NPCInfo npc;
		private final ShopTarget target;

		public Match(NPCInfo npc, ShopTarget target)
		{
			this.npc = npc;
			this.target = target;
		}

		public NPCInfo getNpc()
		{
			return npc;
		}

		public ShopTarget getTarget()
		{
			return target;
		}

		@Override
		public String toString()
		{
			return String.format("Match{npc=%s, target=%s}", npc.getName(),
				target.getNpcName());
		}
	}

	/**
	 * Simplified NPC info for matching purposes. This abstraction allows us to
	 * test the matching logic without requiring Minecraft entity objects.
	 */
	public static final class NPCInfo
	{
		private final String name;
		private final double distanceSq;

		public NPCInfo(String name, double distanceSq)
		{
			this.name = name;
			this.distanceSq = distanceSq;
		}

		public String getName()
		{
			return name;
		}

		public double getDistanceSq()
		{
			return distanceSq;
		}

		@Override
		public String toString()
		{
			return String.format("NPCInfo{name='%s', distanceSq=%.2f}", name,
				distanceSq);
		}
	}

	/**
	 * Finds the first matching (NPC, target) pair from the given NPCs and
	 * targets.
	 *
	 * Matching logic:
	 * - Only considers enabled targets
	 * - Matches if NPC name contains target's npc_name (case-insensitive)
	 * - Returns the first match found (iterates through targets first, then
	 * NPCs)
	 * - Returns empty if no match is found
	 *
	 * Note: If multiple targets could match the same NPC, only the first
	 * target (in list order) will be matched. Complex multi-target scenarios
	 * (e.g., "sell wheat AND sell sugar cane to the same NPC") are not
	 * currently supported.
	 *
	 * @param npcs Available NPCs in range
	 * @param targets Configured shop targets
	 * @return Optional containing the first match, or empty if no match found
	 */
	public static Optional<Match> findFirstMatch(List<NPCInfo> npcs,
		List<ShopTarget> targets)
	{
		if(npcs == null || targets == null)
			return Optional.empty();

		// Filter to only enabled targets
		List<ShopTarget> enabledTargets =
			targets.stream().filter(ShopTarget::isEnabled).toList();

		if(enabledTargets.isEmpty())
			return Optional.empty();

		// Try each target in order
		for(ShopTarget target : enabledTargets)
		{
			// Find first NPC matching this target
			Optional<NPCInfo> matchingNpc = npcs.stream()
				.filter(npc -> npcMatchesTarget(npc, target)).findFirst();

			if(matchingNpc.isPresent())
				return Optional.of(new Match(matchingNpc.get(), target));
		}

		return Optional.empty();
	}

	/**
	 * Checks if an NPC name matches a target's npc_name.
	 *
	 * @param npc The NPC to check
	 * @param target The target to match against
	 * @return true if the NPC name contains the target's npc_name
	 *         (case-insensitive)
	 */
	private static boolean npcMatchesTarget(NPCInfo npc, ShopTarget target)
	{
		if(npc == null || target == null)
			return false;

		String npcName = npc.getName();
		String targetName = target.getNpcName();

		if(npcName == null || targetName == null)
			return false;

		// Case-insensitive contains match
		return npcName.toLowerCase().contains(targetName.toLowerCase());
	}
}
