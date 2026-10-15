#!/usr/bin/env python3
"""Writes the pixel-size font definitions for Geist Mono (font/geist_mono_<px>.json).

Each size is its own TrueType font made at exactly that many pixels, so the Sol UI's mono labels and numbers
land one to one on the screen (the same scheme as font/geist_<px>.json). Stdlib only; run from the repo root.
"""
import json
import pathlib

SIZES = list(range(6, 25)) + list(range(26, 49, 2))
OUT = pathlib.Path("src/main/resources/assets/cosmeticsmod/font")

for px in SIZES:
    data = {"providers": [
        {"type": "ttf", "file": "cosmeticsmod:geist-mono-medium.ttf", "size": px, "oversample": 1.0, "shift": [0, 0]},
        {"type": "reference", "id": "minecraft:default"},
    ]}
    (OUT / f"geist_mono_{px}.json").write_text(json.dumps(data, indent=2) + "\n")
print(f"{len(SIZES)} sizes")
