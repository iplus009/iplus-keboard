"""Gates for the full 500-theme plan. Run: python -m pytest tests -q"""
from __future__ import annotations

import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(HERE / "src"))
from families import FAMILIES  # noqa: E402
from gates import check_manifest, color_grid, dhash, dhash_hue, preview_distance  # noqa: E402

FULL = HERE / "out" / "full" / "themes"
CATALOG = HERE / "data" / "full_themes.json"


def load():
    return json.loads(CATALOG.read_text(encoding="utf-8"))


def test_plan_sums_to_500():
    assert sum(x[1] for x in FAMILIES) == 500


def test_full_count_matches_plan():
    expected = sum(x[1] for x in FAMILIES if not x[10])
    assert len(load()) == expected, f"expected {expected} rendered non-photo themes"


def test_every_manifest_passes_schema_contrast_and_separation():
    failures = []
    for row in load():
        res = check_manifest(FULL / row["id"] / "v1" / "manifest.json")
        if not res["ok"]:
            failures.append((row["id"], res))
    assert not failures, failures[:5]


def test_light_themes_at_least_160():
    lights = sum(1 for r in load() if r["is_light"])
    assert lights >= 160, lights


def test_pairwise_preview_distance():
    rows = load()
    hashes = [
        (r["id"], (dhash(FULL / r["id"] / "v1" / "assets/preview/en_card.png"),
                   dhash_hue(FULL / r["id"] / "v1" / "assets/preview/en_card.png"),
                   color_grid(FULL / r["id"] / "v1" / "assets/preview/en_card.png")))
        for r in rows
    ]
    worst = None
    for i in range(len(hashes)):
        for j in range(i + 1, len(hashes)):
            d = preview_distance(hashes[i][1], hashes[j][1])
            if worst is None or d < worst[0]:
                worst = (d, hashes[i][0], hashes[j][0])
    assert worst[0] >= 18, f"closest pair distance {worst}"


def test_package_sizes_under_600kb():
    big = [(p.name, p.stat().st_size) for p in FULL.glob("*.iplustheme")
           if p.stat().st_size > 600 * 1024]
    assert not big, big[:5]


def test_live_themes_ship_animated_wall():
    rows = [r for r in load() if r["family"] == "live_animated"]
    assert rows, "no live_animated themes in the full batch"
    missing = []
    for r in rows:
        m = json.loads((FULL / r["id"] / "v1" / "manifest.json").read_text(encoding="utf-8"))
        if m["keyboard"].get("motion", {}).get("bgAnimatedAsset") != "assets/bg/live.webp":
            missing.append((r["id"], "manifest"))
        if not (FULL / r["id"] / "v1" / "assets/bg/live.webp").exists():
            missing.append((r["id"], "asset"))
    assert not missing, missing[:5]
