#!/usr/bin/env python3
"""Keybird 500-theme factory (v4.1): 6 distinct visual & interactive archetypes.

Archetypes across the 21 families (total = 500 in --full, 36 in pilot):
1. Minimal / Material You / AMOLED (`amoled_minimal`, `graphite_pro`, `paper_ink`,
   `pastel_soft`, `gradient_calm`, `high_contrast_a11y`) — calm uniform or sleek borderless
2. Ceramic 3D Glaze & Bauhaus (`earth_clay`, `luxury_gold`, `matte_bauhaus`)
3. Frosted & Liquid Glass (`liquid_glass`, `glass_matte`)
4. Neon LED Outline, Rainbow Borderless, Synthwave & Retro (`neon_night`, `gaming_rgb`,
   `cyber_hybrid_matte`, `retro_terminal`, `pixel_8bit`)
5. Live Animated Scenic & Particle Worlds (`live_animated`, `anime_matte`, `seasonal`, `kawaii_cute`)
6. Bangla Cultural Full-Canvas Borderless (`bangla_heritage`)

Usage: python generate_theme_pilot.py [--full]
"""
from __future__ import annotations

import argparse
import hashlib
import json
import random
import shutil
import sys
import zipfile
import zlib
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE / "src"))
from backdrops import CURATED_DARK, CURATED_LIGHT, build_backdrop, build_live_webp  # noqa: E402
from families import ANIMATED_FAMILY, FAMILIES, GLASS_FAMILY  # noqa: E402
from tokens import RADII, contrast, ensure_contrast, ensure_separation, from_hsv, hx, semantic, yiq_text  # noqa: E402

FONT = r"C:\Windows\Fonts\seguisb.ttf"
SYMBOL_FONT = r"C:\Windows\Fonts\seguisym.ttf"
BANGLA_FONT = r"C:\Windows\Fonts\Nirmala.ttc"
SYMBOLS = {"\u232b", "\u21e7", "\u23ce"}
PW, PH = 1080, 720
TW, TH = 540, 360

WORLD_BG = {
    "paper_ink": "#F4EFE4", "pastel_soft": "#F2EDE6", "high_contrast_a11y": "#FFFFFF",
    "kawaii_cute": "#FBEAF0", "matte_bauhaus": "#C8C2B6", "earth_clay": "#E8DCC8",
    "bangla_heritage": "#F6E9CF", "seasonal": "#E6EDE4", "gradient_calm": "#EFF3F5",
    "cyber_hybrid_matte": "#E6E9F0", "anime_matte": "#FCEEE9", "pixel_8bit": "#E4E2DC",
    "liquid_glass": "#EEF1F6",
}

STYLE = {
    "retro_terminal": {"bg": "#070D0A", "key": "#0D1A12", "pressed": "#1B3A26",
                       "functional": "#102218", "enter": "#1E663B", "text": "#7CFF9B",
                       "hint": "#459B62", "border": "#2BE477", "glow": True},
    "luxury_gold": {"bg": "#100E14", "key": "#1D1922", "pressed": "#2F2736",
                    "functional": "#17141C", "text": "#F5EBD8", "hint": "#B0965E",
                    "border": "#D4AF37", "stroke_w": 1.0, "all_keys_border": True},
    "graphite_pro": {"bevel": True},
    "amoled_minimal": {"bg": "#000000", "key": "#16161A", "pressed": "#2C2C33",
                       "functional": "#111114"},
    "high_contrast_a11y": {"bw": True, "stroke_w": 2.0},
    "neon_night": {"rim": True},
    "gaming_rgb": {"wasd": True},
    "pixel_8bit": {"stroke_w": 2.0},
    "pastel_soft": {"border": "#C9BFB4"},
    "kawaii_cute": {"border": "#E8B7CA"},
    "seasonal": {"border": "#9BB8AE"},
    "gradient_calm": {"border": "#B8C2CC"},
    "paper_ink": {"border": "#B9AC96"},
    "matte_bauhaus": {"border": "#8F877D"},
    "earth_clay": {"border": "#9C846E"},
    "bangla_heritage": {"border": "#E5D3B3"},
    "liquid_glass": {"border": "#C7D8EB"},
    "anime_matte": {"border": "#F5AFC6", "stroke_w": 1.0},
    "glass_matte": {"border": "#8ED4FF", "stroke_w": 1.0, "bevel": True},
    "cyber_hybrid_matte": {"border": "#00F5D4", "stroke_w": 1.5, "rim": True},
}

BOTTOM_ROW = [("?123", 1.4), (",", 1.0), (" ", 5.2), (".", 1.0), ("\u23ce", 1.4)]
EN_ROWS = [
    [(c, 1.0) for c in "QWERTYUIOP"],
    [("", 0.45)] + [(c, 1.0) for c in "ASDFGHJKL"] + [("", 0.45)],
    [("\u21e7", 1.5)] + [(c, 1.0) for c in "ZXCVBNM"] + [("\u232b", 1.5)],
    BOTTOM_ROW,
]
BN_ROWS = [
    [(c, 1.0) for c in "\u0985\u0986\u0987\u0988\u0989\u098a\u098f\u0990\u0993\u0994"],
    [(c, 1.0) for c in "\u0995\u0996\u0997\u0998\u0999\u099a\u099b\u099c\u099d\u099e"],
    [("\u21e7", 1.5)] + [(c, 1.0) for c in "\u099f\u09a0\u09a1\u09a2\u09a3\u09a4\u09a5"] + [("\u232b", 1.5)],
    BOTTOM_ROW,
]
NUM_ROWS = [
    [(c, 1.0) for c in "1234567890"],
    [(c, 1.0) for c in "-/:;()$&@\""],
    [(c, 1.0) for c in "#()%+=?!'"] + [("\u232b", 1.5)],
    BOTTOM_ROW,
]
LAYOUTS = {"en": EN_ROWS, "bn": BN_ROWS, "num": NUM_ROWS}

# ONLY matte_bauhaus uses multi-color Bauhaus keys; gaming_rgb highlights WASD.
MULTI_FAMILIES = {"matte_bauhaus"}
PILOT_BLOCKED_FAMILIES = {"anime_matte", "glass_matte", "cyber_hybrid_matte"}
WASD_POS = {(0, 1), (1, 0), (1, 1), (1, 2)}
ANIMATED_FAMILIES = {ANIMATED_FAMILY, "anime_matte", "seasonal", "kawaii_cute"}
PARTICLE_FAMILIES = {"neon_night", "anime_matte", "kawaii_cute", "seasonal", ANIMATED_FAMILY}

DARK_RAINBOW = ["#00F5D4", "#38BDF8", "#4ADE80", "#FACC15", "#FB923C", "#F472B6", "#C084FC"]
LIGHT_RAINBOW = ["#0284C7", "#059669", "#D97706", "#DB2777", "#7C3AED", "#DC2626", "#0F766E"]


def f(size, bangla=False, symbol=False):
    try:
        if symbol:
            return ImageFont.truetype(SYMBOL_FONT, size)
        return ImageFont.truetype(BANGLA_FONT if bangla else FONT, size)
    except OSError:
        return ImageFont.load_default()


def _hexa(hexv, alpha):
    h = hexv.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (alpha,)


def _mix_hex(a_hex, b_hex, t):
    ha, hb = a_hex.lstrip("#"), b_hex.lstrip("#")
    ca = [int(ha[i:i + 2], 16) for i in (0, 2, 4)]
    cb = [int(hb[i:i + 2], 16) for i in (0, 2, 4)]
    return "#%02X%02X%02X" % tuple(int(ca[i] * (1 - t) + cb[i] * t) for i in range(3))


def resolve_key_presentation(fam, idx, light):
    """Determines key_style, key_alpha, rainbow_text per family & variant."""
    if fam == "bangla_heritage":
        return "borderless", 0.0, False
    if fam == "neon_night":
        if idx % 2 == 1:
            return "borderless", 0.0, True
        return "outline", 0.14, False
    if fam == "cyber_hybrid_matte":
        return ("outline", 0.16, False) if idx % 2 == 0 else ("glass", 0.42, False)
    if fam == "retro_terminal":
        return "outline", 0.20, False
    if fam == "liquid_glass":
        return "glass", 0.44, False
    if fam == "glass_matte":
        return "glass", 0.36, False
    if fam in ("live_animated", "anime_matte"):
        if fam == "anime_matte" and idx % 3 == 2:
            return "outline", 0.18, False
        return "glass", 0.46, False
    if fam == "seasonal":
        return ("borderless", 0.0, False) if idx % 3 == 0 else ("glass", 0.54, False)
    if fam == "kawaii_cute":
        return ("pill", 0.85, False) if idx % 2 == 0 else ("glass", 0.58, False)
    if fam == "gradient_calm":
        return ("borderless", 0.0, False) if idx % 2 == 1 else ("glass", 0.62, False)
    if fam == "earth_clay":
        return "ceramic_3d", 1.0, False
    if fam == "luxury_gold":
        return "ceramic_3d" if idx % 2 == 0 else "outline", 0.96 if idx % 2 == 0 else 0.18, False
    return "classic", 1.0, False


def apply_style(fam, sem, light, rng, idx=0):
    """Applies curated community palettes or family overrides + strict bg contrast separation."""
    st = STYLE.get(fam, {})
    if st.get("bw"):
        key_tone = idx * 18 if light else max(150, 255 - 12 * max(0, idx - 3))
        pr_off = (idx * 11) % 0x30
        pr = (0x2A + pr_off) if light else (0xB0 + pr_off)
        if light:
            k = key_tone
            sem.update({"surface/keyboard": "#FFFFFF", "surface/key": "#%02X%02X%02X" % (k, k, k),
                        "surface/key-pressed": "#%02X%02X%02X" % (pr, pr, pr), "surface/key-functional": "#1F1F1F",
                        "surface/enter": "#000000", "border/key": "#000000"})
        else:
            k = key_tone
            wl = max(0, min(60, (idx - 3) * 12))
            sem.update({"surface/keyboard": "#%02X%02X%02X" % (wl, wl, wl), "surface/key": "#%02X%02X%02X" % (k, k, k),
                        "surface/key-pressed": "#%02X%02X%02X" % (pr, pr, pr), "surface/key-functional": "#E0E0E0",
                        "surface/enter": "#FFFFFF", "border/key": "#FFFFFF"})
    else:
        # Inject real-world curated designer palettes on minimal, clay, glass, anime, and cyber families
        if fam in ("graphite_pro", "paper_ink", "pastel_soft", "gradient_calm", "earth_clay",
                   "liquid_glass", "glass_matte", "cyber_hybrid_matte", "anime_matte"):
            pool = CURATED_LIGHT if light else CURATED_DARK
            pal_bg, pal_key, pal_acc, pal_acc2, pal_fg = pool[(idx + (idx // len(pool)) * 3) % len(pool)]
            if idx < len(pool) or fam in ("graphite_pro", "liquid_glass", "paper_ink"):
                blend = (max(0.28, 0.56 - 0.12 * (idx // len(pool))) if (fam == "paper_ink" and idx >= len(pool))
                         else (0.72 if fam in ("graphite_pro", "paper_ink", "pastel_soft") else 0.55))
                sem["surface/keyboard"] = _mix_hex(sem["surface/keyboard"], pal_bg, blend)
                sem["surface/key"] = _mix_hex(sem["surface/key"], pal_key, blend)
                sem["surface/key-functional"] = _mix_hex(sem["surface/key-functional"], pal_bg, 0.35)
                sem["surface/enter"] = pal_acc
                sem["border/spacebar"] = pal_acc2
                sem["trail/gesture"] = pal_acc

        for role, key in (("surface/keyboard", "bg"), ("surface/key", "key"),
                          ("surface/key-pressed", "pressed"), ("surface/key-functional", "functional"),
                          ("surface/enter", "enter"), ("border/key", "border"),
                          ("text/key", "text"), ("text/hint", "hint")):
            if st.get(key) is not None:
                sem[role] = st[key]

    if not st.get("bw"):
        def jitter(hexv, amt):
            h = hexv.lstrip("#")
            rgbv = [int(h[i:i + 2], 16) + rng.randint(-amt, amt) for i in (0, 2, 4)]
            return "#%02X%02X%02X" % tuple(max(0, min(255, v)) for v in rgbv)
        sem["surface/keyboard"] = jitter(sem["surface/keyboard"], 7)
        sem["surface/key"] = jitter(sem["surface/key"], 12)
        if fam == "amoled_minimal":
            sem["surface/keyboard"] = "#000000"
            k = sem["surface/key"].lstrip("#")
            rgbv = [min(255, int(k[i:i + 2], 16) + int(6 + 24 * rng.random())) for i in (0, 2, 4)]
            sem["surface/key"] = "#%02X%02X%02X" % tuple(rgbv)
            sem["surface/enter"] = hx(from_hsv((idx * 0.147 + 0.08) % 1.0, 0.78, 0.90))
            sem["border/spacebar"] = sem["surface/enter"]
        elif fam in ("neon_night", "gaming_rgb"):
            h_neon = (idx * 0.137 + (0.52 if fam == "neon_night" else 0.33)) % 1.0
            neon1 = hx(from_hsv(h_neon, 0.92, 0.96))
            neon2 = hx(from_hsv((h_neon + 0.42) % 1.0, 0.90, 0.96))
            sem["surface/keyboard"] = "#04050A"
            sem["surface/key"] = "#141824"
            sem["surface/key-functional"] = "#101420"
            sem["surface/enter"] = neon1
            sem["border/key"] = neon1
            sem["border/spacebar"] = neon2
            sem["trail/gesture"] = neon2
            sem["surface/key-pressed"] = _mix_hex("#1B2133", neon1, 0.35)
        elif fam == "retro_terminal":
            phosphors = [
                ("#060D09", "#0D1C13", "#7CFF9B", "#2BE477"),  # Classic CRT Green
                ("#0E0905", "#1C130A", "#FFB84D", "#FF9420"),  # Vintage Amber
                ("#050B10", "#0B1924", "#64E9FF", "#00B8FF"),  # IBM Cyan
                ("#070D0C", "#0F1E1B", "#4DFFBE", "#00E699"),  # Matrix Mint
                ("#10060A", "#1E0B13", "#FF7CA3", "#FF2E74"),  # Vector Pink
                ("#060814", "#101630", "#93C5FD", "#3B82F6"),  # Cobalt CRT
                ("#0B0B04", "#1C1C0A", "#FDE047", "#EAB308"),  # Gold Phosphor
            ]
            tbg, tkey, ttxt, tbr = phosphors[idx % len(phosphors)]
            sem["surface/keyboard"] = tbg
            sem["surface/key"] = tkey
            sem["surface/key-functional"] = tbg
            sem["surface/enter"] = tbr
            sem["border/key"] = tbr
            sem["border/spacebar"] = ttxt
            sem["text/key"] = ttxt
            sem["trail/gesture"] = ttxt
        elif fam == "luxury_gold":
            stones = [
                ("#110E14", "#1E1924", "#D4AF37", "#F5EBD8"),  # Obsidian 24K Gold
                ("#091B14", "#122B20", "#E6C767", "#F3F8F4"),  # Royal Emerald Velvet
                ("#0A1324", "#14223D", "#F4C95D", "#EFF4FF"),  # Midnight Sapphire Gold
                ("#1F0B16", "#311323", "#F0A6CA", "#FDF0F6"),  # Burgundy Rose Gold
                ("#1A120E", "#2C1E17", "#E2B46A", "#FAF0E2"),  # Espresso Bronze Marble
                ("#14171D", "#222731", "#CBD5E1", "#F8FAFC"),  # Platinum Slate
            ]
            lbg, lkey, lgold, ltxt = stones[idx % len(stones)]
            sem["surface/keyboard"] = lbg
            sem["surface/key"] = lkey
            sem["surface/key-functional"] = lbg
            sem["surface/enter"] = lgold
            sem["border/key"] = lgold
            sem["border/spacebar"] = lgold
            sem["text/key"] = ltxt
            sem["trail/gesture"] = lgold
        elif fam == "bangla_heritage":
            mode = idx % 5
            if mode == 3 and light:
                sem["surface/keyboard"] = "#E2D5BC"
                sem["surface/key"] = "#FFFFFF"
                sem["surface/key-functional"] = "#F2ECE0"
            else:
                sem["surface/keyboard"] = "#0A1B14" if mode == 0 else ("#0A1634" if mode == 1 else "#14121D")
                sem["surface/key"] = "#F6F8FC"
                sem["surface/key-functional"] = "#1B2332"
            sem["surface/enter"] = hx(from_hsv((idx * 0.21 + 0.02) % 1.0, 0.85, 0.90))
            sem["border/spacebar"] = sem["surface/enter"]
        elif fam == "liquid_glass":
            if light:
                sem["surface/keyboard"] = _mix_hex(sem["surface/keyboard"], "#7FB3D5", 0.55)
                sem["surface/key"] = "#FFFFFF"
                sem["surface/key-functional"] = "#F4F8FC"
            else:
                sem["surface/keyboard"] = "#101626"
                sem["surface/key"] = "#182133"
                sem["surface/key-functional"] = "#131A29"

    if fam == "bangla_heritage":
        wall = sem["surface/keyboard"]
        bg = (wall, wall)
    elif fam == "gradient_calm":
        base = sem["surface/keyboard"]
        bg = (base, hx(from_hsv((rng.random() + idx * 0.17) % 1.0, 0.25 if light else 0.34, 0.80 if light else 0.20)))
    elif fam in ("liquid_glass", "anime_matte", "cyber_hybrid_matte", "live_animated"):
        base = sem["surface/keyboard"]
        if light:
            base = _mix_hex(base, "#7BA6C9", 0.45)
        bg = (base, hx(from_hsv((rng.random() + idx * 0.23) % 1.0, 0.46 if light else 0.52, 0.72 if light else 0.20)))
    else:
        wall = st.get("bg") or (WORLD_BG.get(fam) if light else None) or sem["surface/keyboard"]
        if light and fam in WORLD_BG and not st.get("bg"):
            wcol = [int(WORLD_BG[fam].lstrip("#")[i:i + 2], 16) for i in (0, 2, 4)]
            strat_hue = ((idx * 0.173 + 0.07) % 1.0) if fam == "paper_ink" else rng.random()
            tint = from_hsv(strat_hue, 0.25 if fam == "paper_ink" else 0.20, max(wcol) / 255)
            mix_w = 0.48 if fam == "paper_ink" else 0.55
            wall = "#%02X%02X%02X" % tuple(min(255, int(wcol[i] * mix_w + tint[i] * (1.0 - mix_w))) for i in range(3))
            if fam == "paper_ink":
                sem["surface/enter"] = hx(from_hsv(strat_hue, 0.74, 0.46))
                sem["surface/key-pressed"] = _mix_hex(wall, sem["surface/enter"], 0.42)
        bg = (wall, wall)

    def separate_from_stops(fill_hex, stops, target=1.45, min_fg=4.6):
        cand = fill_hex
        for stop in stops:
            cand = ensure_separation(cand, stop, target)
        if all(contrast(cand, s) >= target for s in stops) and contrast(cand, yiq_text(cand)) >= min_fg:
            return cand
        h = fill_hex.lstrip("#")
        base_rgb = [int(h[i:i + 2], 16) for i in (0, 2, 4)]
        for delta in (-24, 24, -48, 48, -72, 72, -104, 104, -140, 140, -180, 180):
            trial = "#%02X%02X%02X" % tuple(max(0, min(255, v + delta)) for v in base_rgb)
            if all(contrast(trial, s) >= target for s in stops) and contrast(trial, yiq_text(trial)) >= min_fg:
                return trial
        return "#FFFFFF" if not light else "#1D222B"

    for role in ("surface/key", "surface/key-pressed", "surface/key-functional", "surface/enter"):
        min_fg = 4.6 if role == "surface/key" else 0.0
        sem[role] = separate_from_stops(sem[role], bg, 1.45, min_fg=min_fg)

    if fam == "amoled_minimal":
        sem["text/key"] = "#E2E4E9"
        sem["surface/key"] = ensure_contrast(sem["surface/key"], "#E2E4E9", 4.5)
    else:
        sem["text/key"] = yiq_text(sem["surface/key"])
    sem["text/key-pressed"] = yiq_text(sem["surface/key-pressed"])
    sem["text/enter"] = yiq_text(sem["surface/enter"])
    sem["text/hint"] = ensure_contrast(sem["text/hint"], sem["surface/key"], 3.0)
    return sem, bg


def bauhaus_palette(sem, idx, light):
    SETS = [
        ("#E2B438", "#2A5288", "#C23636", "#FAF7F2", "#1C1D20"),
        ("#E09F3E", "#335C67", "#9E2A2B", "#FBF9F4", "#1B1F24"),
        ("#F4A261", "#264653", "#E76F51", "#FCFBF7", "#181C20"),
        ("#D4A373", "#3D5A80", "#E07A5F", "#FAF8EC", "#1D2026"),
        ("#E9C46A", "#1D3557", "#E63946", "#F7FCF5", "#14181F"),
        ("#D88C9A", "#4A4E69", "#2B9348", "#FAF4F0", "#22223B"),
    ]
    c1, c2, c3, light_cap, dark_cap = SETS[idx % len(SETS)]
    if idx >= len(SETS):
        h_base = ((idx * 0.193) + 0.07) % 1.0
        c1 = hx(from_hsv(h_base, 0.72, 0.86 if light else 0.78))
        c2 = hx(from_hsv((h_base + 0.34) % 1.0, 0.68, 0.56 if light else 0.62))
        c3 = hx(from_hsv((h_base + 0.64) % 1.0, 0.74, 0.74 if light else 0.70))
    base_cap = light_cap if light else dark_cap
    alt_cap = dark_cap if light else light_cap
    raw = [base_cap, c1, c2, c3, alt_cap]
    stops = sem.get("_bg_stops", ())
    out = []
    for col in raw:
        c = col
        for st in stops:
            c = ensure_separation(c, st, 1.45)
        out.append(c)
    return out


def letter_fill(sem, fam, row, col, idx, multi):
    if fam == "gaming_rgb":
        return sem["surface/enter"] if (row, col) in WASD_POS else sem["surface/key"]
    if not multi:
        return sem["surface/key"]
    pal = sem.get("_bauhaus_palette") or [sem["surface/key"]]
    stride_r = 2 + (idx % 3)
    stride_c = 1 + ((idx // 3) % 2)
    return pal[(row * stride_r + col * stride_c + idx) % len(pal)]


def render_preview(theme, layout, size, bg_image):
    w, h = size
    sem = theme["semantic"]
    fam = theme["family"]
    st = STYLE.get(fam, {})
    kstyle = theme["key_style"]
    kalpha = theme["key_alpha"]
    rainbow = theme["rainbow_text"]

    img = bg_image.resize((w, h), Image.LANCZOS).convert("RGB")
    d = ImageDraw.Draw(img, "RGBA")

    rows = LAYOUTS[layout]
    bangla = layout == "bn"
    mx, gap = int(w * 0.022), max(3, int(w * 0.013))
    kh, vgap = int(h * 0.185), int(h * 0.045)
    y = int(h * 0.055)
    radius = 0 if fam == "pixel_8bit" else (int(kh * 0.48) if kstyle == "pill" else theme["radius"])
    stroke_w = st.get("stroke_w", theme["stroke"])
    ow = max(1, round(stroke_w * (w / 540))) if stroke_w or st.get("all_keys_border") or kstyle == "outline" else 0
    border = sem["border/key"]
    glow_layer = Image.new("RGBA", (w, h), (0, 0, 0, 0)) if (st.get("glow") or kstyle == "outline" or rainbow) else None

    pressed_pos = theme["pressed_pos"]
    labels_draw = []
    for r, row in enumerate(rows):
        units = sum(u for _, u in row)
        kw_total = w - 2 * mx - (len(row) - 1) * gap
        unit_px = kw_total / units
        x = mx
        letter_col = 0
        for label, u in row:
            kw = int(unit_px * u)
            if label == "":
                x += kw + gap
                continue
            is_letter = label.isalnum() and len(label) == 1
            is_enter = label == "\u23ce"
            is_space = label == " "
            pressed = is_letter and (r, letter_col) == pressed_pos

            if pressed:
                fill, txt = sem["surface/key-pressed"], sem["text/key-pressed"]
            elif is_enter:
                fill, txt = sem["surface/enter"], sem["text/enter"]
            elif is_letter:
                fill = letter_fill(sem, fam, r, letter_col, theme["idx"], theme["multi"])
                if rainbow:
                    rpal = LIGHT_RAINBOW if theme["is_light"] else DARK_RAINBOW
                    txt = rpal[(letter_col + r) % len(rpal)]
                elif kstyle == "borderless":
                    txt = yiq_text(theme["bg_stops"][0])
                    if contrast(txt, theme["bg_stops"][0]) < 4.5:
                        txt = "#FFFFFF" if not theme["is_light"] else "#121212"
                elif kstyle == "outline":
                    txt = sem["text/key"] if fam == "retro_terminal" else ("#FFFFFF" if not theme["is_light"] else "#121212")
                else:
                    txt = yiq_text(fill)
            else:
                fill = sem["surface/key-functional"]
                if kstyle == "borderless":
                    txt = yiq_text(theme["bg_stops"][0])
                elif kstyle == "outline":
                    txt = sem["text/key"] if fam == "retro_terminal" else ("#FFFFFF" if not theme["is_light"] else "#121212")
                else:
                    txt = sem["text/key"]

            # Draw keycap according to archetype key_style
            if kstyle == "borderless":
                if pressed:
                    d.rounded_rectangle((x, y, x + kw, y + kh), radius=radius, fill=_hexa(sem["surface/enter"], 85))
                elif is_space:
                    d.rounded_rectangle((x, y + int(kh * 0.14), x + kw, y + int(kh * 0.86)), radius=int(kh * 0.36),
                                        fill=_hexa(sem["surface/enter"], 36), outline=_hexa(sem["border/spacebar"], 130), width=max(1, ow or 1))
                elif is_enter:
                    d.rounded_rectangle((x, y, x + kw, y + kh), radius=int(kh * 0.48),
                                        fill=_hexa(sem["surface/enter"], 195))
            elif kstyle == "outline":
                rim = sem["border/spacebar"] if (is_enter or is_space or (r, letter_col) in WASD_POS) else border
                # Subtle neon halo outer ring
                d.rounded_rectangle((x - 1, y - 1, x + kw + 1, y + kh + 1), radius=radius + 1,
                                    outline=_hexa(rim, 72), width=max(2, (ow or 1) + 2))
                d.rounded_rectangle((x, y, x + kw, y + kh), radius=radius,
                                    fill=_hexa(fill, 40 if not is_enter else 190),
                                    outline=_hexa(rim, 235), width=max(1, ow or 1))
            elif kstyle == "glass":
                alpha_px = int((0.86 if is_enter else kalpha) * 255)
                d.rounded_rectangle((x, y, x + kw, y + kh), radius=radius,
                                    fill=_hexa(fill, alpha_px),
                                    outline=_hexa("#FFFFFF" if not theme["is_light"] else border, 130),
                                    width=max(1, ow or 1))
                # Diagonal specular sheen inside glass keycap
                d.line((x + radius, y + 2, x + kw - radius, y + 2), fill=(255, 255, 255, 115), width=1)
            elif kstyle == "ceramic_3d":
                # Drop skirt + smooth ceramic glaze bevel without hard blocky inner box
                d.rounded_rectangle((x, y + 3, x + kw, y + kh + 3), radius=radius, fill=(0, 0, 0, 92))
                d.rounded_rectangle((x, y, x + kw, y + kh), radius=radius, fill=_hexa(fill, 255),
                                    outline=_hexa("#FFFFFF" if theme["is_light"] else border, 185), width=max(1, ow or 1))
                d.line((x + radius, y + 2, x + kw - radius, y + 2), fill=(255, 255, 255, 175), width=2)
                d.line((x + radius, y + kh - 2, x + kw - radius, y + kh - 2), fill=(0, 0, 0, 75), width=2)
            else:
                # Classic Minimal / Bauhaus / Gaming RGB
                if fam == "gaming_rgb" and ((r, letter_col) in WASD_POS and is_letter or is_enter):
                    d.rounded_rectangle((x - 2, y - 2, x + kw + 2, y + kh + 2), radius=radius + 2,
                                        outline=_hexa(sem["surface/enter"], 185), width=3)
                elif fam in ("amoled_minimal", "graphite_pro", "paper_ink", "pastel_soft") and not theme["is_light"]:
                    d.rounded_rectangle((x, y + 2, x + kw, y + kh + 2), radius=radius, fill=(0, 0, 0, 75))
                d.rounded_rectangle((x, y, x + kw, y + kh), radius=radius,
                                    fill=_hexa(fill, 255),
                                    outline=_hexa(border, 255) if ow else None, width=ow or 1)
                if st.get("bevel"):
                    d.line((x + radius, y + 1, x + kw - radius, y + 1), fill=(255, 255, 255, 36))

            fs = int(kh * (0.60 if label in ("\u232b", "\u21e7", "\u23ce") else
                           (0.43 if is_letter else (0.30 if label == "?123" else 0.45))))
            tf = f(fs, bangla, symbol=label in SYMBOLS)
            labels_draw.append((x + kw / 2, y + kh / 2, label, tf, txt))
            if is_letter:
                letter_col += 1
            x += kw + gap
        y += kh + vgap

    # Neon / live swipe trail arc cue on neon & live previews
    if fam in ("neon_night", "cyber_hybrid_matte", ANIMATED_FAMILY) and layout == "en":
        trail = [(w * 0.22, h * 0.56), (w * 0.44, h * 0.34), (w * 0.68, h * 0.42), (w * 0.80, h * 0.24)]
        d.line(trail, fill=_hexa(sem["trail/gesture"], 120), width=max(4, int(w * 0.012)), joint="curve")
        d.line(trail, fill=(255, 255, 255, 195), width=max(2, int(w * 0.004)), joint="curve")

    # Subtle glow halo under glyphs on borderless/neon/terminal themes for razor contrast
    if glow_layer or kstyle == "borderless":
        halo = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        gd = ImageDraw.Draw(halo)
        for lx, ly, label, tf, txt in labels_draw:
            shadow_col = _hexa(txt, 175) if glow_layer else ((0, 0, 0, 170) if not theme["is_light"] else (255, 255, 255, 170))
            gd.text((lx, ly), label, font=tf, fill=shadow_col, anchor="mm")
        halo = halo.filter(ImageFilter.GaussianBlur(3 if glow_layer else 2))
        img.paste(halo, (0, 0), halo)

    d = ImageDraw.Draw(img, "RGBA")
    for lx, ly, label, tf, txt in labels_draw:
        d.text((lx, ly), label, font=tf, fill=_hexa(txt, 255), anchor="mm")
    return img


def build_manifest(theme):
    sem = theme["semantic"]
    live = theme["is_live"]
    bg_asset = "assets/bg/static.webp"
    return {
        "schema": 1, "theme_id": theme["id"], "app_id": "keybird",
        "title": theme["title"], "version": 1, "theme_type": "color",
        "is_light": theme["is_light"],
        "colors": {
            "primary": sem["surface/enter"], "on_primary": sem["text/enter"],
            "background": theme["bg_stops"][0], "on_background": sem["text/key"],
            "surface": sem["surface/key"], "on_surface": sem["text/key"],
            "surface_variant": sem["surface/key-functional"], "on_surface_variant": sem["text/hint"],
            "outline": sem["border/key"],
        },
        "keyboard": {
            "bg_type": "gradient" if theme["bg_stops"][0] != theme["bg_stops"][1] else "solid",
            "bg_colors": list(theme["bg_stops"]),
            "key_bg": sem["surface/key"], "key_pressed_bg": sem["surface/key-pressed"],
            "functional_key_bg": sem["surface/key-functional"],
            "enter_key_bg": sem["surface/enter"],
            "keyPressedText": sem["text/key-pressed"],
            "key_color_map": theme["key_color_map"],
            "key_stroke": sem["border/key"], "key_stroke_width_dp": theme["stroke"],
            "key_corner_radius_dp": theme["radius"],
            "key_text": sem["text/key"], "key_hint": sem["text/hint"],
            "popup_bg": sem["popup/bg"], "popup_text": sem["popup/text"],
            "gesture_trail": sem["trail/gesture"], "font_style": theme["font"],
            "scrim_alpha": 0.0,
            "glass_rim_glow": theme["key_style"] in ("glass", "outline") or theme["family"] in ("neon_night", "gaming_rgb", "liquid_glass", "glass_matte"),
            "effect_mode": theme["effect"],
            "key_style": theme["key_style"],
            "key_alpha": theme["key_alpha"],
            "rainbow_text": theme["rainbow_text"],
            "motion": theme["motion"],
            **({"bg_animated_asset": "assets/bg/live.webp"} if live else {}),
        },
        "art_pack": {"bg_full_asset": bg_asset, "keymap": {}, "group_fallbacks": {}},
    }


def write_sheet(cells, path, cols=5, per_sheet=50):
    cw, ch = 232, 168
    rows = (min(len(cells), per_sheet) + cols - 1) // cols
    sheet = Image.new("RGB", (cw * cols, ch * rows), (16, 16, 20))
    d = ImageDraw.Draw(sheet)
    for i, (state, tid, fam) in enumerate(cells[:per_sheet]):
        x, y = (i % cols) * cw, (i // cols) * ch
        if state == "BLOCKED":
            d.rectangle((x + 4, y + 4, x + cw - 4, y + ch - 4), fill=(48, 48, 54), outline=(120, 120, 130), width=2)
            d.text((x + cw / 2, y + ch / 2 - 10), "BLOCKED", font=f(18), fill=(255, 190, 120), anchor="mm")
            d.text((x + cw / 2, y + ch / 2 + 14), "PILOT_PREVIEW", font=f(12), fill=(220, 220, 225), anchor="mm")
        else:
            img = Image.open(tid).resize((cw - 8, 138))
            sheet.paste(img, (x + 4, y + 4))
        d.text((x + 6, y + ch - 22), f"{i + 1:02d} {fam}", font=f(12), fill=(225, 225, 235))
    sheet.save(path)


def main():
    ap = argparse.ArgumentParser(description="Keybird theme factory v4.1")
    ap.add_argument("--full", action="store_true", help="generate all 500 themes")
    ap.add_argument("--resume-since", type=float, default=0.0, help="reuse bundles built at/after this epoch timestamp")
    ap.add_argument("--repack-existing", action="store_true", help="reuse existing static/live backdrops and rebuild previews + slim ZIPs")
    args = ap.parse_args()
    full = args.full
    resume_since = args.resume_since
    repack_existing = args.repack_existing

    out_themes = HERE / "out" / Path("full/themes") if full else HERE / "out" / "themes"
    proof_dir = HERE / "out" / "proof"
    catalog_path = HERE / "data" / ("full_themes.json" if full else "pilot_themes.json")
    prefix = "kb" if full else "kb_pilot"
    out_themes.mkdir(parents=True, exist_ok=True)
    proof_dir.mkdir(parents=True, exist_ok=True)

    catalog = []
    cells = []
    rendered = 0
    blocked = 0
    for fam, count, light_n, dark_n, world, focal, radii, strokes, fonts, effect, is_photo in FAMILIES:
        per = count if full else 2
        for idx in range(per):
            fid = f"{fam}_{idx + 1:02d}" if not full else f"{fam}_{idx + 1:03d}"
            if is_photo or (not full and fam in PILOT_BLOCKED_FAMILIES):
                cells.append(("BLOCKED", fid, fam))
                blocked += 1
                continue
            light = idx < light_n
            if not full and fam == "high_contrast_a11y" and idx == 1:
                light = False
            fam_seed = zlib.crc32(fam.encode()) / 2**32
            accent_hue = (fam_seed + idx * 0.618) % 1.0
            rng = random.Random(f"{fam}:{idx}")
            sem = semantic(world, light, accent_hue, accent_sat=0.65 + rng.random() * 0.25)
            sem["_is_light"] = light
            sem, bg_stops = apply_style(fam, sem, light, rng, idx=idx)
            sem["_bg_stops"] = bg_stops

            if fam == "matte_bauhaus":
                wall = hx(from_hsv(accent_hue, 0.10, 0.78 - (idx % 4) * 0.03)) if light else hx(from_hsv(accent_hue, 0.18, 0.06 + (idx % 5) * 0.02))
                bg_stops = (wall, wall)
                sem["_bg_stops"] = bg_stops
                b_pal = bauhaus_palette(sem, idx, light)
                sem["_bauhaus_palette"] = b_pal
                sem["surface/keyboard"] = wall
                sem["surface/key"] = b_pal[0]
                sem["surface/enter"] = b_pal[2]
                sem["surface/key-functional"] = ensure_separation("#FFFFFF" if light else "#242730", wall, 1.45)
                sem["surface/key-pressed"] = ensure_separation(b_pal[1], wall, 1.45)
                sem["text/key"] = yiq_text(sem["surface/key"])
                sem["text/key-pressed"] = yiq_text(sem["surface/key-pressed"])
                sem["text/enter"] = yiq_text(sem["surface/enter"])
                sem["text/hint"] = ensure_contrast(sem["text/hint"], sem["surface/key"], 3.0)
                key_palette = [ensure_separation(ensure_separation(c, wall, 1.4), wall, 1.4) for c in b_pal]
                pattern_mode = "bauhaus_cycle"
            elif fam == "gaming_rgb":
                key_palette = [
                    ensure_separation(ensure_separation(sem["surface/key"], bg_stops[0], 1.4), bg_stops[1], 1.4),
                    ensure_separation(ensure_separation(sem["surface/enter"], bg_stops[0], 1.4), bg_stops[1], 1.4),
                ]
                pattern_mode = "wasd_accent"
            else:
                key_palette = []
                pattern_mode = "none"

            kstyle, kalpha, rainbow = resolve_key_presentation(fam, idx, light)
            is_live = (fam in ANIMATED_FAMILIES) or (fam in ("neon_night", "cyber_hybrid_matte") and idx % 3 == 0)
            s_slot = (int(fam_seed * 26) + idx * 7) % 26
            pressed_row, pressed_col = ((0, s_slot) if s_slot < 10 else ((1, s_slot - 10) if s_slot < 19 else (2, s_slot - 19))) if fam == "paper_ink" else (
                (lambda pr: (pr, (int(fam_seed * 9) + idx) % (9 if pr == 1 else 7)))(1 + (int(fam_seed * 997) + idx) % 2)
            )
            parallax_dp = 6 if (is_live or idx % 3 == 2) else 0
            has_particles = is_live or (fam in PARTICLE_FAMILIES) or (idx % 4 == 3)

            theme = {
                "id": f"{prefix}_{fid}", "family": fam, "title": f"{world} {idx + 1}",
                "idx": idx, "is_light": light, "semantic": sem,
                "bg_stops": bg_stops,
                "radius": min(RADII, key=lambda r: abs(r - radii[idx % len(radii)])),
                "stroke": strokes[idx % len(strokes)],
                "font": fonts[idx % len(fonts)], "effect": effect, "multi": fam in MULTI_FAMILIES,
                "key_style": kstyle, "key_alpha": kalpha, "rainbow_text": rainbow, "is_live": is_live,
                "world": world, "focal": focal,
                "pressed_pos": (pressed_row, pressed_col),
                "key_color_map": {"palette": key_palette, "patternMode": pattern_mode, "keys": {}},
                "motion": {
                    "bgAnimatedAsset": "assets/bg/live.webp" if is_live else "",
                    "pressEffect": effect, "pressColor": sem["surface/enter"],
                    "parallaxDp": parallax_dp,
                    "particles": has_particles, "particleDensity": 4 if is_live else (3 if has_particles else 0),
                    "particleColor": sem["trail/gesture"] if has_particles else "",
                },
            }

            stage = out_themes / theme["id"] / "v1"
            zp = out_themes / f"{theme['id']}_v1.iplustheme"
            card_p = stage / "assets/preview/en_card.png"
            if (resume_since > 0.0 and zp.exists() and zp.stat().st_mtime >= resume_since
                    and (stage / "manifest.json").exists() and card_p.exists()):
                sha = hashlib.sha256(zp.read_bytes()).hexdigest()
                catalog.append({**{k: v for k, v in theme.items() if k not in ("semantic",)},
                                "sha256": sha, "bundle": str(zp)})
                cells.append(("OK", str(card_p), fam))
                rendered += 1
                if full and rendered % 50 == 0:
                    print(f"  themes rendered {rendered}/500 (resumed)", flush=True)
                continue

            static_bg_path = stage / "assets/bg/static.webp"
            live_bg_path = stage / "assets/bg/live.webp"
            can_reuse_bg = repack_existing and static_bg_path.exists() and (not is_live or live_bg_path.exists())
            if not can_reuse_bg:
                if stage.exists():
                    shutil.rmtree(stage)
                (stage / "assets/preview").mkdir(parents=True, exist_ok=True)
                (stage / "assets/bg").mkdir(parents=True, exist_ok=True)
                bd_full = build_backdrop(theme, PW, PH)
                bd_full.save(static_bg_path, "WEBP", quality=70)
                if is_live:
                    live_bytes = build_live_webp(theme, bd_full, w=450, h=300, frames=8, duration_ms=110)
                    live_bg_path.write_bytes(live_bytes)
            else:
                (stage / "assets/preview").mkdir(parents=True, exist_ok=True)
                with Image.open(static_bg_path) as existing_bg:
                    bd_full = existing_bg.convert("RGB")

            manifest = build_manifest(theme)
            (stage / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=1), encoding="utf-8")
            render_preview(theme, "en", (PW, PH), bd_full).save(stage / "assets/preview/en.png", optimize=True)
            render_preview(theme, "en", (TW, TH), bd_full).save(stage / "assets/preview/en_card.png", optimize=True)
            render_preview(theme, "bn", (TW, TH), bd_full).save(stage / "assets/preview/bn_card.png", optimize=True)
            render_preview(theme, "num", (TW, TH), bd_full).save(stage / "assets/preview/num_card.png", optimize=True)

            if zp.exists():
                zp.unlink()
            with zipfile.ZipFile(zp, "w", zipfile.ZIP_DEFLATED) as z:
                for p in sorted(stage.rglob("*")):
                    rel_posix = p.relative_to(stage).as_posix()
                    if p.is_file() and not rel_posix.startswith("assets/preview/"):
                        z.write(p, rel_posix)
            sha = hashlib.sha256(zp.read_bytes()).hexdigest()
            catalog.append({**{k: v for k, v in theme.items() if k not in ("semantic",)},
                            "sha256": sha, "bundle": str(zp)})
            cells.append(("OK", str(stage / "assets/preview/en_card.png"), fam))
            rendered += 1
            if full and rendered % 50 == 0:
                print(f"  themes rendered {rendered}/500", flush=True)

    catalog_path.write_text(json.dumps(catalog, ensure_ascii=False, indent=1), encoding="utf-8")

    if full:
        per_sheet = 50
        sheets = (len(cells) + per_sheet - 1) // per_sheet
        for s in range(sheets):
            write_sheet(cells[s * per_sheet:(s + 1) * per_sheet], proof_dir / f"full_sheet_{s + 1:02d}.png")
        print(f"full plan: {rendered} rendered, {blocked} blocked, {sheets} proof sheets")
    else:
        write_sheet(cells, proof_dir / "themes_sheet_pilot_01.png")
        print(f"pilot themes: {rendered} rendered, {blocked} blocked")
    print("catalog:", catalog_path)


if __name__ == "__main__":
    main()
