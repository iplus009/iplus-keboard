"""Two-layer design tokens for Keybird themes (Core Colors -> Semantic Roles).

Strict scales from the figma-designer skill: radius in {0,2,4,6,8,12,16,24} dp,
stroke in {0,1.0,1.5,2.0} dp. Neutral ramps are tinted 2-6% toward the world hue.
"""
from __future__ import annotations

import colorsys

RADII = (0, 2, 4, 6, 8, 12, 16, 24)
STROKES = (0, 1.0, 1.5, 2.0)

SEMANTIC_ROLES = (
    "surface/keyboard", "surface/key", "surface/key-pressed", "surface/key-functional",
    "surface/enter", "surface/spacebar", "text/key", "text/key-pressed", "text/hint",
    "text/enter", "border/key", "border/spacebar", "popup/bg", "popup/text", "trail/gesture",
)


def hx(rgb):
    return "#%02X%02X%02X" % tuple(max(0, min(255, int(v))) for v in rgb)


def from_hsv(h, s, v):
    return tuple(int(c * 255) for c in colorsys.hsv_to_rgb(h % 1.0, s, v))


def tint_neutral(hue, lightness, world_hue, tint=0.04):
    """Rotate a neutral slightly toward the world hue (2-6%)."""
    h = (world_hue + (hue - world_hue) * tint) % 1.0
    return from_hsv(h, 0.02 + tint, lightness)


def ramp(world_hue, light):
    """11-step neutral ramp (00 near-white -> 1000 near-black), tinted to world."""
    steps = [0.97, 0.92, 0.86, 0.79, 0.71, 0.62, 0.52, 0.42, 0.31, 0.20, 0.09]
    if not light:
        steps = [0.13, 0.17, 0.22, 0.28, 0.35, 0.43, 0.52, 0.61, 0.72, 0.84, 0.96]
    return [hx(tint_neutral(i / 10, v, world_hue)) for i, v in enumerate(steps)]


def accent_ramp(world_hue, sat=0.72):
    vals = [0.96, 0.91, 0.84, 0.75, 0.65, 0.55, 0.46, 0.38, 0.30, 0.22, 0.14]
    return [hx(from_hsv(world_hue, sat, v)) for i, v in enumerate(vals)]


def yiq_text(bg_hex):
    """Pick whichever of dark/white text has the higher WCAG contrast on bg."""
    dark, white = "#121212", "#FFFFFF"
    return dark if contrast(dark, bg_hex) >= contrast(white, bg_hex) else white


def ensure_separation(fill_hex, bg_hex, target=1.4):
    """Push a fill's lightness away from the keyboard bg until contrast >= target.

    Prevents 'invisible key' failures where the key fill blends into the
    composited background. Tries the natural direction first (away from bg
    luminance); if that saturates at black/white before reaching the target,
    retries the opposite direction and keeps whichever side actually passes.
    """
    if contrast(fill_hex, bg_hex) >= target:
        return fill_hex
    rgb0 = [int(fill_hex.lstrip("#")[i:i + 2], 16) for i in (0, 2, 4)]
    away = 22 if sum(rgb0) >= sum(int(bg_hex.lstrip("#")[i:i + 2], 16) for i in (0, 2, 4)) else -22

    def push(direction):
        rgbv = list(rgb0)
        for _ in range(12):
            rgbv = [max(0, min(255, v + direction)) for v in rgbv]
            cand = "#%02X%02X%02X" % tuple(rgbv)
            if contrast(cand, bg_hex) >= target:
                return cand
        return None

    pushed = push(away)
    if pushed:
        return pushed
    fallback = push(-away)
    return fallback if fallback else fill_hex


def ensure_contrast(bg_hex, fg_hex, target=4.5):
    """Nudge a neutral fill lightness until its label contrast clears target.

    Tries the naive direction first (light fills darken, dark fills lighten);
    if that saturates before reaching the target, retries the opposite
    direction — otherwise mid-gray fills on mid-gray keys never converge.
    """
    def push(start_hex, delta):
        rgbv = [int(start_hex.lstrip("#")[i:i + 2], 16) for i in (0, 2, 4)]
        for _ in range(12):
            if contrast(fg_hex, "#%02X%02X%02X" % tuple(rgbv)) >= target:
                return "#%02X%02X%02X" % tuple(rgbv)
            rgbv = [max(0, min(255, v + delta)) for v in rgbv]
        return None

    rgb0 = [int(bg_hex.lstrip("#")[i:i + 2], 16) for i in (0, 2, 4)]
    if contrast(fg_hex, bg_hex) >= target:
        return bg_hex
    naive = -18 if sum(rgb0) > 382 else 18
    pushed = push(bg_hex, naive)
    if pushed:
        return pushed
    fallback = push(bg_hex, -naive)
    return fallback if fallback else bg_hex


def contrast(a_hex, b_hex):
    def lum(hexv):
        h = hexv.lstrip("#")
        ch = [int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)]
        ch = [c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4 for c in ch]
        return 0.2126 * ch[0] + 0.7152 * ch[1] + 0.0722 * ch[2]
    la, lb = lum(a_hex), lum(b_hex)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)


def semantic(world, light, accent_hue, accent_sat=0.72):
    n = ramp(accent_hue, light)
    a = accent_ramp(accent_hue, accent_sat)
    if light:
        keyboard, key, functional = n[0], n[1], n[2]
        enter, text, hint = a[6], "#121212", "#4A4A52"
        pressed, popup = n[2], n[3]
    else:
        keyboard, key, functional = n[0], n[3], n[2]
        enter, text, hint = a[5], "#F2F2F4", "#9CA3AF"
        pressed, popup = n[5], n[2]
    # Guarantee label contrast over the actual composited key fill.
    text_key = yiq_text(key)
    key = ensure_contrast(key, text_key, 4.5)
    text_key = yiq_text(key)
    text_pressed = yiq_text(pressed)
    pressed = ensure_contrast(pressed, text_pressed, 4.5)
    text_pressed = yiq_text(pressed)
    hint = ensure_contrast(hint, key, 3.0)
    return {
        "world": world,
        "surface/keyboard": keyboard,
        "surface/key": key,
        "surface/key-pressed": pressed,
        "surface/key-functional": functional,
        "surface/enter": enter,
        "surface/spacebar": key,
        "text/key": text_key,
        "text/key-pressed": text_pressed,
        "text/hint": hint,
        "text/enter": yiq_text(enter),
        "border/key": n[7] if light else n[6],
        "border/spacebar": a[6],
        "popup/bg": popup,
        "popup/text": yiq_text(popup),
        "trail/gesture": a[6],
        "core/neutral": n,
        "core/accent": a,
    }
