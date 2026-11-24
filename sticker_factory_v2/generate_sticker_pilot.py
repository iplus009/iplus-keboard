#!/usr/bin/env python3
"""Keybird Sticker Factory v4.1: License-clean Qt6/HarfBuzz + die-cut stickers.

Default pilot: 3 packs x 20 = 60 stickers.
--full: 50 packs x 20 = 1,000 stickers across 5 distinct art styles:
  - Packs 01-12: Bangla Chat Memes & Slang (unbroken Qt6 HarfBuzz conjuncts + die-cut)
  - Packs 13-24: Kawaii Mascots (Mochi Cat, Boba Bear, Frog, Bunny)
  - Packs 25-34: 3D Studio Reactions (volumetric Fluent-style cel light, no black strokes)
  - Packs 35-44: Live Animated Looping RGBA WebP Emotes
  - Packs 45-50: Neon Vinyl & Comic Action Stamps

Usage: python generate_sticker_pilot.py [--full]
"""
from __future__ import annotations

import argparse
import colorsys
import hashlib
import json
import shutil
import sys
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE / "src"))
from gates import check_sticker  # noqa: E402
from qt_stickers import (  # noqa: E402
    render_animated_sticker_webp,
    render_bangla_meme,
    render_fluent_3d,
    render_kawaii_mascot,
    render_neon_vinyl,
)

OUT = HERE / "out" / "stickers"
OUT_FULL = HERE / "out" / "full" / "stickers"
PROOF = HERE / "out" / "proof"
CATALOG = HERE / "data" / "pilot_stickers.json"
CATALOG_FULL = HERE / "data" / "full_stickers.json"
for p in (OUT, OUT_FULL, PROOF, HERE / "data"):
    p.mkdir(parents=True, exist_ok=True)

PACKS = [
    ("kb_sticker_pilot_diecut", "diecut", ["warm", "calm", "citrus"]),
    ("kb_sticker_pilot_bubble", "speech_bubble", ["friendly", "quiet", "bright"]),
    ("kb_sticker_pilot_tag", "handwritten_tag", ["paper", "ink", "cloth"]),
]
PALETTES = {
    "warm": ("#F59E0B", "#5C261A"), "calm": ("#60A5FA", "#1E3A5F"),
    "citrus": ("#FACC15", "#4D4D18"), "friendly": ("#34D399", "#144832"),
    "quiet": ("#A78BFA", "#372760"), "bright": ("#FB7185", "#6B1D2F"),
    "paper": ("#FDE68A", "#574020"), "ink": ("#94A3B8", "#1E293B"),
    "cloth": ("#F472B6", "#581C3C"),
}

BANGLA_MEMES = [
    ("কী অবস্থা?", "ভাই!"), ("অস্থির!!", "বস"), ("চিল করো", "ব্রো"),
    ("চা খাবা?", "গরম!"), ("সেই হইছে!", "জোস"), ("ঈদ মোবারক", "সবাইকে"),
    ("একদম", "ঠিক!"), ("হাহাহা!", "মজা!"), ("কী বলো!", "সত্যি?!"),
    ("মন ভালো", "হয়ে গেল"), ("জলদি আসো", "ভাই"), ("সাবাশ!!", "দারুণ!"),
    ("ওকে বস", "ডান!"), ("সেরা ভাই", "১০০%"), ("উফ আগুন!", "হট!!"),
    ("চলো যাই", "এখন!"), ("ভালো থাকো", "সবসময়"), ("থামো তো!", "ওরে"),
    ("আছি ব্রো", "পাশেই"), ("টাটা বায়!", "পরে দেখা"), ("কই গেলা?", "হ্যালো!"),
    ("মজা নিচ্ছি", "হাহা"), ("ওয়াও!", "কী সুন্দর"), ("লিজেন্ড!", "বস"),
]

KAWAII_CAPTIONS = [
    "HII!", "LOVE", "MEOW", "YAY!", "BOBA", "OKAY", "CHILL", "NICE",
    "UWU", "CUTE", "OMG!", "SHY", "YUM!", "NAP..", "WAIT", "BEST",
    "WOW!", "COOL", "HEHE", "HUGS",
]

NEON_ACTIONS = [
    "LET'S GO!", "GG!!", "BOOM!", "100%", "BOSS!", "WAIT!",
    "FIRE!", "VIBE!", "EPIC!", "YES!!", "WIN!", "HYPE!",
]

FULL_PACK_TITLES = [
    "Bangla Adda Memes", "Deshi Reactions Pro", "Chai & Chill Bangla", "Dhaka Slang Pack",
    "Eid & Utsav Greetings", "Friendly Bangla Chat", "Bangla Funny Pop", "Monsoon Bangla Vibes",
    "College Adda Stickers", "Deshi Boss Stamps", "Sweet Bangla Wishes", "Daily Bangla Express",
    "Mochi Peach Cats", "Boba Milk Bears", "Matcha Frog Club", "Sleepy Panda Pals",
    "Bunny Blush Pack", "Pastel Kitty Drops", "Office Cozy Pets", "Kawaii Foodies",
    "Chibi Hugs & Love", "Cute Sparkle Gang", "Soft Cream Mascots", "Strawberry Mochi",
    "3D Glossy Smiles", "Studio Clay Hearts", "Sunny 3D Emotes", "3D Party Reactions",
    "Vibrant 3D Moods", "Fluent Coral Pop", "Cobalt 3D Club", "Emerald 3D Faces",
    "Amber Glow 3D", "Crystal Luxe 3D", "Live Heart Beats", "Live Party Confetti",
    "Live Hot Chai Steam", "Live Star Twinkle", "Live Kawaii Bounce", "Live Sparkle Pop",
    "Live Morning Cup", "Live Celebration", "Live Vibe Wave", "Live Bubble Joy",
    "Neon Vinyl Arcade", "Cyber Boom Stamps", "Patswerk Pop Club", "Gamer GG Badges",
    "Midnight Hype Neon", "Keybird Legend 50",
]


def _pack_archive(stage: Path, out_zip: Path, pid: str, title: str, accent_hex: str, items: list, shown: list):
    prev = Image.new("RGBA", (480, 320), (20, 22, 28, 255))
    for idx_t, thumb in enumerate(shown[:6]):
        col, row = idx_t % 3, idx_t // 3
        prev.alpha_composite(thumb, (20 + col * 152, 16 + row * 148))
    prev.convert("RGB").save(stage / "preview.webp", "WEBP", quality=78)

    manifest = {
        "schema": 1, "theme_id": pid, "app_id": "keybird",
        "title": title, "version": 1, "theme_type": "sticker",
        "is_light": False, "colors": {"primary": accent_hex, "background": "#14161C"},
        "sticker_pack": {"pack_icon_asset": "assets/stickers/s01_icon.png", "stickers": items},
    }
    (stage / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=1), encoding="utf-8")
    if out_zip.exists():
        out_zip.unlink()
    with zipfile.ZipFile(out_zip, "w", zipfile.ZIP_DEFLATED) as z:
        for p in sorted(stage.rglob("*")):
            if p.is_file():
                z.write(p, p.relative_to(stage).as_posix())


def generate_full_50_packs(resume_since: float = 0.0):
    catalog = []
    sample_cells = []
    for pack_idx in range(50):
        pid = f"kb_stk_hybrid_{pack_idx + 1:02d}"
        title = FULL_PACK_TITLES[pack_idx]
        if pack_idx < 12:
            style = "bangla_meme"
        elif pack_idx < 24:
            style = "kawaii_mascot"
        elif pack_idx < 34:
            style = "fluent_3d"
        elif pack_idx < 44:
            style = "live_animated_webp"
        else:
            style = "neon_vinyl"

        stage = OUT_FULL / pid / "v1"
        zp = OUT_FULL / f"{pid}_v1.iplustheme"
        base_hue = ((pack_idx * 0.173) + 0.04) % 1.0
        accent_hex = "#%02X%02X%02X" % tuple(int(c * 255) for c in colorsys.hsv_to_rgb(base_hue, 0.78, 0.92))
        s01_p = stage / "assets/stickers/s01.webp"
        if (resume_since > 0.0 and zp.exists() and zp.stat().st_mtime >= resume_since
                and (stage / "manifest.json").exists() and s01_p.exists()
                and len(list((stage / "assets/stickers").glob("*.webp"))) == 20):
            with Image.open(s01_p) as im0:
                sample_cells.append((pid, title, im0.convert("RGBA").resize((160, 160), Image.LANCZOS)))
            sha = hashlib.sha256(zp.read_bytes()).hexdigest()
            catalog.append({
                "pack_id": pid, "title": title, "style": style,
                "accent_hex": accent_hex, "count": 20,
                "size_bytes": zp.stat().st_size, "sha256": sha,
            })
            if (pack_idx + 1) % 5 == 0:
                print(f"  sticker packs {pack_idx + 1}/50 (resumed)", flush=True)
            continue

        if stage.exists():
            shutil.rmtree(stage)
        (stage / "assets/stickers").mkdir(parents=True, exist_ok=True)
        items = []
        shown = []

        for i in range(20):
            g = pack_idx * 20 + i
            h1 = (base_hue + i * 0.047) % 1.0
            h2 = (h1 + 0.36) % 1.0
            main = "#%02X%02X%02X" % tuple(int(c * 255) for c in colorsys.hsv_to_rgb(h1, 0.68, 0.92))
            sec = "#%02X%02X%02X" % tuple(int(c * 255) for c in colorsys.hsv_to_rgb(h2, 0.72, 0.90))
            dark = "#%02X%02X%02X" % tuple(int(c * 255) for c in colorsys.hsv_to_rgb(h1, 0.76, 0.28))

            name = f"s{i + 1:02d}"
            rel = f"assets/stickers/{name}.webp"
            out_path = stage / rel

            if style == "bangla_meme":
                l1, l2 = BANGLA_MEMES[(pack_idx * 5 + i) % len(BANGLA_MEMES)]
                label = f"{l1} {l2}".strip()
                img = render_bangla_meme(g, l1, l2, main, sec, dark)
                img.save(out_path, "WEBP", quality=82)
            elif style == "kawaii_mascot":
                cap = KAWAII_CAPTIONS[(pack_idx * 3 + i) % len(KAWAII_CAPTIONS)]
                label = cap
                pastel = "#%02X%02X%02X" % tuple(int(c * 255) for c in colorsys.hsv_to_rgb(h1, 0.34, 0.96))
                warm_ink = "#%02X%02X%02X" % tuple(int(c * 255) for c in colorsys.hsv_to_rgb(h1, 0.56, 0.28))
                img = render_kawaii_mascot(g, pastel, warm_ink, cap)
                img.save(out_path, "WEBP", quality=82)
            elif style == "fluent_3d":
                cap = KAWAII_CAPTIONS[(pack_idx * 7 + i) % len(KAWAII_CAPTIONS)]
                label = cap
                img = render_fluent_3d(g, main, sec, cap if i % 2 == 0 else "")
                img.save(out_path, "WEBP", quality=82)
            elif style == "live_animated_webp":
                cap = KAWAII_CAPTIONS[(pack_idx + i * 2) % len(KAWAII_CAPTIONS)]
                label = cap
                raw_bytes, img = render_animated_sticker_webp(g, main, dark, cap if i % 2 == 0 else "")
                out_path.write_bytes(raw_bytes)
            else:
                cap = NEON_ACTIONS[(pack_idx + i) % len(NEON_ACTIONS)]
                label = cap
                img = render_neon_vinyl(g, main, sec, cap)
                img.save(out_path, "WEBP", quality=82)

            if i == 0:
                img.resize((96, 96), Image.LANCZOS).save(stage / "assets/stickers/s01_icon.png", optimize=True)
                sample_cells.append((pid, title, img.resize((160, 160), Image.LANCZOS)))
            check = check_sticker(out_path)
            if not check["ok"]:
                raise RuntimeError(f"gate failed {pid}/{rel}: {check}")
            items.append({"id": name, "label": label, "asset": rel})
            if len(shown) < 6:
                shown.append(img.resize((136, 136), Image.LANCZOS))

        zp = OUT_FULL / f"{pid}_v1.iplustheme"
        _pack_archive(stage, zp, pid, title, accent_hex, items, shown)
        sha = hashlib.sha256(zp.read_bytes()).hexdigest()
        catalog.append({
            "pack_id": pid, "title": title, "style": style,
            "accent_hex": accent_hex, "count": len(items),
            "size_bytes": zp.stat().st_size, "sha256": sha
        })
        if (pack_idx + 1) % 5 == 0:
            print(f"  sticker packs {pack_idx + 1}/50 ({(pack_idx + 1) * 20} stickers)", flush=True)

    cell = 176
    sheet = Image.new("RGBA", (cell * 10, cell * 5), (22, 24, 30, 255))
    sd = ImageDraw.Draw(sheet)
    for idx_c, (pid, title, thumb) in enumerate(sample_cells):
        col, row = idx_c % 10, idx_c // 10
        x, y = col * cell, row * cell
        bg = (238, 240, 245, 255) if (row + col) % 2 == 0 else (34, 38, 48, 255)
        sd.rectangle((x + 4, y + 4, x + cell - 4, y + cell - 4), fill=bg)
        sheet.alpha_composite(thumb, (x + 8, y + 4))
    sheet.convert("RGB").save(PROOF / "full_stickers_50packs_overview.png")
    CATALOG_FULL.write_text(json.dumps(catalog, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"full stickers complete: {len(catalog)} packs, {sum(x['count'] for x in catalog)} stickers")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--full", action="store_true", help="generate 50 packs x 20 = 1000 stickers")
    ap.add_argument("--resume-since", type=float, default=0.0, help="reuse packs built at/after this epoch timestamp")
    args = ap.parse_args()
    if args.full:
        generate_full_50_packs(resume_since=args.resume_since)
        return

    catalog = []
    for pack_idx, (pid, style, themes) in enumerate(PACKS):
        stage = OUT / pid / "v1"
        if stage.exists():
            shutil.rmtree(stage)
        (stage / "assets/stickers").mkdir(parents=True, exist_ok=True)
        items = []
        shown = []
        for i in range(20):
            g = pack_idx * 20 + i
            main_hex, dark_hex = PALETTES[themes[g % len(themes)]]
            cap = KAWAII_CAPTIONS[g % len(KAWAII_CAPTIONS)]
            if pack_idx == 0:
                l1, l2 = BANGLA_MEMES[g % len(BANGLA_MEMES)]
                img = render_bangla_meme(g, l1, l2, main_hex, "#38BDF8", dark_hex)
            elif pack_idx == 1:
                img = render_kawaii_mascot(g, main_hex, dark_hex, cap)
            else:
                img = render_neon_vinyl(g, main_hex, "#38BDF8", cap)
            name = f"s{i + 1:02d}"
            rel = f"assets/stickers/{name}.webp"
            img.save(stage / rel, "WEBP", quality=82)
            img.resize((96, 96), Image.LANCZOS).save(stage / f"assets/stickers/{name}_icon.png", optimize=True)
            check = check_sticker(stage / rel)
            if not check["ok"]:
                raise RuntimeError(f"gate failed {rel}: {check}")
            items.append({"id": name, "label": cap, "asset": rel})
            shown.append((name, img))
        manifest = {
            "schema": 1, "theme_id": pid, "app_id": "keybird",
            "title": f"Pilot {style}", "version": 1, "theme_type": "sticker",
            "is_light": True, "colors": {"primary": "#E8A33D", "background": "#FFFFFF"},
            "sticker_pack": {"pack_icon_asset": "assets/stickers/s01_icon.png", "stickers": items},
        }
        (stage / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=1), encoding="utf-8")
        zp = OUT / f"{pid}_v1.iplustheme"
        if zp.exists():
            zp.unlink()
        with zipfile.ZipFile(zp, "w", zipfile.ZIP_DEFLATED) as z:
            for p in sorted(stage.rglob("*")):
                if p.is_file():
                    z.write(p, p.relative_to(stage).as_posix())
        sha = hashlib.sha256(zp.read_bytes()).hexdigest()
        catalog.append({"pack_id": pid, "style": style, "count": len(items), "sha256": sha})

        cell, gap = 528, 8
        sheet = Image.new("RGBA", ((cell + gap) * 5, (cell + gap) * 4), (245, 246, 250, 255))
        sd = ImageDraw.Draw(sheet)
        for row in range(4):
            for col in range(5):
                x, y = col * (cell + gap), row * (cell + gap)
                bg = (236, 236, 240, 255) if (row + col) % 2 == 0 else (42, 48, 60, 255)
                sd.rectangle((x, y, x + cell + gap, y + cell + gap), fill=bg)
        for i, (_, img) in enumerate(shown):
            sheet.alpha_composite(img, ((i % 5) * (cell + gap), (i // 5) * (cell + gap)))
        sheet.convert("RGB").save(PROOF / f"pack_{pack_idx + 1:02d}_pilot.png")
        print(f"pack {pid}: {len(items)} stickers | sheet {PROOF / f'pack_{pack_idx + 1:02d}_pilot.png'}")

    CATALOG.write_text(json.dumps(catalog, indent=1), encoding="utf-8")
    print("total stickers:", sum(x["count"] for x in catalog))


if __name__ == "__main__":
    main()
