"""Pure, testable quality gates for sticker assets."""
from __future__ import annotations

from pathlib import Path

from PIL import Image


def dhash(path, hash_size=16):
    img = Image.open(path).convert("L").resize((hash_size + 1, hash_size), Image.LANCZOS)
    px = list(img.getdata())
    bits = 0
    for row in range(hash_size):
        for col in range(hash_size):
            bits = (bits << 1) | (1 if px[row * (hash_size + 1) + col] > px[row * (hash_size + 1) + col + 1] else 0)
    return bits


def hamming(a, b):
    return (a ^ b).bit_count()


def check_sticker(path: Path, max_bytes=100 * 1024, margin=16):
    p = Path(path)
    if not p.exists():
        return {"ok": False, "reason": "missing"}
    if p.stat().st_size > max_bytes:
        return {"ok": False, "reason": "too large"}
    with Image.open(p) as im:
        if im.size != (512, 512):
            return {"ok": False, "reason": "size != 512x512"}
        if im.mode != "RGBA":
            return {"ok": False, "reason": "mode != RGBA"}
        a = im.getchannel("A")
        for x, y in ((0, 0), (511, 0), (0, 511), (511, 511)):
            if a.getpixel((x, y)) != 0:
                return {"ok": False, "reason": "corner alpha != 0"}
        # margin check: no opaque pixels inside the outer 16px band
        for y in range(margin):
            for x in range(512):
                if a.getpixel((x, y)) != 0:
                    return {"ok": False, "reason": "top margin occupied"}
        for y in range(512 - margin, 512):
            for x in range(512):
                if a.getpixel((x, y)) != 0:
                    return {"ok": False, "reason": "bottom margin occupied"}
    return {"ok": True, "bytes": p.stat().st_size}
