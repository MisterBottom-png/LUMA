#!/usr/bin/env python3
"""Convert the ten handoff background PNGs to lossy WebP at source resolution.

Usage (run with -I; the extracted PNG directory is passed in, never the CWD):
  python3 -I scripts/limestone/convert_backgrounds.py <png-dir> <out-dir> [quality] [--metrics-json path]

* Verifies each source SHA-256 (design-inputs.md) before converting; aborts on mismatch.
* No resize / upscale: output keeps 1024x1536.
* Output names are underscore-only, e.g. bg_harbour_mist_light.webp.
* Reports size, MAE, PSNR (against the source), and low-frequency error
  (9x9 box-blurred MAE / max, plus the max error of the row-mean and column-mean
  profiles, which is what gradient banding shows up in).
"""
import hashlib
import io
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image

SOURCES = {
    "bg_limestone_light.png": ("bg_limestone_light", "e6fef132c15b17a45f123f53e711b76af34e074f91051e1578e1bc402509f135"),
    "bg_limestone_dark.png": ("bg_limestone_dark", "d5df88e8bd833bf72106558a7e645572390c3e9ff2b19d6c3e8f620bec0de27c"),
    "bg_harbour-mist_light.png": ("bg_harbour_mist_light", "37e56c49503d762ef84ba6d07fca2f6afd032a777f591089e90c33d3b05b5c12"),
    "bg_harbour-mist_dark.png": ("bg_harbour_mist_dark", "f61900b3aad5c9c0887e79103cafca0ea2bdf400c23d27e18b868530a5b2f14e"),
    "bg_ink-wash_light.png": ("bg_ink_wash_light", "94479771a5fd5fb3f49971e9abcff995ab2402f10d3d88e60fd5b1b52d091ea1"),
    "bg_ink-wash_dark.png": ("bg_ink_wash_dark", "1e5c02dd203b79c8e447f92b4295a6e27fe42a5f7cdce72eb674ee06f14105e0"),
    "bg_pine-fog_light.png": ("bg_pine_fog_light", "2f8ea6cc66772e774d2ea61ae1951e5f935f2af3834c9c52ad0c1cd7e08cc211"),
    "bg_pine-fog_dark.png": ("bg_pine_fog_dark", "305b2ac915d8d29fa0d8179b058832f24e13d1ab15d632774166fb150760e486"),
    "bg_linen-light_light.png": ("bg_linen_light_light", "b62463a0ce666bb374c50804ab1380a52be607a50fbd7830d6e7330680c2f272"),
    "bg_linen-light_dark.png": ("bg_linen_light_dark", "343792ca9c97532f0570f0089af6729ed5f12e67479d789727d564f94c5dfea6"),
}
DEFAULT_QUALITY = 94


def box_blur(a: np.ndarray, k: int = 9) -> np.ndarray:
    p = k // 2
    pa = np.pad(a, ((p, p), (p, p), (0, 0)), mode="edge")
    c = np.pad(np.cumsum(np.cumsum(pa, 0), 1), ((1, 0), (1, 0), (0, 0)))
    s = c[k:, k:] - c[:-k, k:] - c[k:, :-k] + c[:-k, :-k]
    return s / (k * k)


def main() -> int:
    args = [a for a in sys.argv[1:]]
    metrics_path = None
    if "--metrics-json" in args:
        i = args.index("--metrics-json")
        metrics_path = Path(args[i + 1])
        del args[i:i + 2]
    if len(args) < 2:
        print(__doc__)
        return 2
    src_dir, out_dir = Path(args[0]), Path(args[1])
    quality = int(args[2]) if len(args) > 2 else DEFAULT_QUALITY
    out_dir.mkdir(parents=True, exist_ok=True)

    results = {}
    total = 0
    for fname, (stem, sha) in SOURCES.items():
        data = (src_dir / fname).read_bytes()
        got = hashlib.sha256(data).hexdigest()
        if got != sha:
            print(f"FAIL checksum {fname}: {got}")
            return 1
        im = Image.open(io.BytesIO(data))
        if im.size != (1024, 1536) or im.mode != "RGB":
            print(f"FAIL unexpected source format {fname}: {im.size} {im.mode}")
            return 1
        out = out_dir / f"{stem}.webp"
        im.save(out, "WEBP", quality=quality, method=6)
        a = np.asarray(im).astype(np.float64)
        d = np.asarray(Image.open(out).convert("RGB")).astype(np.float64)
        assert d.shape == a.shape, "size changed"
        diff = d - a
        ba, bd = box_blur(a), box_blur(d)
        row = np.abs(a.mean(1) - d.mean(1)).max()
        col = np.abs(a.mean(0) - d.mean(0)).max()
        size = out.stat().st_size
        total += size
        results[stem] = {
            "source_sha256": sha, "source_bytes": len(data), "webp_bytes": size, "quality": quality,
            "mae": round(float(np.abs(diff).mean()), 3),
            "psnr_db": round(float(10 * np.log10(255 ** 2 / (diff ** 2).mean())), 2),
            "blur9_mae": round(float(np.abs(ba - bd).mean()), 3),
            "blur9_max": round(float(np.abs(ba - bd).max()), 2),
            "row_profile_max_err": round(float(row), 3),
            "col_profile_max_err": round(float(col), 3),
        }
        r = results[stem]
        print(f"{out.name}: {size} B  MAE {r['mae']}  PSNR {r['psnr_db']} dB  blur9 MAE/max {r['blur9_mae']}/{r['blur9_max']}"
              f"  row/col profile max {r['row_profile_max_err']}/{r['col_profile_max_err']}")
    print(f"TOTAL {total} bytes ({total / 1e6:.3f} MB) at quality {quality}")
    if metrics_path:
        metrics_path.write_text(json.dumps({"quality": quality, "total_bytes": total, "files": results}, indent=2) + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
