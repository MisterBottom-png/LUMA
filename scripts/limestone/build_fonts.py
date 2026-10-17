#!/usr/bin/env python3
"""Build the bundled static fonts for Tallele (Limestone Harbour P1, D-19).

Usage (run with -I, paths as arguments):
  python3 -I scripts/limestone/build_fonts.py <literata-variable.ttf> <notosans-variable.ttf> <out-dir>

Inputs are the upstream google/fonts variable fonts (OFL). Output: five static,
subsetted TTF files with the contract names used by R.font.*:
  literata_regular, literata_medium, noto_sans_regular, noto_sans_medium, noto_sans_semibold

Neither upstream licence declares a Reserved Font Name, so original family names
are kept (name table copyright/licence records are preserved).
"""
import sys
from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

# Literata: opsz 12 is the font's own text default (design range 7-72 pt);
# body copy in the app is 14-17 sp, i.e. text sizes, not display sizes.
LITERATA_OPSZ = 12
NOTO_WDTH = 100

PLAN = [
    # (source key, out name, axis location, usWeightClass)
    ("literata", "literata_regular", {"wght": 400, "opsz": LITERATA_OPSZ}, 400),
    ("literata", "literata_medium", {"wght": 500, "opsz": LITERATA_OPSZ}, 500),
    ("noto", "noto_sans_regular", {"wght": 400, "wdth": NOTO_WDTH}, 400),
    ("noto", "noto_sans_medium", {"wght": 500, "wdth": NOTO_WDTH}, 500),
    ("noto", "noto_sans_semibold", {"wght": 600, "wdth": NOTO_WDTH}, 600),
]

# Unicode ranges kept (Latin incl. Estonian, Cyrillic incl. Russian, UI punctuation).
RANGES = [
    (0x0020, 0x007E),  # Basic Latin
    (0x00A0, 0x00FF),  # Latin-1 Supplement
    (0x0100, 0x017F),  # Latin Extended-A
    (0x0180, 0x024F),  # Latin Extended-B (kept whole: names of people/places in user text)
    (0x02B0, 0x02FF),  # Spacing modifier letters
    (0x0300, 0x036F),  # Combining diacritical marks
    (0x0400, 0x04FF),  # Cyrillic
    (0x2000, 0x206F),  # General Punctuation
    (0x2070, 0x209F),  # Super/subscripts
    (0x20A0, 0x20CF),  # Currency symbols
    (0x2100, 0x214F),  # Letterlike (e.g. No sign, trade mark)
    (0x2190, 0x21FF),  # Arrows
    (0x2212, 0x2212),  # Minus sign
    (0x2215, 0x2215),  # Division slash
    (0x2022, 0x2022),  # Bullet
    (0xFEFF, 0xFEFF),  # BOM / ZWNBSP
]

FEATURES = ["kern", "liga", "tnum", "pnum", "lnum", "onum", "case", "locl",
            "ccmp", "mark", "mkmk", "calt", "ss01", "frac", "zero", "sups", "subs"]


def _normalise_names(font: TTFont, loc: dict) -> None:
    """Drop the optical-size suffix ("Literata 12pt") the instancer adds so the
    family stays "Literata" / "Noto Sans". No Reserved Font Name applies."""
    name = font["name"]
    for rec in list(name.names):
        if rec.nameID in (1, 4, 6, 16, 17, 3):
            text = rec.toUnicode()
            text = text.replace("Literata 12pt", "Literata").replace("Literata12pt", "Literata")
            name.setName(text, rec.nameID, rec.platformID, rec.platEncID, rec.langID)


def build(src: Path, out: Path, loc: dict, weight: int) -> None:
    font = TTFont(str(src))
    inst = instancer.instantiateVariableFont(font, loc, inplace=False, updateFontNames=True)
    inst["OS/2"].usWeightClass = weight
    _normalise_names(inst, loc)

    opts = subset.Options()
    opts.layout_features = FEATURES
    opts.name_IDs = ["*"]
    opts.name_languages = ["*"]
    opts.notdef_outline = True
    opts.glyph_names = False
    opts.hinting = False
    opts.legacy_kern = False
    opts.drop_tables += ["DSIG", "vmtx", "vhea", "VORG"]
    opts.recalc_bounds = True
    sub = subset.Subsetter(opts)
    unicodes = [c for lo, hi in RANGES for c in range(lo, hi + 1)]
    sub.populate(unicodes=unicodes)
    sub.subset(inst)
    inst.save(str(out))


def main() -> int:
    if len(sys.argv) != 4:
        print(__doc__)
        return 2
    srcs = {"literata": Path(sys.argv[1]), "noto": Path(sys.argv[2])}
    out_dir = Path(sys.argv[3])
    out_dir.mkdir(parents=True, exist_ok=True)
    for key, name, loc, weight in PLAN:
        dst = out_dir / f"{name}.ttf"
        build(srcs[key], dst, loc, weight)
        print(f"{dst.name}: {dst.stat().st_size} bytes")
    return 0


if __name__ == "__main__":
    sys.exit(main())
