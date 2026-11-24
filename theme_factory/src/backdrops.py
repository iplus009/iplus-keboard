"""Procedural high-craft keyboard theme backdrops, curated palettes, and live WebP loops."""
from __future__ import annotations

import io
import math
import random
from typing import Sequence

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

from tokens import from_hsv, hx

# Real-world curated palettes gathered from enthusiast keycaps & UI systems.
CURATED_DARK = [
    ("#1E1E2E", "#313244", "#CBA6F7", "#89B4FA", "#CDD6F4"),  # Catppuccin Mocha Mauve
    ("#1E1E2E", "#2B2D3E", "#F38BA8", "#94E2D5", "#CDD6F4"),  # Catppuccin Mocha Rose-Teal
    ("#1A1B26", "#292E42", "#7AA2F7", "#BB9AF7", "#C0CAF5"),  # Tokyo Night Blue
    ("#1A1B26", "#24283B", "#F7768E", "#73DACA", "#C0CAF5"),  # Tokyo Night Sakura
    ("#191724", "#26233A", "#EB6F92", "#9CCFD8", "#E0DEF4"),  # Rose Pine Dark
    ("#2E3440", "#3B4252", "#88C0D0", "#BF616A", "#ECEFF4"),  # Nord Frost
    ("#16161D", "#252535", "#D27E99", "#7E9CD8", "#DCD7BA"),  # Kanagawa Wave
    ("#1D2021", "#3C3836", "#FE8019", "#8EC07C", "#FBF1C7"),  # Gruvbox Dark
    ("#232A2E", "#343F44", "#A7C080", "#E67E80", "#D3C6AA"),  # Everforest Medium
    ("#091833", "#133E7C", "#EA00D9", "#0ABDC6", "#F2F8FF"),  # Cyberpunk Alley
    ("#0B0620", "#271055", "#E0357F", "#2FD8D0", "#FFF0FA"),  # Classic Synthwave
    ("#19110E", "#2A1D17", "#A47864", "#D9886A", "#E6DCB8"),  # Espresso Mocha Mousse
]

CURATED_LIGHT = [
    ("#EFF1F5", "#DCE0E8", "#8839EF", "#1E66F5", "#4C4F69"),  # Catppuccin Latte
    ("#FAF4ED", "#EBE4DC", "#B4637A", "#286983", "#575279"),  # Rose Pine Dawn
    ("#F0EEE9", "#FFFFFF", "#2F5D62", "#E07A5F", "#1E2229"),  # Pantone Cloud Dancer
    ("#F9F6F0", "#EFE8DE", "#A47864", "#6B8E7B", "#2C1E18"),  # Pantone Mocha Organic
    ("#F3EEE8", "#CFF5C2", "#3A7D5C", "#E07A5F", "#24332B"),  # osume Matcha Latte
    ("#FBF4F7", "#F2C8D6", "#D65A7B", "#5B3A41", "#3B2229"),  # Sakura Blossom
    ("#F2F7FA", "#DCF0FA", "#0284C7", "#38BDF8", "#162A3A"),  # Frutiger Aero Sky
    ("#ECEFF4", "#D8DEE9", "#5E81AC", "#88C0D0", "#2E3440"),  # Nord Snow Storm
]


def _rgb(hexv: str) -> tuple[int, int, int]:
    h = hexv.lstrip("#")
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)


def _mesh_gradient(w: int, h: int, c_tl: tuple[int, int, int], c_tr: tuple[int, int, int],
                   c_bl: tuple[int, int, int], c_br: tuple[int, int, int]) -> Image.Image:
    x = np.linspace(0.0, 1.0, w, dtype=np.float32)[None, :, None]
    y = np.linspace(0.0, 1.0, h, dtype=np.float32)[:, None, None]
    tl = np.array(c_tl, dtype=np.float32)
    tr = np.array(c_tr, dtype=np.float32)
    bl = np.array(c_bl, dtype=np.float32)
    br = np.array(c_br, dtype=np.float32)
    top = tl * (1.0 - x) + tr * x
    bot = bl * (1.0 - x) + br * x
    grid = top * (1.0 - y) + bot * y
    return Image.fromarray(np.clip(grid, 0, 255).astype(np.uint8), "RGB")


def _add_film_grain(img: Image.Image, sigma: float = 5.0, seed: int = 1) -> Image.Image:
    rng = np.random.default_rng(seed)
    arr = np.array(img, dtype=np.float32)
    noise = rng.normal(0.0, sigma, size=(img.height, img.width, 1)).astype(np.float32)
    arr = np.clip(arr + noise, 0, 255).astype(np.uint8)
    return Image.fromarray(arr, "RGB")


def _draw_glow_orb(base: Image.Image, cx: float, cy: float, r: float, color: tuple[int, int, int], alpha: int = 130):
    layer = Image.new("RGBA", base.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    steps = 7
    for i in range(steps, 0, -1):
        rr = r * i / steps
        a = int(alpha * (1.0 - (i - 1) / steps) ** 1.7)
        d.ellipse((cx - rr, cy - rr, cx + rr, cy + rr), fill=color + (a,))
    layer = layer.filter(ImageFilter.GaussianBlur(radius=max(6, int(r * 0.22))))
    base.paste(layer, (0, 0), layer)


def _draw_smriti_soudho(img: Image.Image, idx: int) -> Image.Image:
    w, h = img.size
    d = ImageDraw.Draw(img, "RGBA")
    cx = w * (0.28 + ((idx * 19) % 9) * 0.055)
    sun_r = int(h * (0.28 + (idx % 3) * 0.05))
    sun_y = h * (0.48 + (idx % 3) * 0.06)
    for sr, a in ((sun_r * 1.45, 55), (sun_r * 1.18, 95), (sun_r, 230)):
        d.ellipse((cx - sr, sun_y - sr, cx + sr, sun_y + sr), fill=(224, 30, 48, a))
    base_y = h * 0.96
    spires = [
        (0.46, 0.36, (10, 128, 62, 240)),
        (0.36, 0.50, (12, 148, 72, 245)),
        (0.26, 0.64, (15, 168, 82, 250)),
        (0.16, 0.78, (18, 190, 92, 255)),
        (0.08, 0.90, (24, 212, 108, 255)),
    ]
    for half_w_frac, h_frac, col in spires:
        hw = w * half_w_frac * 0.55
        tip_y = base_y - h * h_frac * 0.86
        d.polygon([(cx - hw, base_y), (cx, tip_y), (cx + hw, base_y)], fill=col)
    d.polygon([(0, h), (0, h * 0.80), (w * 0.28, h * 0.72), (w * 0.62, h * 0.84), (w, h * 0.74), (w, h)],
              fill=(8, 68, 36, 235))
    return img


def _draw_eid_lantern(img: Image.Image, idx: int) -> Image.Image:
    w, h = img.size
    lx = w * (0.72 if idx % 2 == 0 else 0.26)
    mx = w * (0.24 if idx % 2 == 0 else 0.74)
    _draw_glow_orb(img, lx, h * 0.34, h * 0.36, (255, 198, 72), alpha=120)
    _draw_glow_orb(img, mx, h * 0.38, h * 0.28, (90, 180, 255), alpha=75)
    d = ImageDraw.Draw(img, "RGBA")
    rng = random.Random(f"eid:{idx}")
    for _ in range(36):
        sx, sy = rng.uniform(0, w), rng.uniform(0, h * 0.78)
        sr = rng.uniform(1.5, 4.0)
        d.ellipse((sx - sr, sy - sr, sx + sr, sy + sr), fill=(255, 242, 190, rng.randint(120, 230)))
    my, mr = h * (0.34 + (idx % 3) * 0.04), h * 0.19
    d.ellipse((mx - mr, my - mr, mx + mr, my + mr), fill=(255, 224, 130, 245))
    d.ellipse((mx - mr * 0.65, my - mr * 1.1, mx + mr * 1.1, my + mr * 0.82), fill=(12, 26, 66, 255))
    ly = h * 0.38
    d.line((lx, 0, lx, ly - h * 0.16), fill=(240, 198, 95, 230), width=max(2, int(w * 0.004)))
    lw, lh = w * 0.06, h * 0.17
    d.polygon([(lx, ly - lh), (lx + lw, ly), (lx, ly + lh), (lx - lw, ly)],
              fill=(255, 212, 95, 220), outline=(255, 245, 200, 255))
    d.rectangle((0, h * 0.80, w, h), fill=(8, 16, 44, 225))
    dome_cx = w * (0.50 + ((idx % 3) - 1) * 0.12)
    d.pieslice((dome_cx - w * 0.16, h * 0.62, dome_cx + w * 0.16, h * 0.96), 180, 360, fill=(10, 22, 56, 240))
    return img


def _draw_rickshaw_mandala(img: Image.Image, idx: int) -> Image.Image:
    w, h = img.size
    d = ImageDraw.Draw(img, "RGBA")
    pal = [(230, 0, 18), (255, 209, 0), (0, 166, 81), (236, 0, 140), (0, 120, 235)]
    cx = w * (0.32 + ((idx * 23) % 9) * 0.045)
    cy = h * (0.44 + (idx % 3) * 0.06)
    for ring_idx, r_frac in enumerate((0.46, 0.34, 0.22)):
        rr = h * r_frac
        col = pal[(ring_idx + idx * 2) % len(pal)]
        petals = 12 + (ring_idx % 2) * 4
        for k in range(petals):
            a = 2 * math.pi * k / petals + idx * 0.25
            px = cx + math.cos(a) * rr * 0.68
            py = cy + math.sin(a) * rr * 0.68
            pr = rr * 0.34
            d.ellipse((px - pr, py - pr, px + pr, py + pr), fill=col + (185,), outline=(255, 255, 255, 190), width=2)
    d.ellipse((cx - h * 0.12, cy - h * 0.12, cx + h * 0.12, cy + h * 0.12),
              fill=(255, 209, 0, 235), outline=(255, 255, 255, 220), width=3)
    d.rectangle((0, 0, w, h), fill=(10, 10, 16, 105))
    return img


def _draw_jamdani_weave(img: Image.Image, idx: int) -> Image.Image:
    w, h = img.size
    d = ImageDraw.Draw(img, "RGBA")
    step = int(min(w, h) * (0.14 + (idx % 3) * 0.03))
    zari = (224, 184, 72, 165) if idx % 2 == 0 else (184, 92, 68, 165)
    sub = (255, 245, 220, 75)
    for y in range(-step, h + step, step):
        for x in range(-step, w + step, step):
            cx = x + ((y // step) % 2) * (step // 2)
            r = step * 0.36
            d.polygon([(cx, y - r), (cx + r, y), (cx, y + r), (cx - r, y)], outline=zari, width=2)
            d.ellipse((cx - 5, y - 5, cx + 5, y + 5), fill=sub)
    return img


def _draw_river_nouka(img: Image.Image, idx: int) -> Image.Image:
    w, h = img.size
    hor = h * (0.48 + (idx % 3) * 0.05)
    sun_x = w * (0.28 + ((idx * 17) % 9) * 0.055)
    _draw_glow_orb(img, sun_x, hor - h * 0.08, h * 0.34, (255, 165, 65), alpha=155)
    d = ImageDraw.Draw(img, "RGBA")
    d.ellipse((sun_x - h * 0.09, hor - h * 0.19, sun_x + h * 0.09, hor + h * 0.02), fill=(255, 215, 110, 235))
    d.rectangle((0, hor, w, h), fill=(18, 22, 46, 165))
    for k in range(10):
        ry = hor + (k + 0.5) * (h - hor) / 10
        rw = w * (0.28 - k * 0.018)
        d.line((sun_x - rw, ry, sun_x + rw, ry), fill=(255, 195, 105, 150 - k * 10), width=3)
    bx, by = w * (0.72 if sun_x < w * 0.5 else 0.30), hor + h * 0.12
    d.polygon([(bx - w * 0.16, by - h * 0.04), (bx - w * 0.07, by + h * 0.05),
               (bx + w * 0.11, by + h * 0.05), (bx + w * 0.18, by - h * 0.05)], fill=(12, 10, 18, 245))
    d.pieslice((bx - w * 0.06, by - h * 0.08, bx + w * 0.07, by + h * 0.03), 180, 360, fill=(24, 18, 30, 250))
    return img


def _draw_synthwave_grid(img: Image.Image, accent1: tuple[int, int, int], accent2: tuple[int, int, int], idx: int) -> Image.Image:
    w, h = img.size
    hor = int(h * (0.42 + (idx % 4) * 0.06))
    cx = int(w * (0.24 + ((idx * 17) % 11) * 0.052))
    _draw_glow_orb(img, cx, hor - h * 0.05, h * 0.42, accent1, alpha=145)
    d = ImageDraw.Draw(img, "RGBA")
    sr = int(h * (0.20 + (idx % 3) * 0.05))
    cy = hor - int(sr * 0.18)
    sun_col = (255, 200, 72, 245) if idx % 2 == 0 else accent1 + (235,)
    d.ellipse((cx - sr, cy - sr, cx + sr, cy + sr), fill=sun_col)
    # Sliced retro sun bands
    y_cut = cy + int(sr * 0.06)
    for k in range(5):
        band = max(3, int(sr * (0.045 + k * 0.02)))
        d.rectangle((cx - sr - 4, y_cut, cx + sr + 4, y_cut + band), fill=(24, 12, 52, 255))
        y_cut += band + int(sr * 0.12)
    # Ground + perspective rays
    d.rectangle((0, hor, w, h), fill=(10, 6, 22, 232))
    rays = 14
    spread = w * 2.0
    for i in range(rays):
        bx = (i / (rays - 1)) * spread - (spread - w) * 0.5
        d.line((cx, hor, bx, h), fill=accent1 + (165,), width=2)
    for i in range(1, 9):
        f = (i / 9.0) ** 2.1
        yy = int(hor + f * (h - hor))
        d.line((0, yy, w, yy), fill=accent2 + (int(80 + 160 * (i / 9.0)),), width=2)
    return img


def _draw_anime_scene(img: Image.Image, accent1: tuple[int, int, int], accent2: tuple[int, int, int], idx: int, light: bool) -> Image.Image:
    w, h = img.size
    sx = w * (0.28 + (idx % 5) * 0.11)
    sy = h * (0.34 + (idx % 3) * 0.08)
    _draw_glow_orb(img, sx, sy, h * 0.40, accent1, alpha=130)
    _draw_glow_orb(img, w * 0.80, h * 0.25, h * 0.30, accent2, alpha=95)
    d = ImageDraw.Draw(img, "RGBA")
    sr = int(h * 0.19)
    d.ellipse((sx - sr, sy - sr, sx + sr, sy + sr), fill=(255, 242, 230, 235))
    # Distant layered anime mountains + cloud puffs
    sil1 = (46, 36, 72, 130) if not light else (142, 118, 152, 95)
    sil2 = (22, 18, 42, 200) if not light else (105, 86, 124, 130)
    d.polygon([(0, h), (0, h * 0.66), (w * 0.24, h * 0.48), (w * 0.52, h * 0.68), (w * 0.78, h * 0.50), (w, h * 0.64), (w, h)], fill=sil1)
    d.polygon([(0, h), (0, h * 0.78), (w * 0.35, h * 0.64), (w * 0.66, h * 0.78), (w, h * 0.68), (w, h)], fill=sil2)
    # Soft drifting sakura petals + bokeh
    rng = random.Random(f"anime_scn:{idx}")
    for _ in range(18):
        px, py = rng.uniform(0, w), rng.uniform(0, h)
        s = rng.uniform(6, 14)
        d.polygon([(px, py), (px + s, py + s * 0.4), (px + s * 0.5, py + s * 1.1), (px - s * 0.3, py + s * 0.6)],
                  fill=(255, 190, 212, rng.randint(120, 215)))
    return img


def build_backdrop(theme: dict, w: int, h: int) -> Image.Image:
    """Renders the family-authentic high-craft backdrop for static.webp & preview cards."""
    fam = theme["family"]
    idx = theme["idx"]
    light = theme["is_light"]
    sem = theme["semantic"]
    top, bot = theme["bg_stops"]
    c_top, c_bot = _rgb(top), _rgb(bot)
    acc1 = _rgb(sem["surface/enter"])
    acc2 = _rgb(sem["border/spacebar"])
    rng = random.Random(f"bd:{fam}:{idx}")

    if fam == "amoled_minimal":
        return Image.new("RGB", (w, h), (0, 0, 0))

    if fam in ("high_contrast_a11y", "matte_bauhaus"):
        return Image.new("RGB", (w, h), c_top)

    if fam in ("paper_ink", "earth_clay"):
        c_edge = tuple(int(c * 0.85 + a * 0.15) for c, a in zip(c_bot, acc1)) if fam == "earth_clay" else c_bot
        base = _mesh_gradient(w, h, c_top, c_edge, c_bot, c_top)
        if fam == "paper_ink":
            d = ImageDraw.Draw(base, "RGBA")
            for y in range(14 + (idx * 7) % 18, h, 28):
                d.line((0, y, w, y), fill=(80, 68, 52, 22), width=1)
        else:
            ox = w * (0.18 + ((idx * 19) % 8) * 0.09)
            oy = h * (0.24 + (idx % 4) * 0.16)
            _draw_glow_orb(base, ox, oy, h * 0.48, acc1, alpha=82)
            _draw_glow_orb(base, w - ox, h - oy * 0.7, h * 0.36, acc2, alpha=58)
        return base

    if fam == "graphite_pro":
        base = _mesh_gradient(w, h, c_top, tuple(min(255, c + 12) for c in c_top),
                              tuple(max(0, c - 6) for c in c_bot), c_bot)
        d = ImageDraw.Draw(base, "RGBA")
        for y in range(0, h, 6):
            d.line((0, y, w, y), fill=(255, 255, 255, 7))
        return base

    if fam in ("pastel_soft", "gradient_calm"):
        c3 = from_hsv((rng.random() * 0.35 + (idx * 0.19)) % 1.0, 0.32 if light else 0.45, 0.94 if light else 0.28)
        c4 = from_hsv(((idx * 0.31) + 0.52) % 1.0, 0.28 if light else 0.48, 0.90 if light else 0.24)
        base = _mesh_gradient(w, h, c_top, c3, c4, c_bot)
        _draw_glow_orb(base, w * 0.28, h * 0.30, h * 0.42, acc1, alpha=48 if light else 62)
        return base

    if fam in ("liquid_glass", "glass_matte"):
        c3 = acc1
        c4 = acc2
        base = _mesh_gradient(w, h, c_top, c_bot, c_top, c_bot)
        _draw_glow_orb(base, w * (0.25 + (idx % 4) * 0.14), h * 0.32, h * 0.48, c3, alpha=145 if not light else 115)
        _draw_glow_orb(base, w * (0.76 - (idx % 3) * 0.12), h * 0.68, h * 0.46, c4, alpha=135 if not light else 105)
        _draw_glow_orb(base, w * 0.50, h * 0.18, h * 0.34, from_hsv((idx * 0.27) % 1.0, 0.65, 0.95), alpha=85)
        return base

    if fam in ("neon_night", "gaming_rgb"):
        base = Image.new("RGB", (w, h), (3, 4, 9))
        cx, cy = w * 0.5, h * 0.48
        _draw_glow_orb(base, cx, cy, h * 0.48, acc1, alpha=110)
        _draw_glow_orb(base, w * (0.22 + (idx % 4) * 0.18), h * 0.72, h * 0.36, acc2, alpha=95)
        d = ImageDraw.Draw(base, "RGBA")
        # Neon LED circular halo rings / laser underglow bar
        r = min(w, h) * 0.40
        d.ellipse((cx - r, cy - r, cx + r, cy + r), outline=acc1 + (175,), width=max(2, int(w * 0.005)))
        d.ellipse((cx - r * 0.76, cy - r * 0.76, cx + r * 0.76, cy + r * 0.76), outline=acc2 + (145,), width=max(2, int(w * 0.004)))
        if fam == "gaming_rgb":
            # Bottom RGB desk light bar glow
            d.rectangle((w * 0.08, h * 0.91, w * 0.92, h * 0.96), fill=acc1 + (190,))
        return base

    if fam == "cyber_hybrid_matte":
        sky_top = tuple(min(255, max(8, c + 12)) for c in c_top)
        sky_mid = tuple(int(c * 0.5 + a * 0.5) for c, a in zip(c_bot, acc2))
        base = _mesh_gradient(w, h, sky_top, sky_mid, acc1, c_bot)
        return _draw_synthwave_grid(base, acc1, acc2, idx)

    if fam == "anime_matte":
        base = _mesh_gradient(w, h, c_top, acc2 if not light else c_bot, c_bot, acc1 if light else (38, 20, 56))
        return _draw_anime_scene(base, acc1, acc2, idx, light)

    if fam == "bangla_heritage":
        mode = idx % 5
        tint = from_hsv((idx * 0.19) % 1.0, 0.52, 0.24)
        if mode == 0:
            base = _mesh_gradient(w, h, (8, 34, 24), tint, (6, 22, 16), (12, 30, 20))
            return _draw_smriti_soudho(base, idx)
        if mode == 1:
            base = _mesh_gradient(w, h, (10, 20, 56), tint, (16, 28, 68), (8, 16, 44))
            return _draw_eid_lantern(base, idx)
        if mode == 2:
            base = _mesh_gradient(w, h, (16, 18, 46), tint, (12, 28, 42), (18, 12, 28))
            return _draw_rickshaw_mandala(base, idx)
        if mode == 3:
            c_m = (24, 38, 66) if not light else (240, 232, 214)
            base = Image.new("RGB", (w, h), c_m)
            base = _draw_jamdani_weave(base, idx)
            if light:
                d = ImageDraw.Draw(base, "RGBA")
                d.rectangle((0, 0, w, h), fill=(250, 245, 234, 60))
            return base
        sky2 = from_hsv((0.88 + idx * 0.11) % 1.0, 0.65, 0.58)
        base = _mesh_gradient(w, h, (38, 22, 62), tint, sky2, (22, 20, 44))
        return _draw_river_nouka(base, idx)

    if fam == "kawaii_cute":
        base = _mesh_gradient(w, h, (226, 244, 255), (255, 232, 244), (242, 252, 236), (222, 238, 255))
        d = ImageDraw.Draw(base, "RGBA")
        for k in range(10):
            bx = rng.uniform(0.08 * w, 0.92 * w)
            by = rng.uniform(0.10 * h, 0.88 * h)
            br = rng.uniform(h * 0.08, h * 0.22)
            col = acc1 if k % 2 == 0 else (56, 178, 248)
            d.ellipse((bx - br, by - br, bx + br, by + br), fill=col + (55,), outline=(255, 255, 255, 210), width=3)
            d.arc((bx - br * 0.7, by - br * 0.7, bx + br * 0.7, by + br * 0.7), 205, 260, fill=(255, 255, 255, 235), width=4)
        return base

    if fam == "seasonal":
        season = idx % 3
        if season == 0:  # Monsoon rain mist (per-idx sky hue + orb focal shift)
            rain_tint = from_hsv((idx * 0.17 + 0.45) % 1.0, 0.35 if light else 0.45, 0.88 if light else 0.28)
            base = _mesh_gradient(w, h, c_top, rain_tint,
                                  (12, 26, 36) if not light else (196, 218, 226),
                                  acc1 if not light else _rgb(sem["surface/key-pressed"]))
            _draw_glow_orb(base, w * (0.22 + ((idx * 13) % 7) * 0.10), h * 0.34, h * 0.42, acc1, alpha=78)
            d = ImageDraw.Draw(base, "RGBA")
            for _ in range(42):
                rx, ry = rng.uniform(0, w), rng.uniform(0, h)
                d.line((rx, ry, rx - 14, ry + 34), fill=(220, 245, 255, 65 if not light else 95), width=2)
            return base
        if season == 1:  # Mustard gold field
            base = _mesh_gradient(w, h, c_top, (224, 198, 92) if light else (62, 54, 18), c_bot, (205, 168, 48) if light else (42, 36, 12))
            _draw_glow_orb(base, w * (0.25 + (idx % 5) * 0.12), h * 0.3, h * 0.36, (255, 224, 96), alpha=95)
            return base
        base = _mesh_gradient(w, h, c_top, acc1, c_bot, acc2)
        _draw_glow_orb(base, w * (0.28 + (idx % 4) * 0.14), h * 0.50, h * 0.38, acc1, alpha=85)
        return base

    if fam == "luxury_gold":
        stone_pal = [
            ((14, 12, 18), (28, 22, 34), (212, 175, 55)),
            ((8, 28, 20), (16, 48, 34), (230, 199, 103)),
            ((10, 18, 38), (22, 36, 68), (244, 201, 93)),
            ((32, 10, 22), (52, 18, 36), (240, 166, 202)),
            ((26, 18, 14), (44, 30, 22), (226, 180, 106)),
            ((20, 24, 30), (36, 42, 52), (203, 213, 225)),
        ]
        s_a, s_b, vein = stone_pal[idx % len(stone_pal)]
        base = _mesh_gradient(w, h, s_a, s_b, s_b, s_a)
        _draw_glow_orb(base, w * (0.25 + ((idx * 13) % 7) * 0.08), h * (0.28 + (idx % 3) * 0.2), h * 0.42, vein, alpha=70)
        d = ImageDraw.Draw(base, "RGBA")
        for k in range(5):
            pts = []
            y0 = h * (0.12 + k * 0.18)
            for s in range(16):
                t = s / 15.0
                pts.append((t * w, y0 + math.sin(t * 7.0 + idx * 1.3 + k) * h * 0.09 + math.cos(t * 13.0 + idx) * h * 0.04))
            d.line(pts, fill=vein + (110 - k * 12,), width=2)
        return base

    if fam == "retro_terminal":
        base = Image.new("RGB", (w, h), c_top)
        gx = w * (0.28 + ((idx * 17) % 7) * 0.08)
        gy = h * (0.32 + (idx % 3) * 0.16)
        _draw_glow_orb(base, gx, gy, h * 0.44, acc1, alpha=58)
        d = ImageDraw.Draw(base, "RGBA")
        step = 5 + (idx % 4)
        for y in range(idx % 5, h, step):
            d.line((0, y, w, y), fill=(0, 0, 0, 72))
        return base

    if fam == "pixel_8bit":
        base = Image.new("RGB", (w, h), c_top)
        d = ImageDraw.Draw(base, "RGBA")
        cell = max(16, int(w / 24))
        for gx in range(0, w, cell):
            for gy in range(0, h, cell):
                if (gx // cell + gy // cell + idx) % 5 == 0:
                    d.rectangle((gx + 2, gy + 2, gx + cell - 2, gy + cell - 2),
                                fill=acc1 + (28 if not light else 22,))
        return base

    # live_animated default deep aurora mesh wall (varied orb anchors + tri-color mix)
    c_mid = from_hsv((idx * 0.29 + 0.14) % 1.0, 0.56 if light else 0.72, 0.84 if light else 0.42)
    base = _mesh_gradient(w, h, c_top, c_mid, c_bot, acc2)
    ox1 = w * (0.20 + ((idx * 17) % 7) * 0.10)
    oy1 = h * (0.28 + (idx % 3) * 0.18)
    ox2 = w * (0.80 - ((idx * 13) % 7) * 0.09)
    _draw_glow_orb(base, ox1, oy1, h * 0.48, acc1, alpha=135)
    _draw_glow_orb(base, ox2, h - oy1 * 0.7, h * 0.44, acc2, alpha=118)
    return base


def build_live_webp(theme: dict, static_img: Image.Image, w: int = 720, h: int = 480,
                    frames: int = 16, duration_ms: int = 95) -> bytes:
    """Renders seamless multi-frame animated WebP loop layered over static_img."""
    fam = theme["family"]
    idx = theme["idx"]
    sem = theme["semantic"]
    rng = random.Random(f"liveanim:{theme['id']}")
    base = static_img.resize((w, h), Image.LANCZOS).convert("RGBA")
    acc1 = _rgb(sem["surface/enter"])
    acc2 = _rgb(sem["border/spacebar"])

    # Determine animation flavor per family
    if fam == "anime_matte" or fam == "kawaii_cute":
        petals = [(rng.uniform(0, w), rng.uniform(0, h), rng.uniform(18, 42), rng.uniform(-16, 16),
                   rng.uniform(5, 11), rng.uniform(0, 2 * math.pi)) for _ in range(28)]
        mode = "petals"
    elif fam == "neon_night" or (fam == "live_animated" and idx % 3 == 2):
        mode = "shockwave"
    elif fam == "seasonal" or (fam == "live_animated" and idx % 3 == 1):
        stars = [(rng.uniform(0, w), rng.uniform(0, h), rng.uniform(2, 5.5), rng.uniform(0, 2 * math.pi),
                  rng.randint(100, 220)) for _ in range(44)]
        mode = "sparkles"
    else:
        mode = "aurora"

    seq = []
    cx0, cy0 = w * 0.5, h * 0.5
    for f in range(frames):
        phase = 2.0 * math.pi * f / frames
        fr = base.copy()
        ov = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        d = ImageDraw.Draw(ov, "RGBA")
        if mode == "aurora":
            # Rotating twin aurora orbs
            ax = w * (0.5 + 0.30 * math.sin(phase))
            ay = h * (0.48 + 0.24 * math.cos(phase))
            bx = w * (0.5 - 0.28 * math.cos(phase))
            by = h * (0.52 + 0.22 * math.sin(phase))
            for k in range(4, 0, -1):
                rr1 = w * 0.28 * k / 4.0
                rr2 = w * 0.24 * k / 4.0
                d.ellipse((ax - rr1, ay - rr1, ax + rr1, ay + rr1), fill=acc1 + (int(28 * (5 - k)),))
                d.ellipse((bx - rr2, by - rr2, bx + rr2, by + rr2), fill=acc2 + (int(24 * (5 - k)),))
            ov = ov.filter(ImageFilter.GaussianBlur(24))
        elif mode == "shockwave":
            for wave_i in range(2):
                p = ((f / frames) + wave_i * 0.5) % 1.0
                r = max(20.0, p * max(w, h) * 0.58)
                a = int(170 * (1.0 - p))
                col = acc1 if wave_i == 0 else acc2
                d.ellipse((cx0 - r, cy0 - r, cx0 + r, cy0 + r), outline=col + (a,), width=4)
        elif mode == "petals":
            for x0, y0, vy, vx, sz, ph in petals:
                px = (x0 + vx * (f / frames) * 4 + math.sin(phase + ph) * 14) % w
                py = (y0 + vy * (f / frames) * 6) % (h + 20) - 10
                col = (255, 204, 222, 195) if fam == "anime_matte" else (255, 255, 255, 190)
                d.ellipse((px - sz, py - sz * 0.65, px + sz, py + sz * 0.65), fill=col)
        else:
            for x0, y0, sz, ph, base_a in stars:
                tw = 0.35 + 0.65 * (0.5 + 0.5 * math.sin(phase + ph))
                col = (255, 246, 200, int(base_a * tw)) if not theme["is_light"] else acc1 + (int(base_a * tw * 0.8),)
                d.ellipse((x0 - sz, y0 - sz, x0 + sz, y0 + sz), fill=col)
        fr.alpha_composite(ov)
        seq.append(fr.convert("RGB"))

    buf = io.BytesIO()
    seq[0].save(buf, "WEBP", save_all=True, append_images=seq[1:], duration=duration_ms, loop=0, quality=72, method=4)
    return buf.getvalue()
