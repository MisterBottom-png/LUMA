# P1 assets: fonts, backgrounds, contrast

Evidence for the P1 asset work (decisions D-02, D-15, D-19). All scripts are in `scripts/limestone/` and take their input paths as arguments; run them with `python3 -I`. Tools: Python 3, fontTools 4.67.0, Pillow 12.3 (WebP available), NumPy. No Gradle, device or emulator was used; nothing here claims an on-device check.

## 1. Fonts

### Sources and licences

| Item | Value |
|---|---|
| Literata source | `https://raw.githubusercontent.com/google/fonts/main/ofl/literata/Literata[opsz,wght].ttf`, 955,132 B, SHA-256 `b41138c9373112f32abb589cc22e8674b06ed4048b0c513be922bdd26f274440`, version 3.103, axes opsz 7-72 (default 12), wght 200-900 |
| Noto Sans source | `https://raw.githubusercontent.com/google/fonts/main/ofl/notosans/NotoSans[wdth,wght].ttf`, 2,049,096 B, SHA-256 `bfb7bb691513f12e734dc346c03a03f784912432d7e3fa8e56efcf906fe86b3d`, version 2.015, axes wght 100-900, wdth 62.5-100 |
| Literata licence | upstream `OFL.txt`, 4,389 B, SHA-256 `8742963604cd89dc81437811a850018fc03b2bfad686d7422c8235967c87614e`, first line `Copyright 2017 The Literata Project Authors (https://github.com/googlefonts/literata)` |
| Noto Sans licence | upstream `OFL.txt`, 4,396 B, SHA-256 `cee9892f9f0cc8fe882c9e9537ee6a89621d86ee7ceaf70b02e2b2b1c25c061a`, first line `Copyright 2022 The Noto Project Authors (https://github.com/notofonts/latin-greek-cyrillic)` |
| Reserved Font Name | **None** declared in either copyright line (the only match for "Reserved Font Name" in each file is the licence's own definition paragraph). Original family names are therefore kept and subsetting is permitted. |

The GitHub contents API was blocked in this session ("repository access not enabled"); `raw.githubusercontent.com` worked, so file names were taken from the known google/fonts layout. The licence texts are byte-identical copies of the upstream files (hashes above equal the files in `app/src/main/assets/licenses/`).

### Instancing and subsetting (`scripts/limestone/build_fonts.py`)

- Static instances via `fontTools.varLib.instancer` (`updateFontNames=True`), then `OS/2.usWeightClass` set explicitly.
- **Literata at opsz 12**: 12 is the font's own text default (range 7-72); the app's body, titles and receipts are 14-20 sp text sizes, not display sizes, so the text cut gives the sturdier forms intended for reading. Display sizes (opsz 30+) would be lighter and tighter than needed. The instancer adds "12pt" to the family name; the script strips it so the family reads `Literata` / `Literata Medium`.
- **Noto Sans at wdth 100.**
- Subset (`fontTools.subset`), layout features kept: kern, liga, tnum, pnum, lnum, onum, case, locl, ccmp, mark, mkmk, calt, ss01, frac, zero, sups, subs. Name table kept in full (copyright, licence URL). Hinting programs dropped (upstream has only `prep` and `gasp`, no `fpgm`/`cvt`, so nothing meaningful is lost); DSIG and vertical tables dropped.
- Kept ranges: Basic Latin, Latin-1, Latin Extended-A and -B, spacing modifiers, combining diacritics, Cyrillic U+0400-04FF, General Punctuation, super/subscripts, currency, letterlike, arrows U+2190-21FF, minus, bullet.
- Dropped: Cyrillic Supplement and Extended-A/B/C, Latin Extended Additional (Vietnamese), Greek, symbols, all other scripts.

### Files and sizes

| File | Bytes | SHA-256 | usWeightClass | Glyphs |
|---|---:|---|---:|---:|
| `res/font/literata_regular.ttf` | 114,008 | `6ce699a09ffed2ed45f9a9cc9f762711a264a26489fb31334e3048940560875d` | 400 | 765 |
| `res/font/literata_medium.ttf` | 114,252 | `2232f5991c3655fca2c5d07e358f082312aba7cb8e0989b7962852677f547a01` | 500 | 765 |
| `res/font/noto_sans_regular.ttf` | 145,124 | `ebdb76a93178c521c19911f60009b53450ac73a1a2d39352285d27937181ada1` | 400 | 1480 |
| `res/font/noto_sans_medium.ttf` | 145,932 | `992ec9e7c0cf6d6c70a7db690174ac896b8a10d190cbe839db03c0f97315b9cb` | 500 | 1480 |
| `res/font/noto_sans_semibold.ttf` | 146,024 | `4b7d582e1187ee4ef87709a588b37e2f0a7f8714d2579e37d47f882c6e723010` | 600 | 1480 |
| **Total new** | **665,340** | | | |
| Existing `google_sans_flex_*` (3 files, removed in P1 once `Type.kt` switched) | 282,632 | | | |
| **Delta after the old fonts are removed** | **+382,708 B (+0.38 MB) of TTF** | | | |

Licence files added: `app/src/main/assets/licenses/literata-OFL.txt`, `noto-sans-OFL.txt`. `google-sans-flex-OFL.txt` was removed with the old fonts.

### Verification (`scripts/limestone/verify_fonts.py`): PASS

For all five files: loads in fontTools; static (no `fvar`); weight class as above; family name kept; every Estonian letter (a-z, A-Z, õ Õ ä Ä ö Ö ü Ü š Š ž Ž) and every Russian letter (А-я plus ё Ё) present; digits and the UI punctuation set present; every non-ASCII character that occurs in `values*/strings*.xml` (159 distinct characters, includes the em dash, ellipsis, « » „ “ ” ’ · ›) present; features kern, liga, lnum, case, locl, tnum present.

Findings the lead and theme agent should know:

1. **Noto Sans has no arrow glyphs** (U+2190-21FF absent upstream), so any `→` rendered in Noto Sans falls back to a system font. `→` is not used in any string resource (only in Kotlin code, per `grep`). Literata does contain `→`.
2. **Noto Sans digits are tabular by default** (all ten advance widths 572); its `tnum` feature only maps old-style figures to tabular old-style figures, so `fontFeatureSettings = "tnum"` is harmless but already satisfied for lining digits. Literata's default digits are proportional-capable and `tnum` does substitute them.
3. Android resolves `FontFamily` weights from the files you declare, not from the name table; the weight class values above are still correct for tooling.

## 2. Backgrounds

### Sources (all verified before conversion)

`tallele-backgrounds.zip` SHA-256 `60b1abb92e095e704da73706d65f236e7518b242b500a488385a2fe8f9b94b59` (matches `design-inputs.md`). All ten PNG SHA-256 values match `design-inputs.md` (the script aborts on any mismatch), all 1024 x 1536 8-bit RGB.

### Conversion choices (`scripts/limestone/convert_backgrounds.py`)

Lossy WebP, Pillow `quality=94, method=6`, no resize, 1024 x 1536 kept (no upscaling). Why 94: the sources carry fine grain (ink wash light especially, 2.5 MB PNG). Below about q=92 the grain is smoothed away and file sizes collapse (set of 0.64 MB at q92), but above q=94 sizes grow quickly with no further drop in low-frequency error (q95 = 1.06 MB, q93 = 0.76 MB). q94 totals 0.966 MB, closest to the proposal's ~0.9 MB without going under the grain-preserving range. Visual check: contact sheet (`.../scratchpad/contact_sheet.png`) looked right for all ten (light set pale, dark set deep, no inversions or colour shifts), and a 10x contrast-stretched crop comparison of source against WebP for three images (`.../scratchpad/banding_check.png`) showed grain-level blockiness only, no contour bands. Both files stay in the scratchpad, outside the repo.

### Files, sizes and quality (against the source)

| Resource (`res/drawable-nodpi/`) | Bytes | MAE (0-255) | PSNR dB | 9x9-blur MAE / max | Row, column mean-profile max error |
|---|---:|---:|---:|---:|---:|
| `bg_limestone_light.webp` | 98,716 | 1.07 | 45.04 | 0.34 / 2.77 | 0.84 / 0.83 |
| `bg_limestone_dark.webp` | 37,102 | 0.93 | 46.27 | 0.58 / 3.04 | 1.43 / 1.35 |
| `bg_harbour_mist_light.webp` | 65,296 | 0.99 | 45.70 | 0.36 / 2.62 | 0.99 / 0.80 |
| `bg_harbour_mist_dark.webp` | 38,998 | 0.76 | 47.66 | 0.36 / 2.56 | 0.76 / 0.53 |
| `bg_ink_wash_light.webp` | 360,170 | 1.32 | 43.44 | 0.34 / 2.58 | 1.26 / 0.83 |
| `bg_ink_wash_dark.webp` | 66,394 | 0.95 | 45.95 | 0.41 / 2.86 | 0.92 / 0.81 |
| `bg_pine_fog_light.webp` | 36,324 | 0.79 | 47.39 | 0.38 / 2.98 | 0.88 / 0.74 |
| `bg_pine_fog_dark.webp` | 34,924 | 0.81 | 47.22 | 0.39 / 2.95 | 1.05 / 0.71 |
| `bg_linen_light_light.webp` | 131,390 | 1.15 | 44.54 | 0.35 / 3.28 | 0.86 / 0.89 |
| `bg_linen_light_dark.webp` | 96,528 | 1.07 | 45.09 | 0.33 / 2.67 | 0.92 / 0.93 |
| **Total** | **965,842** (0.966 MB) | | | | |

Note: the ten PNG sources total 18,184,782 bytes, so the WebP set is about 5 % of the PNG size. The sources are not copied into the repo. The worst-case 9x9-blurred error of about 3 levels out of 255 is a local deviation of the grain, not a smooth drift; the profile errors (about 1 level) are the YUV 4:2:0 round-trip bias.

## 3. Contrast over the images (`scripts/limestone/measure_background_contrast.py`)

Model: centre crop-to-fill to 9:16, 9:19.5, 9:20 at source resolution (conservative: a phone upscales and smooths it); top 75 % of the height; `wash * page + (1 - wash) * pixel` in sRGB; 9 x 9 box blur; WCAG 2.x contrast. Page #F3F1EC / #121417. Foregrounds as briefed. Values below are the **minimum over pixels, taking the worst of the three ratios** (the 0.1st percentile is in the JSON and is 0.11 to 1.29 higher; the 9:16 ratio, which keeps the most image width, is the binding one for most rows). Tab column: label text-2 over surface at 84 % alpha over the blurred bottom 15 % of the washed image; "raw" is the same without the wash.

| Image | Theme | Wash | text | text-2 | Ink | Harbour | Moss | Plum | Tab (washed) | Tab (raw) |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| limestone | light | 10% | 11.44 | 5.18 | 6.01 | 5.04 | 4.91 | 5.68 | 6.67 | 6.63 |
| limestone | dark | 0% | 13.90 | 8.33 | 8.98 | 9.43 | 9.32 | 9.10 | 8.34 | 8.34 |
| harbour_mist | light | 40% | 10.47 | 4.74 | 5.51 | 4.61 | **4.498** | 5.20 | 6.36 | 5.91 |
| harbour_mist | dark | 0% | 10.05 | 6.02 | 6.49 | 6.82 | 6.74 | 6.57 | 8.07 | 8.07 |
| ink_wash | light | 10% | 10.57 | 4.79 | 5.56 | 4.66 | 4.54 | 5.25 | 6.18 | 6.08 |
| ink_wash | dark | 0% | 12.27 | 7.35 | 7.93 | 8.32 | 8.23 | 8.03 | 8.10 | 8.10 |
| pine_fog | light | 25% | 10.05 | 4.55 | 5.28 | **4.43** | **4.32** | 4.99 | 6.39 | 6.17 |
| pine_fog | dark | 0% | 10.34 | 6.20 | 6.68 | 7.02 | 6.94 | 6.77 | 8.40 | 8.40 |
| linen_light | light | 35% | 10.53 | 4.77 | 5.54 | 4.64 | 4.52 | 5.22 | 6.74 | 6.56 |
| linen_light | dark | 0% | 10.20 | 6.11 | 6.59 | 6.92 | 6.84 | 6.67 | 8.12 | 8.12 |

### Results

- **Text (#1B1F27 / #ECEAE4)**: PASS everywhere (min 10.05).
- **text-2 and accents vs 4.5 floor**: PASS for 18 of 20 image/theme pairs. All dark washes (0 %) pass with margin (min 6.02).
- **FAIL, light theme only, strict minimum below 4.5:**
  - Pine fog 25 %: Harbour 4.43 and Moss 4.32. Smallest passing wash in 5 % steps for every secondary colour at all three ratios: **35 %**.
  - Harbour mist 40 %: Moss 4.498 (a 0.002 miss; 0.1st percentile is 4.69, so only the single lightest blurred pixels at 9:16 fail). Smallest passing wash: **45 %**.
  - Decided in `decisions.md` D-23: the app uses 45 % (Harbour mist light) and 35 % (Pine fog light). Alternatives to a larger wash: make Moss slightly darker in light theme, or accept the percentile figure (0.1st percentile passes everywhere).
- **Glass tab labels (targets 6.2 light / 8.1 dark, hard floor 4.5)**: floor PASS everywhere (min 6.18 light, 8.07 dark). Below the stated target by a hair (floor still met): Ink wash light 6.177 (target 6.2), Harbour mist dark 8.073 and Ink wash dark 8.095 (target 8.1). All others meet the target. Using the raw image (no wash) Harbour mist light drops to 5.91 and Pine fog light to 6.17, so the tab bar depends on the wash being under it. The blur here is a 9x9 box, lighter than the real glass blur; a stronger blur raises the minimum, so these values are conservative for the blur but ignore any content scrolling beneath the bar.
- Limits: this measures glyphs over the bare background. Text on cards or sheets sits on solid surfaces, not the image. Estimated from decoded pixels only; no screenshot or on-device check was run.

Machine-readable output: `scripts/limestone/background_contrast_results.json` (per image, theme, ratio; min and 0.1st percentile for every foreground and for the tab label; recommended washes).

## 4. Commands (all run in this session)

```bash
S=/tmp/claude-0/-home-user-LUMA/12ef93ce-7df9-5652-9250-345b475c133f/scratchpad
pip install fonttools brotli                                             # PASS (fontTools 4.67.0)
mkdir $S/bg && unzip -q $S/handoff/tallele-backgrounds.zip -d $S/bg      # PASS; sha256sum matches design-inputs.md
B=https://raw.githubusercontent.com/google/fonts/main/ofl
curl -sSf -o $S/fonts_src/Literata.ttf "$B/literata/Literata%5Bopsz,wght%5D.ttf"   # PASS
curl -sSf -o $S/fonts_src/NotoSans.ttf "$B/notosans/NotoSans%5Bwdth,wght%5D.ttf"   # PASS
curl -sSf -o $S/fonts_src/literata-OFL.txt $B/literata/OFL.txt           # PASS
curl -sSf -o $S/fonts_src/notosans-OFL.txt $B/notosans/OFL.txt           # PASS
python3 -I scripts/limestone/build_fonts.py $S/fonts_src/Literata.ttf $S/fonts_src/NotoSans.ttf $S/fonts_out   # PASS
python3 -I scripts/limestone/verify_fonts.py $S/fonts_out app/src/main/res                                      # PASS (ALL PASS)
python3 -I scripts/limestone/convert_backgrounds.py $S/bg app/src/main/res/drawable-nodpi 94                    # PASS
python3 -I scripts/limestone/measure_background_contrast.py app/src/main/res/drawable-nodpi scripts/limestone/background_contrast_results.json   # PASS (runs; reports the FAILs above)
python3 scripts/codex/check_workplace_privacy.py --strict                # PASS (heuristic; semantic review still required)
```

Gradle build, lint, unit tests and APK size: run by the lead, recorded in `verification.md` (P1).

## 5. Open points

- Wash for Pine fog light and Harbour mist light: resolved by D-23 (35 % and 45 %).
- Old fonts (`google_sans_flex_*.ttf`, `google-sans-flex-OFL.txt`): removed in P1.
- `tallele-backgrounds-android.zip` was never supplied (D-02); these WebPs were produced from the PNGs.

## 5. Re-measurement with the shipped washes (lead, after D-23)

`WASH` in the script now holds the values the app ships (`CaptureBackground.kt`): Harbour mist light 45 %, Pine fog light 35 %, others as §7.17. `background_contrast_results.json` was regenerated.

Command: `python3 -I scripts/limestone/measure_background_contrast.py app/src/main/res/drawable-nodpi scripts/limestone/background_contrast_results.json` → **PASS**, "FAILURES: none".

| Background | Light wash | Lowest text-2 / accent (light) | Lowest text-2 / accent (dark) | Glass tab label light / dark |
|---|---:|---|---|---|
| Limestone | 10 % | 5.18 / 4.91 (Moss) | 8.33 / 8.98 | 6.67 / 8.34 |
| Harbour mist | 45 % | 4.88 / 4.63 (Moss) | 6.02 / 6.49 | 6.42 / 8.07 |
| Ink wash | 10 % | 4.79 / 4.54 (Moss) | 7.35 / 7.93 | 6.18 / 8.10 |
| Pine fog | 35 % | 4.80 / 4.55 (Moss) | 6.20 / 6.68 | 6.48 / 8.40 |
| Linen light | 35 % | 4.77 / 4.52 (Moss) | 6.11 / 6.59 | 6.74 / 8.12 |

Minimum over the three ratios after a 9×9 px blur. Main text ≥ 10.05 everywhere. Three tab-label values sit a hair under the proposal's quoted 6.2 / 8.1 (Ink wash light 6.18, Harbour mist dark 8.07, Ink wash dark 8.10) but well above the 4.5 floor. These are pixel calculations on the decoded files, not screenshots: **NEEDS OWNER PHONE VERIFICATION** in both themes once P3 places backgrounds on Capture.
