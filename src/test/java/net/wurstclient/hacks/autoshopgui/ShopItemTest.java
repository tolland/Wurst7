/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks.autoshopgui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Tests for ShopItem price and stock parsing.
 */
class ShopItemTest
{
	// Price patterns from ShopItem
	private static final Pattern BUY_PRICE_PATTERN =
		Pattern.compile("(?i)buy\\s+price:?\\s*\\$?([0-9,]+)");
	private static final Pattern SELL_PRICE_PATTERN =
		Pattern.compile("(?i)sell\\s+price:?\\s*\\$?([0-9,]+)");
	private static final Pattern GENERIC_PRICE_PATTERN =
		Pattern.compile("(?i)(?<!buy\\s)(?<!sell\\s)price:?\\s*\\$?([0-9,]+)");

	@Test
	void testBuyPricePattern_Explicit()
	{
		// Test ShopGUIPlus "Buy price: $10" format
		String line = "Buy price: $10";
		Matcher matcher = BUY_PRICE_PATTERN.matcher(line);

		assertTrue(matcher.find(), "Should match 'Buy price: $10'");
		assertEquals("10", matcher.group(1), "Should extract '10'");
	}

	@Test
	void testSellPricePattern_Explicit()
	{
		// Test ShopGUIPlus "Sell price: $2" format
		String line = "Sell price: $2";
		Matcher matcher = SELL_PRICE_PATTERN.matcher(line);

		assertTrue(matcher.find(), "Should match 'Sell price: $2'");
		assertEquals("2", matcher.group(1), "Should extract '2'");
	}

	@Test
	void testBuyAndSellPricePattern_BothPresent()
	{
		// Test tooltip with both buy and sell prices
		String tooltip = "Carrot\nBuy Price: $10\nSell Price: $2";

		Matcher buyMatcher = BUY_PRICE_PATTERN.matcher(tooltip);
		assertTrue(buyMatcher.find(), "Should find buy price");
		assertEquals("10", buyMatcher.group(1), "Should extract buy price '10'");

		Matcher sellMatcher = SELL_PRICE_PATTERN.matcher(tooltip);
		assertTrue(sellMatcher.find(), "Should find sell price");
		assertEquals("2", sellMatcher.group(1),
			"Should extract sell price '2'");
	}

	@Test
	void testGenericPricePattern_NoBuySellPrefix()
	{
		// Test generic "Price: $X" without Buy/Sell prefix
		String line = "Price: $50";
		Matcher matcher = GENERIC_PRICE_PATTERN.matcher(line);

		assertTrue(matcher.find(), "Should match generic 'Price: $50'");
		assertEquals("50", matcher.group(1), "Should extract '50'");
	}

	@Test
	void testGenericPricePattern_DoesNotMatchBuyOrSell()
	{
		// Generic pattern should NOT match "Buy price" or "Sell price"
		String buyLine = "Buy price: $10";
		Matcher buyMatcher = GENERIC_PRICE_PATTERN.matcher(buyLine);
		assertFalse(buyMatcher.find(),
			"Generic pattern should NOT match 'Buy price'");

		String sellLine = "Sell price: $5";
		Matcher sellMatcher = GENERIC_PRICE_PATTERN.matcher(sellLine);
		assertFalse(sellMatcher.find(),
			"Generic pattern should NOT match 'Sell price'");
	}

	@Test
	void testPricePattern_WithCommas()
	{
		// Test price with comma separators
		String line = "Buy price: $1,000";
		Matcher matcher = BUY_PRICE_PATTERN.matcher(line);

		assertTrue(matcher.find(), "Should match 'Buy price: $1,000'");
		assertEquals("1,000", matcher.group(1),
			"Should extract '1,000' (comma preserved)");
	}

	@Test
	void testPricePattern_NoDollarSign()
	{
		// Test without dollar sign
		String line = "Sell price: 50";
		Matcher matcher = SELL_PRICE_PATTERN.matcher(line);

		assertTrue(matcher.find(), "Should match 'Sell price: 50'");
		assertEquals("50", matcher.group(1), "Should extract '50'");
	}

	@Test
	void testPricePattern_CaseInsensitive()
	{
		// Test case insensitivity for buy price
		String[] variants = {"buy price: $10", "Buy Price: $10",
			"BUY PRICE: $10", "BuY pRiCe: $10"};

		for(String line : variants)
		{
			Matcher matcher = BUY_PRICE_PATTERN.matcher(line);
			assertTrue(matcher.find(),
				"Should match case-insensitive: " + line);
			assertEquals("10", matcher.group(1));
		}
	}

	@Test
	void testPricePattern_NoMatch()
	{
		// Test strings that should not match any price pattern
		String[] nonMatches =
			{"Sugar Cane", "click MMB to sell all", "Stock: 100"};

		for(String line : nonMatches)
		{
			assertFalse(BUY_PRICE_PATTERN.matcher(line).find(),
				"Buy pattern should NOT match: " + line);
			assertFalse(SELL_PRICE_PATTERN.matcher(line).find(),
				"Sell pattern should NOT match: " + line);
			assertFalse(GENERIC_PRICE_PATTERN.matcher(line).find(),
				"Generic pattern should NOT match: " + line);
		}
	}

	@Test
	void testFullTooltip_RealExample()
	{
		// Test real ShopGUIPlus tooltip format
		String tooltip =
			"Carrot\nBuy Price: $10\nSell Price: $2\nclick with MMB to sell all";

		Matcher buyMatcher = BUY_PRICE_PATTERN.matcher(tooltip);
		assertTrue(buyMatcher.find(), "Should find buy price in full tooltip");
		assertEquals("10", buyMatcher.group(1),
			"Should extract buy price '10'");

		Matcher sellMatcher = SELL_PRICE_PATTERN.matcher(tooltip);
		assertTrue(sellMatcher.find(),
			"Should find sell price in full tooltip");
		assertEquals("2", sellMatcher.group(1),
			"Should extract sell price '2'");
	}

	@Test
	void testFullTooltip_OnlySellPrice()
	{
		// Some items might only have a sell price
		String tooltip = "Sugar Cane\nSell price: $2\nclick MMB to sell all";

		Matcher buyMatcher = BUY_PRICE_PATTERN.matcher(tooltip);
		assertFalse(buyMatcher.find(), "Should NOT find buy price");

		Matcher sellMatcher = SELL_PRICE_PATTERN.matcher(tooltip);
		assertTrue(sellMatcher.find(), "Should find sell price");
		assertEquals("2", sellMatcher.group(1), "Should extract '2'");
	}

	@Test
	void testFullTooltip_OnlyGenericPrice()
	{
		// Some shops might use generic "Price" for buy-only items
		String tooltip = "Diamond\nPrice: $100\nclick to purchase";

		Matcher genericMatcher = GENERIC_PRICE_PATTERN.matcher(tooltip);
		assertTrue(genericMatcher.find(), "Should find generic price");
		assertEquals("100", genericMatcher.group(1), "Should extract '100'");
	}
}
