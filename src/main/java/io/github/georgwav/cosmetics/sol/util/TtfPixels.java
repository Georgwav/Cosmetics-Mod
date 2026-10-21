package io.github.georgwav.cosmetics.sol.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A pixel font's glyphs as bitmaps, read straight from its TrueType file: for fonts drawn on a pixel grid (Silkscreen
 * is 8 pixels to the em, 125 font units a pixel), each glyph's outline is sampled at the middle of every grid cell,
 * so the bitmap is exactly the pixels the designer drew. Only the tables a pixel font needs are read (head, maxp,
 * hhea, hmtx, loca, glyf, cmap formats 4 and 12); composite glyphs (the accented letters) are put together from
 * their parts. Pure Java, no Minecraft: the tests read the bundled fonts with it.
 */
public final class TtfPixels {
    /**
     * One glyph in font pixels: {@code advance} to the next glyph, its bitmap {@code w} x {@code h} with its left
     * edge {@code left} pixels right of the pen and its top {@code top} pixels above the baseline, rows top down.
     */
    public record Glyph(int codepoint, int advance, int left, int top, int w, int h, boolean[] bits) {
        public boolean at(int x, int y) {
            return x >= 0 && y >= 0 && x < w && y < h && bits[y * w + x];
        }
    }

    /** Pixels to the em, and the font's ascent and descent in pixels (descent below the baseline, positive). */
    public final int em;
    public final int ascent;
    public final int descent;
    private final Map<Integer, Glyph> glyphs = new HashMap<>();

    private final ByteBuffer b;
    private final int unit;
    private final int[] loca;
    private final int glyf;
    private final int[] advances;

    private TtfPixels(byte[] data, int emPixels) {
        b = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        Map<String, Integer> tables = new HashMap<>();
        int numTables = u16(4);
        for (int i = 0; i < numTables; i++) {
            int rec = 12 + i * 16;
            String tag = new String(new char[]{(char) b.get(rec), (char) b.get(rec + 1), (char) b.get(rec + 2), (char) b.get(rec + 3)});
            tables.put(tag, (int) (b.getInt(rec + 8) & 0xFFFFFFFFL));
        }
        int head = need(tables, "head"), maxp = need(tables, "maxp"), hhea = need(tables, "hhea");
        int upem = u16(head + 18);
        int locFormat = b.getShort(head + 50);
        int numGlyphs = u16(maxp + 4);
        em = emPixels;
        unit = upem / emPixels;
        ascent = Math.round(b.getShort(hhea + 4) / (float) unit);
        descent = Math.round(-b.getShort(hhea + 6) / (float) unit);
        int numMetrics = u16(hhea + 34);
        int hmtx = need(tables, "hmtx");
        advances = new int[numGlyphs];
        for (int i = 0; i < numGlyphs; i++) {
            advances[i] = u16(hmtx + Math.min(i, numMetrics - 1) * 4);
        }
        int locaAt = need(tables, "loca");
        loca = new int[numGlyphs + 1];
        for (int i = 0; i <= numGlyphs; i++) {
            loca[i] = locFormat == 0 ? u16(locaAt + i * 2) * 2 : (int) (b.getInt(locaAt + i * 4) & 0xFFFFFFFFL);
        }
        glyf = need(tables, "glyf");
        Map<Integer, Integer> cmap = cmap(need(tables, "cmap"));
        for (Map.Entry<Integer, Integer> e : cmap.entrySet()) {
            int gid = e.getValue();
            if (gid <= 0 || gid >= numGlyphs) {
                continue;
            }
            glyphs.put(e.getKey(), raster(e.getKey(), gid));
        }
    }

    /** Reads {@code ttf}, a font drawn on a grid of {@code emPixels} pixels to the em. */
    public static TtfPixels parse(byte[] ttf, int emPixels) {
        return new TtfPixels(ttf, emPixels);
    }

    /** The glyph for a code point, or null when the font hasn't got it. */
    public Glyph glyph(int codepoint) {
        return glyphs.get(codepoint);
    }

    public Map<Integer, Glyph> all() {
        return glyphs;
    }

    private static int need(Map<String, Integer> tables, String tag) {
        Integer at = tables.get(tag);
        if (at == null) {
            throw new IllegalArgumentException("no " + tag + " table");
        }
        return at;
    }

    private int u16(int at) {
        return b.getShort(at) & 0xFFFF;
    }

    // ------------------------------------------------------------------ cmap

    private Map<Integer, Integer> cmap(int at) {
        int n = u16(at + 2);
        int best = -1, bestFormat = 0;
        for (int i = 0; i < n; i++) {
            int rec = at + 4 + i * 8;
            int platform = u16(rec), encoding = u16(rec + 2);
            int sub = at + (int) (b.getInt(rec + 4) & 0xFFFFFFFFL);
            int format = u16(sub);
            boolean unicode = platform == 0 || platform == 3 && (encoding == 1 || encoding == 10);
            if (unicode && (format == 12 || format == 4 && bestFormat != 12)) {
                best = sub;
                bestFormat = format;
            }
        }
        Map<Integer, Integer> map = new HashMap<>();
        if (best < 0) {
            return map;
        }
        if (bestFormat == 12) {
            long groups = b.getInt(best + 12) & 0xFFFFFFFFL;
            for (int g = 0; g < groups; g++) {
                int rec = best + 16 + g * 12;
                int start = b.getInt(rec), end = b.getInt(rec + 4), gid = b.getInt(rec + 8);
                for (int c = start; c <= end && c - start < 0x10000; c++) {
                    map.put(c, gid + (c - start));
                }
            }
            return map;
        }
        int segs = u16(best + 6) / 2;
        int ends = best + 14, starts = ends + segs * 2 + 2, deltas = starts + segs * 2, ranges = deltas + segs * 2;
        for (int s = 0; s < segs; s++) {
            int end = u16(ends + s * 2), start = u16(starts + s * 2);
            int delta = b.getShort(deltas + s * 2), range = u16(ranges + s * 2);
            for (int c = start; c <= end && c != 0xFFFF; c++) {
                int gid;
                if (range == 0) {
                    gid = (c + delta) & 0xFFFF;
                } else {
                    int at2 = ranges + s * 2 + range + (c - start) * 2;
                    gid = u16(at2);
                    if (gid != 0) {
                        gid = (gid + delta) & 0xFFFF;
                    }
                }
                if (gid != 0) {
                    map.put(c, gid);
                }
            }
        }
        return map;
    }

    // ------------------------------------------------------------------ outlines

    /** A glyph's contours in font units: each contour a closed polygon (curves flattened). */
    private void outline(int gid, int dx, int dy, List<float[]> out, int depth) {
        if (depth > 8 || loca[gid] == loca[gid + 1]) {
            return;
        }
        int at = glyf + loca[gid];
        int contours = b.getShort(at);
        if (contours >= 0) {
            simple(at, contours, dx, dy, out);
            return;
        }
        int p = at + 10;
        while (true) {
            int flags = u16(p), part = u16(p + 2);
            p += 4;
            int ax, ay;
            if ((flags & 0x1) != 0) {
                ax = b.getShort(p);
                ay = b.getShort(p + 2);
                p += 4;
            } else {
                ax = b.get(p);
                ay = b.get(p + 1);
                p += 2;
            }
            if ((flags & 0x8) != 0) {
                p += 2;
            } else if ((flags & 0x40) != 0) {
                p += 4;
            } else if ((flags & 0x80) != 0) {
                p += 8;
            }
            // Offsets only (ARGS_ARE_XY_VALUES); a pixel font's parts are never scaled.
            if ((flags & 0x2) != 0) {
                outline(part, dx + ax, dy + ay, out, depth + 1);
            }
            if ((flags & 0x20) == 0) {
                break;
            }
        }
    }

    private void simple(int at, int contours, int dx, int dy, List<float[]> out) {
        int[] ends = new int[contours];
        for (int i = 0; i < contours; i++) {
            ends[i] = u16(at + 10 + i * 2);
        }
        int points = contours == 0 ? 0 : ends[contours - 1] + 1;
        int p = at + 10 + contours * 2;
        p += 2 + u16(p);
        int[] flags = new int[points];
        for (int i = 0; i < points; ) {
            int f = b.get(p++) & 0xFF;
            flags[i++] = f;
            if ((f & 0x8) != 0) {
                int repeat = b.get(p++) & 0xFF;
                for (int r = 0; r < repeat && i < points; r++) {
                    flags[i++] = f;
                }
            }
        }
        int[] xs = new int[points], ys = new int[points];
        int v = 0;
        for (int i = 0; i < points; i++) {
            int f = flags[i];
            if ((f & 0x2) != 0) {
                int d = b.get(p++) & 0xFF;
                v += (f & 0x10) != 0 ? d : -d;
            } else if ((f & 0x10) == 0) {
                v += b.getShort(p);
                p += 2;
            }
            xs[i] = v + dx;
        }
        v = 0;
        for (int i = 0; i < points; i++) {
            int f = flags[i];
            if ((f & 0x4) != 0) {
                int d = b.get(p++) & 0xFF;
                v += (f & 0x20) != 0 ? d : -d;
            } else if ((f & 0x20) == 0) {
                v += b.getShort(p);
                p += 2;
            }
            ys[i] = v + dy;
        }
        int start = 0;
        for (int c = 0; c < contours; c++) {
            int end = ends[c];
            out.add(flatten(xs, ys, flags, start, end));
            start = end + 1;
        }
    }

    /** One contour as a polygon, quadratic curves split into a few lines (pixel fonts have none). */
    private static float[] flatten(int[] xs, int[] ys, int[] flags, int start, int end) {
        int n = end - start + 1;
        List<Float> pts = new ArrayList<>();
        int first = -1;
        for (int i = 0; i < n; i++) {
            if ((flags[start + i] & 1) != 0) {
                first = i;
                break;
            }
        }
        float sx, sy;
        if (first < 0) {
            sx = (xs[start] + xs[start + n - 1]) / 2f;
            sy = (ys[start] + ys[start + n - 1]) / 2f;
            first = 0;
        } else {
            sx = xs[start + first];
            sy = ys[start + first];
        }
        pts.add(sx);
        pts.add(sy);
        float cx = Float.NaN, cy = Float.NaN, px = sx, py = sy;
        for (int k = 1; k <= n; k++) {
            int i = start + (first + k) % n;
            float x = xs[i], y = ys[i];
            boolean on = (flags[i] & 1) != 0;
            if (on) {
                if (!Float.isNaN(cx)) {
                    curve(pts, px, py, cx, cy, x, y);
                    cx = Float.NaN;
                } else {
                    pts.add(x);
                    pts.add(y);
                }
                px = x;
                py = y;
            } else if (!Float.isNaN(cx)) {
                float mx = (cx + x) / 2f, my = (cy + y) / 2f;
                curve(pts, px, py, cx, cy, mx, my);
                px = mx;
                py = my;
                cx = x;
                cy = y;
            } else {
                cx = x;
                cy = y;
            }
        }
        if (!Float.isNaN(cx)) {
            curve(pts, px, py, cx, cy, sx, sy);
        }
        float[] poly = new float[pts.size()];
        for (int i = 0; i < poly.length; i++) {
            poly[i] = pts.get(i);
        }
        return poly;
    }

    private static void curve(List<Float> pts, float x0, float y0, float cx, float cy, float x1, float y1) {
        for (int s = 1; s <= 6; s++) {
            float t = s / 6f, u = 1 - t;
            pts.add(u * u * x0 + 2 * u * t * cx + t * t * x1);
            pts.add(u * u * y0 + 2 * u * t * cy + t * t * y1);
        }
    }

    /** Non-zero winding of the polygons round (x, y). */
    private static int winding(List<float[]> polys, float x, float y) {
        int w = 0;
        for (float[] p : polys) {
            int n = p.length / 2;
            for (int i = 0; i < n; i++) {
                float x0 = p[i * 2], y0 = p[i * 2 + 1], x1 = p[((i + 1) % n) * 2], y1 = p[((i + 1) % n) * 2 + 1];
                if (y0 <= y) {
                    if (y1 > y && (x1 - x0) * (y - y0) - (x - x0) * (y1 - y0) > 0) {
                        w++;
                    }
                } else if (y1 <= y && (x1 - x0) * (y - y0) - (x - x0) * (y1 - y0) < 0) {
                    w--;
                }
            }
        }
        return w;
    }

    private Glyph raster(int codepoint, int gid) {
        List<float[]> polys = new ArrayList<>();
        outline(gid, 0, 0, polys, 0);
        int advance = Math.round(advances[gid] / (float) unit);
        if (polys.isEmpty()) {
            return new Glyph(codepoint, advance, 0, 0, 0, 0, new boolean[0]);
        }
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (float[] p : polys) {
            for (int i = 0; i < p.length; i += 2) {
                minX = Math.min(minX, p[i]);
                maxX = Math.max(maxX, p[i]);
                minY = Math.min(minY, p[i + 1]);
                maxY = Math.max(maxY, p[i + 1]);
            }
        }
        int x0 = (int) Math.floor(minX / unit), x1 = (int) Math.ceil(maxX / unit);
        int y0 = (int) Math.floor(minY / unit), y1 = (int) Math.ceil(maxY / unit);
        int w = Math.max(0, x1 - x0), h = Math.max(0, y1 - y0);
        boolean[] bits = new boolean[w * h];
        for (int row = 0; row < h; row++) {
            float sy = (y1 - row - 0.5f) * unit;
            for (int col = 0; col < w; col++) {
                float sx = (x0 + col + 0.5f) * unit;
                bits[row * w + col] = winding(polys, sx, sy) != 0;
            }
        }
        return new Glyph(codepoint, advance, x0, y1, w, h, bits);
    }
}
