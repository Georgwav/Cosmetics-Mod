#!/usr/bin/env python3
"""Side-by-side sheets of Sol's in-game frames and the prototypes' (docs/look/), for the PR and the report.

    python3 tools/look/compare.py <run dir with screenshot-sol_*.png> <prototype shots dir> <out dir>

ember_compare.png: Bloom Client's measured colours (the floor), the Sun lab at the same sun height (the target) and
the in-game frame, each with the same points sampled. themes_compare.png: each theme's prototype frame next to its
in-game showpiece. Needs Pillow.
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

BLOOM = [("sky top", "#5d1b0f"), ("sky middle", "#672012"), ("above the glow", "#7c2e19"), ("horizon band", "#e77839"),
         ("glow round the sun", "#d56735"), ("far fog", "#ea7b3a"), ("cane in the fog", "#b56a3b"),
         ("sand near", "#f8d985"), ("sand at the fog", "#f5bf78")]
# Where those are in a frame facing the sun with the horizon a little below the middle (fractions of width/height).
POINTS = [(0.1, 0.04), (0.1, 0.22), (0.1, 0.42), (0.08, 0.53), (0.38, 0.5), (0.95, 0.6), (0.2, 0.66), (0.3, 0.95),
          (0.85, 0.76)]
THEMES = [("sakura_dusk", "sakura"), ("frost_morning", "frost"), ("ember_solstice", "ember"), ("aurora", "aurora"),
          ("firefly_meadow", "firefly"), ("monsoon", "monsoon"), ("maple_afternoon", "maple"),
          ("eclipse", "eclipse"), ("deep_field", "mono"), ("silver_night", "silver"), ("grace", "grace")]


def sample(im, fx, fy):
    w, h = im.size
    x, y = int(fx * w), int(fy * h)
    px = [im.getpixel((min(w - 1, max(0, x + dx)), min(h - 1, max(0, y + dy)))) for dx in range(-4, 5) for dy in range(-4, 5)]
    return tuple(sum(p[i] for p in px) // len(px) for i in range(3))


def hexc(c):
    return "#%02x%02x%02x" % c


def font(size):
    for f in ("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", "/usr/share/fonts/dejavu/DejaVuSans.ttf"):
        if os.path.exists(f):
            return ImageFont.truetype(f, size)
    return ImageFont.load_default()


def ember(run, proto, out):
    W, H = 640, 360
    ours = Image.open(os.path.join(run, "screenshot-sol_ember_sunlab.png")).convert("RGB").resize((W, H))
    lab = Image.open(os.path.join(proto, "lab_ember_e18.png")).convert("RGB").resize((W, H))
    f, fs = font(16), font(12)
    sheet = Image.new("RGB", (W * 3, H + 40 + 28 * len(BLOOM)), (12, 12, 16))
    d = ImageDraw.Draw(sheet)
    # Bloom: the measured colours as a frame of swatches.
    sw = Image.new("RGB", (W, H), (0, 0, 0))
    sd = ImageDraw.Draw(sw)
    rows = len(BLOOM)
    for i, (name, c) in enumerate(BLOOM):
        y0 = i * H // rows
        sd.rectangle([0, y0, W, (i + 1) * H // rows], fill=c)
        sd.text((10, y0 + 6), name + "  " + c, fill="#ffffff" if i < 3 else "#000000", font=fs)
    for i, (im, title) in enumerate(((sw, "Bloom Client (measured colours: the floor)"), (lab, "Sol prototype: Sun lab, e = .18 (the target)"),
                                     (ours, "Sol Client in game, 1.21.11, tick 12300"))):
        sheet.paste(im, (i * W, 30))
        d.text((i * W + 8, 6), title, fill="#ffffff", font=f)
    y = H + 40
    for j, (name, c) in enumerate(BLOOM):
        fx, fy = POINTS[j]
        cl, co = sample(lab, fx, fy), sample(ours, fx, fy)
        d.text((8, y + j * 28), name, fill="#cccccc", font=fs)
        for k, (col, label) in enumerate(((c, c), (hexc(cl), hexc(cl)), (hexc(co), hexc(co)))):
            x = 180 + k * 200
            d.rectangle([x, y + j * 28, x + 40, y + j * 28 + 20], fill=col)
            d.text((x + 48, y + j * 28 + 3), label, fill="#cccccc", font=fs)
    for fx, fy in POINTS:
        for k in (1, 2):
            cx, cy = k * W + int(fx * W), 30 + int(fy * H)
            d.ellipse([cx - 4, cy - 4, cx + 4, cy + 4], outline="#00ffff")
    sheet.save(os.path.join(out, "ember_compare.png"))


def themes(run, proto, out):
    W, H = 480, 270
    f = font(14)
    sheet = Image.new("RGB", (W * 2 + 10, (H + 26) * len(THEMES)), (12, 12, 16))
    d = ImageDraw.Draw(sheet)
    for i, (sol, p) in enumerate(THEMES):
        y = i * (H + 26)
        a = os.path.join(proto, "clean_%s.png" % p)
        b = os.path.join(run, "screenshot-sol_%s_show.png" % sol)
        if os.path.exists(a):
            sheet.paste(Image.open(a).convert("RGB").resize((W, H)), (0, y + 22))
        if os.path.exists(b):
            sheet.paste(Image.open(b).convert("RGB").resize((W, H)), (W + 10, y + 22))
        d.text((6, y + 3), "%s: prototype" % sol, fill="#ffffff", font=f)
        d.text((W + 16, y + 3), "%s: in game" % sol, fill="#ffffff", font=f)
    sheet.save(os.path.join(out, "themes_compare.png"))


if __name__ == "__main__":
    run, proto, out = sys.argv[1:4]
    os.makedirs(out, exist_ok=True)
    ember(run, proto, out)
    themes(run, proto, out)
    print("written to", out)
