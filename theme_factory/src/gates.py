"""Pure, testable quality gates for theme manifests and previews."""
from __future__ import annotations

import json
from pathlib import Path

from PIL import Image

from tokens import RADII, STROKES, contrast

REQUIRED_TOP = {"schema", "theme_id", "title", "version", "theme_type", "is_light", "colors", "keyboard", "art_pack"}
REQUIRED_KB = {"key_bg", "key_pressed_bg", "key_text", "keyPressedText", "key_color_map",
               "key_corner_radius_dp", "key_stroke_width_dp", "effect_mode", "motion"}


def check_manifest(path: Path):
    p = Path(path)
    if not p.exists():
        return {"ok": False, "reason": "missing manifest"}
    m = json.loads(p.read_text(encoding="utf-8"))
    if not REQUIRED_TOP.issubset(m):
        return {"ok": False, "reason": "missing top-level fields"}
    k = m["keyboard"]
    if not REQUIRED_KB.issubset(k):
        return {"ok": False, "reason": "missing keyboard fields"}
    if k["key_corner_radius_dp"] not in RADII:
        return {"ok": False, "reason": "radius off strict scale"}
    if float(k["key_stroke_width_dp"]) not in STROKES:
        return {"ok": False, "reason": "stroke off strict scale"}
    art = m.get("art_pack") or {}
    if art.get("keymap", {}) != {} or art.get("group_fallbacks", {}) != {}:
        return {"ok": False, "reason": "art_pack keymap/fallbacks must be empty"}
    bg_asset = art.get("bg_full_asset")
    if bg_asset and not (p.parent / bg_asset).exists():
        return {"ok": False, "reason": f"missing bg_full_asset {bg_asset}"}
    # WCAG 4.5:1 for key labels over their key fill; >= 3:1 for hints
    cr = contrast(k["key_text"], k["key_bg"])
    hint = contrast(k["key_hint"], k["key_bg"])
    if cr < 4.5 or hint < 3.0:
        return {"ok": False, "reason": f"contrast key={cr:.2f} hint={hint:.2f}", "key_contrast": cr, "hint_contrast": hint}
    # Every key fill must stay visually separate from the keyboard background,
    # otherwise keys go invisible (the bug the 1.0 pilot shipped with).
    bgs = k.get("bg_colors") or [m["colors"]["background"]]
    fills = [k["key_bg"], k["key_pressed_bg"], k["functional_key_bg"], k["enter_key_bg"]]
    fills += list(k.get("key_color_map", {}).get("palette") or [])
    worst_fill, worst_ratio = None, 1e9
    for fill in fills:
        for bg in bgs:
            r = contrast(fill, bg)
            if r < worst_ratio:
                worst_ratio, worst_fill = r, fill
    if worst_ratio < 1.4:
        return {"ok": False, "reason": f"key fill {worst_fill} blends into bg ({worst_ratio:.2f}:1)",
                "key_contrast": cr, "hint_contrast": hint, "bg_separation": worst_ratio}
    return {"ok": True, "key_contrast": cr, "hint_contrast": hint, "bg_separation": worst_ratio, "theme_id": m["theme_id"]}


def dhash(path, hash_size=16):
    img = Image.open(path).convert("L").resize((hash_size + 1, hash_size), Image.LANCZOS)
    px = img.tobytes()  # getdata() is deprecated (removal in Pillow 14)
    return _hash_bits(px, hash_size)


def dhash_hue(path, hash_size=16):
    """dHash over the hue channel: catches chroma-only differences that the
    grayscale hash is blind to (true-QWERTY layouts share structure)."""
    img = Image.open(path).convert("HSV").resize((hash_size + 1, hash_size), Image.LANCZOS)
    px = img.tobytes()[0: (hash_size + 1) * hash_size]
    return _hash_bits(px, hash_size)


def _hash_bits(px, hash_size):
    bits = 0
    for row in range(hash_size):
        for col in range(hash_size):
            bits = (bits << 1) | (1 if px[row * (hash_size + 1) + col] > px[row * (hash_size + 1) + col + 1] else 0)
    return bits


def color_grid(path, n=8):
    """Coarse average-color grid: sees flat-field tone differences that
    gradient-based hashes are blind to."""
    img = Image.open(path).convert("RGB").resize((n, n), Image.LANCZOS)
    return list(img.tobytes())


def preview_distance(a, b):
    """Human-view distinctness: structure + hue (2x) + coarse color field."""
    l1 = sum(abs(x - y) for x, y in zip(a[2], b[2]))
    return hamming(a[0], b[0]) + 2 * hamming(a[1], b[1]) + min(l1 // 96, 60)
    bits = 0
    for row in range(hash_size):
        for col in range(hash_size):
            bits = (bits << 1) | (1 if px[row * (hash_size + 1) + col] > px[row * (hash_size + 1) + col + 1] else 0)
    return bits


def hamming(a, b):
    return (a ^ b).bit_count()
