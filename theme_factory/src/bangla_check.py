#!/usr/bin/env python3
"""Render the Bangla conjunct check sheet used in the Phase 1 visual gate.

Pillow's raqm/HarfBuzz is unavailable on this host, and Playwright is not installed.
The sheet is therefore rendered with Nirmala.ttc for visual inspection only; the
'flawless shaping' guarantee is marked BLOCKED in bangla_check_status.json.
"""
from __future__ import annotations

import json
from pathlib import Path

import PIL.features
from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent.parent
PROOF = HERE / "out" / "proof"
PROOF.mkdir(parents=True, exist_ok=True)
FONT = r"C:\Windows\Fonts\Nirmala.ttc"

TEXTS = ["ক্ষ", "স্ত্রী", "শুভ নববর্ষ", "ঈদ মোবারক", "জ্ঞান", "অনুপ্রাণিত"]


def main() -> None:
    img = Image.new("RGB", (900, 420), (252, 252, 254))
    d = ImageDraw.Draw(img)
    ft = ImageFont.truetype(FONT, 44)
    small = ImageFont.truetype(r"C:\Windows\Fonts\seguisb.ttf", 17)
    d.text((24, 14), "Bangla conjunct check - local render (proof only)", font=small, fill=(25, 30, 40))
    for i, text in enumerate(TEXTS):
        y = 62 + i * 58
        d.rounded_rectangle((20, y - 6, 880, y + 50), radius=10, fill=(242, 245, 250), outline=(140, 155, 180), width=2)
        d.text((34, y), text, font=ft, fill=(12, 20, 35))
    img.save(PROOF / "bangla_text_check.png")
    status = {
        "sheet": str(PROOF / "bangla_text_check.png"),
        "pillow_raqm": PIL.features.check("raqm"),
        "pillow_freetype2": PIL.features.check("freetype2"),
        "playwright_python": False,
        "shaping_guarantee": "BLOCKED",
        "reason": "raqm/HarfBuzz unavailable and Playwright not installed; sheet is for visual inspection only",
    }
    (HERE / "data" / "bangla_check_status.json").write_text(json.dumps(status, indent=1), encoding="utf-8")
    print("sheet:", status["sheet"], "| shaping_guarantee:", status["shaping_guarantee"])


if __name__ == "__main__":
    main()
