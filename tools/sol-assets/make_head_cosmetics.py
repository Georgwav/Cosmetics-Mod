#!/usr/bin/env python3
"""Sol's horns and cat ears (B.2.10): two head cosmetics of Sol's own, in the cosmetic format's cubes.

Writes src/main/resources/assets/cosmeticsmod/cosmetics/{horns,cat_ears}.json and their 32 x 16 textures in
textures/cosmetic/. Each cube has its own box-UV place on the texture, painted with a lit top and a darker side, so
the tint, rainbow and colour settings work on them like on every other cosmetic. Stdlib only; run from the root.
"""

import json
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "src/main/resources/assets/cosmeticsmod"


def png(w, h, px):
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
    raw = b"".join(b"\x00" + bytes(c for p in px[y * w:(y + 1) * w] for c in ((p >> 16) & 255, (p >> 8) & 255, p & 255, (p >> 24) & 255))
                   for y in range(h))
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")


def shade(c, k):
    r, g, b = (c >> 16) & 255, (c >> 8) & 255, c & 255
    return 0xFF000000 | min(255, int(r * k)) << 16 | min(255, int(g * k)) << 8 | min(255, int(b * k))


class Tex:
    def __init__(self, w=32, h=16):
        self.w, self.h = w, h
        self.px = [0] * (w * h)

    def fill(self, x0, y0, w, h, c):
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                if 0 <= x < self.w and 0 <= y < self.h:
                    self.px[y * self.w + x] = c

    def box(self, u, v, w, h, d, side, top=None, front=None):
        """Paints a cube's box UV: top and bottom, the four sides; the front (north) may differ."""
        top = top if top is not None else shade(side, 1.2)
        self.fill(u + d, v, w, d, top)
        self.fill(u + d + w, v, w, d, shade(side, 0.7))
        self.fill(u, v + d, d, h, shade(side, 0.85))
        self.fill(u + d, v + d, w, h, front if front is not None else side)
        self.fill(u + d + w, v + d, d, h, shade(side, 0.85))
        self.fill(u + 2 * d + w, v + d, w, h, shade(side, 0.8))


def cube(frm, size, uv, rot=None, pivot=None):
    c = {"from": frm, "to": [frm[0] + size[0], frm[1] + size[1], frm[2] + size[2]], "uv": uv}
    if rot:
        c["rotation"] = rot
        c["pivot"] = pivot
    return c


def horns():
    t = Tex()
    bone, mid, tip = 0xFFEDE2CF, 0xFFCDB89A, 0xFF7A6650
    # Base 2x2x2 at (0,0), middle 2x2x2 at (8,0), tip 1x2x1 at (16,0); the right horn mirrors the left's UV.
    t.box(0, 0, 2, 2, 2, bone)
    t.box(8, 0, 2, 2, 2, mid)
    t.box(16, 0, 1, 2, 1, tip)
    parts = []
    for side in (1, -1):
        x = 2.5 if side > 0 else -4.5
        cubes = [
            cube([x, -9.5, -1], [2, 2, 2], [0, 0], [0, 0, 18 * side], [x + 1, -8.5, 0]),
            cube([x + 0.9 * side, -11.2, -0.5], [2, 2, 2], [8, 0], [-12, 0, 36 * side], [x + 1 + 0.9 * side, -10.2, 0.5]),
            cube([x + 2.0 * side + (0.5 if side > 0 else 0.5), -13, 0.2], [1, 2, 1], [16, 0], [-22, 0, 52 * side],
                 [x + 2.5 * side + 1, -12, 0.7]),
        ]
        for c in cubes:
            if side < 0:
                c["mirror"] = True
        parts.append({"name": "left" if side > 0 else "right", "cubes": cubes})
    cos = {"name": "Horns", "slot": "head", "texture": "cosmeticsmod:textures/cosmetic/horns.png", "texture_size": [32, 16],
           "parts": parts}
    return "horns", cos, t


def cat_ears():
    t = Tex()
    fur, inner = 0xFF3B3540, 0xFFF29BB8
    # Each ear: base 3x1x1, middle 2x1x1, tip 1x1x1; the front (facing out) pink inside.
    t.box(0, 0, 3, 1, 1, fur, front=inner)
    t.box(0, 4, 2, 1, 1, fur, front=inner)
    t.box(0, 8, 1, 1, 1, fur)
    parts = []
    for side in (1, -1):
        x = 1 if side > 0 else -4
        tilt = [0, 0, 12 * side]
        pivot = [x + 1.5, -8, -0.5]
        cubes = [
            cube([x, -9, -1.5], [3, 1, 1], [0, 0], tilt, pivot),
            cube([x + 0.5, -10, -1.5], [2, 1, 1], [0, 4], tilt, pivot),
            cube([x + 1, -11, -1.5], [1, 1, 1], [0, 8], tilt, pivot),
        ]
        parts.append({"name": "left" if side > 0 else "right", "cubes": cubes})
    cos = {"name": "Cat Ears", "slot": "head", "texture": "cosmeticsmod:textures/cosmetic/cat_ears.png", "texture_size": [32, 16],
           "parts": parts}
    return "cat_ears", cos, t


def main():
    for make in (horns, cat_ears):
        cid, cos, tex = make()
        (RES / f"cosmetics/{cid}.json").write_text(json.dumps(cos, indent=2) + "\n")
        (RES / f"textures/cosmetic/{cid}.png").write_bytes(png(tex.w, tex.h, tex.px))
        print("wrote", cid)


if __name__ == "__main__":
    main()
