"""21 theme families. Counts sum to 500; light themes >= 160.

Each family has a concrete world/material (no AI-template look) and exactly one bright
focal point. `effect` values MUST be inside the app's normalizeEffectMode whitelist
(glass / motion / live / 3d_live_motion / touch / neon / aurora / holographic /
parallax / ripple) so every theme ships real runtime animation — no silent
"falls back to glass" values.

Capability coverage requested by the user:
- Live/Animated  -> `live_animated` family (procedural animated WebP per theme)
- Liquid Glass   -> `liquid_glass` family (frosted translucent keys, glass_rim_glow)
- Motion/Parallax, Particles, Press Effect -> per-variant `motion` attributes
  distributed across ALL families (parallaxDp / particles / pressEffect) in the
  generator, so every capability appears inside the 500 plan.

Photo families are BLOCKED until PEXELS_API_KEY is set.
"""
from __future__ import annotations

APP_EFFECTS = {
    "glass", "motion", "live", "3d_live_motion", "touch",
    "neon", "aurora", "holographic", "parallax", "ripple",
}

FAMILIES = [
    # id, count, light, dark, world, focal, radii, strokes, fonts, effect, photo
    ("matte_bauhaus", 26, 10, 16, "Chalk and slate classroom", "Matte key pattern", (4, 6), (0,), ("clean", "doto"), "glass", False),
    ("paper_ink", 27, 27, 0, "Warm newsprint", "Ink hairline", (6, 8), (1.0,), ("elegant", "clean"), "touch", False),
    ("pastel_soft", 31, 31, 0, "Sylhet tea mist", "Soft tonal key", (12, 16), (0,), ("clean",), "ripple", False),
    ("earth_clay", 27, 13, 14, "Clay pot and sand", "Opaque earth key", (6, 12), (0, 1.0), ("clean", "elegant"), "3d_live_motion", False),
    ("amoled_minimal", 23, 0, 23, "Night window", "Enter accent only", (8, 12), (0,), ("inter",), "glass", False),
    ("graphite_pro", 23, 0, 23, "Graphite workshop", "Surface step", (6, 8), (0, 1.0), ("inter",), "3d_live_motion", False),
    ("anime_matte", 40, 18, 22, "Shibuya Sunset Cel Matte", "Cel backdrop + hybrid matte keys", (10, 14), (1.0,), ("clean",), "glass", False),
    ("glass_matte", 22, 0, 22, "Smoked Frosted Glass Matte", "Frosted glass + solid matte pop", (8, 12), (1.0,), ("inter",), "motion", False),
    ("cyber_hybrid_matte", 27, 13, 14, "Mecha Carbon Hybrid Matte", "Dual-tone hybrid matte keycaps", (4, 8), (0, 1.0), ("elegant", "clean"), "parallax", False),
    ("bangla_heritage", 31, 15, 16, "Nakshi Kantha stitch", "Patterned key", (6, 12), (1.0,), ("clean",), "touch", False),
    ("seasonal", 22, 11, 11, "Monsoon and mustard field", "Season key tint", (8, 14), (0, 1.0), ("clean",), "aurora", False),
    ("gradient_calm", 26, 13, 13, "One-hue dawn", "Single-hue tone shift", (10, 16), (0,), ("clean", "inter"), "glass", False),
    ("retro_terminal", 14, 0, 14, "Phosphor terminal", "Mono glow text", (0, 2), (1.0,), ("tech_mono",), "live", False),
    ("pixel_8bit", 13, 7, 6, "Handheld console", "Square pixel key", (0,), (2.0,), ("doto",), "3d_live_motion", False),
    ("neon_night", 22, 0, 22, "Neon signage", "One neon rim", (10, 14), (1.5,), ("inter",), "neon", False),
    ("kawaii_cute", 22, 22, 0, "Paper sticker album", "Soft dot pattern", (16, 24), (0,), ("clean",), "aurora", False),
    ("high_contrast_a11y", 9, 4, 5, "Signal black and white", "Bold hairline", (6,), (2.0,), ("clean",), "glass", False),
    ("gaming_rgb", 18, 0, 18, "Matte obsidian desk", "WASD accent", (4, 8), (1.5,), ("tech_mono",), "neon", False),
    ("luxury_gold", 22, 0, 22, "Old dhaka jeweller", "Gold hairline", (6, 10), (1.0,), ("elegant",), "glass", False),
    ("live_animated", 30, 12, 18, "Breathing aurora and drifting light", "Animated wall", (8, 12), (0,), ("inter",), "motion", False),
    ("liquid_glass", 25, 10, 15, "Frosted pane over soft light", "Glass rim glow", (12, 16), (0,), ("clean",), "glass", False),
]

assert sum(x[1] for x in FAMILIES) == 500
assert sum(x[2] for x in FAMILIES) >= 160
assert all(x[9] in APP_EFFECTS for x in FAMILIES), "effect_mode must match the app whitelist"

PHOTO_FAMILIES = {x[0] for x in FAMILIES if x[10]}
NON_PHOTO_FAMILIES = [x for x in FAMILIES if not x[10]]
ANIMATED_FAMILY = "live_animated"
GLASS_FAMILY = "liquid_glass"
