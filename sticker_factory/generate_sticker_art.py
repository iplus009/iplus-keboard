#!/usr/bin/env python3
"""
Local sticker-art generator for the Keybird Sticker Factory.

No AI service required: renders real color emoji (Segoe UI Emoji) on frosted
gradient cards, plus code-drawn wallpaper banners. Output matches the raw layout
that build_sticker_packs.py expects:

    art/raw/<theme>_sheet.png   2x3 grid of 6 sticker cells (A..Z mapping)
    art/raw/<theme>_hero.png    1080x720 keyboard backdrop
"""
from __future__ import annotations

import math
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont, ImageFilter

HERE = Path(__file__).resolve().parent
RAW = HERE / "art" / "raw"
RAW.mkdir(parents=True, exist_ok=True)

EMOJI_FONT = r"C:\Windows\Fonts\seguiemj.ttf"
LABEL_FONT = r"C:\Windows\Fonts\seguisb.ttf"

SHEET_W, SHEET_H = 1536, 1024          # 3 cols x 2 rows, each 512x512
BG_W, BG_H = 1080, 720


def font(size: int, emoji: bool = False) -> ImageFont.FreeTypeFont:
    path = EMOJI_FONT if emoji else LABEL_FONT
    try:
        return ImageFont.truetype(path, size)
    except OSError:
        return ImageFont.load_default()


def hexrgb(h: str) -> tuple[int, int, int]:
    h = h.lstrip("#")
    if len(h) == 8:
        h = h[:6]
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def mix(a, b, t):
    return tuple(round(a[i] * (1 - t) + b[i] * t) for i in range(3))


def gradient(w: int, h: int, top, bottom, diagonal=False) -> Image.Image:
    img = Image.new("RGB", (w, h))
    px = img.load()
    for y in range(h):
        for x in range(w):
            t = (y / max(1, h - 1) + x / max(1, w - 1)) / 2 if diagonal else y / max(1, h - 1)
            px[x, y] = mix(top, bottom, max(0.0, min(1.0, t)))
    return img


def rounded_mask(w: int, h: int, radius: int) -> Image.Image:
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, w - 1, h - 1), radius=radius, fill=255)
    return mask


def make_sticker(cell_index: int, letter: str, name: str, emoji: str,
                 top, bottom, accent) -> Image.Image:
    """One 512x512 sticker cell: frosted gradient card, big emoji, name + letter badge."""
    S = 512
    card = gradient(S, S, top, bottom, diagonal=True)

    # soft radial glow behind the emoji
    glow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    for r in range(190, 0, -8):
        a = int(26 * (1 - r / 190))
        gd.ellipse((S // 2 - r, S // 2 - 40 - r, S // 2 + r, S // 2 - 40 + r),
                   fill=(*accent, a))
    card = Image.alpha_composite(card.convert("RGBA"), glow)

    # frosted rim / inner sheen + top specular highlight via alpha-composited overlay
    chrome = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    cd = ImageDraw.Draw(chrome)
    cd.rounded_rectangle((10, 10, S - 11, S - 11), radius=64,
                         outline=(*accent, 150), width=6)
    cd.rounded_rectangle((18, 18, S - 19, S - 19), radius=58,
                         outline=(255, 255, 255, 55), width=3)
    cd.rounded_rectangle((28, 26, S - 29, 150), radius=46,
                         fill=(255, 255, 255, 22))
    card = Image.alpha_composite(card, chrome)

    # big color emoji on its own transparent layer so edges blend cleanly
    emoji_layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ed = ImageDraw.Draw(emoji_layer)
    ef = font(220, emoji=True)
    box = ed.textbbox((0, 0), emoji, font=ef)
    ew, eh = box[2] - box[0], box[3] - box[1]
    ex = (S - ew) / 2 - box[0]
    ey = 40 - box[1]
    ed.text((ex, ey), emoji, font=ef, embedded_color=True)
    card = Image.alpha_composite(card, emoji_layer)

    # bottom readability scrim via alpha-composited fade layer
    scrim = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    sd = ImageDraw.Draw(scrim)
    for y in range(320, S - 12):
        a = int(190 * (y - 320) / (S - 332))
        sd.line((20, y, S - 20, y), fill=(0, 0, 0, a))
    card = Image.alpha_composite(card, scrim)

    # character name + top-left letter badge
    labels = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ld = ImageDraw.Draw(labels)
    nf = font(46)
    nb = ld.textbbox((0, 0), name, font=nf)
    nw = nb[2] - nb[0]
    ld.text(((S - nw) / 2, 420), name, font=nf, fill=(255, 255, 255, 255),
            stroke_width=2, stroke_fill=(0, 0, 0, 180))

    bf = font(52)
    ld.rounded_rectangle((24, 22, 104, 102), radius=24, fill=(*accent, 230))
    bb = ld.textbbox((0, 0), letter, font=bf)
    bw, bh = bb[2] - bb[0], bb[3] - bb[1]
    ld.text((64 - bw / 2, 62 - bh / 2 - bb[1]), letter, font=bf, fill=(10, 12, 20, 255))
    card = Image.alpha_composite(card, labels)

    return card.convert("RGB")


def make_sheet(theme: dict) -> Image.Image:
    sheet = Image.new("RGB", (SHEET_W, SHEET_H), (0, 0, 0))
    top = hexrgb(theme["top"])
    bottom = hexrgb(theme["bottom"])
    accent = hexrgb(theme["accent"])
    cells = theme["cells"]
    for i, (letter, name, emoji) in enumerate(
        zip(cells["letters"], cells["names"], cells["emojis"])
    ):
        sticker = make_sticker(i, letter, name, emoji, top, bottom, accent)
        c, r = i % 3, i // 3
        sheet.paste(sticker, (c * 512, r * 512))
    return sheet


def make_hero(theme: dict) -> Image.Image:
    """Code-drawn 1080x720 wallpaper: gradient sky, glow orbs, scattered emoji motif."""
    rnd = random.Random(theme["seed"])
    top = hexrgb(theme["top"])
    bottom = hexrgb(theme["bottom"])
    accent = hexrgb(theme["accent"])
    img = gradient(BG_W, BG_H, bottom, top).convert("RGBA")

    overlay = Image.new("RGBA", (BG_W, BG_H), (0, 0, 0, 0))
    od = ImageDraw.Draw(overlay)
    # glow orbs
    for _ in range(9):
        x, y = rnd.randint(0, BG_W), rnd.randint(0, BG_H // 2)
        r = rnd.randint(60, 190)
        od.ellipse((x - r, y - r, x + r, y + r), fill=(*accent, rnd.randint(6, 18)))
    # subtle diagonal light streaks
    for i in range(0, BG_W, 90):
        od.polygon([(i, 0), (i + 34, 0), (i + 34 + 220, BG_H), (i + 220, BG_H)],
                   fill=(255, 255, 255, 5))
    img = Image.alpha_composite(img, overlay)

    # big central motif emoji, softly blurred halo + crisp copy
    motif = theme["cells"]["emojis"][1]
    mf = font(300, emoji=True)
    layer = Image.new("RGBA", (BG_W, BG_H), (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    b = ld.textbbox((0, 0), motif, font=mf)
    mw, mh = b[2] - b[0], b[3] - b[1]
    lx, ly = (BG_W - mw) / 2 - b[0], (BG_H - mh) / 2 - b[1] - 40
    ld.text((lx, ly), motif, font=mf, embedded_color=True)
    halo = layer.filter(ImageFilter.GaussianBlur(26))
    halo.putalpha(halo.getchannel("A").point(lambda a: int(a * 0.45)))
    img = Image.alpha_composite(img, halo)
    img = Image.alpha_composite(img, layer)

    # dark bottom fade for white key text readability
    fade = Image.new("RGBA", (BG_W, BG_H), (0, 0, 0, 0))
    fd = ImageDraw.Draw(fade)
    for y in range(int(BG_H * 0.45), BG_H):
        a = int(150 * (y - BG_H * 0.45) / (BG_H * 0.55))
        fd.line((0, y, BG_W, y), fill=(0, 0, 0, a))
    img = Image.alpha_composite(img, fade)
    return img.convert("RGB")


THEMES = [
    {
        "stem": "bangla", "seed": 11, "top": "#0A3B26", "bottom": "#03110B", "accent": "#F5B301",
        "cells": {
            "letters": ["A", "D", "N", "P", "R", "T"],
            "names": ["Alpona", "Durga", "Nouka", "Pujo", "Tagore", "Tabla"],
            "emojis": ["🎨", "🛕", "🛶", "🪔", "🧔", "🥁"],
        },
    },
    {
        "stem": "animals", "seed": 22, "top": "#0B3B31", "bottom": "#04140F", "accent": "#4ADE80",
        "cells": {
            "letters": ["A", "B", "C", "D", "E", "F"],
            "names": ["Ant", "Bear", "Cat", "Dog", "Eagle", "Fox"],
            "emojis": ["🐜", "🐻", "🐱", "🐶", "🦅", "🦊"],
        },
    },
    {
        "stem": "anime", "seed": 33, "top": "#15203B", "bottom": "#080B14", "accent": "#F59E0B",
        "cells": {
            "letters": ["A", "K", "L", "N", "S", "Z"],
            "names": ["Ace", "Kira", "Luffy", "Naruto", "Sasuke", "Zoro"],
            "emojis": ["⭐", "👑", "🔥", "🍜", "⚡", "⚔️"],
        },
    },
    {
        "stem": "space", "seed": 44, "top": "#16123A", "bottom": "#05040F", "accent": "#22D3EE",
        "cells": {
            "letters": ["A", "B", "C", "D", "E", "F"],
            "names": ["Astronaut", "Bot", "Comet", "Droid", "Earth", "Falcon"],
            "emojis": ["👨‍🚀", "🤖", "☄️", "🛰️", "🌍", "🚀"],
        },
    },
]


def main() -> None:
    print("Generating local sticker art (Segoe UI Emoji, no AI service)...")
    for t in THEMES:
        sheet = make_sheet(t)
        sp = RAW / f"{t['stem']}_sheet.png"
        sheet.save(sp)
        hero = make_hero(t)
        hp = RAW / f"{t['stem']}_hero.png"
        hero.save(hp)
        print(f"  {t['stem']}: {sp.name} {sheet.size}, {hp.name} {hero.size}")
    print("Done.")


if __name__ == "__main__":
    main()
