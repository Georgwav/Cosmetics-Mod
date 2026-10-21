#!/usr/bin/env python3
"""Sol Styles' own 3D models (B.2.4): a sword, a totem and a crystal, as the Model choice of their parts.

Each is a Java item model (Blockbench's "Java block/item" format: cuboid elements with their own UVs), so the game
draws it itself on every version: in your hand, in the hotbar, dropped and in item frames. This script is their
source of truth; it writes, for each model:

- src/styles/models/<id>.bbmodel: the Blockbench project (open and edit it in Blockbench, then run this again or
  export the Java model over the one below);
- src/main/resources/assets/cosmeticsmod/styles/models/<id>.json: the Java model;
- src/main/resources/assets/cosmeticsmod/textures/item/styles/<id>.png: its 16 x 16 texture;

and src/main/resources/assets/cosmeticsmod/styles/models/index.json: which part each model is for.

Stdlib only (its own PNG writer). Run from the repository root: python3 tools/sol-assets/make_style_models.py
"""

import base64
import json
import struct
import uuid
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
    """A 16 x 16 texture painted in regions: each region a colour with a light top-left and a darker bottom-right."""

    def __init__(self):
        self.px = [0] * 256

    def region(self, x0, y0, x1, y1, c, dither=True):
        for y in range(y0, y1):
            for x in range(x0, x1):
                k = 1.0
                if x == x0 or y == y0:
                    k = 1.18
                elif x == x1 - 1 or y == y1 - 1:
                    k = 0.78
                elif dither and (x * 7 + y * 13) % 5 == 0:
                    k = 0.92
                self.px[y * 16 + x] = shade(c, k)
        return (x0, y0, x1, y1)


def cube(name, frm, to, uv, rotation=None):
    """An element whose six faces all take the same texture region."""
    faces = {f: {"uv": list(uv), "texture": "#0"} for f in ("north", "south", "east", "west", "up", "down")}
    e = {"name": name, "from": frm, "to": to, "faces": faces}
    if rotation:
        e["rotation"] = rotation
    return e


# The flat items' display, as the game's own handheld and generated items have it, so the models sit in the hand
# where the item did.
HANDHELD = {
    "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4, 0.5], "scale": [0.85, 0.85, 0.85]},
    "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 4, 0.5], "scale": [0.85, 0.85, 0.85]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
    "fixed": {"rotation": [0, 180, 0], "scale": [1, 1, 1]},
    "gui": {"rotation": [12, -24, 0], "scale": [0.95, 0.95, 0.95]},
}
GENERATED = {
    "thirdperson_righthand": {"translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
    "thirdperson_lefthand": {"translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
    "fixed": {"rotation": [0, 180, 0], "scale": [1, 1, 1]},
    "gui": {"rotation": [18, -30, 0], "scale": [0.9, 0.9, 0.9]},
}


def sword():
    t = Tex()
    blade = t.region(0, 0, 4, 12, 0xFFE8F4FF)
    edge = t.region(4, 0, 6, 12, 0xFF8FD8FF)
    guard = t.region(6, 0, 12, 3, 0xFFFFC84A)
    grip = t.region(6, 4, 9, 12, 0xFF5A3A2A)
    gem = t.region(10, 4, 13, 7, 0xFF39E6FF, dither=False)
    pommel = t.region(10, 8, 14, 11, 0xFFFFC84A)
    rot = {"angle": -45, "axis": "z", "origin": [8, 8, 8]}
    els = [
        cube("blade", [7, 6, 7.5], [9, 16, 8.5], blade, rot),
        cube("edge", [9, 7, 7.75], [9.5, 15, 8.25], edge, rot),
        cube("tip", [7.5, 16, 7.6], [8.5, 17, 8.4], edge, rot),
        cube("guard", [5, 5, 7], [11, 6, 9], guard, rot),
        cube("gem", [7.5, 5, 6.75], [8.5, 6, 9.25], gem, rot),
        cube("grip", [7.5, 1.5, 7.6], [8.5, 5, 8.4], grip, rot),
        cube("pommel", [7, 0.5, 7.25], [9, 1.5, 8.75], pommel, rot),
    ]
    return t, els, HANDHELD


def totem():
    t = Tex()
    body = t.region(0, 0, 6, 8, 0xFFE6C34A)
    face = t.region(6, 0, 12, 5, 0xFFF2D67A)
    eye = t.region(12, 0, 14, 2, 0xFF2BD96B, dither=False)
    wing = t.region(0, 8, 6, 12, 0xFFC99A2E)
    band = t.region(6, 6, 12, 8, 0xFF2BD96B)
    els = [
        cube("body", [5, 2, 7], [11, 9, 9], body),
        cube("belt", [4.75, 5, 6.75], [11.25, 6, 9.25], band),
        cube("head", [5, 9, 6.5], [11, 14, 9.5], face),
        cube("eye_left", [6, 11, 9.5], [7.5, 12, 9.75], eye),
        cube("eye_right", [8.5, 11, 9.5], [10, 12, 9.75], eye),
        cube("wing_left", [2, 4, 7.5], [5, 8, 8.5], wing),
        cube("wing_right", [11, 4, 7.5], [14, 8, 8.5], wing),
        cube("feet", [5.5, 1, 7.25], [10.5, 2, 8.75], wing),
    ]
    return t, els, GENERATED


def crystal():
    t = Tex()
    frame = t.region(0, 0, 4, 4, 0xFFE9E2FF)
    core = t.region(4, 0, 10, 6, 0xFFE040C8)
    glow = t.region(10, 0, 14, 4, 0xFFFF9BEA, dither=False)
    base = t.region(0, 6, 8, 10, 0xFF2A2236)
    els = []
    lo, hi, w = 4.0, 12.0, 0.75
    # The frame's twelve edges, turned 45 degrees about the vertical.
    rot = {"angle": 45, "axis": "y", "origin": [8, 8, 8]}
    for (a, b) in [((lo, lo, lo), (hi, lo + w, lo + w)), ((lo, hi - w, lo), (hi, hi, lo + w)), ((lo, lo, hi - w), (hi, lo + w, hi)),
                   ((lo, hi - w, hi - w), (hi, hi, hi)), ((lo, lo, lo), (lo + w, hi, lo + w)), ((hi - w, lo, lo), (hi, hi, lo + w)),
                   ((lo, lo, hi - w), (lo + w, hi, hi)), ((hi - w, lo, hi - w), (hi, hi, hi)), ((lo, lo, lo), (lo + w, lo + w, hi)),
                   ((hi - w, lo, lo), (hi, lo + w, hi)), ((lo, hi - w, lo), (lo + w, hi, hi)), ((hi - w, hi - w, lo), (hi, hi, hi))]:
        els.append(cube("frame", list(a), list(b), frame, rot))
    els.append(cube("core", [6, 6, 6], [10, 10, 10], core, {"angle": 22.5, "axis": "x", "origin": [8, 8, 8]}))
    els.append(cube("glow", [7, 7, 7], [9, 9, 9], glow))
    els.append(cube("base", [3, 0, 3], [13, 2, 13], base))
    return t, els, GENERATED


MODELS = {"sol_sword": ("swords", sword), "sol_totem": ("totem", totem), "sol_crystal": ("crystal", crystal)}


def bbmodel(name, els, display, tex_png):
    out = {
        "meta": {"format_version": "4.10", "model_format": "java_block", "box_uv": False},
        "name": name,
        "resolution": {"width": 16, "height": 16},
        "elements": [],
        "outliner": [],
        "textures": [{"name": name + ".png", "id": "0", "width": 16, "height": 16, "uv_width": 16, "uv_height": 16,
                      "uuid": str(uuid.uuid5(uuid.NAMESPACE_URL, name + "/texture")),
                      "source": "data:image/png;base64," + base64.b64encode(tex_png).decode()}],
        "display": display,
    }
    for i, e in enumerate(els):
        rid = str(uuid.uuid5(uuid.NAMESPACE_URL, f"{name}/{i}"))
        rot = e.get("rotation")
        rotation = [0, 0, 0]
        origin = [8, 8, 8]
        if rot:
            rotation["xyz".index(rot["axis"])] = rot["angle"]
            origin = rot["origin"]
        out["elements"].append({"name": e["name"], "type": "cube", "uuid": rid, "from": e["from"], "to": e["to"], "origin": origin,
                                "rotation": rotation,
                                "faces": {f: {"uv": v["uv"], "texture": 0} for f, v in e["faces"].items()}})
        out["outliner"].append(rid)
    return out


def main():
    index = {}
    for mid, (part, make) in MODELS.items():
        tex, els, display = make()
        data = png(16, 16, tex.px)
        texture = f"cosmeticsmod:item/styles/{mid}"
        model = {"credit": "Sol Client, made with tools/sol-assets/make_style_models.py", "texture_size": [16, 16],
                 "textures": {"0": texture, "particle": texture}, "elements": els, "display": display}
        (RES / "styles/models").mkdir(parents=True, exist_ok=True)
        (RES / "textures/item/styles").mkdir(parents=True, exist_ok=True)
        (ROOT / "src/styles/models").mkdir(parents=True, exist_ok=True)
        (RES / f"styles/models/{mid}.json").write_text(json.dumps(model, indent=2) + "\n")
        (RES / f"textures/item/styles/{mid}.png").write_bytes(data)
        (ROOT / f"src/styles/models/{mid}.bbmodel").write_text(json.dumps(bbmodel(mid, els, display, data), indent=2) + "\n")
        index.setdefault(part, []).append(mid)
    (RES / "styles/models/index.json").write_text(json.dumps(index, indent=2) + "\n")
    print("wrote", ", ".join(MODELS))


if __name__ == "__main__":
    main()
