#!/usr/bin/env python3
"""
Keybird Custom Sticker Factory (Phase 2 Art-Pack builder).

Turns raw generated art (a hero wallpaper + a 2x3 sticker sheet per theme) into
fully valid offline `.iplustheme` ZIP bundles:

    art/raw/<theme>_hero.<ext>   1080x720 keyboard backdrop
    art/raw/<theme>_sheet.<ext>  2 rows x 3 cols grid of 6 sticker cells

Output per theme (in art/out/):
    <theme_id>_v1.iplustheme
    ├── manifest.json
    ├── preview/preview_low.webp
    └── assets/
        ├── bg/<theme_id>_bg.webp
        ├── groups/{letter,functional,accent}.webp
        └── keys/k_<letter>_<name>.webp ... + k_space_banner.webp

It also prints the SHA-256 of each bundle (paste into the catalog's bundle_sha256).
Requires: Python 3 + Pillow (`pip install pillow`).
"""
from __future__ import annotations

import hashlib
import json
import shutil
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

HERE = Path(__file__).resolve().parent
RAW = HERE / "art" / "raw"
OUT = HERE / "art" / "out"

KEY_W, KEY_H = 192, 256          # keycap sticker size from the master template
SPACE_W, SPACE_H = 720, 160      # spacebar banner size
BG_W, BG_H = 1080, 720           # backdrop size


def find_raw(stem: str) -> Path:
    hits = sorted(RAW.glob(stem + ".*"))
    if not hits:
        raise FileNotFoundError(f"Missing raw art: {RAW / (stem + '.*')}")
    return hits[0]


def cover_resize(img: Image.Image, w: int, h: int) -> Image.Image:
    """Center-crop to the target aspect ratio, then resize."""
    img = img.convert("RGB")
    sw, sh = img.size
    scale = max(w / sw, h / sh)
    nw, nh = max(1, round(sw * scale)), max(1, round(sh * scale))
    img = img.resize((nw, nh), Image.LANCZOS)
    left, top = (nw - w) // 2, (nh - h) // 2
    return img.crop((left, top, left + w, top + h))


def slice_sheet(img: Image.Image, cols: int = 3, rows: int = 2, gap: float = 0.02) -> list[Image.Image]:
    """Slice a strict grid into individual cells, trimming a small inner gap."""
    img = img.convert("RGB")
    w, h = img.size
    cw, ch = w / cols, h / rows
    gx, gy = cw * gap, ch * gap
    cells: list[Image.Image] = []
    for r in range(rows):
        for c in range(cols):
            box = (
                round(c * cw + gx), round(r * ch + gy),
                round((c + 1) * cw - gx), round((r + 1) * ch - gy),
            )
            cells.append(img.crop(box))
    return cells


def gradient_card(top: tuple[int, int, int], bottom: tuple[int, int, int], w=KEY_W, h=KEY_H) -> Image.Image:
    """A code-drawn frosted-glass fallback card for unknown / Bangla slot keys."""
    base = Image.new("RGB", (w, h))
    px = base.load()
    for y in range(h):
        t = y / max(1, h - 1)
        c = tuple(round(top[i] * (1 - t) + bottom[i] * t) for i in range(3))
        for x in range(w):
            px[x, y] = c
    d = ImageDraw.Draw(base, "RGBA")
    d.rounded_rectangle((3, 3, w - 4, h - 4), radius=20, outline=(255, 255, 255, 40), width=2)
    return base


THEMES = [
    {
        "theme_id": "kb_bangla_utsav", "raw_stem": "bangla",
        "title": "Bangla Utsav",
        "category": "culture",
        "accent": "#F5B301",
        "primary": "#F5B301",
        "bg": ["#04170F", "#0A2E1E", "#03110B"],
        "key_bg": "#CC12190C", "key_stroke": "#F5D27A",
        "enter": "#F5B301", "space_stroke": "#F5B301", "text": "#FFFFFF",
        "glass": True, "scrim": 0.60, "font": "bold",
        "cells": ["A", "D", "N", "P", "R", "T"],
        "names": {
            "A": "alpona", "D": "durga", "N": "nouka",
            "P": "pujo", "R": "tagore", "T": "tabla",
        },
        "names_full": {
            "A": "Alpona", "D": "Durga", "N": "Nouka",
            "P": "Pujo", "R": "Tagore", "T": "Tabla",
        },
    },
    {
        "theme_id": "kb_cute_zoo", "raw_stem": "animals",
        "title": "Cute Zoo",
        "category": "animals",
        "accent": "#4ADE80",
        "primary": "#4ADE80",
        "bg": ["#04140F", "#0B2A22", "#050F0C"],
        "key_bg": "#CC0E1E19", "key_stroke": "#7FE3B8",
        "enter": "#4ADE80", "space_stroke": "#4ADE80", "text": "#FFFFFF",
        "glass": True, "scrim": 0.58, "font": "bold",
        "cells": ["A", "B", "C", "D", "E", "F"],
        "names": {
            "A": "ant", "B": "bear", "C": "cat",
            "D": "dog", "E": "eagle", "F": "fox",
        },
        "names_full": {
            "A": "Ant", "B": "Bear", "C": "Cat",
            "D": "Dog", "E": "Eagle", "F": "Fox",
        },
    },
    {
        "theme_id": "kb_anime_heroes", "raw_stem": "anime",
        "title": "Anime Heroes",
        "category": "anime",
        "accent": "#F59E0B",
        "primary": "#F59E0B",
        "bg": ["#080B14", "#15203B", "#0B101D"],
        "key_bg": "#CC121829", "key_stroke": "#8CB8FF",
        "enter": "#F59E0B", "space_stroke": "#F59E0B", "text": "#FFFFFF",
        "glass": True, "scrim": 0.62, "font": "bold",
        "cells": ["A", "K", "L", "N", "S", "Z"],
        "names": {
            "A": "ace", "K": "kira", "L": "luffy",
            "N": "naruto", "S": "sasuke", "Z": "zoro",
        },
        "names_full": {
            "A": "Ace", "K": "Kira", "L": "Luffy",
            "N": "Naruto", "S": "Sasuke", "Z": "Zoro",
        },
    },
    {
        "theme_id": "kb_space_crew", "raw_stem": "space",
        "title": "Space Crew",
        "category": "scifi",
        "accent": "#22D3EE",
        "primary": "#22D3EE",
        "bg": ["#0A0618", "#16123A", "#05040F"],
        "key_bg": "#CC14122A", "key_stroke": "#67E8F9",
        "enter": "#22D3EE", "space_stroke": "#A78BFA", "text": "#FFFFFF",
        "glass": True, "scrim": 0.55, "font": "bold",
        "cells": ["A", "B", "C", "D", "E", "F"],
        "names": {
            "A": "astronaut", "B": "bot", "C": "comet",
            "D": "droid", "E": "earth", "F": "falcon",
        },
        "names_full": {
            "A": "Astronaut", "B": "Bot", "C": "Comet",
            "D": "Droid", "E": "Earth", "F": "Falcon",
        },
    },
]

LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"


def build_manifest(t: dict, key_assets: dict[str, str]) -> dict:
    keymap: dict[str, str] = {}
    for letter in LETTERS:
        low = letter.lower()
        keymap[f"k_{low}"] = key_assets.get(letter, "assets/groups/letter_glass.webp")

    # Functional / accent / space keys
    keymap.update({
        "k_shift": "assets/groups/functional_glass.webp",
        "k_delete": "assets/groups/functional_glass.webp",
        "k_sym": "assets/groups/functional_glass.webp",
        "k_emoji": "assets/groups/functional_glass.webp",
        "k_enter": "assets/groups/accent_gold_glass.webp",
        "k_space": "assets/keys/k_space_banner.webp",
    })
    # Bangla Avro / Probhat slot fallbacks
    for r in range(3):
        for c in range(10 if r < 2 else 7):
            keymap[f"slot_r{r}_c{c}"] = "assets/groups/letter_glass.webp"

    return {
        "schema": 1,
        "theme_id": t["theme_id"],
        "app_id": "keybird",
        "title": t["title"],
        "version": 1,
        "theme_type": "art_pack",
        "is_light": False,
        "colors": {
            "primary": t["primary"], "on_primary": "#090C14",
            "primary_container": "#151D31", "on_primary_container": "#F8FAFC",
            "secondary": t["accent"], "on_secondary": "#090C14",
            "background": t["bg"][0], "on_background": "#FFFFFF",
            "surface": "#101626", "on_surface": "#FFFFFF",
            "surface_variant": "#172036", "on_surface_variant": "#CBD5E1",
            "outline": t["key_stroke"],
        },
        "keyboard": {
            "bg_type": "gradient", "bg_colors": t["bg"],
            "key_bg": t["key_bg"], "key_pressed_bg": "#4D7FA8E8",
            "functional_key_bg": "#B80F1422",
            "enter_key_bg": t["enter"], "enter_key_fg": "#090C14",
            "spacebar_bg": "#151C2E", "spacebar_stroke": t["space_stroke"],
            "key_stroke": t["key_stroke"], "key_stroke_width_dp": 1.15,
            "key_corner_radius_dp": 14.0, "key_text": t["text"],
            "key_hint": "#E2E8F0", "popup_bg": "#162038",
            "popup_text": "#FFFFFF", "gesture_trail": t["accent"],
            "font_style": t["font"], "scrim_alpha": t["scrim"],
            "glass_rim_glow": t["glass"],
        },
        "art_pack": {
            "bg_full_asset": f"assets/bg/{t['theme_id']}_bg.webp",
            "spacebar_asset": "assets/keys/k_space_banner.webp",
            "scrim_mode": "auto_yiq",
            "keymap": keymap,
            "group_fallbacks": {
                "letter": "assets/groups/letter_glass.webp",
                "functional": "assets/groups/functional_glass.webp",
                "accent": "assets/groups/accent_gold_glass.webp",
                "space": "assets/keys/k_space_banner.webp",
                "default": "assets/groups/letter_glass.webp",
            },
        },
    }


def build_sticker_pack(t: dict, art_pack_dir: Path, key_assets: dict[str, str]) -> tuple[Path, str]:
    """Build a `theme_type=sticker` bundle consumable by the in-keyboard Stickers tab."""
    pack_id = f"{t['theme_id']}_stickers"
    pack = OUT / pack_id / f"{pack_id}_v1"
    if pack.exists():
        shutil.rmtree(pack)
    (pack / "assets/stickers").mkdir(parents=True, exist_ok=True)
    (pack / "preview").mkdir(parents=True, exist_ok=True)

    stickers = []
    for letter in t["cells"]:
        src = art_pack_dir / key_assets[letter]
        rel = f"assets/stickers/{key_assets[letter].split('/')[-1]}"
        shutil.copy2(src, pack / rel)
        stickers.append({
            "id": f"stk_{letter.lower()}_{t['names'][letter]}",
            "label": f"{letter} = {t['names_full'][letter]}",
            "asset": rel,
        })

    manifest = {
        "schema": 1,
        "theme_id": pack_id,
        "app_id": "keybird",
        "title": f"{t['title']} Stickers",
        "version": 1,
        "theme_type": "sticker",
        "is_light": False,
        "colors": {"primary": t["primary"], "background": t["bg"][0]},
        "sticker_pack": {
            "pack_icon_asset": f"assets/stickers/{key_assets[t['cells'][0]].split('/')[-1]}",
            "stickers": stickers,
        },
    }
    (pack / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")

    # preview from the art-pack preview
    prev_src = art_pack_dir / "preview/preview_low.webp"
    if prev_src.exists():
        shutil.copy2(prev_src, pack / "preview/preview_low.webp")

    zip_path = OUT / f"{pack_id}_v1.iplustheme"
    if zip_path.exists():
        zip_path.unlink()
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as z:
        for f in sorted(pack.rglob("*")):
            if f.is_file():
                z.write(f, f.relative_to(pack).as_posix())
    sha = hashlib.sha256(zip_path.read_bytes()).hexdigest()
    (OUT / f"{pack_id}_v1.sha256.txt").write_text(sha + "\n", encoding="utf-8")
    return zip_path, sha


def build_theme(t: dict) -> dict:
    pack = OUT / t["theme_id"] / f"{t['theme_id']}_v1"
    if pack.exists():
        shutil.rmtree(pack)
    for sub in ("preview", "assets/bg", "assets/groups", "assets/keys"):
        (pack / sub).mkdir(parents=True, exist_ok=True)

    stem = t["raw_stem"]
    hero = Image.open(find_raw(f"{stem}_hero"))
    sheet = Image.open(find_raw(f"{stem}_sheet"))
    cells = slice_sheet(sheet)

    # Backdrop + preview
    cover_resize(hero, BG_W, BG_H).save(pack / "assets/bg" / f"{t['theme_id']}_bg.webp", "WEBP", quality=80)
    preview = cover_resize(hero, 720, 480)
    d = ImageDraw.Draw(preview, "RGBA")
    d.rectangle((0, 430, 720, 480), fill=(0, 0, 0, 150))
    preview.save(pack / "preview" / "preview_low.webp", "WEBP", quality=60)

    # Keycap stickers (letter-matched, from the 2x3 sheet)
    key_assets: dict[str, str] = {}
    for letter, cell in zip(t["cells"], cells):
        name = t["names"][letter]
        rel = f"assets/keys/k_{letter.lower()}_{name}.webp"
        cover_resize(cell, KEY_W, KEY_H).save(pack / rel, "WEBP", quality=80)
        key_assets[letter] = rel

    # Code-drawn frosted fallback cards for unassigned letters / Bangla slots
    def to_rgb(hexstr: str) -> tuple[int, int, int]:
        h = hexstr.lstrip("#")
        if len(h) == 8:
            h = h[:6]
        return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))

    accent = to_rgb(t["accent"])
    gradient_card((accent[0] // 6, accent[1] // 6, accent[2] // 6), (8, 10, 16)).save(
        pack / "assets/groups/letter_glass.webp", "WEBP", quality=80)
    gradient_card((20, 24, 34), (8, 10, 16)).save(
        pack / "assets/groups/functional_glass.webp", "WEBP", quality=80)
    gradient_card((80, 60, 12), (24, 16, 6)).save(
        pack / "assets/groups/accent_gold_glass.webp", "WEBP", quality=80)

    # Spacebar banner from the hero bottom strip
    cover_resize(hero, SPACE_W, SPACE_H).save(
        pack / "assets/keys/k_space_banner.webp", "WEBP", quality=80)

    manifest = build_manifest(t, key_assets)
    (pack / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")

    zip_path = OUT / f"{t['theme_id']}_v1.iplustheme"
    if zip_path.exists():
        zip_path.unlink()
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as z:
        for f in sorted(pack.rglob("*")):
            if f.is_file():
                z.write(f, f.relative_to(pack).as_posix())

    sha = hashlib.sha256(zip_path.read_bytes()).hexdigest()
    (OUT / f"{t['theme_id']}_v1.sha256.txt").write_text(sha + "\n", encoding="utf-8")

    # Chat sticker pack for the existing in-keyboard Stickers tab
    sticker_zip, sticker_sha = build_sticker_pack(t, pack, key_assets)

    return {
        "theme_id": t["theme_id"], "title": t["title"],
        "bytes": zip_path.stat().st_size, "sha256": sha,
        "sticker_zip": sticker_zip.name, "sticker_sha256": sticker_sha,
        "stickers": ", ".join(f"{k}={t['names_full'][k]}" for k in t["cells"]),
    }


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    print("Keybird Custom Sticker Factory\n" + "=" * 60)
    rows = []
    for t in THEMES:
        try:
            rows.append(build_theme(t))
        except FileNotFoundError as e:
            print(f"[SKIP] {t['theme_id']}: {e}")
    for r in rows:
        print(f"\n{r['title']}  ({r['theme_id']})")
        print(f"  bundle : {OUT / (r['theme_id'] + '_v1.iplustheme')}")
        print(f"  size   : {r['bytes']:,} bytes")
        print(f"  sha256 : {r['sha256']}")
        print(f"  sticker pack : {r['sticker_zip']}")
        print(f"  sticker sha  : {r['sticker_sha256']}")
        print(f"  keys   : {r['stickers']}")
    print("\nDone. Paste each sha256 into the catalog's bundle_sha256 column.")


if __name__ == "__main__":
    main()
