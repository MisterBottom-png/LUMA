# Flowing Ribbon L launcher logo design

## Goal

Replace the current launcher mark with a soft, fluid capital L that remains unmistakable at Android launcher and Recent Apps sizes while retaining LUMA's calm, luminous character.

## Included

- A simplified adaptive launcher-icon foreground mark.
- Corresponding launcher and Recent Apps appearance.
- Guidance for a compatible splash-mark treatment.

## Excluded

- Changes to application behavior, navigation, themes, or data.
- A new product wordmark or typography system.
- Changes to notification-icon conventions.

## Visual design

The foreground mark is one broad, rounded ribbon that forms a capital L:

- The vertical stroke begins near the upper center-left, flows downward, and makes one generous, soft turn into a short horizontal base.
- The L reads first as a letter; the glass and lighting effects remain secondary.
- The ribbon transitions smoothly from deep indigo at the upper stroke, through violet at the bend, to cyan at the horizontal tip.
- A restrained peach highlight appears only on the outer bend. It must not compete with the L silhouette.
- Avoid sharp seams, thin cuts, independent faceted shards, overlaid letters, or fine interior details.
- The icon background is a near-black blue-violet, light enough to preserve separation against dark launcher wallpaper.

## Android adaptive-icon constraints

- Keep approximately 18–20% transparent padding around the foreground L in the full source canvas.
- Size the visible L to approximately 65–70% of the source canvas so Android launcher masks do not crop or crowd it.
- Center the optical mass, rather than merely centering the bounding box: the lower arm must not make the mark look bottom-heavy.
- Use the same simplified mark for launcher and Recent Apps. A richer but recognizably related version may appear on the splash screen.

## Asset responsibilities

- `luma_brand_launcher_foreground.png`: the transparent flowing-ribbon L, exported at the existing foreground asset dimensions.
- `luma_brand_launcher_background.png`: the near-black blue-violet field, retaining existing adaptive-icon support.
- `luma_brand_splash_logo.png`: optional enlarged rendition using the same L silhouette and palette; no additional symbol language.
- Adaptive-icon XML remains responsible for layering foreground and background rather than embedding a flattened replacement.

## Acceptance criteria

- The capital L is recognisable at a typical small launcher size without relying on the app label.
- On dark wallpaper, the upper stroke and lower arm remain visibly separate from the background.
- In Recent Apps, the mark remains centred, unclipped, and visually quieter than surrounding application content.
- The asset remains calm, premium, and soft rather than faceted, high-contrast, or decorative.
- Existing adaptive-icon packaging and all non-logo behavior remain unchanged.

## Verification

1. Build and install a debug build.
2. Inspect launcher and Recent Apps on light and dark wallpaper, including Android's applied icon mask.
3. Compare normal and monochrome/themed icon behavior if the launcher supports it.
4. Confirm the splash mark uses the same recognisable L silhouette.
