# Design inputs

Recorded in P0 on 2026-10-17 from `main` at `f2662dd9c7efad43cf657325beb30e6be167b6f7`.

## Bundle

`Tallele_Claude_Code_Cloud_Handoff.zip` (repository root, 15,719,468 bytes)
SHA-256 `fe186ea4ff690c8a5403d8baf8411cc6619b17887a16b509f5ecf8b225cc1b81`

Extract to a temporary directory outside the repository, for example:

```bash
d=$(mktemp -d) && unzip -q Tallele_Claude_Code_Cloud_Handoff.zip -d "$d" && mkdir "$d/bg" && unzip -q "$d/tallele-backgrounds.zip" -d "$d/bg"
```

| File in bundle | Bytes | SHA-256 | Role |
|---|---:|---|---|
| `tallele-limestone-harbour-proposal.md` | 55,076 | `048ddb13edec35c8a242da427c1227ff66793a1a61e02511e71f131300f22223` | **Design authority, v1.3 (14 Oct 2026)** |
| `Tallele_Limestone_Harbour_Claude_Code_Prompt.md` | 22,141 | `5560e9f40a17c40b5c6e0a076fe8fdb065c81a1011a57e2f4dbf8a00fe35bcbd` | Master implementation prompt (identical to the copy at the repository root) |
| `Tallele_Cloud_Handoff_README.md` | 2,195 | `d52448fc815bfecbcd43dc9b6aa29fe5adccda289bbe8c6878e837371e06e979` | Handoff workflow notes |
| `tallele-backgrounds.zip` | 15,681,931 | `60b1abb92e095e704da73706d65f236e7518b242b500a488385a2fe8f9b94b59` | Ten source PNGs |

The proposal is not copied into the repository verbatim; see `decisions.md` D-01.

## Background source images

All ten are 1024 × 1536 px, 8-bit RGB PNG, no alpha. They are **sources**, not Android resources: P1 converts them to WebP under `res/drawable-nodpi/` with underscore names (`bg_harbour_mist_light`, …).

| Source file | Bytes | SHA-256 | Mean luminance, top 75 % (0–255) | Target resource |
|---|---:|---|---:|---|
| `bg_limestone_light.png` | 1,893,688 | `e6fef132c15b17a45f123f53e711b76af34e074f91051e1578e1bc402509f135` | 231 | `bg_limestone_light` |
| `bg_limestone_dark.png` | 1,628,480 | `d5df88e8bd833bf72106558a7e645572390c3e9ff2b19d6c3e8f620bec0de27c` | 24 | `bg_limestone_dark` |
| `bg_harbour-mist_light.png` | 1,775,909 | `37e56c49503d762ef84ba6d07fca2f6afd032a777f591089e90c33d3b05b5c12` | 215 | `bg_harbour_mist_light` |
| `bg_harbour-mist_dark.png` | 1,546,411 | `f61900b3aad5c9c0887e79103cafca0ea2bdf400c23d27e18b868530a5b2f14e` | 34 | `bg_harbour_mist_dark` |
| `bg_ink-wash_light.png` | 2,562,027 | `94479771a5fd5fb3f49971e9abcff995ab2402f10d3d88e60fd5b1b52d091ea1` | 235 | `bg_ink_wash_light` |
| `bg_ink-wash_dark.png` | 1,710,904 | `1e5c02dd203b79c8e447f92b4295a6e27fe42a5f7cdce72eb674ee06f14105e0` | 22 | `bg_ink_wash_dark` |
| `bg_pine-fog_light.png` | 1,606,806 | `2f8ea6cc66772e774d2ea61ae1951e5f935f2af3834c9c52ad0c1cd7e08cc211` | 225 | `bg_pine_fog_light` |
| `bg_pine-fog_dark.png` | 1,611,755 | `305b2ac915d8d29fa0d8179b058832f24e13d1ab15d632774166fb150760e486` | 38 | `bg_pine_fog_dark` |
| `bg_linen-light_light.png` | 1,981,951 | `b62463a0ce666bb374c50804ab1380a52be607a50fbd7830d6e7330680c2f272` | 230 | `bg_linen_light_light` |
| `bg_linen-light_dark.png` | 1,866,851 | `343792ca9c97532f0570f0089af6729ed5f12e67479d789727d564f94c5dfea6` | 41 | `bg_linen_light_dark` |

Visual inspection (contact sheet, P0): all ten are calm, soft and low-detail with no text, people, logos or sharp edges. Light versions are pale (mean 215–235), dark versions deep (mean 22–41). Harbour mist and Pine fog have the most structure (horizon/water, tree trunks); Ink wash concentrates its colour in the lower third; Linen light has soft leaf shadows at the corners; Limestone light is very close to plain paper (proposal §12 item 6).

## Wash values to apply in P1 (proposal §7.17)

| Background | Light wash | Dark wash |
|---|---:|---:|
| Limestone | 10 % | 0 % |
| Harbour mist | 40 % | 0 % |
| Ink wash | 10 % | 0 % |
| Pine fog | 25 % | 0 % |
| Linen light | 35 % | 0 % |

The proposal says these values and a `TalleleBackground` / `CaptureBackground` implementation ship in `tallele-backgrounds-android.zip`. **That file is not in the bundle.** P1 implements the list and composable from the table above and re-measures contrast on the rendered result (`decisions.md` D-02).

## Not supplied

- The design canvas ("a style sheet, a map and 15 phone screens"), screenshots and older proposals. Nothing is inferred from them.
- `tallele-backgrounds-android.zip` (finished WebP set and sample code).
