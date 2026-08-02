# LUMA appearance reference

LUMA uses a Material 3 colour system with Light, Dark, and Auto theme modes. Auto follows the device's system theme.

## Core theme colours

| Role | Light | Dark |
| --- | --- | --- |
| Primary | `#6550C8` | `#CDBDFF` |
| Primary container | `#E8DFFF` | `#4D3992` |
| Secondary | `#3F7479` | `#A9CED1` |
| Secondary container | `#C5ECEF` | `#294F53` |
| Background | `#F7F3FB` | `#15121B` |
| Surface | `#F9F5FD` | `#19161F` |
| Surface variant | `#E8E1EC` | `#49454F` |
| Primary text | `#211D27` | `#EAE3ED` |
| Secondary text | `#4A454E` | `#CCC4CF` |
| Outline | `#7B747E` | `#958E98` |

## Accent palettes

Each accent replaces the Material primary and secondary colours, including their containers and on-colours.

| Accent | Light primary | Dark primary | Light secondary | Dark secondary |
| --- | --- | --- | --- | --- |
| LUMA Violet | `#6550C8` | `#CDBDFF` | `#3F7479` | `#A9CED1` |
| Sage | `#3E6F45` | `#B6D9B8` | `#74642F` | `#CFC5A4` |
| Rose | `#99415E` | `#FFB4C6` | `#725A42` | `#D6C1A7` |
| Amber | `#865400` | `#FFC46B` | `#5D6F47` | `#BFD4BE` |
| Ocean | `#2D6684` | `#A4D5F3` | `#5C6090` | `#BFC7E9` |

## Text palettes

| Text treatment | Light primary / secondary | Dark primary / secondary |
| --- | --- | --- |
| Default | `#211D27` / `#4A454E` | `#EAE3ED` / `#CCC4CF` |
| Ink | `#17141B` / `#4B4650` | `#F3EDF7` / `#D1CAD8` |
| Plum | `#2A173C` / `#55445F` | `#F1E5FF` / `#D4C6E7` |
| Forest | `#152A1D` / `#3E4F41` | `#E6F1E5` / `#C8D8C5` |
| Warm Ivory | `#241B12` / `#493D30` | `#FFF1DB` / `#E0D1BD` |

## Background presets

Each preset is a three-stop vertical gradient.

| Preset | Light | Dark |
| --- | --- | --- |
| Soft Dawn | `#FFF8F4` -> `#F6EDF7` -> `#F5F1EC` | `#171218` -> `#251B29` -> `#202125` |
| Violet Mist | `#FBF8FF` -> `#EFE8FA` -> `#F4EEFA` | `#15111D` -> `#241A34` -> `#181827` |
| Calm Sky | `#F6FBFD` -> `#E7F3F7` -> `#EBF0FA` | `#10181D` -> `#15262D` -> `#17202B` |
| Night Glow | `#F5F5FC` -> `#E8E9F6` -> `#E9F0F4` | `#0B0C16` -> `#15172B` -> `#101A25` |

## Spaces and system colours

- Space accent options: `#6D7CFF`, `#B270D6`, `#4E91D8`, `#D58B62`, `#62A77A`, `#D7798D`, `#59A6A6`, `#E0A84F`.
- Android window background: `#F4F0FB`.
- Splash background: `#030712`.
- Modal scrim: black at 32% opacity in Light mode and 44% in Dark mode.

## Material and layout treatment

- Custom background images support blur (default `0.35`) and dimming (default `0.12`).
- Glass strength defaults to `0.72`; surfaces use blurred translucent material, accent tinting, subtle white edges, and theme-aware shadows.
- Typography uses the system sans-serif family: display 36sp, headline 28sp, title 22sp, body 17sp and 15sp, and label 14sp.
- Shared corner radii: 14dp, 18dp, 28dp, and 32dp for modal top corners.
- Shared spacing: 2dp, 8dp, 12dp, 16dp, 24dp, and 32dp.
- Motion durations: 120ms, 220ms, and 320ms; pressed scale is `0.975`, selected scale is `1.03`.

## Source of truth

- `app/src/main/java/com/orbit/app/ui/theme/Theme.kt`
- `app/src/main/java/com/orbit/app/ui/components/OrbitBackground.kt`
- `app/src/main/java/com/orbit/app/ui/components/GlassSurface.kt`
- `app/src/main/java/com/orbit/app/ui/theme/DesignTokens.kt`
- `app/src/main/java/com/orbit/app/ui/theme/Type.kt`
