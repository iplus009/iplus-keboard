"""Phase 1 pilot gates for themes. Run: python -m pytest tests -q"""
from __future__ import annotations

import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(HERE / "src"))
from gates import check_manifest, color_grid, dhash, dhash_hue, preview_distance  # noqa: E402

CATALOG = HERE / "data" / "pilot_themes.json"


def load():
    return json.loads(CATALOG.read_text(encoding="utf-8"))


def test_pilot_count():
    assert len(load()) == 36


def test_every_manifest_passes_schema_and_contrast():
    rows = load()
    failures = []
    for row in rows:
        p = HERE / "out" / "themes" / row["id"] / "v1" / "manifest.json"
        res = check_manifest(p)
        if not res["ok"]:
            failures.append((row["id"], res))
    assert not failures, failures[:5]


def test_key_label_contrast_is_at_least_4_5():
    rows = load()
    for row in rows:
        p = HERE / "out" / "themes" / row["id"] / "v1" / "manifest.json"
        res = check_manifest(p)
        assert res["ok"] and res["key_contrast"] >= 4.5, (row["id"], res)


def test_pairwise_preview_distance():
    rows = load()
    hashes = [
        (r["id"], (dhash(HERE / "out" / "themes" / r["id"] / "v1" / "assets/preview/en_card.png"),
                   dhash_hue(HERE / "out" / "themes" / r["id"] / "v1" / "assets/preview/en_card.png"),
                   color_grid(HERE / "out" / "themes" / r["id"] / "v1" / "assets/preview/en_card.png")))
        for r in rows
    ]
    worst = None
    for i in range(len(hashes)):
        for j in range(i + 1, len(hashes)):
            d = preview_distance(hashes[i][1], hashes[j][1])
            if worst is None or d < worst[0]:
                worst = (d, hashes[i][0], hashes[j][0])
    assert worst[0] >= 18, f"closest pair distance {worst}"


def test_light_themes_present():
    rows = load()
    assert any(r["is_light"] for r in rows)
    assert any(not r["is_light"] for r in rows)
