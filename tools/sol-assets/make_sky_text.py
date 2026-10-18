"""Builds the night sky's lettering: the Sol font (Geist Mono and Geist) cut into glyphs at every pixel size the sky's
names are written at, so they are drawn in the sky itself, each glyph a small picture on the sky's pixels.

    python3 tools/sol-assets/make_sky_text.py <resources dir>

Reads assets/cosmeticsmod/font/geist-mono-medium.ttf and geist-medium.ttf from the resources and writes sky/sky_text.png (white,
coverage in alpha) and sky/sky_text.dat, big-endian: "SOLT", version, number of fonts, then per font its family
(0 mono, 1 sans), pixel size, ascent and glyph count, and per glyph its code point (int), place in the picture
(x, y, w, h), offset from the pen on the baseline (dx, dy) and advance (shorts).
"""
import os
import struct
import sys

from PIL import Image, ImageDraw, ImageFont

SIZES = range(6, 29)
# Constellations are written in capitals; stars, planets and worlds as they are named.
MONO = list(" ABCDEFGHIJKLMNOPQRSTUVWXYZÖ'-.")
SANS = list(" ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyzÖö'-.0123456789")
WIDTH = 1024


def glyphs(path, size, chars):
    font = ImageFont.truetype(path, size)
    ascent, _ = font.getmetrics()
    out = []
    for ch in chars:
        x0, y0, x1, y1 = font.getbbox(ch, anchor="ls")
        adv = round(font.getlength(ch))
        w, h = max(0, x1 - x0), max(0, y1 - y0)
        img = Image.new("L", (max(1, w), max(1, h)), 0)
        if w and h:
            ImageDraw.Draw(img).text((-x0, -y0), ch, font=font, fill=255, anchor="ls")
        out.append((ord(ch), img, w, h, x0, y0, adv))
    return ascent, out


def main():
    res = sys.argv[1]
    font = os.path.join(res, "assets", "cosmeticsmod", "font")
    fonts = [(0, os.path.join(font, "geist-mono-medium.ttf"), MONO), (1, os.path.join(font, "geist-medium.ttf"), SANS)]
    cut = [(fam, size) + glyphs(path, size, chars) for fam, path, chars in fonts for size in SIZES]
    # Shelf packing, a texel apart.
    x = y = shelf = 0
    places = []
    for _, _, _, gl in cut:
        row = []
        for code, img, w, h, dx, dy, adv in gl:
            if x + w + 1 > WIDTH:
                x, y, shelf = 0, y + shelf + 1, 0
            row.append((x, y))
            x += w + 1
            shelf = max(shelf, h)
        places.append(row)
    height = 1
    while height < y + shelf + 1:
        height *= 2
    sheet = Image.new("RGBA", (WIDTH, height), (255, 255, 255, 0))
    data = bytearray(b"SOLT") + struct.pack(">ii", 1, len(cut))
    for (fam, size, ascent, gl), row in zip(cut, places):
        data += struct.pack(">iiii", fam, size, ascent, len(gl))
        for (code, img, w, h, dx, dy, adv), (px, py) in zip(gl, row):
            if w and h:
                white = Image.new("RGBA", (w, h), (255, 255, 255, 0))
                white.putalpha(img)
                sheet.paste(white, (px, py))
            data += struct.pack(">ihhhhhhh", code, px, py, w, h, dx, dy, adv)
    os.makedirs(os.path.join(res, "assets", "cosmeticsmod", "sky"), exist_ok=True)
    sheet.save(os.path.join(res, "assets", "cosmeticsmod", "sky", "sky_text.png"), optimize=True)
    with open(os.path.join(res, "assets", "cosmeticsmod", "sky", "sky_text.dat"), "wb") as f:
        f.write(data)
    print(len(cut), "fonts,", WIDTH, "x", height)


if __name__ == "__main__":
    main()
