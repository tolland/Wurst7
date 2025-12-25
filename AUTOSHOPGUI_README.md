# AutoShopGUI - Automated Trading for ShopGUIPlus

## Overview

AutoShopGUI is a Wurst hack that automates trading with ShopGUIPlus-DynaShop merchants. Unlike AutoLibrarian which works with vanilla villagers, AutoShopGUI handles multi-stage custom inventory GUIs.

## How It Works

1. **Finds the NPC** - Locates a nearby NPC by name (partial match)
2. **Opens Shop** - Right-clicks the NPC to open the shop GUI
3. **Navigates Menus** - Clicks through configured slots (categories, pages, items)
4. **Executes Trade** - Completes the purchase/sale transaction
5. **Closes & Completes** - Closes the GUI and finishes

## Configuration

### Config File Location
`.minecraft/wurst/shopgui.json`

The config file will be auto-generated with examples on first run. You can also copy `shopgui-example.json` from the Wurst7 repository.

### Config Structure

```json
{
  "targets": [
    {
      "enabled": true,
      "npc_name": "Shop Keeper",
      "item_name": "Diamond Sword",
      "action": "buy",
      "max_price": 1000,
      "quantity": 1,
      "navigation": [
        {"slot": 10, "click": "left"},
        {"slot": 15, "click": "left"},
        {"slot": 11, "click": "left"}
      ]
    }
  ]
}
```

### Field Descriptions

- **enabled** - Set to `true` to activate this target
- **npc_name** - Partial name of the NPC (case-insensitive contains match)
- **item_name** - Name of the item to buy/sell (used to find items in inventory for "sell" action)
- **action** - "buy" or "sell"
  - **"sell"**: Automatically sells ALL matching items in your inventory (ignores quantity field)
  - **"buy"**: Not fully implemented yet (navigation only)
- **max_price** - Maximum price willing to pay (not enforced yet, planned feature)
- **quantity** - How many to buy/sell
  - For "sell": **Currently ignored** - always sells all matching items
  - For "buy": Not implemented yet
- **navigation** - Array of navigation steps with slot and click type
  - **New format** (recommended): `{"slot": 10, "click": "left"}` or `{"slot": 10, "click": "right"}`
  - **Legacy format** (backward compatible): Just a number like `10` (defaults to left click)
  - **click** options: `"left"` or `"right"` (use right-click for some shop GUIs that require it)

## How to Configure Navigation

This is the most important part! You need to figure out which slots to click.

### Step-by-Step Process

1. **Enable Debug Mode**
   - Open Wurst menu (Right Shift by default)
   - Find AutoShopGUI
   - Enable "Debug mode" setting

2. **Manually Test Navigation**
   - Find the shop NPC in-game
   - Right-click to open the shop
   - Note the screen title shown in chat (debug mode)
   - Count the slots to find which ones to click

3. **Understand Slot Numbering**
   - Slots are numbered left-to-right, top-to-bottom
   - Starting from 0 (top-left corner)
   - For a standard chest GUI (9 slots wide):
     ```
     Row 1:  0  1  2  3  4  5  6  7  8
     Row 2:  9 10 11 12 13 14 15 16 17
     Row 3: 18 19 20 21 22 23 24 25 26
     etc.
     ```

4. **Record Your Navigation Path**
   - Example: To buy a Diamond Sword:
     - Slot 10: "Weapons" category (left-click)
     - Slot 15: "Diamond Sword" item (left-click)
     - Slot 11: "Confirm Purchase" button (left-click)
   - Add to config:
     ```json
     "navigation": [
       {"slot": 10, "click": "left"},
       {"slot": 15, "click": "left"},
       {"slot": 11, "click": "left"}
     ]
     ```
   - **Note**: Most shops use left-click, but some may require right-click for certain actions

5. **Test Your Config**
   - Save `shopgui.json`
   - Stand near the NPC
   - Enable AutoShopGUI
   - Watch debug messages to see if it works

### Example: Multi-Page Navigation

If an item is on page 2:
```json
{
  "navigation": [
    {"slot": 13, "click": "left"},    // Open "Enchantments" category
    {"slot": 31, "click": "left"},    // Click "Next Page" button
    {"slot": 25, "click": "left"},    // Select "Sharpness V"
    {"slot": 11, "click": "left"}     // Confirm purchase
  ]
}
```

**Legacy format** (still supported):
```json
{
  "navigation": [13, 31, 25, 11]    // All default to left-click
}
```

## Settings

### Range (1-6 blocks)
- How far away the NPC can be
- Default: 5 blocks

### Face Target
- **Off** - Don't rotate camera (detectable by anti-cheat)
- **Server-side** - Rotate on server only (recommended)
- **Client-side** - Rotate camera visually
- **Client and Server** - Rotate both

### Swing Hand
- How to animate when clicking NPC and GUI
- **Off** / **Server-side** / **Client-side**

### Click Delay (50-1000ms)
- Minimum delay between GUI clicks
- Default: 250ms
- With "Wait for update" enabled, this is just the minimum wait time
- Increase if server lags or has anti-cheat

### Wait for GUI Update
- **Enabled by default** - Waits for the inventory to update after each click instead of using fixed delays
- More reliable on laggy servers but may be slightly slower
- Disable for faster operation on low-latency servers

### Max Wait Time (500-5000ms)
- Maximum time to wait for GUI update before proceeding anyway
- Default: 2000ms
- Only used when "Wait for update" is enabled
- Increase if server is very laggy

### Debug Mode
- Shows detailed information about:
  - Which NPC was found
  - Screen titles
  - Slot indices being clicked
  - Item names in each slot
  - GUI update events
  - Quantity sequences for selling

## Limitations & Future Enhancements

### Current Limitations
- Only processes ONE target per activation
- Doesn't verify prices before buying
- Doesn't check if you have enough money
- Navigation is purely slot-based (blind clicking)

### Planned Features
- [ ] Parse item lore to verify prices
- [ ] Check inventory for required currency
- [ ] Support multiple targets in sequence
- [ ] Auto-retry if shop GUI changes
- [ ] Recording mode (click manually, save path automatically)
- [ ] Pattern recognition for common shop layouts
- [ ] Conditional logic (if price < X, buy Y quantity)

## Troubleshooting

### "No shop config found or config is empty"
- Create `.minecraft/wurst/shopgui.json`
- Copy from `shopgui-example.json`
- Make sure at least one target has `"enabled": true`

### "Looking for NPC: Shop Keeper" (keeps searching)
- NPC might not be in range
- Check NPC name is correct (F3 to see exact name)
- Try increasing Range setting

### "Shop GUI closed unexpectedly"
- Click delay might be too fast
- Increase "Click delay" setting to 200-300ms
- Server might have anti-spam protection

### Wrong items being clicked
- Re-check slot indices in debug mode
- Slot numbering starts at 0, not 1
- Count carefully: left-to-right, top-to-bottom

### Navigation completes but nothing happens
- Add confirmation slot to navigation
- Some shops require clicking "Confirm" button
- Check if you have enough currency in inventory

## Examples

### Simple Buy
```json
{
  "enabled": true,
  "npc_name": "Tool Shop",
  "item_name": "Diamond Pickaxe",
  "action": "buy",
  "navigation": [
    {"slot": 12, "click": "left"},
    {"slot": 14, "click": "left"},
    {"slot": 11, "click": "left"}
  ]
}
```

### Sell Items (Sells ALL matching items)
```json
{
  "enabled": true,
  "npc_name": "Merchant",
  "item_name": "Cobblestone",
  "action": "sell",
  "quantity": 64,
  "navigation": [
    {"slot": 9, "click": "left"},
    {"slot": 18, "click": "right"}
  ]
}
```
**Note**: The "sell" action automatically sells ALL Cobblestone in your inventory, regardless of the quantity field. The bot intelligently handles the quantity selection buttons.

### Multi-Stage Navigation
```json
{
  "enabled": true,
  "npc_name": "Rare Items",
  "item_name": "Netherite Sword",
  "action": "buy",
  "navigation": [
    {"slot": 10, "click": "left"},    // Category: Weapons
    {"slot": 26, "click": "left"},    // Subcategory: Rare
    {"slot": 31, "click": "left"},    // Next page
    {"slot": 15, "click": "left"},    // Item: Netherite Sword
    {"slot": 11, "click": "left"}     // Confirm
  ]
}
```

## Comparison with AutoLibrarian

| Feature | AutoLibrarian | AutoShopGUI |
|---------|---------------|-------------|
| Target | Villagers | NPCs (Citizens plugin) |
| GUI Type | Vanilla merchant screen | Custom inventory GUIs |
| Data Source | TradeOfferList API | Manual slot mapping |
| Configuration | Wurst settings menu | JSON config file |
| Price Detection | Automatic (from trade data) | Manual (planned: lore parsing) |
| Multi-stage | No (single screen) | Yes (navigate multiple menus) |
| Job Site Breaking | Yes (lectern) | No (NPCs don't have job sites) |
| Complexity | Simple | Complex (requires mapping) |

## Credits

Based on the AutoLibrarian hack architecture. Designed for use with ShopGUIPlus-DynaShop and compatible shop plugins.
