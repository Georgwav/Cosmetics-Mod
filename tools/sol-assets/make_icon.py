"""The Sol Client icon: Sol's square pixel sun setting over a dune, in Ember Solstice's sunset.

Drawn on a 32 x 32 pixel grid and scaled up with hard pixels:
- assets/cosmeticsmod/icon.png (128 x 128): Mod Menu and the launcher;
- docs/solclient-icon.png (512 x 512): the Modrinth page.

Stdlib only (its own PNG writer). Run from the repository root: python3 tools/sol-assets/make_icon.py
"""
import math
import struct
import zlib

N = 32


def hexc(s, a=255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def over(dst, src, k):
    """src over dst at strength k (0..1), both RGBA; the result is opaque where dst was."""
    return tuple(round(dst[i] + (src[i] - dst[i]) * k) for i in range(3)) + (max(dst[3], round(255 * k)),)


def add(dst, src, k):
    """src added to dst (light), at strength k."""
    return tuple(min(255, round(dst[i] + src[i] * k)) for i in range(3)) + (dst[3],)


def draw():
    px = [[(0, 0, 0, 0)] * N for _ in range(N)]
    # The sky: Ember's dome, top to horizon glow, in pixel bands.
    bands = ["#2e0704", "#4a0c05", "#5e0f06", "#7a1a07", "#9a280c", "#c2461a", "#e2672a", "#fe8a2c"]
    horizon = 24
    for y in range(N):
        for x in range(N):
            b = min(len(bands) - 1, y * len(bands) // horizon)
            px[y][x] = hexc(bands[b])
    # The halo: square rings round the sun, fainter outward (the Sun lab's stepped halo).
    sx0, sy0, s = 12, 13, 8
    for d, k in ((5, 0.07), (3, 0.14), (1, 0.3)):
        for y in range(sy0 - d, sy0 + s + d):
            for x in range(sx0 - d, sx0 + s + d):
                if 0 <= x < N and 0 <= y < N:
                    px[y][x] = add(px[y][x], hexc("#ffb347"), k)
    # Rays: short pixel rays from the sun's sides and corners.
    cx, cy = sx0 + s / 2 - 0.5, sy0 + s / 2 - 0.5
    for a in range(180, 361, 45):
        r = math.radians(a)
        for t in (7.5, 8.5):
            x, y = round(cx + math.cos(r) * t), round(cy + math.sin(r) * t)
            if 0 <= x < N and 0 <= y < horizon:
                px[y][x] = add(px[y][x], hexc("#ffd36b"), 0.55 if a % 90 == 0 else 0.35)
    # The sun: a square, white-hot inside, gold to amber at its rim.
    layers = ["#ffb347", "#ffd36b", "#fff3c4", "#ffffff"]
    for y in range(sy0, sy0 + s):
        for x in range(sx0, sx0 + s):
            d = min(x - sx0, y - sy0, sx0 + s - 1 - x, sy0 + s - 1 - y)
            px[y][x] = hexc(layers[min(d, len(layers) - 1)])
    # The dune: a dark silhouette with a lit rim along its top, sand catching the low sun.
    for x in range(N):
        top = horizon + round(1.2 * math.sin(x / 4.6 + 0.8) + 0.6 * math.sin(x / 2.1))
        for y in range(top, N):
            depth = y - top
            px[y][x] = hexc("#fccf71") if depth == 0 else hexc("#7a3410") if depth == 1 else hexc("#3c1608")
    # Embers rising.
    for x, y, c in ((5, 8, "#ffcf6a"), (26, 6, "#ffb347"), (25, 19, "#fff0b8"), (6, 17, "#ffb347")):
        px[y][x] = add(px[y][x], hexc(c), 0.9)
    # Rounded corners: two pixels off each corner.
    for x, y in ((0, 0), (1, 0), (0, 1)):
        for fx, fy in ((x, y), (N - 1 - x, y), (x, N - 1 - y), (N - 1 - x, N - 1 - y)):
            px[fy][fx] = (0, 0, 0, 0)
    return px


def png(px, scale, path):
    w = h = N * scale
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        row = px[y // scale]
        for x in range(w):
            raw.extend(row[x // scale])
    def chunk(t, data):
        return struct.pack(">I", len(data)) + t + data + struct.pack(">I", zlib.crc32(t + data) & 0xFFFFFFFF)
    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
    data += chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(data)


if __name__ == "__main__":
    pixels = draw()
    png(pixels, 4, "src/main/resources/assets/cosmeticsmod/icon.png")
    png(pixels, 16, "docs/solclient-icon.png")
    print("icon written: 128 and 512 px")
