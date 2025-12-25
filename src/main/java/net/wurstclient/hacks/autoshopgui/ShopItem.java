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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.wurstclient.WurstClient;

/**
 * Represents an item in a shop GUI with parsed price and stock information.
 */
public final class ShopItem
{
	private static final Pattern BUY_PRICE_PATTERN =
		Pattern.compile("(?i)buy\\s+price:?\\s*\\$?([0-9,]+)");
	private static final Pattern SELL_PRICE_PATTERN =
		Pattern.compile("(?i)sell\\s+price:?\\s*\\$?([0-9,]+)");
	private static final Pattern GENERIC_PRICE_PATTERN =
		Pattern.compile("(?i)(?<!buy\\s)(?<!sell\\s)price:?\\s*\\$?([0-9,]+)");
	private static final Pattern STOCK_PATTERN =
		Pattern.compile("(?i)stock:?\\s*([0-9,]+)");

	private final String name;
	private final int buyPrice;
	private final int sellPrice;
	private final int stock;
	private final int slotIndex;

	public ShopItem(String name, int buyPrice, int sellPrice, int stock,
		int slotIndex)
	{
		this.name = name;
		this.buyPrice = buyPrice;
		this.sellPrice = sellPrice;
		this.stock = stock;
		this.slotIndex = slotIndex;
	}

	/**
	 * Parses a ShopItem from an ItemStack by reading its lore.
	 */
	public static ShopItem fromItemStack(ItemStack stack, int slotIndex)
	{
		if(stack.isEmpty())
			return null;

		String name = stack.getHoverName().getString();
		List<String> lore = getLoreLines(stack);

		int buyPrice = parseBuyPrice(lore);
		int sellPrice = parseSellPrice(lore);
		int stock = parseStock(lore);

		return new ShopItem(name, buyPrice, sellPrice, stock, slotIndex);
	}

	private static List<String> getLoreLines(ItemStack stack)
	{
		List<String> lines = new ArrayList<>();

		// Get tooltip text (includes lore)
		List<Component> tooltip =
			stack.getTooltipLines(Item.TooltipContext.EMPTY,
				WurstClient.MC.player, TooltipFlag.NORMAL);

		// Skip first line (item name) and extract lore
		for(int i = 1; i < tooltip.size(); i++)
		{
			String line = tooltip.get(i).getString();
			lines.add(line);
		}

		return lines;
	}

	private static int parseBuyPrice(List<String> lore)
	{
		// First try to find explicit "Buy price: $X"
		for(String line : lore)
		{
			Matcher matcher = BUY_PRICE_PATTERN.matcher(line);
			if(matcher.find())
			{
				String priceStr = matcher.group(1).replace(",", "");
				try
				{
					return Integer.parseInt(priceStr);
				}catch(NumberFormatException e)
				{
					// Invalid price format
				}
			}
		}

		// If no explicit buy price, check for generic "Price: $X"
		// (shops that only sell or only buy use generic "Price")
		for(String line : lore)
		{
			Matcher matcher = GENERIC_PRICE_PATTERN.matcher(line);
			if(matcher.find())
			{
				String priceStr = matcher.group(1).replace(",", "");
				try
				{
					return Integer.parseInt(priceStr);
				}catch(NumberFormatException e)
				{
					// Invalid price format
				}
			}
		}

		return -1; // Buy price not found
	}

	private static int parseSellPrice(List<String> lore)
	{
		// Look for explicit "Sell price: $X"
		for(String line : lore)
		{
			Matcher matcher = SELL_PRICE_PATTERN.matcher(line);
			if(matcher.find())
			{
				String priceStr = matcher.group(1).replace(",", "");
				try
				{
					return Integer.parseInt(priceStr);
				}catch(NumberFormatException e)
				{
					// Invalid price format
				}
			}
		}

		// Note: We don't fall back to generic price for sell price
		// because if there's a generic "Price", it's more likely to be a buy
		// price
		return -1; // Sell price not found
	}

	private static int parseStock(List<String> lore)
	{
		for(String line : lore)
		{
			Matcher matcher = STOCK_PATTERN.matcher(line);
			if(matcher.find())
			{
				String stockStr = matcher.group(1).replace(",", "");
				try
				{
					return Integer.parseInt(stockStr);
				}catch(NumberFormatException e)
				{
					// Invalid stock format
				}
			}
		}
		return -1; // Stock not found
	}

	public String getName()
	{
		return name;
	}

	public int getBuyPrice()
	{
		return buyPrice;
	}

	public int getSellPrice()
	{
		return sellPrice;
	}

	public int getStock()
	{
		return stock;
	}

	public int getSlotIndex()
	{
		return slotIndex;
	}

	public boolean hasValidBuyPrice()
	{
		return buyPrice >= 0;
	}

	public boolean hasValidSellPrice()
	{
		return sellPrice >= 0;
	}

	public boolean hasValidStock()
	{
		return stock >= 0;
	}

	@Override
	public String toString()
	{
		return String.format(
			"ShopItem{name='%s', buyPrice=%d, sellPrice=%d, stock=%d, slot=%d}",
			name, buyPrice, sellPrice, stock, slotIndex);
	}
}
