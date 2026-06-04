# XephiraOS Dynamic Island

A premium Dynamic Island implementation for Android 15 (AOSP/LineageOS) with ultra-fluid liquid animations.

## Features

- **Media Playback** — Now playing info with album art, progress bar, and transport controls
- **Phone Calls** — Incoming/active call display with caller info and live duration timer
- **Charging** — Battery level, charging type (USB/Fast/Wireless), temperature with pulsing animation
- **Timer** — Live countdown with animated circular progress ring
- **Split View** — Shows two providers simultaneously in compact split mode

## Animation System

All transitions use custom liquid spring physics for organic, fluid motion:

| Animation | Damping | Stiffness | Description |
|-----------|---------|-----------|-------------|
| Size morph | 0.72 | 180 | Slow elastic pill resizing |
| Tap press | 0.55 | 500 | Snappy squish feedback |
| Content slide | 0.80 | 300 | Smooth content entrance |
| Entrance | 0.65 | 220 | Bouncy drop-in appear |

Additional effects:
- Sweep gradient animated border that rotates continuously
- Breathing dot with synchronized scale + alpha oscillation  
- Staggered reveal for expanded content (header → divider → body)
- Outer glow halo with breathing intensity

## Integration

### 1. Add to ROM manifest

```xml
<project path="packages/apps/XephiraIsland"
         name="XephiraOS/android_packages_apps_XephiraIsland"
         remote="github" />
```

### 2. Add to device makefile

```makefile
PRODUCT_PACKAGES += \
    XephiraIsland
```

### 3. Settings integration (optional)

Add a toggle in XephiraOS Settings:

```xml
<!-- packages/apps/Settings/res/xml/xephira_settings.xml -->
<SwitchPreference
    android:key="xephira_dynamic_island_enabled"
    android:title="Dynamic Island"
    android:summary="Show a floating pill with live info at the top of the screen"
    android:defaultValue="true" />
```

### 4. Required Settings.Secure key

```
xephira_dynamic_island_enabled = 1  (enabled, default)
xephira_dynamic_island_enabled = 0  (disabled)
```

## Architecture

```
IslandService (Foreground Service)
    ├── IslandOverlayManager (WindowManager overlay)
    │   └── ComposeView → IslandOverlayContent()
    │       ├── IslandPill (morphing container)
    │       │   ├── CompactContent
    │       │   ├── CompactSplitContent
    │       │   └── ExpandedContent
    │       └── Spring Animation Engine
    └── Providers (content sources)
        ├── MediaIslandProvider (MediaSession)
        ├── CallIslandProvider (TelephonyManager)
        ├── ChargingIslandProvider (BatteryManager)
        ├── TimerIslandProvider (Timer broadcasts)
        └── NotificationIslandProvider (NotificationListener)
```

## License

Apache License 2.0 — Copyright (C) 2026 XephiraOS
