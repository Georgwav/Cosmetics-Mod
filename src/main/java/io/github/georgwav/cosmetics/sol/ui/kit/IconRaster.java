package io.github.georgwav.cosmetics.sol.ui.kit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the Sol line icons (the menu prototype's SOL_ICONS, as shapes in assets/cosmeticsmod/sol/icons.json) at an
 * exact pixel size: 24-unit line icons, 1.75 strokes with round caps and joins, and one soft fill per icon kept in a
 * layer of its own so it can be drawn at 16% (30% when active). Rasterised with 4×4 samples per pixel straight at
 * the size it is shown, so an icon is sharp at every GUI scale. Pure Java (no game classes), so it is tested alone.
 */
public final class IconRaster {
    /** The icon grid and its stroke width, in grid units. */
    public static final float GRID = 24f;
    public static final float STROKE = 1.75f;
    private static final int SS = 4;

    /** One icon's outlines in grid units: polylines to stroke and closed polygons to fill (the soft part). */
    public static final class Shape {
        public final List<float[]> strokes = new ArrayList<>();
        public final List<float[]> fills = new ArrayList<>();
    }

    private IconRaster() {
    }

    /** An icon from its shapes as icons.json stores them. */
    public static Shape parse(JsonArray shapes) {
        Shape out = new Shape();
        for (JsonElement e : shapes) {
            JsonObject o = e.getAsJsonObject();
            boolean duo = o.has("duo") && o.get("duo").getAsBoolean();
            List<float[]> polys = new ArrayList<>();
            boolean closed = true;
            switch (o.get("type").getAsString()) {
                case "path" -> {
                    PathData p = PathData.parse(o.get("d").getAsString());
                    polys.addAll(p.subpaths);
                    closed = false;
                    if (duo) {
                        out.fills.addAll(p.subpaths);
                        continue;
                    }
                    for (int i = 0; i < p.subpaths.size(); i++) {
                        float[] sp = p.subpaths.get(i);
                        out.strokes.add(p.closed.get(i) ? close(sp) : sp);
                    }
                    continue;
                }
                case "rect" -> polys.add(roundRect(f(o, "x"), f(o, "y"), f(o, "w"), f(o, "h"), f(o, "rx")));
                case "circle" -> polys.add(ellipse(f(o, "cx"), f(o, "cy"), f(o, "r"), f(o, "r")));
                case "ellipse" -> polys.add(ellipse(f(o, "cx"), f(o, "cy"), f(o, "rx"), f(o, "ry")));
                default -> {
                    continue;
                }
            }
            if (o.has("rotate")) {
                JsonArray r = o.getAsJsonArray("rotate");
                rotate(polys.get(0), r.get(0).getAsFloat(), r.get(1).getAsFloat(), r.get(2).getAsFloat());
            }
            if (duo) {
                out.fills.addAll(polys);
            } else if (o.has("dash")) {
                JsonArray d = o.getAsJsonArray("dash");
                out.strokes.addAll(dash(close(polys.get(0)), d.get(0).getAsFloat(), d.get(1).getAsFloat()));
            } else {
                for (float[] p : polys) {
                    out.strokes.add(closed ? close(p) : p);
                }
            }
        }
        return out;
    }

    private static float f(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsFloat() : 0f;
    }

    /**
     * The icon at {@code px} x {@code px} pixels: {@code fill} gets the soft part's coverage, {@code stroke} the
     * lines' (0..1 per pixel, row by row).
     */
    public static void raster(Shape s, int px, float[] fill, float[] stroke) {
        float k = px / GRID;
        int n = px * SS;
        // Lines: every sample within half a stroke of a segment (round caps and joins come out of that).
        long[] bits = new long[px * px];
        float half = STROKE * k / 2f * SS;
        for (float[] line : s.strokes) {
            for (int i = 0; i + 3 < line.length; i += 2) {
                capsule(bits, px, line[i] * k * SS, line[i + 1] * k * SS, line[i + 2] * k * SS, line[i + 3] * k * SS, half, n);
            }
            if (line.length == 2) {
                capsule(bits, px, line[0] * k * SS, line[1] * k * SS, line[0] * k * SS, line[1] * k * SS, half, n);
            }
        }
        for (int i = 0; i < px * px; i++) {
            stroke[i] = Long.bitCount(bits[i]) / (float) (SS * SS);
        }
        // The soft fill: non-zero winding of all the fill outlines together, per sample row.
        java.util.Arrays.fill(bits, 0L);
        float[] xs = new float[64];
        int[] dirs = new int[64];
        for (int sy = 0; sy < n; sy++) {
            float y = (sy + 0.5f) / (k * SS);
            int count = 0;
            for (float[] poly : s.fills) {
                int m = poly.length / 2;
                for (int i = 0; i < m; i++) {
                    float x0 = poly[i * 2], y0 = poly[i * 2 + 1];
                    float x1 = poly[((i + 1) % m) * 2], y1 = poly[((i + 1) % m) * 2 + 1];
                    if ((y0 <= y && y1 > y) || (y1 <= y && y0 > y)) {
                        if (count == xs.length) {
                            xs = java.util.Arrays.copyOf(xs, count * 2);
                            dirs = java.util.Arrays.copyOf(dirs, count * 2);
                        }
                        xs[count] = x0 + (y - y0) / (y1 - y0) * (x1 - x0);
                        dirs[count] = y1 > y0 ? 1 : -1;
                        count++;
                    }
                }
            }
            for (int sx = 0; sx < n; sx++) {
                float x = (sx + 0.5f) / (k * SS);
                int wind = 0;
                for (int c = 0; c < count; c++) {
                    if (xs[c] < x) {
                        wind += dirs[c];
                    }
                }
                if (wind != 0) {
                    int p = (sy / SS) * px + sx / SS;
                    bits[p] |= 1L << ((sy % SS) * SS + sx % SS);
                }
            }
        }
        for (int i = 0; i < px * px; i++) {
            fill[i] = Long.bitCount(bits[i]) / (float) (SS * SS);
        }
    }

    private static void capsule(long[] bits, int px, float ax, float ay, float bx, float by, float r, int n) {
        int x0 = Math.max(0, (int) Math.floor(Math.min(ax, bx) - r)), x1 = Math.min(n - 1, (int) Math.ceil(Math.max(ax, bx) + r));
        int y0 = Math.max(0, (int) Math.floor(Math.min(ay, by) - r)), y1 = Math.min(n - 1, (int) Math.ceil(Math.max(ay, by) + r));
        float dx = bx - ax, dy = by - ay, len2 = dx * dx + dy * dy, r2 = r * r;
        for (int sy = y0; sy <= y1; sy++) {
            float py = sy + 0.5f;
            for (int sx = x0; sx <= x1; sx++) {
                float pxs = sx + 0.5f;
                float t = len2 > 0f ? Math.max(0f, Math.min(1f, ((pxs - ax) * dx + (py - ay) * dy) / len2)) : 0f;
                float cx = ax + dx * t - pxs, cy = ay + dy * t - py;
                if (cx * cx + cy * cy <= r2) {
                    bits[(sy / SS) * px + sx / SS] |= 1L << ((sy % SS) * SS + sx % SS);
                }
            }
        }
    }

    // ------------------------------------------------------------------ shapes as polylines

    private static float[] close(float[] p) {
        if (p.length >= 4 && (p[0] != p[p.length - 2] || p[1] != p[p.length - 1])) {
            float[] c = java.util.Arrays.copyOf(p, p.length + 2);
            c[p.length] = p[0];
            c[p.length + 1] = p[1];
            return c;
        }
        return p;
    }

    static float[] ellipse(float cx, float cy, float rx, float ry) {
        int n = 64;
        float[] out = new float[n * 2];
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            out[i * 2] = cx + (float) Math.cos(a) * rx;
            out[i * 2 + 1] = cy + (float) Math.sin(a) * ry;
        }
        return out;
    }

    static float[] roundRect(float x, float y, float w, float h, float r) {
        r = Math.min(r, Math.min(w, h) / 2f);
        int seg = r > 0f ? 8 : 0;
        List<Float> pts = new ArrayList<>();
        float[][] c = {{x + w - r, y + r, -90}, {x + w - r, y + h - r, 0}, {x + r, y + h - r, 90}, {x + r, y + r, 180}};
        for (float[] corner : c) {
            for (int i = 0; i <= seg; i++) {
                double a = Math.toRadians(corner[2] + (seg == 0 ? 0 : 90.0 * i / seg));
                pts.add(corner[0] + (float) Math.cos(a) * r);
                pts.add(corner[1] + (float) Math.sin(a) * r);
            }
        }
        float[] out = new float[pts.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = pts.get(i);
        }
        return out;
    }

    private static void rotate(float[] p, float deg, float cx, float cy) {
        double a = Math.toRadians(deg);
        float cs = (float) Math.cos(a), sn = (float) Math.sin(a);
        for (int i = 0; i < p.length; i += 2) {
            float dx = p[i] - cx, dy = p[i + 1] - cy;
            p[i] = cx + dx * cs - dy * sn;
            p[i + 1] = cy + dx * sn + dy * cs;
        }
    }

    /** A closed outline cut into dashes {@code on} long with {@code off} gaps, along its length. */
    static List<float[]> dash(float[] p, float on, float off) {
        List<float[]> out = new ArrayList<>();
        List<Float> cur = new ArrayList<>();
        boolean drawing = true;
        float left = on;
        for (int i = 0; i + 3 < p.length; i += 2) {
            float ax = p[i], ay = p[i + 1], bx = p[i + 2], by = p[i + 3];
            float len = (float) Math.hypot(bx - ax, by - ay), pos = 0f;
            while (pos < len) {
                float step = Math.min(left, len - pos);
                float t0 = pos / len, t1 = (pos + step) / len;
                if (drawing) {
                    if (cur.isEmpty()) {
                        cur.add(ax + (bx - ax) * t0);
                        cur.add(ay + (by - ay) * t0);
                    }
                    cur.add(ax + (bx - ax) * t1);
                    cur.add(ay + (by - ay) * t1);
                }
                pos += step;
                left -= step;
                if (left <= 1e-5f) {
                    if (drawing && cur.size() >= 4) {
                        float[] d = new float[cur.size()];
                        for (int k = 0; k < d.length; k++) {
                            d[k] = cur.get(k);
                        }
                        out.add(d);
                    }
                    cur.clear();
                    drawing = !drawing;
                    left = drawing ? on : off;
                }
            }
        }
        if (drawing && cur.size() >= 4) {
            float[] d = new float[cur.size()];
            for (int k = 0; k < d.length; k++) {
                d[k] = cur.get(k);
            }
            out.add(d);
        }
        return out;
    }

    /** SVG path data flattened to polylines (absolute and relative M, L, H, V, C, S, Q, T, A, Z). */
    static final class PathData {
        final List<float[]> subpaths = new ArrayList<>();
        final List<Boolean> closed = new ArrayList<>();
        private final List<Float> cur = new ArrayList<>();
        private float x, y, sx, sy, lcx, lcy;
        private char lastCmd;

        static PathData parse(String d) {
            PathData p = new PathData();
            Tokens t = new Tokens(d);
            char cmd = 0;
            while (t.more()) {
                if (t.isCommand()) {
                    cmd = t.command();
                }
                p.step(cmd, t);
                // After a move, further pairs are lines.
                if (cmd == 'M') {
                    cmd = 'L';
                } else if (cmd == 'm') {
                    cmd = 'l';
                }
            }
            p.end(false);
            return p;
        }

        private void step(char cmd, Tokens t) {
            boolean rel = Character.isLowerCase(cmd);
            float ox = rel ? x : 0f, oy = rel ? y : 0f;
            switch (Character.toUpperCase(cmd)) {
                case 'M' -> {
                    end(false);
                    x = ox + t.num();
                    y = oy + t.num();
                    sx = x;
                    sy = y;
                    add(x, y);
                }
                case 'L' -> {
                    x = ox + t.num();
                    y = oy + t.num();
                    add(x, y);
                }
                case 'H' -> {
                    x = ox + t.num();
                    add(x, y);
                }
                case 'V' -> {
                    y = oy + t.num();
                    add(x, y);
                }
                case 'C' -> {
                    float x1 = ox + t.num(), y1 = oy + t.num(), x2 = ox + t.num(), y2 = oy + t.num();
                    float ex = ox + t.num(), ey = oy + t.num();
                    cubic(x1, y1, x2, y2, ex, ey);
                }
                case 'S' -> {
                    boolean smooth = "CcSs".indexOf(lastCmd) >= 0;
                    float x1 = smooth ? 2 * x - lcx : x, y1 = smooth ? 2 * y - lcy : y;
                    float x2 = ox + t.num(), y2 = oy + t.num(), ex = ox + t.num(), ey = oy + t.num();
                    cubic(x1, y1, x2, y2, ex, ey);
                }
                case 'Q' -> {
                    float qx = ox + t.num(), qy = oy + t.num(), ex = ox + t.num(), ey = oy + t.num();
                    quad(qx, qy, ex, ey);
                }
                case 'T' -> {
                    boolean smooth = "QqTt".indexOf(lastCmd) >= 0;
                    float qx = smooth ? 2 * x - lcx : x, qy = smooth ? 2 * y - lcy : y;
                    float ex = ox + t.num(), ey = oy + t.num();
                    quad(qx, qy, ex, ey);
                }
                case 'A' -> {
                    float rx = t.num(), ry = t.num(), rot = t.num();
                    boolean large = t.flag(), sweep = t.flag();
                    float ex = ox + t.num(), ey = oy + t.num();
                    arc(rx, ry, rot, large, sweep, ex, ey);
                }
                case 'Z' -> {
                    x = sx;
                    y = sy;
                    add(x, y);
                    end(true);
                    add(x, y);
                }
                default -> t.num();
            }
            lastCmd = cmd;
        }

        private void add(float px, float py) {
            cur.add(px);
            cur.add(py);
        }

        private void end(boolean wasClosed) {
            if (cur.size() >= 4) {
                float[] a = new float[cur.size()];
                for (int i = 0; i < a.length; i++) {
                    a[i] = cur.get(i);
                }
                subpaths.add(a);
                closed.add(wasClosed);
            }
            cur.clear();
        }

        private void cubic(float x1, float y1, float x2, float y2, float ex, float ey) {
            int n = 16;
            for (int i = 1; i <= n; i++) {
                float t = i / (float) n, u = 1 - t;
                add(u * u * u * x + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t * t * t * ex,
                        u * u * u * y + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * ey);
            }
            lcx = x2;
            lcy = y2;
            x = ex;
            y = ey;
        }

        private void quad(float qx, float qy, float ex, float ey) {
            int n = 12;
            for (int i = 1; i <= n; i++) {
                float t = i / (float) n, u = 1 - t;
                add(u * u * x + 2 * u * t * qx + t * t * ex, u * u * y + 2 * u * t * qy + t * t * ey);
            }
            lcx = qx;
            lcy = qy;
            x = ex;
            y = ey;
        }

        /** The SVG endpoint arc, through its centre form (SVG 1.1 appendix F.6). */
        private void arc(float rx, float ry, float rotDeg, boolean large, boolean sweep, float ex, float ey) {
            if (rx == 0f || ry == 0f) {
                x = ex;
                y = ey;
                add(x, y);
                return;
            }
            rx = Math.abs(rx);
            ry = Math.abs(ry);
            double phi = Math.toRadians(rotDeg), cs = Math.cos(phi), sn = Math.sin(phi);
            double dx = (x - ex) / 2.0, dy = (y - ey) / 2.0;
            double x1p = cs * dx + sn * dy, y1p = -sn * dx + cs * dy;
            double lam = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry);
            if (lam > 1) {
                rx *= (float) Math.sqrt(lam);
                ry *= (float) Math.sqrt(lam);
            }
            double num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p;
            double den = rx * rx * y1p * y1p + ry * ry * x1p * x1p;
            double co = Math.sqrt(Math.max(0, num / den)) * (large == sweep ? -1 : 1);
            double cxp = co * rx * y1p / ry, cyp = -co * ry * x1p / rx;
            double cx = cs * cxp - sn * cyp + (x + ex) / 2.0, cy = sn * cxp + cs * cyp + (y + ey) / 2.0;
            double a1 = Math.atan2((y1p - cyp) / ry, (x1p - cxp) / rx);
            double a2 = Math.atan2((-y1p - cyp) / ry, (-x1p - cxp) / rx);
            double da = a2 - a1;
            if (sweep && da < 0) {
                da += Math.PI * 2;
            } else if (!sweep && da > 0) {
                da -= Math.PI * 2;
            }
            int n = Math.max(4, (int) Math.ceil(Math.abs(da) / (Math.PI / 16)));
            for (int i = 1; i <= n; i++) {
                double a = a1 + da * i / n;
                double px = cx + rx * Math.cos(a) * cs - ry * Math.sin(a) * sn;
                double py = cy + rx * Math.cos(a) * sn + ry * Math.sin(a) * cs;
                add((float) px, (float) py);
            }
            x = ex;
            y = ey;
        }
    }

    /** Path data tokens: command letters and numbers (with SVG's compact forms like "1.5.5" and "-2-3"). */
    private static final class Tokens {
        private final String s;
        private int i;

        Tokens(String s) {
            this.s = s;
        }

        private void skip() {
            while (i < s.length() && (Character.isWhitespace(s.charAt(i)) || s.charAt(i) == ',')) {
                i++;
            }
        }

        boolean more() {
            skip();
            return i < s.length();
        }

        boolean isCommand() {
            skip();
            return i < s.length() && Character.isLetter(s.charAt(i)) && s.charAt(i) != 'e' && s.charAt(i) != 'E';
        }

        char command() {
            skip();
            return s.charAt(i++);
        }

        /** An arc flag: one character, 0 or 1 (SVG lets them run into the next number). */
        boolean flag() {
            skip();
            return i < s.length() && s.charAt(i++) == '1';
        }

        float num() {
            skip();
            int start = i;
            if (i < s.length() && (s.charAt(i) == '-' || s.charAt(i) == '+')) {
                i++;
            }
            boolean dot = false;
            while (i < s.length()) {
                char c = s.charAt(i);
                if (Character.isDigit(c)) {
                    i++;
                } else if (c == '.' && !dot) {
                    dot = true;
                    i++;
                } else if ((c == 'e' || c == 'E') && i + 1 < s.length()) {
                    i++;
                    if (s.charAt(i) == '-' || s.charAt(i) == '+') {
                        i++;
                    }
                } else {
                    break;
                }
            }
            if (start == i) {
                // A flag in an arc can be a lone digit glued to the next number ("0 01 5 5"); otherwise skip.
                i++;
                return 0f;
            }
            return Float.parseFloat(s.substring(start, i));
        }
    }
}
