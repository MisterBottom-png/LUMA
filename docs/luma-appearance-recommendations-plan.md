# LUMA Appearance: Ink & Paper Recommendations & Plan

## Recommended direction

The current combination of lavender, cyan-teal, misty gradients, translucent
glass, and glow has become a recognisable visual shortcut for an AI assistant.
For a more distinctive and enduring LUMA, adopt **Ink & Paper**: mineral
neutrals, a deep blue-green primary, and a muted clay secondary.

The desired feeling is a considered personal organiser or field notebook:
calm, capable, tactile, and premium without looking futuristic by default.

## New default colour scheme

| Role | Light | Dark |
| --- | --- | --- |
| Primary | `#3D5962` | `#A5CAD3` |
| On primary | `#FFFFFF` | `#07363F` |
| Primary container | `#C1DDE4` | `#254B54` |
| On primary container | `#001F26` | `#C1E6EF` |
| Secondary | `#705D4A` | `#DEC3A8` |
| On secondary | `#FFFFFF` | `#3E2D1D` |
| Secondary container | `#FADDBD` | `#574331` |
| Background | `#F7F5F0` | `#121412` |
| Surface | `#FCFAF5` | `#191C1A` |
| Surface variant | `#E2E3DD` | `#434842` |
| Primary text / on background | `#1B1C19` | `#E2E3DD` |
| Secondary text | `#434842` | `#C3C8C1` |
| Outline | `#737770` | `#8D928A` |
| Error | `#BA1A1A` | `#FFB4AB` |

### Default background preset

| Mode | Three-stop vertical gradient |
| --- | --- |
| Light | `#FBF9F5` → `#F2F0EA` → `#EEF2F0` |
| Dark | `#101311` → `#182022` → `#121716` |

The background is deliberately subtle. It should support content rather than
announce itself.

## Material treatment

- Use near-opaque tonal surfaces and fine outlines for normal cards, lists, and
  navigation.
- Use transparent glass, noticeable blur, and any glow only for the AI composer,
  a focused AI interaction, and important modals.
- Use blue-green for primary actions, navigation selection, focus, and progress.
- Use clay for supporting metadata and secondary actions; do not use it for
  warnings or errors.
- Avoid cyan borders, bright violet glows, broad white edges, and animated
  gradient washes. These are the strongest “generic AI product” signals in the
  original direction.

## Surface hierarchy

Replace a single generic `surfaceVariant` treatment with a tonal ladder.

| Role | Light | Dark |
| --- | --- | --- |
| Background | `#F7F5F0` | `#121412` |
| Surface container lowest | `#FFFCF8` | `#0D0F0E` |
| Surface container low | `#F6F4EE` | `#171A18` |
| Surface container | `#F0EFE9` | `#1C1F1D` |
| Surface container high | `#EAE9E3` | `#272A28` |
| Surface container highest | `#E4E3DD` | `#323532` |

These should be validated against device screenshots and contrast tests before
they become final tokens.

## Personalisation rules

Keep customisation, but make it predictable and safe.

| Setting | Changes | Remains stable |
| --- | --- | --- |
| Accent | Primary family, primary container, focus and selection | Clay secondary and content-status meanings |
| Text tone | Text semantic roles | Accent and status colours |
| Background | Atmospheric gradient or wallpaper | Core surfaces and text roles |
| Full palette, advanced | Primary and secondary families | Content-status meanings |

Recommended default configuration:

- Accent: Ink & Paper blue-green
- Secondary: fixed Ink & Paper clay
- Text tone: Neutral
- Background: Ink & Paper
- Glass: Standard, with an opaque fallback
- Background dimming: Adaptive

Existing violet, sage, rose, amber, and ocean choices can stay as optional
accents. They should not redefine success, warning, error, or archived states.

## Implementation plan

### 1. Theme foundations

1. Replace the LUMA Violet/teal default values in `Theme.kt` with the Ink &
   Paper colour scheme.
2. Add the complete Material semantic roles: on-colours, outline variant,
   surface tint, inverse surface, error roles, and the surface-container ladder.
3. Add semantic app colours for success, warning, info, AI output, needs review,
   and archived content. Keep these independent of user accents.

### 2. Accent and text model

1. Make standard accents change only primary-family roles.
2. Keep the clay secondary family fixed by default.
3. Retain full primary-and-secondary replacement as an advanced option.
4. Simplify text tones to Neutral, Plum, Forest, and Warm Ivory; consider
   removing Ink if real screenshot comparison shows no useful distinction.

### 3. Surfaces and glass

1. Add subtle, standard, and prominent materials to `GlassSurface.kt`.
2. Use opaque tonal fallback surfaces whenever blur is disabled, unavailable, or
   unsuitable for device performance.
3. Apply glass only by prominence: passive groups subtle, regular controls
   standard, AI/modals prominent.
4. Map background blur to named choices—None, Soft, Medium, Strong—in
   `OrbitBackground.kt`, rather than exposing an implementation number.

### 4. Layout and interaction

1. Centralise state overlays, motion, spacing, and radius mappings in
   `DesignTokens.kt`.
2. Use state layers for compact controls; reserve press scaling for large cards
   and primary actions.
3. Do not scale bottom-navigation or calendar selections, as this causes layout
   jitter.
4. Expand `Type.kt` into semantic styles with size, weight, and line height.

### 5. Quality gates

1. Test contrast for every built-in accent, text tone, background, and theme.
2. Use adaptive dimming, stronger surfaces, or a scrim to maintain contrast over
   custom wallpapers.
3. Test 1.0×, 1.3×, 1.5×, and 2.0× font scale; reduced motion; high contrast;
   battery saver; and blur fallback.
4. Add live Settings previews showing text, a surface, selection, and a button.
5. Provide **Restore LUMA appearance** as the reset action.

## Source-file responsibility map

| File | Changes |
| --- | --- |
| `app/src/main/java/com/orbit/app/ui/theme/Theme.kt` | Ink & Paper scheme, semantic roles, accent/text-tone mapping. |
| `app/src/main/java/com/orbit/app/ui/components/OrbitBackground.kt` | Named blur modes and adaptive contrast/dimming. |
| `app/src/main/java/com/orbit/app/ui/components/GlassSurface.kt` | Glass hierarchy, edge/shadow rules, opaque fallback. |
| `app/src/main/java/com/orbit/app/ui/theme/DesignTokens.kt` | States, motion, radii, spacing, surface/material tokens. |
| `app/src/main/java/com/orbit/app/ui/theme/Type.kt` | Semantic typography and scalable layout rules. |

## Priority order

1. Adopt the Ink & Paper default scheme and tonal surface ladder.
2. Make accents primary-only by default.
3. Restrict glass/glow to AI and modal moments, with opaque fallbacks.
4. Add automated contrast protection for wallpapers and built-in themes.
5. Finish live previews and accessibility validation.
