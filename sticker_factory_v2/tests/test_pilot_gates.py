"""Phase 1 pilot gates for stickers. Run: python -m pytest tests -q"""
from __future__ import annotations

import json
import sys
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(HERE / "src"))
from gates import check_sticker, dhash, hamming  # noqa: E402

CATALOG = HERE / "data" / "pilot_stickers.json"


def packs():
    return json.loads(CATALOG.read_text(encoding="utf-8"))


def sticker_files():
    out = []
    for pack in packs():
        root = HERE / "out" / "stickers" / pack["pack_id"] / "v1" / "assets" / "stickers"
        out.extend(sorted(root.glob("*.webp")))
    return out


def test_three_packs_sixty_stickers():
    assert len(packs()) == 3
    assert sum(p["count"] for p in packs()) == 60
    assert len(sticker_files()) == 60


def test_every_sticker_is_transparent_512_rgba():
    failures = [(str(p), check_sticker(p)) for p in sticker_files() if not check_sticker(p)["ok"]]
    assert not failures, failures[:5]


def test_tray_icons_are_96px_and_small():
    for pack in packs():
        root = HERE / "out" / "stickers" / pack["pack_id"] / "v1" / "assets" / "stickers"
        for icon in sorted(root.glob("*_icon.png")):
            with Image.open(icon) as im:
                assert im.size == (96, 96)
            assert icon.stat().st_size <= 50 * 1024


def test_no_duplicate_stickers():
    hs = [(p.name, dhash(p)) for p in sticker_files()]
    worst = None
    for i in range(len(hs)):
        for j in range(i + 1, len(hs)):
            d = hamming(hs[i][1], hs[j][1])
            if worst is None or d < worst[0]:
                worst = (d, hs[i][0], hs[j][0])
    assert worst[0] >= 10, f"closest pair distance {worst}"


def test_full_50_packs_1000_stickers():
    full_catalog = HERE / "data" / "full_stickers.json"
    if not full_catalog.exists():
        return
    rows = json.loads(full_catalog.read_text(encoding="utf-8"))
    assert len(rows) == 50
    assert sum(p["count"] for p in rows) == 1000
    full_root = HERE / "out" / "full" / "stickers"
    zips = list(full_root.glob("*.iplustheme"))
    assert len(zips) == 50
    webps = list(full_root.rglob("assets/stickers/*.webp"))
    assert len(webps) == 1000
