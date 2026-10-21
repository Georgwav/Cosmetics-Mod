#!/usr/bin/env python3
"""Sol's own pets that follow you (C.4): a dog, a little ghost and a mini you.

Writes, under src/main/resources/assets/cosmeticsmod/:
- cosmetic_models/sol_dog.geo.json and .animation.json, cosmetic_models/sol_ghost.geo.json and .animation.json:
  Bedrock entity models (feet at y = 24, the neck's height, so they stand on the ground in a screen and in the world)
  with their walk, sit and float animations, picked by the pet's own movement (q.is_moving, q.is_sitting);
- textures/cosmetic/sol_dog.png, sol_ghost.png, sol_mini_you.png: their pixel art, every cube on its own box-UV place;
- cosmetics/sol_dog.json, sol_ghost.json, sol_mini_you.json.

The mini you is drawn in the world as a little copy of its owner in their own skin; its cubes here (a small figure)
are what the Wardrobe shows. Stdlib only; run from the repository root.
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
    """A texture with a shelf packer for box-UV places."""

    def __init__(self, w, h):
        self.w, self.h = w, h
        self.px = [0] * (w * h)
        self.x = self.y = self.row = 0

    def fill(self, x0, y0, w, h, c):
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                if 0 <= x < self.w and 0 <= y < self.h:
                    self.px[y * self.w + x] = c

    def put(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y * self.w + x] = c

    def place(self, w, h, d):
        """A free box-UV place for a w x h x d cube: (u, v)."""
        bw, bh = 2 * (w + d), d + h
        if self.x + bw > self.w:
            self.x = 0
            self.y += self.row
            self.row = 0
        u, v = self.x, self.y
        assert v + bh <= self.h, "texture too small"
        self.x += bw
        self.row = max(self.row, bh)
        return u, v

    def box(self, u, v, w, h, d, side, top=None, front=None, bottom=None):
        """Paints a cube's box UV: top and bottom, then west, north (front), east and south."""
        self.fill(u + d, v, w, d, top if top is not None else shade(side, 1.15))
        self.fill(u + d + w, v, w, d, bottom if bottom is not None else shade(side, 0.7))
        self.fill(u, v + d, d, h, shade(side, 0.88))
        self.fill(u + d, v + d, w, h, front if front is not None else side)
        self.fill(u + d + w, v + d, d, h, shade(side, 0.88))
        self.fill(u + 2 * d + w, v + d, w, h, shade(side, 0.82))
        return (u + d, v + d)  # the front face's corner, for details


def cube(origin, size, uv, inflate=0):
    c = {"origin": origin, "size": size, "uv": uv}
    if inflate:
        c["inflate"] = inflate
    return c


def geo(name, w, h, bones):
    return {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": f"geometry.cosmeticsmod.{name}", "texture_width": w, "texture_height": h,
                        "visible_bounds_width": 2, "visible_bounds_height": 2, "visible_bounds_offset": [0, 1, 0]},
        "bones": bones}]}


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    if isinstance(data, (dict, list)):
        path.write_text(json.dumps(data, indent=2) + "\n")
    else:
        path.write_bytes(data)


# ------------------------------------------------------------------ the dog

def dog():
    t = Tex(64, 64)
    fur, light, dark, nose, collar = 0xFFD9A066, 0xFFF2D3A2, 0xFF8C5A2B, 0xFF2B1D16, 0xFFFFB347
    F = 24  # the feet's height in the model (the neck's): it stands on the origin
    bones = [{"name": "dog", "pivot": [0, F, 0]}]

    def part(name, parent, pivot, origin, size, colour, top=None, front=None, bottom=None, detail=None):
        w, h, d = size
        u, v = t.place(w, h, d)
        fu, fv = t.box(u, v, w, h, d, colour, top, front, bottom)
        if detail:
            detail(fu, fv)
        bones.append({"name": name, "parent": parent, "pivot": pivot, "cubes": [cube(origin, size, [u, v])]})

    # The body: 6 wide, 5 tall, 10 long, its belly lighter.
    part("body", "dog", [0, F + 6, 5], [-3, F + 6, -4], [6, 5, 10], fur, top=shade(fur, 1.08), bottom=light)

    def face(fu, fv):
        # Eyes (dark with a light glint), and a lighter muzzle band.
        for ex in (1, 4):
            t.put(fu + ex, fv + 2, nose)
            t.put(fu + ex + 1, fv + 2, 0xFFFFFFFF)
        t.fill(fu + 1, fv + 4, 5, 2, light)

    part("head", "body", [0, F + 9, -4], [-3.5, F + 8, -10], [7, 6, 6], fur, front=fur, detail=face)

    def snout(fu, fv):
        t.fill(fu + 1, fv, 2, 1, nose)

    part("snout", "head", [0, F + 9, -10], [-2, F + 8, -12], [4, 3, 2], light, top=light, detail=snout)
    part("ear_left", "head", [2.5, F + 14, -7], [1.5, F + 14, -8], [2, 2, 1], dark)
    part("ear_right", "head", [-2.5, F + 14, -7], [-3.5, F + 14, -8], [2, 2, 1], dark)
    part("collar", "body", [0, F + 9, -4], [-3.5, F + 7, -4.5], [7, 4, 1], collar, top=shade(collar, 1.1))
    part("tail", "body", [0, F + 10, 6], [-1, F + 9.5, 6], [2, 2, 5], fur, top=light)
    for name, x, z in (("leg_front_left", 1, -3), ("leg_front_right", -3, -3), ("leg_back_left", 1, 3), ("leg_back_right", -3, 3)):
        part(name, "dog", [x + 1, F + 6, z + 1], [x, F, z], [2, 6, 2], fur, bottom=dark)
    write(RES / "textures/cosmetic/sol_dog.png", png(t.w, t.h, t.px))
    write(RES / "cosmetic_models/sol_dog.geo.json", geo("sol_dog", t.w, t.h, bones))

    walk = "math.sin(q.modified_distance_moved * 520) * 38"
    walk_back = "-math.sin(q.modified_distance_moved * 520) * 38"
    anims = {"format_version": "1.8.0", "animations": {
        "walk": {"loop": True, "bones": {
            "leg_front_left": {"rotation": [walk, 0, 0]},
            "leg_back_right": {"rotation": [walk, 0, 0]},
            "leg_front_right": {"rotation": [walk_back, 0, 0]},
            "leg_back_left": {"rotation": [walk_back, 0, 0]},
            "tail": {"rotation": [-30, "math.sin(q.anim_time * 900) * 25", 0]},
            "head": {"rotation": ["math.sin(q.modified_distance_moved * 1040) * 3", 0, 0]},
            "body": {"position": [0, "math.abs(math.sin(q.modified_distance_moved * 520)) * 0.5", 0]}}},
        "sit": {"loop": True, "bones": {
            # The rear down, the front up: the body tips back round the hips, the back legs fold under it.
            "body": {"rotation": [-28, 0, 0], "position": [0, -2.5, 0]},
            "head": {"rotation": [24, 0, 0]},
            "leg_back_left": {"rotation": [-90, 0, 0], "position": [0, -2.5, -2]},
            "leg_back_right": {"rotation": [-90, 0, 0], "position": [0, -2.5, -2]},
            "leg_front_left": {"rotation": [-8, 0, 0]},
            "leg_front_right": {"rotation": [-8, 0, 0]},
            "tail": {"rotation": [10, "math.sin(q.anim_time * 600) * 30", 0]},
            "ear_left": {"rotation": [0, 0, "math.sin(q.anim_time * 90) * 6"]}}},
        "idle": {"loop": True, "bones": {
            "tail": {"rotation": [-20, "math.sin(q.anim_time * 400) * 18", 0]},
            "head": {"rotation": [0, "math.sin(q.anim_time * 40) * 14", 0]}}}}}
    write(RES / "cosmetic_models/sol_dog.animation.json", anims)
    write(RES / "cosmetics/sol_dog.json", {
        "name": "Sol Dog",
        "slot": "pet",
        "pet_position": "follow",
        "texture": "cosmeticsmod:textures/cosmetic/sol_dog.png",
        "model": "cosmeticsmod:sol_dog",
        "model_animations": "cosmeticsmod:sol_dog.animation",
        "model_states": [
            {"animation": "sit", "when": "q.is_sitting", "blend": 0.3},
            {"animation": "walk", "when": "q.is_moving", "blend": 0.15},
            {"animation": "idle", "blend": 0.3}],
        })


# ------------------------------------------------------------------ the little ghost

def ghost():
    t = Tex(64, 32)
    body, edge, eye, cheek = 0xFFF1EEFF, 0xFFC9C0F2, 0xFF2A2440, 0xFFF7B8D8
    F = 24
    bones = [{"name": "ghost", "pivot": [0, F, 0]}]

    def part(name, parent, pivot, origin, size, colour, front=None, detail=None):
        w, h, d = size
        u, v = t.place(w, h, d)
        fu, fv = t.box(u, v, w, h, d, colour, front=front)
        if detail:
            detail(fu, fv)
        bones.append({"name": name, "parent": parent, "pivot": pivot, "cubes": [cube(origin, size, [u, v])]})

    def face(fu, fv):
        # Two tall eyes and pink cheeks, a little smile.
        for ex in (2, 5):
            t.fill(fu + ex, fv + 3, 1, 2, eye)
        t.put(fu + 1, fv + 5, cheek)
        t.put(fu + 6, fv + 5, cheek)
        t.put(fu + 3, fv + 6, eye)
        t.put(fu + 4, fv + 6, eye)

    part("body", "ghost", [0, F + 6, 0], [-4, F + 4, -4], [8, 8, 8], body, front=body, detail=face)
    part("top", "body", [0, F + 12, 0], [-3, F + 12, -3], [6, 1, 6], body)
    # The ragged hem: four bits that wave.
    for i, (x, z) in enumerate(((-4, -4), (2, -4), (-4, 2), (2, 2))):
        part(f"hem_{i}", "body", [x + 1, F + 4, z + 1], [x, F + 2, z], [2, 2, 2], edge)
    part("arm_left", "body", [4, F + 8, 0], [4, F + 6, -1], [2, 2, 2], body)
    part("arm_right", "body", [-4, F + 8, 0], [-6, F + 6, -1], [2, 2, 2], body)
    write(RES / "textures/cosmetic/sol_ghost.png", png(t.w, t.h, t.px))
    write(RES / "cosmetic_models/sol_ghost.geo.json", geo("sol_ghost", t.w, t.h, bones))
    hem = {f"hem_{i}": {"position": [0, f"math.sin(q.anim_time * 240 + {i * 90}) * 0.6", 0]} for i in range(4)}
    anims = {"format_version": "1.8.0", "animations": {
        "float": {"loop": True, "bones": dict(hem, **{
            "body": {"position": [0, "math.sin(q.anim_time * 150) * 1.2", 0],
                     "rotation": ["math.min(q.ground_speed * 3, 18)", 0, "math.sin(q.anim_time * 75) * 4"]},
            "arm_left": {"rotation": [0, 0, "-20 - math.sin(q.anim_time * 300) * 25"]},
            "arm_right": {"rotation": [0, 0, "20 + math.sin(q.anim_time * 300) * 25"]}})},
        "rest": {"loop": True, "bones": dict(hem, **{
            "body": {"position": [0, "math.sin(q.anim_time * 90) * 0.8", 0], "rotation": [0, "math.sin(q.anim_time * 30) * 10", 0]},
            "arm_left": {"rotation": [0, 0, -10]},
            "arm_right": {"rotation": [0, 0, 10]}})}}}
    write(RES / "cosmetic_models/sol_ghost.animation.json", anims)
    write(RES / "cosmetics/sol_ghost.json", {
        "name": "Little Ghost",
        "slot": "pet",
        "pet_position": "follow_flying",
        "texture": "cosmeticsmod:textures/cosmetic/sol_ghost.png",
        "model": "cosmeticsmod:sol_ghost",
        "model_animations": "cosmeticsmod:sol_ghost.animation",
        "model_states": [
            {"animation": "rest", "when": "q.is_sitting", "blend": 0.4},
            {"animation": "float", "blend": 0.3}],
        "bones": {"body": {"mode": "emissive"}, "top": {"mode": "emissive"}}})


# ------------------------------------------------------------------ the mini you (what the Wardrobe shows)

def mini_you():
    t = Tex(64, 32)
    skin, shirt, pants, hair = 0xFFC9946B, 0xFF5FD4FF, 0xFF3A3F6B, 0xFF4A3426
    parts = []

    def part(name, frm, size, colour, top=None, front=None, detail=None):
        w, h, d = size
        u, v = t.place(w, h, d)
        fu, fv = t.box(u, v, w, h, d, colour, top, front)
        if detail:
            detail(fu, fv)
        parts.append({"name": name, "cubes": [{"from": frm, "to": [frm[0] + w, frm[1] + h, frm[2] + d], "uv": [u, v]}]})

    def face(fu, fv):
        t.fill(fu, fv, 4, 1, hair)
        t.put(fu + 1, fv + 2, 0xFF1A1A2A)
        t.put(fu + 2, fv + 2, 0xFF1A1A2A)

    # A figure five pixels tall at the shoulders, standing on the origin and growing up (-Y): a third of you.
    part("head", [-2, -11, -2], [4, 4, 4], skin, top=hair, detail=face)
    part("body", [-2, -7, -1], [4, 4, 2], shirt)
    part("arm_left", [2, -7, -1], [1, 4, 2], skin)
    part("arm_right", [-3, -7, -1], [1, 4, 2], skin)
    part("leg_left", [0, -3, -1], [2, 3, 2], pants)
    part("leg_right", [-2, -3, -1], [2, 3, 2], pants)
    write(RES / "textures/cosmetic/sol_mini_you.png", png(t.w, t.h, t.px))
    write(RES / "cosmetics/sol_mini_you.json", {
        "name": "Mini You",
        "slot": "pet",
        "pet_position": "follow",
        "texture": "cosmeticsmod:textures/cosmetic/sol_mini_you.png",
        "texture_size": [t.w, t.h],
        "parts": parts})


if __name__ == "__main__":
    dog()
    ghost()
    mini_you()
    print("pets written")
