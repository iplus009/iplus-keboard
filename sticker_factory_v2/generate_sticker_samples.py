#!/usr/bin/env python3
"""
Phase 1 sample generator for Keybird Sticker Factory v2.

Produces 20 REAL transparent stickers (RGBA, alpha=0 background) across license-clean
families and a 5x4 checkerboard contact sheet that proves transparency. Does NOT
generate the full 1000-batch.

Families: Bangla typography, recomposed emoji+caption, original doodle characters,
hand-drawn doodles. All art is generated locally -> license-clean.

Usage: python generate_sticker_samples.py
"""
from __future__ import annotations

import math
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
OUT = HERE / "out" / "stickers"
PROOF = HERE / "out" / "proof"
OUT.mkdir(parents=True, exist_ok=True)
PROOF.mkdir(parents=True, exist_ok=True)

S = 256
FONT = r"C:\Windows\Fonts\seguisb.ttf"
BANGLA_FONT = r"C:\Windows\Fonts\Nirmala.ttc"
EMOJI_FONT = r"C:\Windows\Fonts\seguiemj.ttf"

FAMILIES = [
    ("bangla_type", 4),
    ("emoji_caption", 4),
    ("doodle_char", 4),
    ("doodle_line", 4),
    ("emoji_caption", 4),
]


def f(size, kind="normal"):
    path = {"normal": FONT, "bangla": BANGLA_FONT, "emoji": EMOJI_FONT}[kind]
    try:
        return ImageFont.truetype(path, size)
    except OSError:
        return ImageFont.load_default()


def outline_text(d, xy, text, font, fill=(255, 255, 255, 255), stroke=(24, 24, 32, 255), w=5):
    d.text(xy, text, font=font, fill=fill, stroke_width=w, stroke_fill=stroke, anchor="mm")


def emoji_center(d, em, cy, size=120):
    ft = f(size, "emoji")
    b = d.textbbox((0, 0), em, font=ft)
    d.text((S / 2 - (b[2] - b[0]) / 2 - b[0], cy - (b[3] - b[1]) / 2 - b[1]), em,
           font=ft, embedded_color=True)


def bangla_type(d, i):
    words = ["অদ্ভুত", "মজা", "ওয়াও", "চলো", "নমস্কার", "ধন্যবাদ", "জয়", "সুন্দর"]
    word = words[i % len(words)]
    d.ellipse((22, 22, S - 22, S - 22), fill=(18, 30, 55, 235), outline=(255, 255, 255, 255), width=6)
    outline_text(d, (S / 2, S / 2 - 14), word, f(46, "bangla"), fill=(255, 214, 90, 255))
    outline_text(d, (S / 2, S / 2 + 46), "Keybird", f(22), fill=(255, 255, 255, 240))


def emoji_caption(d, i):
    em = ["🔥", "😂", "💖", "🎉", "🐱", "🚀", "🌸", "👑"][i % 8]
    cap = ["FIRE", "LOL", "LOVE", "PARTY", "CAT", "LAUNCH", "BLOOM", "KING"][i % 8]
    d.ellipse((18, 18, S - 18, S - 18), fill=(255, 255, 255, 245), outline=(30, 30, 40, 255), width=5)
    emoji_center(d, em, S / 2 - 26, 116)
    outline_text(d, (S / 2, S / 2 + 62), cap, f(34), fill=(20, 20, 30, 255), stroke=(255, 255, 255, 255), w=3)


def doodle_char(d, i):
    rnd = random.Random(i * 17 + 3)
    body = [(50, 60, 90, 205), (60, 45, 100, 205), (40, 70, 110, 205)][i % 3]
    # original license-clean character: round body + eyes + smile + arms
    d.ellipse((40, 40, S - 40, S - 24), fill=body, outline=(255, 255, 255, 255), width=6)
    d.ellipse((90, 105, 108, 123), fill=(255, 255, 255, 255))
    d.ellipse((148, 105, 166, 123), fill=(255, 255, 255, 255))
    d.ellipse((94, 109, 104, 119), fill=(20, 20, 30, 255))
    d.ellipse((152, 109, 162, 119), fill=(20, 20, 30, 255))
    d.arc((96, 130, 160, 175), 20, 160, fill=(255, 255, 255, 255), width=6)
    d.line((44, 150, 18, 120), fill=(255, 255, 255, 255), width=8)
    d.line((212, 150, 238, 120), fill=(255, 255, 255, 255), width=8)
    d.text((S / 2, S - 48), f"BUDDY {i + 1}", font=f(20), fill=(255, 255, 255, 255), anchor="mm")


def doodle_line(d, i):
    rnd = random.Random(i * 29 + 7)
    # hand-drawn style doodle: heart/star/bolt/smile in white outline
    kind = i % 4
    if kind == 0:
        # Standard parametric heart, centered and scaled.
        pts = []
        for step in range(101):
            t = 2 * math.pi * step / 100
            hx = 16 * math.sin(t) ** 3
            hy = 13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t)
            pts.append((128 + hx * 6, 130 - hy * 6))
        d.line(pts, fill=(255, 120, 150, 255), width=12, joint="curve")
    elif kind == 1:
        pts = [(128 + rnd.randint(-4, 4), 55), (150, 105), (205, 105), (162, 140),
               (178, 195), (128, 160), (78, 195), (94, 140), (51, 105), (106, 105)]
        d.line(pts + [pts[0]], fill=(255, 210, 80, 255), width=10, joint="curve")
    elif kind == 2:
        d.polygon([(150, 40), (95, 130), (135, 130), (110, 215), (175, 110), (135, 110)],
                  fill=(120, 200, 255, 255), outline=(255, 255, 255, 255), width=5)
    else:
        d.ellipse((45, 55, 211, 221), outline=(160, 255, 190, 255), width=12)
        d.ellipse((95, 115, 110, 130), fill=(160, 255, 190, 255))
        d.ellipse((146, 115, 161, 130), fill=(160, 255, 190, 255))
        d.arc((100, 130, 160, 185), 15, 165, fill=(160, 255, 190, 255), width=10)


PAINTERS = {"bangla_type": bangla_type, "emoji_caption": emoji_caption,
            "doodle_char": doodle_char, "doodle_line": doodle_line}


def main():
    jobs = []
    n = 0
    for fam, count in FAMILIES:
        for k in range(count):
            jobs.append((fam, n))
            n += 1
    assert len(jobs) == 20, len(jobs)

    imgs = []
    for fam, idx in jobs:
        img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        d = ImageDraw.Draw(img, "RGBA")
        PAINTERS[fam](d, idx)
        name = f"stk_{idx + 1:02d}_{fam}.png"
        img.save(OUT / name)
        imgs.append((name, img))

    # 5x4 checkerboard contact sheet to prove transparency
    cell = S + 16
    sheet = Image.new("RGBA", (cell * 5, cell * 4), (255, 255, 255, 255))
    d = ImageDraw.Draw(sheet)
    for row in range(4):
        for col in range(5):
            x, y = col * cell, row * cell
            bg = (235, 235, 240, 255) if (row + col) % 2 == 0 else (70, 78, 95, 255)
            d.rectangle((x, y, x + cell, y + cell), fill=bg)
    for i, (name, img) in enumerate(imgs):
        x, y = (i % 5) * cell + 8, (i // 5) * cell + 8
        sheet.alpha_composite(img, (x, y))
    sheet.convert("RGB").save(PROOF / "sticker_checker_01.png")
    print(f"stickers: {len(imgs)} | contact: {PROOF / 'sticker_checker_01.png'}")


if __name__ == "__main__":
    main()
