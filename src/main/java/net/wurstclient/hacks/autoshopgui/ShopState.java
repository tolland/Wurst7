/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks.autoshopgui;

public enum ShopState
{
	FIND_NPC("Finding NPC"),
	OPEN_SHOP("Opening shop"),
	NAVIGATE_MENU("Navigating menu"),
	EXECUTE_TRADE("Executing trade"),
	COMPLETE("Complete"),
	ERROR("Error");

	private final String description;

	ShopState(String description)
	{
		this.description = description;
	}

	public String getDescription()
	{
		return description;
	}
}
