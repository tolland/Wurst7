/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks.autoshopgui;

import com.google.gson.JsonObject;

/**
 * Represents a single navigation step with slot index and click type.
 */
public final class NavigationStep
{
	private final int slot;
	private final ClickType clickType;
	
	public NavigationStep(int slot, ClickType clickType)
	{
		this.slot = slot;
		this.clickType = clickType;
	}
	
	public static NavigationStep fromJson(JsonObject json)
	{
		int slot = json.get("slot").getAsInt();
		String clickStr =
			json.has("click") ? json.get("click").getAsString() : "left";
		ClickType clickType = ClickType.fromString(clickStr);
		
		return new NavigationStep(slot, clickType);
	}
	
	public JsonObject toJson()
	{
		JsonObject json = new JsonObject();
		json.addProperty("slot", slot);
		json.addProperty("click", clickType.toString().toLowerCase());
		return json;
	}
	
	public int getSlot()
	{
		return slot;
	}
	
	public ClickType getClickType()
	{
		return clickType;
	}
	
	public enum ClickType
	{
		LEFT,
		RIGHT;
		
		public static ClickType fromString(String str)
		{
			return switch(str.toLowerCase())
			{
				case "right", "r" -> RIGHT;
				default -> LEFT;
			};
		}
	}
	
	@Override
	public String toString()
	{
		return String.format("NavigationStep{slot=%d, click=%s}", slot,
			clickType);
	}
}
