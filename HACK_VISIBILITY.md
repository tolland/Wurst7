# Hack Visibility Configuration

## Overview

Wurst now supports configuring which hacks appear in the UI (ClickGUI, Navigator, TabGUI). This is useful for:
- Hiding hacks you don't use to declutter menus
- Creating "clean" client configurations for specific use cases
- Separating multiplayer-safe hacks from single-player-only hacks

## Configuration File

The hack visibility is controlled by `.minecraft/wurst/hack-visibility.json`

### Configuration Format

```json
{
  "mode": "show_all",
  "hacks": []
}
```

### Modes

#### `show_all` (Default)
Shows all hacks in menus. This is the default behavior.

```json
{
  "mode": "show_all",
  "hacks": []
}
```

#### `whitelist`
Only shows hacks listed in the `hacks` array. All other hacks are hidden from menus.

```json
{
  "mode": "whitelist",
  "hacks": [
    "Fullbright",
    "Freecam",
    "NoFall",
    "X-Ray"
  ]
}
```

#### `blacklist`
Shows all hacks EXCEPT those listed in the `hacks` array.

```json
{
  "mode": "blacklist",
  "hacks": [
    "Killaura",
    "CrystalAura",
    "ForceOP"
  ]
}
```

## Important Notes

### Hidden vs Disabled
- **Hidden hacks are still functional** - they can still be toggled via keybinds or commands
- Hidden hacks just don't appear in UI menus (ClickGUI, Navigator, TabGUI)
- All hacks remain in saved profiles and enabled-hacks.json

### Use Cases

**Multiplayer-Safe Client:**
```json
{
  "mode": "whitelist",
  "hacks": [
    "Fullbright",
    "Zoom",
    "NoFog",
    "AutoTool"
  ]
}
```

**Hide Rarely-Used Hacks:**
```json
{
  "mode": "blacklist",
  "hacks": [
    "ForceOP",
    "CrashChest",
    "ItemGenerator",
    "KillPotion"
  ]
}
```

## Dynamic Hack Registration (For Addon Developers)

Third-party mods can now register their own hacks dynamically:

```java
import net.wurstclient.WurstClient;
import net.wurstclient.hack.Hack;

public class MyCustomHack extends Hack {
    public MyCustomHack() {
        super("MyCustomHack");
        setCategory(Category.OTHER);
    }

    // Your hack implementation...
}

// In your mod initializer:
WurstClient.INSTANCE.getHax().registerHack(new MyCustomHack());
```

## Configuration Management

You can also programmatically manage visibility:

```java
HackList hax = WurstClient.INSTANCE.getHax();

// Check if a hack is visible
boolean visible = hax.isHackVisible("Killaura");

// Set hack visibility
hax.setHackVisible("Killaura", false);

// Get the visibility file for advanced operations
HackVisibilityFile visFile = hax.getVisibilityFile();
visFile.setMode(VisibilityMode.WHITELIST);
```

## Reloading Configuration

The configuration is loaded on startup. To reload:
1. Edit `.minecraft/wurst/hack-visibility.json`
2. Restart Minecraft

Future versions may include an in-game reload command.
