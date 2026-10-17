#!/usr/bin/env python3
"""Measure text/accent contrast over the converted background WebPs (P1, proposal 6.4 / 7.17).

Usage (run with -I):
  python3 -I scripts/limestone/measure_background_contrast.py <webp-dir> <out-json> [--table-md path]

<webp-dir> is app/src/main/res/drawable-nodpi (the WebPs produced by convert_backgrounds.py).

Model of the Capture screen:
  1. Centre crop-to-fill the 1024x1536 image to a portrait ratio (9:16, 9:19.5, 9:20).
     The crop is measured in source pixels (the phone scales it up ~1.1-1.5x, which only
     smooths it further, so source resolution is the conservative case).
  2. Keep the top 75 % of the crop height (the area behind text and cards).
  3. Composite the page-colour wash:  result = wash*page + (1-wash)*pixel  (sRGB values,
     as Compose's alpha compositing does).
  4. 9x9 box blur (approximates the local background of one glyph).
  5. WCAG 2.x contrast of each foreground colour against every pixel; report the minimum
     and the 0.1st percentile of the contrast values.
Glass tab bar: surface colour at 84 % alpha over the blurred (washed) image, bottom 15 % of
the screen; label colour text-2. Also reported without the wash (raw image) as a stricter bound.
If a foreground fails 4.5 the smallest wash (5 % steps) that passes for all three ratios
is reported as a recommendation (never applied).
"""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image

PAGE = {"light": "#F3F1EC", "dark": "#121417"}
SURFACE = {"light": "#FBFAF7", "dark": "#1A1D22"}
TAB_ALPHA = 0.84
FG = {
    "light": {"text": "#1B1F27", "text-2": "#4F5562", "ink": "#2D4B7E", "harbour": "#1F5F66", "moss": "#3D6136", "plum": "#69406F"},
    "dark": {"text": "#ECEAE4", "text-2": "#B4B7BE", "ink": "#A8BFE8", "harbour": "#8FCDD3", "moss": "#A9CB9C", "plum": "#D6B4DC"},
}
# image key -> (light wash, dark wash): the values the app ships (CaptureBackground.kt).
# Proposal 7.17 had Harbour mist light 40 % and Pine fog light 25 %; both failed 4.5 here and
# were raised to the smallest passing value (decisions.md D-23).
WASH = {
    "limestone": (0.10, 0.0),
    "harbour_mist": (0.45, 0.0),
    "ink_wash": (0.10, 0.0),
    "pine_fog": (0.35, 0.0),
    "linen_light": (0.35, 0.0),
}
RATIOS = {"9:16": 9 / 16, "9:19.5": 9 / 19.5, "9:20": 9 / 20}
TOP_FRACTION = 0.75
TAB_FRACTION = 0.15
FLOOR = 4.5
TAB_TARGET = {"light": 6.2, "dark": 8.1}
STEP = 0.05
SECONDARY = ("text-2", "ink", "harbour", "moss", "plum")  # checked against 4.5 for the wash recommendation


def hex_rgb(h: str) -> np.ndarray:
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], dtype=np.float64)


def lum(rgb255: np.ndarray) -> np.ndarray:
    c = rgb255 / 255.0
    lin = np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)
    return lin @ np.array([0.2126, 0.7152, 0.0722])


def contrast(lf: float, lb: np.ndarray) -> np.ndarray:
    hi, lo = np.maximum(lf, lb), np.minimum(lf, lb)
    return (hi + 0.05) / (lo + 0.05)


def box_blur(a: np.ndarray, k: int = 9) -> np.ndarray:
    p = k // 2
    pa = np.pad(a, ((p, p), (p, p), (0, 0)), mode="edge")
    c = np.pad(np.cumsum(np.cumsum(pa, 0), 1), ((1, 0), (1, 0), (0, 0)))
    return (c[k:, k:] - c[:-k, k:] - c[k:, :-k] + c[:-k, :-k]) / (k * k)


def centre_crop(a: np.ndarray, ratio: float) -> np.ndarray:
    h, w = a.shape[:2]
    if w / h > ratio:  # too wide -> crop sides
        nw = int(round(h * ratio))
        x0 = (w - nw) // 2
        return a[:, x0:x0 + nw]
    nh = int(round(w / ratio))
    y0 = (h - nh) // 2
    return a[y0:y0 + nh]


def stats(fgs: dict, bg_rgb: np.ndarray) -> dict:
    lb = lum(bg_rgb)
    out = {}
    for name, hexv in fgs.items():
        c = contrast(float(lum(hex_rgb(hexv))), lb)
        out[name] = {"min": round(float(c.min()), 3), "p0.1": round(float(np.percentile(c, 0.1)), 3)}
    return out


def measure(img: np.ndarray, theme: str, wash: float) -> dict:
    page = hex_rgb(PAGE[theme])
    res = {}
    for rname, ratio in RATIOS.items():
        crop = centre_crop(img, ratio)
        h = crop.shape[0]
        top = crop[: int(round(h * TOP_FRACTION))]
        bg = box_blur(wash * page + (1 - wash) * top)
        entry = {"text": stats(FG[theme], bg)}
        bottom = crop[h - int(round(h * TAB_FRACTION)):]
        sur = hex_rgb(SURFACE[theme])
        for label, w in (("washed", wash), ("raw_image", 0.0)):
            under = box_blur(w * page + (1 - w) * bottom)
            glass = TAB_ALPHA * sur + (1 - TAB_ALPHA) * under
            entry["tab_" + label] = stats({"text-2": FG[theme]["text-2"]}, glass)["text-2"]
        res[rname] = entry
    return res


def recommend(img: np.ndarray, theme: str) -> dict:
    """Smallest wash in 5 % steps for which every secondary foreground passes 4.5 (min, all ratios)."""
    page = hex_rgb(PAGE[theme])
    n = int(round(1 / STEP))
    for i in range(n + 1):
        w = i * STEP
        ok = True
        for ratio in RATIOS.values():
            crop = centre_crop(img, ratio)
            top = crop[: int(round(crop.shape[0] * TOP_FRACTION))]
            lb = lum(box_blur(w * page + (1 - w) * top))
            for name in SECONDARY:
                if contrast(float(lum(hex_rgb(FG[theme][name]))), lb).min() < FLOOR:
                    ok = False
        if ok:
            return {"smallest_passing_wash": round(w, 2)}
    return {"smallest_passing_wash": None}


def main() -> int:
    args = sys.argv[1:]
    table_md = None
    if "--table-md" in args:
        i = args.index("--table-md")
        table_md = Path(args[i + 1])
        del args[i:i + 2]
    if len(args) != 2:
        print(__doc__)
        return 2
    src, out = Path(args[0]), Path(args[1])
    results = {}
    failures = []
    lines = []
    for key, (wl, wd) in WASH.items():
        for theme, wash in (("light", wl), ("dark", wd)):
            f = src / f"bg_{key}_{theme}.webp"
            img = np.asarray(Image.open(f).convert("RGB")).astype(np.float64)
            m = measure(img, theme, wash)
            rec = recommend(img, theme)
            worst = {}
            for name in FG[theme]:
                worst[name] = {
                    "min": min(m[r]["text"][name]["min"] for r in m),
                    "p0.1": min(m[r]["text"][name]["p0.1"] for r in m),
                }
            tab = {lab: {"min": min(m[r]["tab_" + lab]["min"] for r in m),
                         "p0.1": min(m[r]["tab_" + lab]["p0.1"] for r in m)} for lab in ("washed", "raw_image")}
            results[f"{key}_{theme}"] = {"wash": wash, "per_ratio": m, "worst_over_ratios": worst,
                                         "tab_bar_worst": tab, "recommendation": rec}
            fails = [n for n in FG[theme] if worst[n]["min"] < FLOOR]
            tabfail = tab["washed"]["min"] < TAB_TARGET[theme]
            if fails:
                failures.append((key, theme, fails, rec["smallest_passing_wash"]))
            lines.append((key, theme, wash, worst, tab, rec, fails, tabfail))
            print(f"{key:13s} {theme:5s} wash {int(wash * 100):3d}%  " +
                  "  ".join(f"{n} {worst[n]['min']:.2f}/{worst[n]['p0.1']:.2f}" for n in FG[theme]) +
                  f"  | tab washed {tab['washed']['min']:.2f}/{tab['washed']['p0.1']:.2f} raw {tab['raw_image']['min']:.2f}"
                  f"{'  FAIL ' + ','.join(fails) + ' -> wash ' + str(rec['smallest_passing_wash']) if fails else ''}"
                  f"{'  TAB<target' if tabfail else ''}")
    doc = {"model": {"page": PAGE, "surface": SURFACE, "foregrounds": FG, "washes_light_dark": WASH,
                     "ratios": list(RATIOS), "top_fraction": TOP_FRACTION, "tab_fraction": TAB_FRACTION,
                     "tab_alpha": TAB_ALPHA, "blur": "9x9 box", "floor": FLOOR, "tab_targets": TAB_TARGET},
           "images": results}
    out.write_text(json.dumps(doc, indent=2) + "\n")
    if table_md:
        md = ["| Image | Theme | Wash | " + " | ".join(["text", "text-2", "Ink", "Harbour", "Moss", "Plum"]) + " | Tab (washed) | Tab (raw) |",
              "|---|---|---:|" + "---:|" * 8]
        for key, theme, wash, worst, tab, rec, fails, tabfail in lines:
            cells = " | ".join(f"{worst[n]['min']:.2f}" for n in ("text", "text-2", "ink", "harbour", "moss", "plum"))
            md.append(f"| {key} | {theme} | {int(wash * 100)}% | {cells} | {tab['washed']['min']:.2f} | {tab['raw_image']['min']:.2f} |")
        table_md.write_text("\n".join(md) + "\n")
    print("FAILURES:", failures if failures else "none")
    return 0


if __name__ == "__main__":
    sys.exit(main())
