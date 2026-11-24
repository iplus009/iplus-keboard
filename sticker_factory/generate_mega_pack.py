#!/usr/bin/env python3
"""
Keybird UNIQUE MEGA pack generator: 500 mutually-distinct theme bundles + 1000 stickers.

Guarantees no two themes share a fingerprint. Variety comes from:
  - palette scheme (analogous / complementary / triadic / mono / split) + jittered hue
  - one of 12 hand-drawn wallpaper patterns (rings, waves, dots, grid, aurora, …)
  - a unique 6-emoji set sampled without replacement
  - per-theme corner radius, stroke width, scrim, font style, glass flag, effect mode
  - unique card sheen side / badge shape

    python generate_mega_pack.py            # 500 themes + 1000 stickers
    python generate_mega_pack.py 20 40      # quick test

Output: art/mega/themes/, art/mega/stickers/, catalog_*.json (with uniqueness fingerprint)
"""
from __future__ import annotations

import colorsys
import hashlib
import json
import math
import random
import shutil
import sys
import zipfile
from functools import lru_cache
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = Path(__file__).resolve().parent
MEGA = HERE / "art" / "mega"
THEMES_OUT = MEGA / "themes"
STICKERS_OUT = MEGA / "stickers"
for d in (THEMES_OUT, STICKERS_OUT):
    d.mkdir(parents=True, exist_ok=True)

EMOJI_FONT = r"C:\Windows\Fonts\seguiemj.ttf"
LABEL_FONT = r"C:\Windows\Fonts\seguisb.ttf"
KW, KH = 192, 256
BW, BH = 1080, 720
SW, SH = 256, 256

EFFECTS = ["glass", "motion", "live", "3d_live_motion", "touch",
           "neon", "aurora", "holographic", "parallax", "ripple"]
FONTS = ["bold", "clean", "tech_mono", "elegant", "doto", "inter"]
SCHEMES = ["analogous", "complementary", "triadic", "mono", "split"]

ADJECTIVES = [
    "Neon", "Midnight", "Ocean", "Crystal", "Golden", "Frost", "Crimson", "Electric",
    "Cosmic", "Velvet", "Solar", "Arctic", "Tropical", "Shadow", "Aurora", "Ruby",
    "Sapphire", "Emerald", "Amber", "Lunar", "Storm", "Silk", "Fire", "Glacier", "Onyx",
]
NOUNS = [
    "Dragon", "Bloom", "Wave", "Nebula", "Phantom", "Comet", "Garden", "Thunder",
    "Whisper", "Mirage", "Cascade", "Phoenix", "Aurora", "Reef", "Halo", "Prism",
    "Tide", "Ember", "Frost", "Orbit",
]

NOUN_MOTIF = {
    "Dragon": "🐉", "Bloom": "🌸", "Wave": "🌊", "Nebula": "🌌", "Phantom": "👻",
    "Comet": "☄️", "Garden": "🌺", "Thunder": "⚡", "Whisper": "🎐", "Mirage": "🏝️",
    "Cascade": "💧", "Phoenix": "🔥", "Aurora": "🌈", "Reef": "🐚", "Halo": "✨",
    "Prism": "💎", "Tide": "🐬", "Ember": "🌋", "Frost": "❄️", "Orbit": "🪐",
}

NOUN_CATEGORY = {
    "Dragon": "fantasy", "Bloom": "nature", "Wave": "nature", "Nebula": "space",
    "Phantom": "dark", "Comet": "space", "Garden": "nature", "Thunder": "neon",
    "Whisper": "aesthetic", "Mirage": "aesthetic", "Cascade": "nature", "Phoenix": "fantasy",
    "Aurora": "neon", "Reef": "nature", "Halo": "aesthetic", "Prism": "neon",
    "Tide": "nature", "Ember": "dark", "Frost": "aesthetic", "Orbit": "space",
}

EMOJI = [
    "🐱", "🐶", "🦊", "🐻", "🐼", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵", "🐔", "🐧",
    "🦄", "🐝", "🦋", "🐢", "🐙", "🦑", "🦀", "🐬", "🐳", "🦈", "🐴", "🦓", "🐘",
    "🌸", "🌺", "🌻", "🌹", "🌷", "🌼", "🌵", "🌲", "🌴", "🍀", "🍁", "🍄", "🌙",
    "⭐", "🌟", "✨", "⚡", "🔥", "💎", "🔮", "🎨", "🎵", "🎮", "🚀", "🛸", "🌍",
    "🪐", "☄️", "🌈", "🦅", "🦉", "🦚", "🐺", "🐗", "🦌", "🌊", "🐞", "🦗", "🐛",
    "☕", "🍵", "🍕", "🍔", "🍜", "🍣", "🍰", "🍓", "🍎", "🍇", "🥑", "🌮", "🍩",
    "🏀", "⚽", "🎾", "🏈", "🎲", "🎯", "🎸", "🎤", "🎧", "📷", "💡", "🔔", "🎁",
    "💖", "💙", "💚", "💜", "🧡", "🤍", "😀", "😎", "🥳", "🤩", "😺", "🙈", "🦾",
    "🎩", "👑", "🛡️", "⚔️", "🗡️", "🏹", "🎣", "🧭", "⌛", "📱", "💻", "🖥️", "⌨️",
    "🧿", "🎐", "🪷", "🕊️", "🐉", "🦖", "🧙", "🧚", "🦸", "🥷", "🪄", "🎭", "🪗",
    "❄️", "🌌", "👻", "🏝️", "💧", "🐚", "🌋",
]


@lru_cache(maxsize=96)
def get_font(size: int, emoji: bool) -> ImageFont.FreeTypeFont:
    try:
        return ImageFont.truetype(EMOJI_FONT if emoji else LABEL_FONT, size)
    except OSError:
        return ImageFont.load_default()


def rgb(h):
    return "#%02X%02X%02X" % (int(h[0] * 255), int(h[1] * 255), int(h[2] * 255))


def fast_gradient(w, h, top, bottom):
    strip = Image.new("RGB", (1, h))
    px = strip.load()
    for y in range(h):
        t = y / max(1, h - 1)
        px[0, y] = tuple(round(top[i] * (1 - t) + bottom[i] * t) for i in range(3))
    return strip.resize((w, h))


def draw_emoji(d, emoji, size, cx, cy):
    f = get_font(size, True)
    b = d.textbbox((0, 0), emoji, font=f)
    w, h = b[2] - b[0], b[3] - b[1]
    d.text((cx - w / 2 - b[0], cy - h / 2 - b[1]), emoji, font=f, embedded_color=True)


# ---------- 12 distinct wallpaper pattern painters ----------

def p_rings(d, rnd, w, h, accent):
    cx, cy = w * rnd.uniform(0.3, 0.7), h * rnd.uniform(0.25, 0.5)
    for r in range(40, int(max(w, h) * 0.9), rnd.randint(28, 54)):
        d.ellipse((cx - r, cy - r, cx + r, cy + r), outline=(*accent, rnd.randint(8, 26)), width=rnd.randint(1, 3))


def p_waves(d, rnd, w, h, accent):
    for k in range(rnd.randint(5, 9)):
        y0 = h * (0.25 + 0.07 * k)
        pts = [(x, y0 + math.sin(x / rnd.uniform(50, 130) + k) * rnd.uniform(12, 45)) for x in range(0, w + 20, 12)]
        d.line(pts, fill=(*accent, rnd.randint(10, 32)), width=rnd.randint(2, 6))


def p_dots(d, rnd, w, h, accent):
    step = rnd.randint(34, 70)
    for y in range(0, h, step):
        for x in range(0, w, step):
            r = rnd.randint(2, 7)
            d.ellipse((x - r, y - r, x + r, y + r), fill=(*accent, rnd.randint(10, 40)))


def p_grid(d, rnd, w, h, accent):
    for x in range(0, w, rnd.randint(50, 100)):
        d.line((x, 0, x, h), fill=(*accent, 14), width=1)
    for y in range(0, h, rnd.randint(50, 100)):
        d.line((0, y, w, y), fill=(*accent, 14), width=1)


def p_aurora(d, rnd, w, h, accent):
    for k in range(rnd.randint(3, 6)):
        x0 = rnd.randint(0, w)
        d.polygon([(x0, 0), (x0 + rnd.randint(60, 160), 0),
                   (x0 + rnd.randint(-120, 120), h), (x0 - rnd.randint(60, 160), h)],
                  fill=(*accent, rnd.randint(8, 20)))


def p_stars(d, rnd, w, h, accent):
    for _ in range(rnd.randint(80, 180)):
        x, y = rnd.randint(0, w), rnd.randint(0, h)
        r = rnd.randint(1, 4)
        d.ellipse((x - r, y - r, x + r, y + r), fill=(255, 255, 255, rnd.randint(30, 150)))


def p_chevrons(d, rnd, w, h, accent):
    step = rnd.randint(60, 110)
    for y in range(0, h, step):
        pts = []
        for x in range(0, w + step, step):
            pts += [(x, y), (x + step // 2, y + step // 2)]
        d.line(pts, fill=(*accent, rnd.randint(10, 28)), width=rnd.randint(2, 5))


def p_hex(d, rnd, w, h, accent):
    s = rnd.randint(40, 80)
    for row, y in enumerate(range(0, h + s, int(s * 0.86))):
        for col, x in enumerate(range(0, w + s, s + s // 2)):
            xx = x + (s // 4 if row % 2 else 0)
            pts = [(xx + s * math.cos(a * math.pi / 3), y + s * math.sin(a * math.pi / 3)) for a in range(6)]
            d.line(pts + [pts[0]], fill=(*accent, rnd.randint(8, 22)), width=2)


def p_mountains(d, rnd, w, h, accent):
    for k in range(3):
        base = h * (0.45 + 0.14 * k)
        pts = [(0, h)]
        for x in range(0, w + 40, 40):
            pts.append((x, base - rnd.randint(20, 130)))
        pts.append((w, h))
        d.polygon(pts, fill=(*accent, rnd.randint(10, 22)))


def p_bubbles(d, rnd, w, h, accent):
    for _ in range(rnd.randint(14, 30)):
        x, y, r = rnd.randint(0, w), rnd.randint(0, h), rnd.randint(20, 100)
        d.ellipse((x - r, y - r, x + r, y + r), outline=(*accent, rnd.randint(12, 30)), width=rnd.randint(2, 5))


def p_sparkles(d, rnd, w, h, accent):
    for _ in range(rnd.randint(20, 50)):
        x, y, r = rnd.randint(0, w), rnd.randint(0, h), rnd.randint(6, 20)
        d.line((x - r, y, x + r, y), fill=(255, 255, 255, rnd.randint(40, 120)), width=2)
        d.line((x, y - r, x, y + r), fill=(255, 255, 255, rnd.randint(40, 120)), width=2)


def p_beams(d, rnd, w, h, accent):
    for x in range(-w, w * 2, rnd.randint(70, 130)):
        d.polygon([(x, 0), (x + rnd.randint(20, 60), 0), (x + 300, h), (x + 250, h)],
                  fill=(255, 255, 255, rnd.randint(3, 12)))


PATTERNS = [p_rings, p_waves, p_dots, p_grid, p_aurora, p_stars,
            p_chevrons, p_hex, p_mountains, p_bubbles, p_sparkles, p_beams]


def wallpaper(top, bottom, accent, motif, rnd: random.Random, pattern) -> Image.Image:
    img = fast_gradient(BW, BH, bottom, top).convert("RGBA")
    ov = Image.new("RGBA", (BW, BH), (0, 0, 0, 0))
    pattern(ImageDraw.Draw(ov), rnd, BW, BH, accent)
    img = Image.alpha_composite(img, ov)
    layer = Image.new("RGBA", (BW, BH), (0, 0, 0, 0))
    draw_emoji(ImageDraw.Draw(layer), motif, rnd.randint(180, 280), BW * rnd.uniform(0.35, 0.65), BH * rnd.uniform(0.3, 0.45))
    halo = layer.filter(ImageFilter.GaussianBlur(rnd.randint(14, 26)))
    halo.putalpha(halo.getchannel("A").point(lambda a: int(a * 0.4)))
    img = Image.alpha_composite(img, halo)
    img = Image.alpha_composite(img, layer)
    fade = Image.new("RGBA", (BW, BH), (0, 0, 0, 0))
    fd = ImageDraw.Draw(fade)
    start = int(BH * rnd.uniform(0.42, 0.58))
    for y in range(start, BH):
        fd.line((0, y, BW, y), fill=(0, 0, 0, int(150 * (y - start) / max(1, BH - start))))
    return Image.alpha_composite(img, fade).convert("RGB")


def sticker_card(top, bottom, accent, emoji, label, w, h, rnd=None, badge="round", sheen="top"):
    rnd = rnd or random.Random(0)
    img = fast_gradient(w, h, top, bottom).convert("RGBA")

    # Border & glass sheen on a separate RGBA overlay so Pillow alpha-blends cleanly
    ov = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    od = ImageDraw.Draw(ov)
    rad = 14 if badge == "round" else 6
    od.rounded_rectangle((4, 4, w - 5, h - 5), radius=rad, outline=(*accent, 175), width=rnd.randint(2, 4))
    sheen_alpha = rnd.randint(14, 30)
    if sheen == "top":
        od.rounded_rectangle((8, 8, w - 9, min(h - 9, int(h * 0.28))), radius=rad, fill=(255, 255, 255, sheen_alpha))
    else:
        od.rounded_rectangle((8, max(8, h - int(h * 0.28)), w - 9, h - 9), radius=rad, fill=(255, 255, 255, sheen_alpha))
    img = Image.alpha_composite(img, ov)

    if emoji:
        em_layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        em_cy = h * (0.44 if label else 0.50)
        draw_emoji(ImageDraw.Draw(em_layer), emoji, int(min(w, h) * rnd.uniform(0.46, 0.58)), w / 2, em_cy)
        img = Image.alpha_composite(img, em_layer)

    # Subtle bottom gradient scrim via alpha-composited fade layer (stronger only when label text exists)
    fade = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    fd = ImageDraw.Draw(fade)
    max_scrim = 175 if label else 70
    start_y = int(h * 0.68)
    span = max(1, h - start_y - 4)
    for y in range(start_y, h - 4):
        fd.line((6, y, w - 6, y), fill=(0, 0, 0, int(max_scrim * (y - start_y) / span)))
    img = Image.alpha_composite(img, fade)

    if label:
        lbl_layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        ld = ImageDraw.Draw(lbl_layer)
        f = get_font(max(14, int(w * 0.12)), False)
        b = ld.textbbox((0, 0), label, font=f)
        ld.text(((w - (b[2] - b[0])) / 2, h - int(h * 0.22)), label, font=f, fill=(255, 255, 255, 255))
        img = Image.alpha_composite(img, lbl_layer)
    return img.convert("RGB")


def write_zip(stage, out):
    if out.exists():
        out.unlink()
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for f in sorted(stage.rglob("*")):
            if f.is_file():
                z.write(f, f.relative_to(stage).as_posix())
    sha = hashlib.sha256(out.read_bytes()).hexdigest()
    out.with_suffix(out.suffix + ".sha256.txt").write_text(sha + "\n", encoding="utf-8")
    return sha


def theme_manifest(tid, title, effect, colors, keymap, flags):
    letters = "abcdefghijklmnopqrstuvwxyz"
    km = {f"k_{l}": keymap.get(l, "assets/groups/letter.webp") for l in letters}
    km.update({"k_shift": "assets/groups/functional.webp", "k_delete": "assets/groups/functional.webp",
               "k_sym": "assets/groups/functional.webp", "k_emoji": "assets/groups/functional.webp",
               "k_enter": "assets/groups/accent.webp", "k_space": "assets/keys/k_space.webp"})
    for r in range(3):
        for c in range(10 if r < 2 else 7):
            km[f"slot_r{r}_c{c}"] = "assets/groups/letter.webp"
    return {
        "schema": 1, "theme_id": tid, "app_id": "keybird", "title": title,
        "version": 1, "theme_type": "art_pack", "is_light": False,
        "colors": colors,
        "keyboard": {
            "bg_type": "gradient", "bg_colors": [colors["background"], colors["surface"], colors["background"]],
            "key_bg": "#CC14182A", "key_pressed_bg": "#E83A4A6E", "functional_key_bg": "#B810141C",
            "enter_key_bg": colors["primary"], "enter_key_fg": "#0A0C14",
            "spacebar_bg": "#151C2E", "spacebar_stroke": colors["primary"],
            "key_stroke": colors["outline"], "key_stroke_width_dp": flags["stroke"],
            "key_corner_radius_dp": flags["radius"], "key_text": "#FFFFFF",
            "key_hint": "#E2E8F0", "popup_bg": "#162038", "popup_text": "#FFFFFF",
            "gesture_trail": colors["primary"], "font_style": flags["font"],
            "scrim_alpha": flags["scrim"], "glass_rim_glow": flags["glass"],
            "effect_mode": effect,
        },
        "art_pack": {
            "bg_full_asset": f"assets/bg/{tid}_bg.webp", "spacebar_asset": "assets/keys/k_space.webp",
            "scrim_mode": "auto_yiq", "keymap": km,
            "group_fallbacks": {"letter": "assets/groups/letter.webp", "functional": "assets/groups/functional.webp",
                                "accent": "assets/groups/accent.webp", "space": "assets/keys/k_space.webp",
                                "default": "assets/groups/letter.webp"},
        },
    }


def gen_theme(i: int, used: set) -> dict:
    rnd = random.Random(i * 7919 + 13)
    tid = f"kb_gen_{i + 1:04d}"
    noun = NOUNS[i % len(NOUNS)]
    title = f"{ADJECTIVES[i // len(NOUNS)]} {noun}"
    category = NOUN_CATEGORY.get(noun, "general")
    effect = EFFECTS[rnd.randrange(len(EFFECTS))]
    scheme = SCHEMES[rnd.randrange(len(SCHEMES))]
    hue = (i * 0.61803398875 + rnd.uniform(-0.02, 0.02)) % 1.0
    if scheme == "analogous":
        hues = [hue, (hue + 0.06) % 1, (hue - 0.06) % 1]
    elif scheme == "complementary":
        hues = [hue, (hue + 0.5) % 1, hue]
    elif scheme == "triadic":
        hues = [hue, (hue + 0.33) % 1, (hue + 0.66) % 1]
    elif scheme == "mono":
        hues = [hue, hue, hue]
    else:
        hues = [hue, (hue + 0.42) % 1, (hue + 0.58) % 1]
    sat = rnd.uniform(0.55, 0.9)
    prim = colorsys.hsv_to_rgb(hues[0], sat, rnd.uniform(0.85, 1.0))
    bg = colorsys.hsv_to_rgb(hues[1], rnd.uniform(0.35, 0.65), rnd.uniform(0.06, 0.14))
    surf = colorsys.hsv_to_rgb(hues[2], rnd.uniform(0.3, 0.6), rnd.uniform(0.14, 0.24))
    outline = colorsys.hsv_to_rgb(hues[0], rnd.uniform(0.3, 0.7), rnd.uniform(0.55, 0.85))
    colors = {"primary": rgb(prim), "on_primary": "#0A0C14", "background": rgb(bg),
              "on_background": "#FFFFFF", "surface": rgb(surf), "on_surface": "#FFFFFF",
              "outline": rgb(outline)}
    top = tuple(int(c * 255) for c in surf)
    bottom = tuple(int(c * 255) for c in bg)
    accent = tuple(int(c * 255) for c in prim)
    pattern = PATTERNS[i % len(PATTERNS)]
    motif = NOUN_MOTIF.get(noun, EMOJI[i % len(EMOJI)])
    other_pool = [e for e in EMOJI if e != motif]
    emoji_set = [motif] + rnd.sample(other_pool, 5)
    badge = "round" if i % 2 == 0 else "square"
    sheen = "top" if i % 3 else "bottom"
    radius = rnd.choice([6, 8, 10, 12, 14, 16, 18])
    stroke = rnd.choice([0.8, 1.0, 1.15, 1.3, 1.6])
    scrim = round(rnd.uniform(0.35, 0.75), 2)
    font = FONTS[rnd.randrange(len(FONTS))]
    glass = effect in ("glass", "motion", "live", "3d_live_motion", "touch", "holographic", "ripple")

    fingerprint = hashlib.sha256(
        f"{title}|{scheme}|{round(hue,4)}|{pattern.__name__}|{','.join(emoji_set)}|{effect}|{radius}|{font}".encode()
    ).hexdigest()[:16]
    if fingerprint in used:
        raise RuntimeError(f"Duplicate fingerprint {fingerprint} at {tid}")
    used.add(fingerprint)

    stage = THEMES_OUT / tid / "v1"
    if stage.exists():
        shutil.rmtree(stage)
    for s in ("assets/bg", "assets/groups", "assets/keys"):
        (stage / s).mkdir(parents=True, exist_ok=True)

    wallpaper(top, bottom, accent, motif, rnd, pattern).save(stage / "assets/bg" / f"{tid}_bg.webp", "WEBP", quality=72)
    wallpaper(top, bottom, accent, motif, random.Random(i * 31 + 7), pattern).resize((480, 300)).save(
        stage / "preview.webp", "WEBP", quality=55)

    keymap, letters = {}, "abcdefghijklmnopqrstuvwxyz"
    shuffled_letters = rnd.sample(list(letters), 6)
    for letter, em in zip(shuffled_letters, emoji_set):
        rel = f"assets/keys/k_{letter}.webp"
        sticker_card(top, bottom, accent, em, "", KW, KH, rnd, badge, sheen).save(stage / rel, "WEBP", quality=70)
        keymap[letter] = rel
    sticker_card(top, bottom, accent, motif, "", 720, SH, rnd, badge, sheen).save(
        stage / "assets/keys/k_space.webp", "WEBP", quality=70)
    for g in ("letter", "functional", "accent"):
        sticker_card(top, bottom, accent, "", "", KW, KH, rnd, badge, sheen).save(
            stage / f"assets/groups/{g}.webp", "WEBP", quality=70)

    manifest = theme_manifest(tid, title, effect, colors, keymap,
                              {"scrim": scrim, "glass": glass, "stroke": stroke, "radius": radius, "font": font})
    (stage / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False), encoding="utf-8")
    out_zip = THEMES_OUT / f"{tid}_v1.iplustheme"
    sha = write_zip(stage, out_zip)
    return {"theme_id": tid, "title": title, "category": category, "accent_hex": colors["primary"],
            "effect": effect, "scheme": scheme, "pattern": pattern.__name__,
            "fingerprint": fingerprint, "size_bytes": out_zip.stat().st_size,
            "preview_asset": "preview.webp", "sha256": sha}


def gen_sticker_pack(pack_idx, count):
    pid = f"kb_sticker_mega_{pack_idx + 1:02d}"
    rnd = random.Random(9000 + pack_idx)
    stage = STICKERS_OUT / pid / "v1"
    if stage.exists():
        shutil.rmtree(stage)
    (stage / "assets/stickers").mkdir(parents=True, exist_ok=True)
    hue = (pack_idx * 0.137) % 1.0
    top = tuple(int(c * 255) for c in colorsys.hsv_to_rgb(hue, 0.5, 0.25))
    bottom = tuple(int(c * 255) for c in colorsys.hsv_to_rgb(hue, 0.6, 0.08))
    accent = tuple(int(c * 255) for c in colorsys.hsv_to_rgb(hue, 0.8, 0.9))
    accent_hex = rgb(colorsys.hsv_to_rgb(hue, 0.8, 0.9))
    bg_hex = rgb(colorsys.hsv_to_rgb(hue, 0.6, 0.08))
    items = []
    sample_thumbs = []
    for j in range(count):
        em = EMOJI[(pack_idx * 37 + j * 11) % len(EMOJI)]
        name = f"s{j + 1:03d}"
        rel = f"assets/stickers/{name}.webp"
        card_img = sticker_card(top, bottom, accent, em, "", SW, SH, rnd, "round" if j % 2 else "square", "top" if j % 3 else "bottom")
        card_img.save(stage / rel, "WEBP", quality=68)
        if len(sample_thumbs) < 6:
            sample_thumbs.append(card_img.resize((140, 140), Image.LANCZOS))
        items.append({"id": name, "label": f"{pack_idx + 1}-{j + 1}", "asset": rel})

    # Build a 3x2 sticker pack preview collage for store listings
    prev = fast_gradient(480, 320, top, bottom)
    for idx_t, thumb in enumerate(sample_thumbs):
        col, row = idx_t % 3, idx_t // 3
        prev.paste(thumb, (18 + col * 152, 16 + row * 148))
    prev.save(stage / "preview.webp", "WEBP", quality=70)

    title = f"Mega Stickers {pack_idx + 1}"
    manifest = {"schema": 1, "theme_id": pid, "app_id": "keybird",
                "title": title, "version": 1,
                "theme_type": "sticker", "is_light": False,
                "colors": {"primary": accent_hex, "background": bg_hex},
                "sticker_pack": {"pack_icon_asset": items[0]["asset"], "stickers": items}}
    (stage / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False), encoding="utf-8")
    out_zip = STICKERS_OUT / f"{pid}_v1.iplustheme"
    sha = write_zip(stage, out_zip)
    return {"pack_id": pid, "title": title, "category": "stickers", "accent_hex": accent_hex,
            "stickers": count, "size_bytes": out_zip.stat().st_size,
            "preview_asset": "preview.webp", "sha256": sha}


def main():
    theme_count = int(sys.argv[1]) if len(sys.argv) > 1 else 500
    sticker_count = int(sys.argv[2]) if len(sys.argv) > 2 else 1000
    packs, per_pack = 10, max(1, sticker_count // 10)
    print(f"Generating {theme_count} UNIQUE themes + {packs * per_pack} stickers ...", flush=True)
    used, themes = set(), []
    for i in range(theme_count):
        themes.append(gen_theme(i, used))
        if (i + 1) % 50 == 0:
            print(f"  themes {i + 1}/{theme_count}", flush=True)
    (MEGA / "catalog_themes.json").write_text(json.dumps(themes, ensure_ascii=False, indent=1), encoding="utf-8")
    assert len({t["fingerprint"] for t in themes}) == len(themes), "Duplicate theme fingerprint!"
    assert len({t["title"] for t in themes}) == len(themes), "Duplicate theme title!"
    print(f"  uniqueness OK: {len(themes)} distinct fingerprints + titles", flush=True)

    sticker_packs = [gen_sticker_pack(p, per_pack) for p in range(packs)]
    (MEGA / "catalog_stickers.json").write_text(json.dumps(sticker_packs, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"\nDone: {len(themes)} unique themes, {sum(x['stickers'] for x in sticker_packs)} stickers", flush=True)


if __name__ == "__main__":
    main()
