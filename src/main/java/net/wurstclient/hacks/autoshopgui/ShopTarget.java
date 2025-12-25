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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Represents a single trading target configuration.
 */
public final class ShopTarget
{
	private final String npcName;
	private final String itemName;
	private final String action; // "buy" or "sell"
	private final int maxPrice;
	private final int quantity;
	private final List<NavigationStep> navigation; // Navigation steps with click types
	private final boolean enabled;

	public ShopTarget(String npcName, String itemName, String action,
		int maxPrice, int quantity, List<NavigationStep> navigation,
		boolean enabled)
	{
		this.npcName = npcName;
		this.itemName = itemName;
		this.action = action;
		this.maxPrice = maxPrice;
		this.quantity = quantity;
		this.navigation = navigation;
		this.enabled = enabled;
	}
	
	public static ShopTarget fromJson(JsonObject json)
	{
		String npcName = json.get("npc_name").getAsString();
		String itemName = json.get("item_name").getAsString();
		String action = json.get("action").getAsString();
		int maxPrice = json.has("max_price") ? json.get("max_price").getAsInt()
			: Integer.MAX_VALUE;
		int quantity =
			json.has("quantity") ? json.get("quantity").getAsInt() : 1;
		boolean enabled =
			json.has("enabled") ? json.get("enabled").getAsBoolean() : true;

		List<NavigationStep> navigation = new ArrayList<>();
		if(json.has("navigation"))
		{
			JsonArray navArray = json.getAsJsonArray("navigation");
			for(int i = 0; i < navArray.size(); i++)
			{
				var element = navArray.get(i);
				if(element.isJsonObject())
				{
					// New format: {slot: 10, click: "right"}
					navigation
						.add(NavigationStep.fromJson(element.getAsJsonObject()));
				}else
				{
					// Legacy format: just a number (defaults to left click)
					int slot = element.getAsInt();
					navigation.add(new NavigationStep(slot,
						NavigationStep.ClickType.LEFT));
				}
			}
		}
		
		return new ShopTarget(npcName, itemName, action, maxPrice, quantity,
			navigation, enabled);
	}
	
	public JsonObject toJson()
	{
		JsonObject json = new JsonObject();
		json.addProperty("npc_name", npcName);
		json.addProperty("item_name", itemName);
		json.addProperty("action", action);
		json.addProperty("max_price", maxPrice);
		json.addProperty("quantity", quantity);
		json.addProperty("enabled", enabled);
		
		JsonArray navArray = new JsonArray();
		for(NavigationStep step : navigation)
			navArray.add(step.toJson());
		json.add("navigation", navArray);
		
		return json;
	}
	
	public String getNpcName()
	{
		return npcName;
	}
	
	public String getItemName()
	{
		return itemName;
	}
	
	public String getAction()
	{
		return action;
	}
	
	public int getMaxPrice()
	{
		return maxPrice;
	}
	
	public int getQuantity()
	{
		return quantity;
	}
	
	public List<NavigationStep> getNavigation()
	{
		return navigation;
	}
	
	public boolean isEnabled()
	{
		return enabled;
	}
}
