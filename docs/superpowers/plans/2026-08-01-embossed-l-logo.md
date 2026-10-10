# Embossed L Logo Implementation Plan

**Goal:** Replace every user-visible LUMA mark with a legible, calm, premium embossed L that is safe under Android launcher masks.

**Architecture:** Keep the existing Android resource wiring. Generate a coherent set of raster derivatives from one rounded L path: an embossed colour foreground over a refined dark background, a matching splash mark, and a flat monochrome themed/notification mark.

## Constraints

- The colour mark is one rounded L silhouette with no diagonal cuts, shards, text, or secondary symbols.
- Position the visible L within the adaptive-icon safe zone: x = 132–347 and y = 78–330 on the 432 px foreground canvas.
- Use a restrained lavender-to-cyan gradient, a shallow lower-right shadow, and a subtle upper-left highlight only.
- Preserve transparent ARGB foreground/splash/themed assets and opaque RGB background dimensions.
- The themed and notification versions are monochrome silhouettes without gradients, shadows, or backgrounds.
- Do not change manifest, adaptive-icon XML, app behavior, data, navigation, dependencies, or copy.

## Files

- Modify: `app/src/main/res/drawable-nodpi/luma_brand_launcher_foreground.png` — 432 px transparent embossed colour L.
- Modify: `app/src/main/res/drawable-nodpi/luma_brand_launcher_background.png` — 432 px opaque dark navy/violet/cyan ambient background.
- Modify: `app/src/main/res/drawable-nodpi/luma_brand_splash_logo.png` — 1024 px transparent embossed colour L.
- Modify: `app/src/main/res/drawable-nodpi/luma_brand_themed_icon.png` — 432 px transparent monochrome L.
- Modify: `app/src/main/res/drawable/ic_luma_notification.xml` — simple matching monochrome L vector.

## Verification

1. Check every raster's dimensions, alpha format, transparent corners, and safe-zone bounds.
2. Confirm unchanged manifest/adaptive/splash resource references resolve the five updated resources.
3. Build `:app:assembleDebug` with Java 17.
4. On-device, inspect normal launcher masks, unmasked/square launcher display, themed icons, Recent Apps, status-bar notification, and cold-launch splash.
5. Run the strict workplace privacy check and inspect the final diff when Git metadata is available.
