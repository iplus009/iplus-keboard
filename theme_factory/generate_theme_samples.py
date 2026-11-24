#!/usr/bin/env python3
"""
Phase 1 sample generator for the Keybird Theme Factory.

Produces 40 sample themes across handcrafted design families and a 5x8 contact sheet
for visual sign-off. Does NOT generate the full 500-batch.

Categories: Matte Bauhaus, Live Animated (real animated WebP), Motion Parallax,
Touch Press Effects, Bangla Heritage, Light variants, Dark variants.
Free Stock Photos is intentionally absent: PEXELS_API_KEY / PIXABAY_API_KEY are read
only from the environment and are currently unset (BLOCKED, see STOP report).

Usage: python generate_theme_samples.py
"""
from __future__ import annotations

import colorsys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
OUT = HERE / "out" / "themes"
PROOF = HERE / "out" / "proof"
OUT.mkdir(parents=True, exist_ok=True)
PROOF.mkdir(parents=True, exist_ok=True)

W, H = 320, 200
FONT = r"C:\Windows\Fonts\seguisb.ttf"
BANGLA_FONT = r"C:\Windows\Fonts\Nirmala.ttc"

CATEGORIES = [
    ("matte_bauhaus", 6),
    ("live_animated", 6),
    ("motion_parallax", 6),
    ("touch_press", 6),
    ("bangla_heritage", 6),
    ("light_variant", 5),
    ("dark_variant", 5),
]


def f(size: int, bangla: bool = False) -> ImageFont.FreeTypeFont:
    try:
        return ImageFont.truetype(BANGLA_FONT if bangla else FONT, size)
    except OSError:
        return ImageFont.load_default()


def hexrgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def rr(d, box, radius, fill=None, outline=None, width=1):
    d.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)


def keyboard_mock(d, bg, key, accent, text, dark=True, pressed_col=None):
    """Mini QWERTY mock so every preview reads as a real keyboard theme."""
    d.rectangle((0, 0, W, H), fill=bg)
    for r in range(3):
        y = 26 + r * 52
        n = 10 - r
        x0 = 12 + r * 10
        for c in range(n):
            x = x0 + c * 29
            col = accent if (r == 2 and c == 4) else key
            if pressed_col and r == 1 and c == 3:
                col = pressed_col
            rr(d, (x, y, x + 26, y + 42), 7, fill=col, outline=(255, 255, 255, 40) if dark else (0, 0, 0, 40), width=1)
            if text:
                tf = f(13)
                d.text((x + 13, y + 14), text[(r * n + c) % len(text)], font=tf,
                       fill=(255, 255, 255) if dark else (20, 20, 28), anchor="mm")


def matte_bauhaus(d, pal, i):
    bg, key, accent = pal
    d.rectangle((0, 0, W, H), fill=bg)
    # bold geometric shapes, flat matte fills, no gradients
    d.ellipse((-30, -30, 110, 110), fill=accent)
    d.rectangle((W - 90, 0, W, 90), fill=key)
    d.polygon([(W - 120, H), (W - 40, H), (W - 80, H - 70)], fill=accent)
    for r in range(3):
        for c in range(10 - r):
            x, y = 12 + r * 10 + c * 29, 26 + r * 52
            rr(d, (x, y, x + 26, y + 42), 3, fill=key, outline=tuple(min(255, v + 40) for v in key), width=2)


def motion_parallax(d, pal, i):
    bg, key, accent = pal
    d.rectangle((0, 0, W, H), fill=bg)
    # three explicit depth layers with offset
    for k in range(3):
        off = k * 18
        d.polygon([(0, 60 + off), (W, 20 + off), (W, H), (0, H)],
                  fill=tuple(max(0, v - k * 18) for v in accent))
    for r in range(3):
        for c in range(10 - r):
            x, y = 12 + r * 10 + c * 29, 26 + r * 52
            rr(d, (x, y, x + 26, y + 42), 9, fill=(*key, 210), outline=(*accent, 120), width=2)


def touch_press(d, pal, i):
    bg, key, accent = pal
    keyboard_mock(d, bg, key, accent, "ASDFGHJKLZXCVBNM", True, pressed_col=accent)
    # press ripple + glow rings around one key
    cx, cy = 12 + 29 + 13, 26 + 52 + 21
    for r in (18, 28, 40):
        d.ellipse((cx - r, cy - r, cx + r, cy + r), outline=(*accent, 160 - r * 2), width=3)
    d.ellipse((cx - 6, cy - 6, cx + 6, cy + 6), fill=(255, 255, 255, 200))


def bangla_heritage(d, pal, i):
    bg, key, accent = pal
    d.rectangle((0, 0, W, H), fill=bg)
    # alpona-like arcs
    for r in range(30, 130, 14):
        d.arc((-20, -20, r * 2, r * 2), 180, 360, fill=(*accent, 120), width=2)
    words = ["অ", "আ", "ক", "খ", "গ", "ঘ"]
    for r in range(3):
        for c in range(10 - r):
            x, y = 12 + r * 10 + c * 29, 26 + r * 52
            rr(d, (x, y, x + 26, y + 42), 8, fill=(*key, 230), outline=(*accent, 160), width=2)
            d.text((x + 13, y + 14), words[(r * 3 + c + i) % len(words)], font=f(15, True),
                   fill=(255, 255, 255), anchor="mm")


def light_variant(d, pal, i):
    bg, key, accent = pal
    d.rectangle((0, 0, W, H), fill=bg)
    for r in range(3):
        for c in range(10 - r):
            x, y = 12 + r * 10 + c * 29, 26 + r * 52
            rr(d, (x + 1, y + 2, x + 27, y + 44), 9, fill=(0, 0, 0, 28))
            rr(d, (x, y, x + 26, y + 42), 9, fill=key, outline=(*accent, 120), width=2)


def dark_variant(d, pal, i):
    bg, key, accent = pal
    keyboard_mock(d, bg, key, accent, "QWERTYUIOPASDFGHJKLZXCVBNM")


PAINTERS = {
    "matte_bauhaus": matte_bauhaus,
    "motion_parallax": motion_parallax,
    "touch_press": touch_press,
    "bangla_heritage": bangla_heritage,
    "light_variant": light_variant,
    "dark_variant": dark_variant,
}


def build_live_webp(path: Path, pal, i) -> Image.Image:
    """Real animated WebP: a light sweep travels across a parallax keyboard mock."""
    bg, key, accent = pal
    frames = []
    for t in range(4):
        fr = Image.new("RGB", (W, H))
        d = ImageDraw.Draw(fr, "RGBA")
        d.rectangle((0, 0, W, H), fill=bg)
        sweep = int((t / 4) * (W + 120)) - 60
        d.polygon([(sweep, 0), (sweep + 70, 0), (sweep + 200, H), (sweep + 130, H)], fill=(*accent, 42))
        for r in range(3):
            for c in range(10 - r):
                x, y = 12 + r * 10 + c * 29, 26 + r * 52
                rr(d, (x, y, x + 26, y + 42), 9, fill=(*key, 235), outline=(*accent, 100), width=2)
        frames.append(fr)
    frames[0].save(path, "WEBP", save_all=True, append_images=frames[1:], duration=160, loop=0, quality=72)
    return frames[0]


def main():
    jobs = []
    for cat, count in CATEGORIES:
        for k in range(count):
            jobs.append((cat, k))
    assert len(jobs) == 40, len(jobs)

    thumbs = []
    for idx, (cat, k) in enumerate(jobs):
        tid = f"sample_{cat}_{k + 1:02d}"
        hue = (idx * 0.618) % 1.0
        dark = not cat.startswith("light")
        if dark:
            bg = tuple(int(c * 255) for c in colorsys.hsv_to_rgb((hue + 0.55) % 1, 0.5, 0.10))
            key = tuple(int(c * 255) for c in colorsys.hsv_to_rgb(hue, 0.35, 0.20))
        else:
            bg = (245, 245, 250)
            key = (255, 255, 255)
        accent = tuple(int(c * 255) for c in colorsys.hsv_to_rgb(hue, 0.8, 0.85))
        pal = (bg, key, accent)

        if cat == "live_animated":
            img = build_live_webp(OUT / f"{tid}_preview.webp", pal, k)
        else:
            img = Image.new("RGB", (W, H))
            d = ImageDraw.Draw(img, "RGBA")
            PAINTERS[cat](d, pal, k)
            img.save(OUT / f"{tid}_preview.png")
        thumbs.append((tid, cat, img.convert("RGB")))

    # 5x8 contact sheet
    cw, ch = W + 12, H + 34
    sheet = Image.new("RGB", (cw * 5, ch * 8), (18, 18, 22))
    d = ImageDraw.Draw(sheet)
    for i, (tid, cat, img) in enumerate(thumbs):
        x, y = (i % 5) * cw, (i // 5) * ch
        sheet.paste(img, (x + 6, y + 4))
        d.text((x + 8, y + H + 8), f"{i + 1:02d} {cat}", font=f(13), fill=(230, 230, 240))
    sheet.save(PROOF / "theme_contact_01.png")
    print(f"themes: {len(thumbs)} | contact: {PROOF / 'theme_contact_01.png'}")

    # Bangla conjunct test sheet
    conjuncts = ["ক্ষ", "জ্ঞ", "ন্য", "শ্র", "ত্র", "গ্ন", "চ্ছ", "জ্জ", "ট্ট",
                 "ড্ড", "ত্ত", "দ্ধ", "দ্ভ", "ম্ম", "ল্ল", "শ্শ", "হ্ম", "ঞ্চ"]
    bs = Image.new("RGB", (720, 480), (250, 250, 252))
    bd = ImageDraw.Draw(bs)
    bd.text((20, 12), "Bangla conjunct render test (Nirmala.ttc)", font=f(20), fill=(20, 20, 30))
    for i, c in enumerate(conjuncts):
        x, y = 20 + (i % 6) * 115, 60 + (i // 6) * 120
        bd.rounded_rectangle((x, y, x + 100, y + 100), radius=12, fill=(240, 244, 250), outline=(120, 140, 170), width=2)
        bd.text((x + 50, y + 50), c, font=f(42, True), fill=(15, 25, 45), anchor="mm")
    bs.save(PROOF / "bangla_conjunct_sheet.png")
    print(f"bangla sheet: {PROOF / 'bangla_conjunct_sheet.png'}")


if __name__ == "__main__":
    main()
