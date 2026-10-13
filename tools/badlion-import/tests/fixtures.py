"""Synthetic input folders shaped like the Badlion layout. Every file here is made up for the tests."""

from __future__ import annotations

import hashlib
import io
import json
import zipfile
from pathlib import Path

from PIL import Image

T = "assets/minecraft/blc/textures/cosmetics/"


def avif_still(size=(32, 16), opaque=None, color=(200, 40, 40, 255)) -> bytes:
    """A still AVIF. `opaque` is a (x0, y0, x1, y1) fraction box that gets painted; the rest is clear."""
    image = Image.new("RGBA", size, (0, 0, 0, 0) if opaque else color)
    if opaque:
        x0, y0, x1, y1 = opaque
        box = (int(x0 * size[0]), int(y0 * size[1]), int(x1 * size[0]), int(y1 * size[1]))
        image.paste(color, box)
    out = io.BytesIO()
    image.save(out, format="AVIF", quality=100)
    return out.getvalue()


def avif_sequence(durations, size=(16, 16)) -> bytes:
    frames = [Image.new("RGBA", size, (40 * i % 256, 90, 200, 255)) for i in range(len(durations))]
    out = io.BytesIO()
    frames[0].save(out, format="AVIF", save_all=True, append_images=frames[1:], duration=list(durations), loop=0,
                   quality=100)
    return out.getvalue()


# A flat cape hanging from one joint: UVs cover the left half of the texture.
CAPE_MD5MESH = """MD5Version 10
commandline ""
numJoints 2
numMeshes 1
joints {
  "root" -1 ( 0 0 0 ) ( 0 0 0 )
  "cloth" 0 ( 0 0 -1 ) ( 0 0 0 )
}
mesh {
  shader "cloak"
  numverts 4
  vert 0 ( 0 0 ) 0 1
  vert 1 ( 0.5 0 ) 1 1
  vert 2 ( 0.5 1 ) 2 1
  vert 3 ( 0 1 ) 3 1
  numtris 2
  tri 0 0 1 2
  tri 1 0 2 3
  numweights 4
  weight 0 1 1.0 ( -5 0 0 )
  weight 1 1 1.0 ( 5 0 0 )
  weight 2 1 1.0 ( 5 0 -16 )
  weight 3 1 1.0 ( -5 0 -16 )
}
"""


def md5anim(frames=2, rate=24) -> str:
    lines = ["MD5Version 10", 'commandline ""', f"numFrames {frames}", "numJoints 2", f"frameRate {rate}",
             "numAnimatedComponents 1", "hierarchy {", '  "root" -1 0 0', '  "cloth" 0 8 0', "}", "bounds {"]
    lines += ["  ( -5 -1 -16 ) ( 5 1 0 )"] * frames
    lines += ["}", "baseframe {", "  ( 0 0 0 ) ( 0 0 0 )", "  ( 0 0 -1 ) ( 0 0 0 )", "}"]
    for f in range(frames):
        lines += [f"frame {f} {{", f"  {0.1 * f:.3f}", "}"]
    return "\n".join(lines) + "\n"


# A decoy cloak model whose UVs cover the right half.
DECOY_OBJ = """o decoy
v -5 0 0
v 5 0 0
v 5 16 0
v -5 16 0
vt 0.5 1
vt 1 1
vt 1 0
vt 0.5 0
f 1/1 2/2 3/3 4/4
"""

HAT_OBJ = """# a box hat
o hat
v -4 -8 -4
v 4 -8 -4
v 4 -12 -4
v -4 -12 -4
vt 0 0
vt 1 0
vt 1 1
vt 0 1
f 1/1 2/2 3/3 4/4
"""

EXAMPLE = """{
  // Every cosmetic type
  "registeredCosmetics": {
    /* one list per type */
    "HAT": [ { "cosmeticId": 1, "name": "Example", } ],
  }
}
"""


def cosmetics_json() -> dict:
    return {"registeredCosmetics": {
        "CAPE": [
            {"cosmeticId": 1, "name": "Red Cape", "hasModel": False, "allowChroma": True, "allowSaturation": False,
             "multiLayer": False, "multiLayerColoredIndex": 0, "glow": False, "transparent": False,
             "particleSpawnRate": 0, "renderPlayerHead": False, "defaultColor": -1},
            {"cosmeticId": 2, "name": "Blue Cape", "hasModel": False},
            {"cosmeticId": 7, "name": "Flowing Cape", "hasModel": False,
             "animationData": {"frameDelay": 50, "customFrameDelay": True}},
            {"cosmeticId": 9, "name": "Not Owned Cape", "hasModel": False},
        ],
        "WINGS": [
            {"cosmeticId": 6, "name": "Angel", "hasModel": True, "modelName": "angel",
             "wingData": {"flapBase": 10.0, "wingTranslate": 1.5}},
            {"cosmeticId": 1, "name": "Plain Wings", "hasModel": True},
        ],
        "HAT": [
            {"cosmeticId": 1, "name": "Top Hat", "hasModel": True, "modelName": "tophat",
             "sizingData": {"scale": 1.2}, "mysteryField": 3},
        ],
    }}


def models_json() -> dict:
    return {"modelData": {
        "HAT": {"tophat": {"scale": 0.0, "translation": {"x": 0, "y": 1, "z": 0}, "flipUVs": False,
                           "applyChroma": False, "md5format": False, "subModelTexture": False, "useSubFolder": False}},
        "WINGS": {"angel": {"scale": 1.0, "flipUVs": True, "md5format": False}},
    }}


def build(folder: Path, *, missing_listed=True) -> Path:
    """Writes assets.json, data0.jar (textures, models) and data5.jar (definitions) into `folder`."""
    folder.mkdir(parents=True, exist_ok=True)
    files = {
        0: {
            T + "cloak/cloak_1.avif": avif_still((64, 32), opaque=(0, 0, 0.5, 1)),
            T + "cloak/cloak_2.avif": avif_still((64, 32), opaque=(0, 0, 0.5, 1), color=(30, 30, 200, 255)),
            T + "cloak/7/cloak_7.avif": avif_sequence([100, 40, 250]),
            T + "cloak/gui/cloak_1_gui.avif": avif_still((8, 8)),
            T + "cloak/models/cloak_v2/cloak_v2.md5mesh": CAPE_MD5MESH.encode(),
            T + "cloak/models/cloak_v2/rect.md5anim": md5anim().encode(),
            T + "cloak/models/cloak_v2/curved.md5anim": md5anim(3).encode(),
            T + "cloak/models/cloak_1.obj": DECOY_OBJ.encode(),
            T + "wing/wing_6.avif": avif_still((32, 32)),
            T + "wing/wing_1.avif": avif_still((32, 32)),
            T + "hat/hat_1.avif": avif_still((16, 16)),
            T + "hat/models/tophat.obj": HAT_OBJ.encode(),
            T + "hat/hat_99_extra.avif": avif_still((8, 8)),
        },
        5: {
            "blc/cosmetics/cosmetics.json": json.dumps(cosmetics_json()).encode(),
            "blc/cosmetics/models.json": json.dumps(models_json()).encode(),
            "blc/cosmetics/example.json": EXAMPLE.encode(),
        },
    }
    # Listed in assets.json but trimmed out of the jars: not owned.
    trimmed = {0: {T + "cloak/cloak_9.avif": b"x" * 10, T + "wing/models/angel.obj": b"y" * 10}, 3: {"other/file.bin": b"z"}}
    chunk_data = []
    for chunk in sorted(set(files) | set(trimmed)):
        entries = []
        for path, data in {**files.get(chunk, {}), **(trimmed.get(chunk, {}) if missing_listed else {})}.items():
            entries.append({"hash": hashlib.sha1(data).hexdigest(), "size": len(data), "absolutePath": path})
        chunk_data.append({"chunkId": chunk, "files": entries})
        if chunk in files:
            with zipfile.ZipFile(folder / f"data{chunk}.jar", "w", compression=zipfile.ZIP_STORED) as z:
                for path, data in files[chunk].items():
                    z.writestr(path, data)
    (folder / "assets.json").write_text(json.dumps({"id": "test", "version": "1", "buildType": "TEST",
                                                    "chunkData": chunk_data}), encoding="utf-8")
    return folder
