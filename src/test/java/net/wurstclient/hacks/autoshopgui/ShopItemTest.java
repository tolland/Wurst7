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
	// Price pattern from ShopItem
	private static final Pattern PRICE_PATTERN =
		Pattern.compile("(?i)price:?\\s*\\$?([0-9,]+)");

	@Test
	void testPricePattern_SellPrice()
	{
		// Test ShopGUIPlus "Sell price: $2" format
		String line = "Sell price: $2";
		Matcher matcher = PRICE_PATTERN.matcher(line);

		assertTrue(matcher.find(), "Should match 'Sell price: $2'");
		assertEquals("2", matcher.group(1), "Should extract '2'");
	}

	@Test
	void testPricePattern_BuyPrice()
	{
		// Test ShopGUIPlus "Buy price: $10" format
		String line = "Buy price: $10";
		Matcher matcher = PRICE_PATTERN.matcher(line);

		assertTrue(matcher.find(), "Should match 'Buy price: $10'");
		assertEquals("10", matcher.group(1), "Should extract '10'");
	}

	@Test
	void testPricePattern_WithCommas()
	{
		// Test price with comma separators
		String line = "Price: $1,000";
		Matcher matcher = PRICE_PATTERN.matcher(line);

		assertTrue(matcher.find(), "Should match 'Price: $1,000'");
		assertEquals("1,000", matcher.group(1),
			"Should extract '1,000' (comma preserved)");
	}

	@Test
	void testPricePattern_NoDollarSign()
	{
		// Test without dollar sign
		String line = "Price: 50";
		Matcher matcher = PRICE_PATTERN.matcher(line);

		assertTrue(matcher.find(), "Should match 'Price: 50'");
		assertEquals("50", matcher.group(1), "Should extract '50'");
	}

	@Test
	void testPricePattern_NoColon()
	{
		// Test without colon
		String line = "Price $75";
		Matcher matcher = PRICE_PATTERN.matcher(line);

		assertTrue(matcher.find(), "Should match 'Price $75'");
		assertEquals("75", matcher.group(1), "Should extract '75'");
	}

	@Test
	void testPricePattern_CaseInsensitive()
	{
		// Test case insensitivity
		String[] variants =
			{"price: $10", "Price: $10", "PRICE: $10", "PrIcE: $10"};

		for(String line : variants)
		{
			Matcher matcher = PRICE_PATTERN.matcher(line);
			assertTrue(matcher.find(),
				"Should match case-insensitive: " + line);
			assertEquals("10", matcher.group(1));
		}
	}

	@Test
	void testPricePattern_NoMatch()
	{
		// Test strings that should not match
		String[] nonMatches = {"Sugar Cane", "click MMB to sell all",
			"Stock: 100", "Cost 5 emeralds", // "Cost" instead of "Price"
		};

		for(String line : nonMatches)
		{
			Matcher matcher = PRICE_PATTERN.matcher(line);
			assertFalse(matcher.find(), "Should NOT match: " + line);
		}
	}

	@Test
	void testPricePattern_FullTooltip()
	{
		// Test full tooltip text from ShopGUIPlus
		String fullTooltip =
			"Sugar Cane\nSell price: $2\nclick MMB to sell all";

		Matcher matcher = PRICE_PATTERN.matcher(fullTooltip);
		assertTrue(matcher.find(), "Should find price in full tooltip");
		assertEquals("2", matcher.group(1), "Should extract '2' from tooltip");
	}

	@Test
	void testPricePattern_MultilineWithBuyAndSell()
	{
		// Some shops might show both buy and sell prices
		String tooltip = "Wheat\nBuy price: $5\nSell price: $2";

		Matcher matcher = PRICE_PATTERN.matcher(tooltip);
		assertTrue(matcher.find(), "Should find first price");
		assertEquals("5", matcher.group(1),
			"Should extract '5' (first occurrence)");

		// Check second occurrence
		assertTrue(matcher.find(), "Should find second price");
		assertEquals("2", matcher.group(1),
			"Should extract '2' (second occurrence)");
	}
}
