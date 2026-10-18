"""Builds the night sky's data files from the public catalogues.

    python3 tools/sol-assets/make_sky.py <hygdata_v41.csv> <constellations.lines.json> <constellations.json> <out dir>

Inputs (downloaded by hand, never committed):
- the HYG database v4.1 (https://github.com/astronexus/HYG-Database, CC BY-SA 4.0): every star down to magnitude 6.5,
  with its position (J2000), magnitude, B-V colour index and proper name;
- d3-celestial's constellation lines and names (https://github.com/ofrohn/d3-celestial, BSD 3-clause).

Outputs, big-endian (Java's DataInputStream):
- sky/stars.dat: "SOLS", version, count, then per star RA (hours, float), Dec (degrees, float), magnitude x 100
  (short), B-V x 1000 (short); then the named stars: count, then per name the star's index (int) and the name (UTF).
- sky/constellations.dat: "SOLC", version, count, then per constellation its id, English name, label RA (degrees)
  and Dec, and its line segments as RA/Dec pairs in degrees.
"""
import csv
import json
import os
import struct
import sys

LIMIT = 6.5
NAMED_LIMIT = 2.2
# The constellations drawn: the Night Sky lab's 21, the zodiac, and the northern ones (with those on the equator).
LAB = {"Ori", "Tau", "Gem", "Aur", "CMa", "CMi", "UMa", "UMi", "Cas", "Cyg", "Lyr", "Aql", "Sco", "Sgr", "Leo", "Boo",
       "CrB", "Peg", "And", "Per", "Ari"}
ZODIAC = {"Ari", "Tau", "Gem", "Cnc", "Leo", "Vir", "Lib", "Sco", "Sgr", "Cap", "Aqr", "Psc"}
SOUTH_LIMIT = -10.0


def utf(s):
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def stars(path):
    out, names = [], []
    with open(path, newline="") as f:
        for r in csv.DictReader(f):
            if r["id"] == "0":
                continue  # the Sun
            try:
                mag = float(r["mag"])
            except ValueError:
                continue
            if mag > LIMIT:
                continue
            try:
                bv = float(r["ci"])
            except ValueError:
                bv = 0.6
            out.append((float(r["ra"]), float(r["dec"]), mag, bv, r["proper"].strip()))
    out.sort(key=lambda s: s[2])
    data = bytearray(b"SOLS") + struct.pack(">ii", 1, len(out))
    for ra, dec, mag, bv, _ in out:
        data += struct.pack(">ffhh", ra, dec, round(mag * 100), round(max(-0.5, min(2.5, bv)) * 1000))
    for i, s in enumerate(out):
        if s[4] and s[2] <= NAMED_LIMIT:
            names.append((i, s[4]))
    data += struct.pack(">i", len(names))
    for i, n in names:
        data += struct.pack(">i", i) + utf(n)
    return data, len(out), len(names)


def constellations(lines_path, names_path):
    lines = {f["id"]: f["geometry"]["coordinates"] for f in json.load(open(lines_path))["features"]}
    meta = {f["id"]: f["properties"] for f in json.load(open(names_path))["features"]}
    picked = []
    for cid, segs in sorted(lines.items()):
        p = meta.get(cid)
        if p is None:
            continue
        lon, lat = p["display"][0], p["display"][1]
        if lat < SOUTH_LIMIT and cid not in LAB and cid not in ZODIAC:
            continue
        pairs = []
        for line in segs:
            for a, b in zip(line, line[1:]):
                pairs.append((a[0], a[1], b[0], b[1]))
        picked.append((cid, p["name"], lon, lat, pairs))
    data = bytearray(b"SOLC") + struct.pack(">ii", 1, len(picked))
    for cid, name, lon, lat, pairs in picked:
        data += utf(cid) + utf(name) + struct.pack(">ffi", lon, lat, len(pairs))
        for p in pairs:
            data += struct.pack(">ffff", *p)
    return data, len(picked)


def main():
    hyg, lines, names, out = sys.argv[1:5]
    os.makedirs(os.path.join(out, "sky"), exist_ok=True)
    s, n, named = stars(hyg)
    with open(os.path.join(out, "sky", "stars.dat"), "wb") as f:
        f.write(s)
    c, nc = constellations(lines, names)
    with open(os.path.join(out, "sky", "constellations.dat"), "wb") as f:
        f.write(c)
    print(n, "stars,", named, "named,", nc, "constellations")


if __name__ == "__main__":
    main()
