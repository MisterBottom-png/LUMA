#!/usr/bin/env python3
"""Verify the bundled static fonts. Usage: python3 -I verify_fonts.py <font-dir>
Exit 0 only if every check passes."""
import string
import sys
from pathlib import Path

from fontTools.ttLib import TTFont

EXPECTED = {
    "literata_regular": ("Literata", 400), "literata_medium": ("Literata", 500),
    "noto_sans_regular": ("Noto Sans", 400), "noto_sans_medium": ("Noto Sans", 500),
    "noto_sans_semibold": ("Noto Sans", 600),
}
ESTONIAN = string.ascii_letters + "õÕäÄöÖüÜšŠžŽ"
RUSSIAN = "".join(chr(c) for c in range(0x0410, 0x0450)) + "ёЁ"
PUNCT = "·…–—‘’“”„«»›€№•−\u00a0°×±%&@#"
ARROW = "→"  # Literata only; Noto Sans upstream has no arrows (system fallback)
DIGITS = string.digits
REQUIRED_FEATURES = {"kern", "tnum", "liga", "lnum", "case", "locl"}


def main() -> int:
    d = Path(sys.argv[1])
    ok = True
    app_chars = set()
    for res in sys.argv[2:]:  # optional: res dirs whose strings*.xml must be fully covered
        for f in Path(res).glob("values*/strings*.xml"):
            app_chars |= {c for c in f.read_text(encoding="utf-8") if ord(c) > 32 and c not in "\u200b"}
    print(f"app string characters checked: {len(app_chars)}")
    for name, (family, weight) in EXPECTED.items():
        path = d / f"{name}.ttf"
        t = TTFont(str(path))
        cmap = t.getBestCmap()
        missing = [c for c in ESTONIAN + RUSSIAN + PUNCT + DIGITS if ord(c) not in cmap]
        if name.startswith("literata"):
            missing += [c for c in ARROW if ord(c) not in cmap]
        missing += [c for c in app_chars if ord(c) not in cmap and c not in missing]
        arrow_in = ord(ARROW) in cmap
        gsub = {fr.FeatureTag for fr in t["GSUB"].table.FeatureList.FeatureRecord}
        gpos = {fr.FeatureTag for fr in t["GPOS"].table.FeatureList.FeatureRecord}
        feats = gsub | gpos
        # tnum must actually substitute the digits
        tnum_ok = False
        tnum_subs = {}
        lookups = t["GSUB"].table.LookupList.Lookup
        for fr in t["GSUB"].table.FeatureList.FeatureRecord:
            if fr.FeatureTag == "tnum":
                for li in fr.Feature.LookupListIndex:
                    for st in lookups[li].SubTable:
                        st = getattr(st, "ExtSubTable", st)
                        tnum_subs.update(getattr(st, "mapping", {}))
        hm = t["hmtx"]
        widths = {hm[cmap[ord(c)]][0] for c in DIGITS}
        # Noto Sans digits are tabular by default; Literata needs the tnum substitution.
        tnum_ok = len(widths) == 1 or all(cmap[ord(c)] in tnum_subs for c in DIGITS)
        fam = t["name"].getDebugName(1)
        results = {
            "loads": True,
            "static (no fvar)": "fvar" not in t,
            "usWeightClass": t["OS/2"].usWeightClass == weight,
            "family name kept": fam is not None and fam.startswith(family),
            "no missing glyphs": not missing,
            "features": REQUIRED_FEATURES <= feats,
            "digits tabular (default or via tnum)": tnum_ok,
            "copyright record kept": bool(t["name"].getDebugName(0)),
        }
        good = all(results.values())
        ok &= good
        print(f"{'PASS' if good else 'FAIL'} {path.name} size={path.stat().st_size} "
              f"usWeightClass={t['OS/2'].usWeightClass} family='{fam}' sub='{t['name'].getDebugName(2)}' "
              f"full='{t['name'].getDebugName(4)}' glyphs={len(t.getGlyphOrder())} "
              f"arrow_U+2192={arrow_in} features={sorted(feats & {'kern','liga','tnum','pnum','lnum','onum','case','locl'})}")
        for k, v in results.items():
            if not v:
                print(f"   FAILED: {k}", f"missing={missing}" if k == "no missing glyphs" else "")
    print("ALL PASS" if ok else "SOME FAILED")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
