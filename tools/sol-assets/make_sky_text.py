"""Builds the night sky's lettering: the Sol font (Geist Mono and Geist) cut into glyphs at every pixel size the sky's
names are written at, so they are drawn in the sky itself, each glyph a small picture on the sky's pixels. Also the
lyrics' lettering (Geist SemiBold with punctuation and accented letters, at the sizes the line being sung is written
at), in its own picture so the names' stays small.

    python3 tools/sol-assets/make_sky_text.py <resources dir> [sky|lyrics]   (both when not named)

Reads assets/cosmeticsmod/font/geist-mono-medium.ttf, geist-medium.ttf and geist-semibold.ttf from the resources and
writes sky/sky_text.png and sky/lyrics_text.png (white, coverage in alpha) with sky/sky_text.dat and
sky/lyrics_text.dat, big-endian: "SOLT", version, number of fonts, then per font its family (0 mono, 1 sans, 2 lyrics),
pixel size, ascent and glyph count, and per glyph its code point (int), place in the picture (x, y, w, h), offset from
the pen on the baseline (dx, dy) and advance (shorts).
"""
import os
import struct
import sys

from PIL import Image, ImageDraw, ImageFont

SIZES = range(6, 29)
# Constellations are written in capitals; stars, planets and worlds as they are named.
MONO = list(" ABCDEFGHIJKLMNOPQRSTUVWXYZÖ'-.")
SANS = list(" ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyzÖö'-.0123456789")
# Lyrics: printable ASCII, the accented letters of the Latin languages, curly quotes, dashes and the ellipsis.
LYRICS = [chr(c) for c in range(32, 127)] + list("¡¿ÀÁÂÃÄÅÆÇÈÉÊËÌÍÎÏÑÒÓÔÕÖØÙÚÛÜÝßàáâãäåæçèéêëìíîïñòóôõöøùúûüýÿŒœ‘’“”–—…")
LYRICS_SIZES = (14, 16, 18, 20, 22, 24, 26, 28, 32, 36, 40, 44)
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
    which = sys.argv[2] if len(sys.argv) > 2 else "both"
    font = os.path.join(res, "assets", "cosmeticsmod", "font")
    if which in ("sky", "both"):
        fonts = [(0, os.path.join(font, "geist-mono-medium.ttf"), MONO), (1, os.path.join(font, "geist-medium.ttf"), SANS)]
        write(res, "sky_text", [(fam, size) + glyphs(path, size, chars) for fam, path, chars in fonts for size in SIZES])
    if which in ("lyrics", "both"):
        path = os.path.join(font, "geist-semibold.ttf")
        write(res, "lyrics_text", [(2, size) + glyphs(path, size, LYRICS) for size in LYRICS_SIZES])


def write(res, name, cut):
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
    sheet.save(os.path.join(res, "assets", "cosmeticsmod", "sky", name + ".png"), optimize=True)
    with open(os.path.join(res, "assets", "cosmeticsmod", "sky", name + ".dat"), "wb") as f:
        f.write(data)
    print(name + ":", len(cut), "fonts,", WIDTH, "x", height)


if __name__ == "__main__":
    main()
