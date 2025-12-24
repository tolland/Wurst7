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
      "navigation": [10, 15, 11]
    }
  ]
}
```

### Field Descriptions

- **enabled** - Set to `true` to activate this target
- **npc_name** - Partial name of the NPC (case-insensitive contains match)
- **item_name** - Name of the item (for reference only, not used for detection yet)
- **action** - "buy" or "sell" (for reference, not enforced yet)
- **max_price** - Maximum price willing to pay (not enforced yet, planned feature)
- **quantity** - How many to buy/sell (not implemented yet)
- **navigation** - Array of slot indices to click in order

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
     - Slot 10: "Weapons" category
     - Slot 15: "Diamond Sword" item
     - Slot 11: "Confirm Purchase" button
   - Add to config: `"navigation": [10, 15, 11]`

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
    13,    // Open "Enchantments" category
    31,    // Click "Next Page" button
    25,    // Select "Sharpness V"
    11     // Confirm purchase
  ]
}
```

## Settings

### Range (1-6 blocks)
- How far away the NPC can be
- Default: 5 blocks

### Facing
- **Off** - Don't rotate camera (detectable by anti-cheat)
- **Server-side** - Rotate on server only (recommended)
- **Client-side** - Rotate camera visually

### Swing Hand
- How to animate when clicking NPC and GUI
- **Off** / **Server-side** / **Client-side**

### Click Delay (50-1000ms)
- Delay between GUI clicks
- Default: 150ms
- Increase if server lags or has anti-cheat

### Debug Mode
- Shows detailed information about:
  - Which NPC was found
  - Screen titles
  - Slot indices being clicked
  - Item names in each slot

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
  "navigation": [12, 14, 11]
}
```

### Sell Items
```json
{
  "enabled": true,
  "npc_name": "Merchant",
  "item_name": "Cobblestone",
  "action": "sell",
  "navigation": [9, 18, 11]
}
```

### Multi-Stage Navigation
```json
{
  "enabled": true,
  "npc_name": "Rare Items",
  "item_name": "Netherite Sword",
  "action": "buy",
  "navigation": [
    10,    // Category: Weapons
    26,    // Subcategory: Rare
    31,    // Next page
    15,    // Item: Netherite Sword
    11     // Confirm
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
