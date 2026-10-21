package io.github.georgwav.cosmetics.sol.ui.kit;

import io.github.georgwav.cosmetics.sol.render.GuiDraw;
import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.sol.util.Sprites;
import io.github.georgwav.cosmetics.ui.Canvas;

/**
 * The kit's drawing, in Sol's pixel style: flat slabs on whole screen pixels, outlines and bevels in whole screen
 * pixels, stepped glows, pixel circles, pixel icons and pixel type (Silkscreen), plus Geist for body text. Everything
 * is placed in design pixels (see {@link Design}) and snapped to the screen's pixel grid; art drawn on a pixel grid
 * (icons, the logo, pixel type) gets a whole number of screen pixels per art pixel and is never resampled.
 *
 * <p>Geometry is batched until the texture or blend changes, or Geist text, a clip or a game draw comes in between,
 * so a screen is a few dozen GPU batches. Everything is multiplied by {@link #alpha} (fades and staggered entrances).
 * Render thread only.
 */
public final class Gfx {
    /** A colour per vertex, from its position in screen pixels. Implementations are reused, never per frame. */
    public interface Paint {
        int at(float x, float y);
    }

    /** One flat colour. */
    public static final class Solid implements Paint {
        public int argb;

        @Override
        public int at(float x, float y) {
            return argb;
        }
    }

    /** A vertical gradient between two screen-pixel heights. */
    public static final class VGrad implements Paint {
        public float y0;
        public float y1;
        public int c0;
        public int c1;

        @Override
        public int at(float x, float y) {
            float t = y1 == y0 ? 0f : Math.max(0f, Math.min(1f, (y - y0) / (y1 - y0)));
            return Colors.mix(c0, c1, t);
        }
    }

    /** A radial gradient with up to four stops (offsets 0..1 of the radius), centred in screen pixels. */
    public static final class Radial implements Paint {
        public float cx;
        public float cy;
        public float r = 1f;
        public final float[] at = new float[4];
        public final int[] color = new int[4];
        public int stops;

        public Radial set(float cx, float cy, float r) {
            this.cx = cx;
            this.cy = cy;
            this.r = Math.max(1e-3f, r);
            stops = 0;
            return this;
        }

        public Radial stop(float offset, int argb) {
            at[stops] = offset;
            color[stops] = argb;
            stops++;
            return this;
        }

        @Override
        public int at(float x, float y) {
            float dx = x - cx, dy = y - cy;
            float t = (float) Math.sqrt(dx * dx + dy * dy) / r;
            if (t <= at[0]) {
                return color[0];
            }
            for (int i = 1; i < stops; i++) {
                if (t <= at[i]) {
                    float k = (t - at[i - 1]) / Math.max(1e-4f, at[i] - at[i - 1]);
                    return Colors.mix(color[i - 1], color[i], k);
                }
            }
            return color[stops - 1];
        }
    }

    public final Design d;
    private Canvas canvas;
    /** Multiplies every colour's alpha. */
    public float alpha = 1f;

    private boolean open;
    private SolTexture tex;
    private boolean add;

    private final Solid solid = new Solid();
    private final Solid solid2 = new Solid();
    private final VGrad vgrad = new VGrad();
    /** Scratch radial gradients for callers (reused, so a frame allocates nothing). */
    public final Radial radialA = new Radial();
    public final Radial radialB = new Radial();

    // Two contours of a shape at different offsets, in screen pixels.
    private float[] ax = new float[256];
    private float[] ay = new float[256];
    private float[] bx = new float[256];
    private float[] by = new float[256];
    private float[] nx = new float[256];
    private float[] ny = new float[256];

    private static SolTexture glowTexture;

    public Gfx(Design d) {
        this.d = d;
    }

    /** Starts a frame on {@code canvas} (whose transform is already scaled to screen pixels). */
    public void begin(Canvas canvas) {
        this.canvas = canvas;
        alpha = 1f;
        open = false;
        io.github.georgwav.cosmetics.sol.render.GuiDraw.newFrame();
    }

    public Canvas canvas() {
        return canvas;
    }

    /** Sends the geometry so far, before something that has to go over it. */
    public void flush() {
        if (open) {
            GuiDraw.end();
            open = false;
            tex = null;
        }
    }

    /** Ends the frame. */
    public void end() {
        flush();
    }

    private void use(SolTexture t, boolean additive) {
        if (!open || tex != t || add != additive) {
            flush();
            GuiDraw.begin(canvas, t, additive);
            tex = t;
            add = additive;
            open = true;
        }
    }

    private void solidBatch() {
        use(GuiDraw.white(false), false);
    }

    private int a(int argb) {
        if (alpha >= 1f) {
            return argb;
        }
        return Colors.fade(argb, Math.max(0f, alpha));
    }

    private void v(float x, float y, int argb) {
        GuiDraw.vertex(x, y, 0.5f, 0.5f, a(argb));
    }

    private Solid solid(int argb) {
        solid.argb = argb;
        return solid;
    }

    /** One flat colour as a paint, for the paths and fills that take one (reused: use it before asking again). */
    public Paint flat(int argb) {
        solid2.argb = argb;
        return solid2;
    }

    // ------------------------------------------------------------------ rectangles

    /** A flat rectangle, snapped to whole screen pixels. */
    public void rect(float x, float y, float w, float h, int argb) {
        if (Colors.a(argb) == 0 || w <= 0 || h <= 0) {
            return;
        }
        float x0 = Math.round(d.sx(x)), y0 = Math.round(d.sy(y)), x1 = Math.round(d.sx(x + w)), y1 = Math.round(d.sy(y + h));
        if (x1 <= x0 || y1 <= y0) {
            return;
        }
        solidBatch();
        int c = a(argb);
        GuiDraw.vertex(x0, y0, 0.5f, 0.5f, c);
        GuiDraw.vertex(x0, y1, 0.5f, 0.5f, c);
        GuiDraw.vertex(x1, y1, 0.5f, 0.5f, c);
        GuiDraw.vertex(x1, y0, 0.5f, 0.5f, c);
    }

    /** A rectangle in screen pixels, not snapped (for the full-window veil and washes). */
    public void rectScreen(float x0, float y0, float x1, float y1, int top, int bottom) {
        solidBatch();
        GuiDraw.vertex(x0, y0, 0.5f, 0.5f, a(top));
        GuiDraw.vertex(x0, y1, 0.5f, 0.5f, a(bottom));
        GuiDraw.vertex(x1, y1, 0.5f, 0.5f, a(bottom));
        GuiDraw.vertex(x1, y0, 0.5f, 0.5f, a(top));
    }

    /** A vertical gradient rectangle. */
    public void vgradient(float x, float y, float w, float h, int top, int bottom) {
        rectScreen(d.sx(x), d.sy(y), d.sx(x + w), d.sy(y + h), top, bottom);
    }

    /** A horizontal gradient rectangle with up to five stops ({@code at} in 0..1 across). */
    public void hgradient(float x, float y, float w, float h, float[] at, int[] colors, int n) {
        solidBatch();
        float y0 = d.sy(y), y1 = d.sy(y + h);
        for (int i = 0; i + 1 < n; i++) {
            float xa = d.sx(x + w * at[i]), xb = d.sx(x + w * at[i + 1]);
            GuiDraw.vertex(xa, y0, 0.5f, 0.5f, a(colors[i]));
            GuiDraw.vertex(xa, y1, 0.5f, 0.5f, a(colors[i]));
            GuiDraw.vertex(xb, y1, 0.5f, 0.5f, a(colors[i + 1]));
            GuiDraw.vertex(xb, y0, 0.5f, 0.5f, a(colors[i + 1]));
        }
    }

    // ------------------------------------------------------------------ slabs

    private void grow(int n) {
        if (ax.length < n) {
            int size = Math.max(n, ax.length * 2);
            ax = new float[size];
            ay = new float[size];
            bx = new float[size];
            by = new float[size];
            nx = new float[size];
            ny = new float[size];
        }
    }

    /** Screen-pixel box of a design rectangle, snapped to whole pixels. */
    private float bx0, by0, bx1, by1, br;

    private void box(float x, float y, float w, float h, float r) {
        bx0 = Math.round(d.sx(x));
        by0 = Math.round(d.sy(y));
        bx1 = Math.round(d.sx(x + w));
        by1 = Math.round(d.sy(y + h));
        br = Math.min(r * d.u, Math.min(bx1 - bx0, by1 - by0) / 2f);
    }

    /** A design length as whole screen pixels, at least one: 3 design px is max(1, round(3u)). */
    public int px(float design) {
        return Math.max(1, Math.round(design * d.u));
    }

    /** A flat rectangle in whole screen pixels. */
    public void rectPx(float x0, float y0, float x1, float y1, int argb) {
        if (x1 <= x0 || y1 <= y0 || Colors.a(argb) == 0) {
            return;
        }
        solidBatch();
        int c = a(argb);
        GuiDraw.vertex(x0, y0, 0.5f, 0.5f, c);
        GuiDraw.vertex(x0, y1, 0.5f, 0.5f, c);
        GuiDraw.vertex(x1, y1, 0.5f, 0.5f, c);
        GuiDraw.vertex(x1, y0, 0.5f, 0.5f, c);
    }

    /** A square frame {@code t} screen pixels thick just inside the screen box (x0, y0)-(x1, y1). */
    public void framePx(float x0, float y0, float x1, float y1, int t, int argb) {
        if (t <= 0 || Colors.a(argb) == 0) {
            return;
        }
        t = Math.min(t, (int) Math.max(1, Math.min(x1 - x0, y1 - y0) / 2));
        rectPx(x0, y0, x1, y0 + t, argb);
        rectPx(x0, y1 - t, x1, y1, argb);
        rectPx(x0, y0 + t, x0 + t, y1 - t, argb);
        rectPx(x1 - t, y0 + t, x1, y1 - t, argb);
    }

    /** A filled slab (pixel style has no rounded corners: {@code r} is ignored). */
    public void round(float x, float y, float w, float h, float r, int argb) {
        if (Colors.a(argb) == 0) {
            return;
        }
        box(x, y, w, h, 0f);
        rectPx(bx0, by0, bx1, by1, argb);
    }

    /** A filled slab painted by {@code p}: a vertical gradient in 3 hard steps, any other paint flat (its middle). */
    public void round(float x, float y, float w, float h, float r, Paint p) {
        box(x, y, w, h, 0f);
        if (bx1 - bx0 < 1 || by1 - by0 < 1) {
            return;
        }
        if (p instanceof VGrad vg) {
            stepped(bx0, by0, bx1, by1, vg.c0, vg.c1, (vg.y1 - by0) / Math.max(1f, by1 - by0), 3);
            return;
        }
        rectPx(bx0, by0, bx1, by1, p.at((bx0 + bx1) / 2f, (by0 + by1) / 2f));
    }

    /**
     * A screen box filled from {@code top} at the top to {@code bottom} at {@code end} (a part of its height, then
     * {@code bottom} to the bottom) in {@code steps} hard bands: the prototype's gradients, not smooth.
     */
    public void stepped(float x0, float y0, float x1, float y1, int top, int bottom, float end, int steps) {
        float h = y1 - y0, stop = y0 + h * Math.max(0f, Math.min(1f, end));
        float prev = y0;
        for (int i = 0; i < steps; i++) {
            float yb = Math.round(y0 + (stop - y0) * (i + 1) / steps);
            rectPx(x0, prev, x1, yb, Colors.mix(top, bottom, i / (float) steps));
            prev = yb;
        }
        rectPx(x0, prev, x1, y1, bottom);
    }

    /** A slab filled from {@code top} down to {@code bottom}, in 3 hard steps. */
    public void roundV(float x, float y, float w, float h, float r, int top, int bottom) {
        box(x, y, w, h, 0f);
        stepped(bx0, by0, bx1, by1, top, bottom, 1f, 3);
    }

    /** A square outline {@code width} screen pixels (rounded to whole ones) wide, inside the edge. */
    public void stroke(float x, float y, float w, float h, float r, float width, int argb) {
        if (Colors.a(argb) == 0) {
            return;
        }
        box(x, y, w, h, 0f);
        framePx(bx0, by0, bx1, by1, Math.max(1, Math.round(width)), argb);
    }

    /** A square outline whose outer edge is {@code offset} design pixels outside the box (focus outlines, 2 px out). */
    public void stroke(float x, float y, float w, float h, float r, float width, Paint p, float offset) {
        box(x - offset, y - offset, w + 2 * offset, h + 2 * offset, 0f);
        if (bx1 - bx0 < 1 || by1 - by0 < 1) {
            return;
        }
        framePx(bx0, by0, bx1, by1, Math.max(1, Math.round(width)), p.at((bx0 + bx1) / 2f, by0));
    }

    /** The border every Sol panel has: 1 design pixel (at least one screen pixel). */
    public float hairline() {
        return Math.max(1f, d.u);
    }

    /**
     * A glow round a slab, in the pixel style: stepped rings, not a blur. Three square rings of falling alpha out to
     * {@code extent} design pixels.
     */
    public void glow(float x, float y, float w, float h, float r, float extent, int argb) {
        if (Colors.a(argb) == 0 || extent <= 0) {
            return;
        }
        box(x, y, w, h, 0f);
        int e = px(extent), e1 = Math.max(1, Math.round(e * 0.3f)), e2 = Math.max(e1 + 1, Math.round(e * 0.65f));
        ringPx(bx0, by0, bx1, by1, 0, e1, Colors.fade(argb, 0.6f));
        ringPx(bx0, by0, bx1, by1, e1, e2, Colors.fade(argb, 0.3f));
        ringPx(bx0, by0, bx1, by1, e2, e, Colors.fade(argb, 0.12f));
    }

    /** A square ring outside the screen box, from {@code from} to {@code to} screen pixels out. */
    public void ringPx(float x0, float y0, float x1, float y1, int from, int to, int argb) {
        if (to <= from) {
            return;
        }
        rectPx(x0 - to, y0 - to, x1 + to, y0 - from, argb);
        rectPx(x0 - to, y1 + from, x1 + to, y1 + to, argb);
        rectPx(x0 - to, y0 - from, x0 - from, y1 + from, argb);
        rectPx(x1 + from, y0 - from, x1 + to, y1 + from, argb);
    }

    /** The lit top edge inside a slab: a band 2 design px (whole screen pixels) under a 1 px border. */
    public void highlight(float x, float y, float w, float h, float r, int argb) {
        if (Colors.a(argb) == 0) {
            return;
        }
        box(x, y, w, h, 0f);
        int b = px(1f), t = px(2f);
        rectPx(bx0 + b, by0 + b, bx1 - b, by0 + b + t, argb);
    }

    /** Screen pixels per art pixel for pixel shapes drawn on the 2-design-pixel grid. */
    public int art() {
        return px(2f);
    }

    /** A filled pixel circle. */
    public void circle(float cx, float cy, float r, int argb) {
        disc(cx, cy, r, solid(argb));
    }

    public void circle(float cx, float cy, float r, Paint p) {
        disc(cx, cy, r, p);
    }

    /**
     * A pixel circle: art pixels on the 2-design-pixel grid (whole screen pixels each), filled where an art pixel's
     * middle is inside the radius, each painted by {@code p} at its middle. Hard edges, no smoothing.
     */
    public void disc(float cx, float cy, float r, Paint p) {
        int k = art();
        float scx = Math.round(d.sx(cx)), scy = Math.round(d.sy(cy));
        float sr = r * d.u;
        int n = (int) Math.ceil(sr / k);
        boolean flat = p instanceof Solid;
        solidBatch();
        for (int j = -n; j < n; j++) {
            float yc = (j + 0.5f) * k;
            float half = (float) Math.sqrt(Math.max(0f, sr * sr - yc * yc));
            int cells = Math.round(half / k);
            if (cells <= 0) {
                continue;
            }
            float y0 = scy + j * k, y1 = y0 + k;
            if (flat) {
                rectPx(scx - cells * k, y0, scx + cells * k, y1, p.at(scx, y0));
                continue;
            }
            for (int i = -cells; i < cells; i++) {
                float x0 = scx + i * k;
                rectPx(x0, y0, x0 + k, y1, p.at(x0 + k / 2f, y0 + k / 2f));
            }
        }
    }

    /** A pixel ring {@code width} screen pixels wide at radius {@code r} (design px), on the art grid. */
    public void ring(float cx, float cy, float r, float width, int argb) {
        if (Colors.a(argb) == 0) {
            return;
        }
        int k = Math.max(1, Math.min(art(), Math.round(width)));
        float scx = Math.round(d.sx(cx)), scy = Math.round(d.sy(cy));
        float ro = r * d.u, ri = Math.max(0f, ro - Math.max(k, Math.round(width)));
        int n = (int) Math.ceil(ro / k);
        for (int j = -n; j < n; j++) {
            float yc = (j + 0.5f) * k;
            float ho = (float) Math.sqrt(Math.max(0f, ro * ro - yc * yc)), hi = (float) Math.sqrt(Math.max(0f, ri * ri - yc * yc));
            int co = Math.round(ho / k), ci = Math.round(hi / k);
            if (co <= 0) {
                continue;
            }
            float y0 = scy + j * k, y1 = y0 + k;
            if (ci <= 0 || yc * yc >= ri * ri) {
                rectPx(scx - co * k, y0, scx + co * k, y1, argb);
            } else {
                rectPx(scx - co * k, y0, scx - ci * k, y1, argb);
                rectPx(scx + ci * k, y0, scx + co * k, y1, argb);
            }
        }
    }

    /**
     * A radial gradient like CSS's {@code radial-gradient(rx ry at cx cy, ...)}: an ellipse in design coordinates
     * with {@code stops}' colours at its offsets (its own centre and radius are ignored). Drawn as rings with an edge
     * at every stop, so the colour between two stops is exact; nothing is drawn past the last stop.
     */
    public void radial(float cx, float cy, float rx, float ry, Radial stops, int segments) {
        if (stops.stops == 0) {
            return;
        }
        float scx = d.sx(cx), scy = d.sy(cy), srx = rx * d.u, sry = ry * d.u;
        solidBatch();
        float prev = 0f;
        int prevColor = stops.color[0];
        for (int i = 0; i < stops.stops; i++) {
            float r0 = prev, r1 = Math.max(prev, stops.at[i]);
            int c0 = prevColor, c1 = stops.color[i];
            if (r1 > r0 && (Colors.a(c0) > 0 || Colors.a(c1) > 0)) {
                for (int k = 0; k < segments; k++) {
                    double a0 = k * Math.PI * 2 / segments, a1 = (k + 1) * Math.PI * 2 / segments;
                    float x0 = (float) Math.cos(a0), y0 = (float) Math.sin(a0), x1 = (float) Math.cos(a1), y1 = (float) Math.sin(a1);
                    v(scx + x0 * srx * r0, scy + y0 * sry * r0, c0);
                    v(scx + x0 * srx * r1, scy + y0 * sry * r1, c1);
                    v(scx + x1 * srx * r1, scy + y1 * sry * r1, c1);
                    v(scx + x1 * srx * r0, scy + y1 * sry * r0, c0);
                }
            }
            prev = r1;
            prevColor = c1;
        }
    }

    // ------------------------------------------------------------------ lines and paths

    /** A straight line {@code width} screen pixels wide, anti-aliased along its sides. Design coordinates. */
    public void line(float x0, float y0, float x1, float y1, float width, int argb) {
        lineScreen(d.sx(x0), d.sy(y0), d.sx(x1), d.sy(y1), width, solid(argb));
    }

    public void lineScreen(float x0, float y0, float x1, float y1, float width, Paint p) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-3f) {
            return;
        }
        float px = -dy / len, py = dx / len;
        float k = Math.min(1f, width);
        float hw = Math.max(1f, width) / 2f;
        solidBatch();
        // core
        quadAlong(x0, y0, x1, y1, px, py, -hw + 0.5f, k, hw - 0.5f, k, p);
        // fringes
        quadAlong(x0, y0, x1, y1, px, py, hw - 0.5f, k, hw + 0.5f, 0f, p);
        quadAlong(x0, y0, x1, y1, px, py, -hw - 0.5f, 0f, -hw + 0.5f, k, p);
    }

    private void quadAlong(float x0, float y0, float x1, float y1, float px, float py, float o0, float a0, float o1, float a1,
                           Paint p) {
        if (o1 - o0 <= 1e-3f) {
            return;
        }
        float ax0 = x0 + px * o0, ay0 = y0 + py * o0, ax1 = x1 + px * o0, ay1 = y1 + py * o0;
        float bx0 = x0 + px * o1, by0 = y0 + py * o1, bx1 = x1 + px * o1, by1 = y1 + py * o1;
        v(ax0, ay0, Colors.fade(p.at(ax0, ay0), a0));
        v(bx0, by0, Colors.fade(p.at(bx0, by0), a1));
        v(bx1, by1, Colors.fade(p.at(bx1, by1), a1));
        v(ax1, ay1, Colors.fade(p.at(ax1, ay1), a0));
    }

    /**
     * A polyline (screen pixels, {@code n} points in {@code xs}/{@code ys}) stroked {@code width} screen pixels
     * wide with anti-aliased sides and a mitred join at each point.
     */
    public void strokePath(float[] xs, float[] ys, int n, boolean closed, float width, Paint p) {
        if (n < 2) {
            return;
        }
        grow(n);
        // Normals per point: the average of the two edges' normals, lengthened for the mitre (capped).
        for (int i = 0; i < n; i++) {
            int prev = i > 0 ? i - 1 : (closed ? n - 1 : 0);
            int next = i < n - 1 ? i + 1 : (closed ? 0 : n - 1);
            float ex0 = xs[i] - xs[prev], ey0 = ys[i] - ys[prev];
            float ex1 = xs[next] - xs[i], ey1 = ys[next] - ys[i];
            float l0 = (float) Math.sqrt(ex0 * ex0 + ey0 * ey0), l1 = (float) Math.sqrt(ex1 * ex1 + ey1 * ey1);
            float n0x = 0, n0y = 0, n1x = 0, n1y = 0;
            if (l0 > 1e-4f) {
                n0x = -ey0 / l0;
                n0y = ex0 / l0;
            }
            if (l1 > 1e-4f) {
                n1x = -ey1 / l1;
                n1y = ex1 / l1;
            }
            if (l0 <= 1e-4f) {
                n0x = n1x;
                n0y = n1y;
            }
            if (l1 <= 1e-4f) {
                n1x = n0x;
                n1y = n0y;
            }
            float mx = n0x + n1x, my = n0y + n1y;
            float ml = (float) Math.sqrt(mx * mx + my * my);
            if (ml < 1e-4f) {
                nx[i] = n0x;
                ny[i] = n0y;
            } else {
                mx /= ml;
                my /= ml;
                float dot = mx * n0x + my * n0y;
                float scale = 1f / Math.max(0.35f, dot);
                nx[i] = mx * scale;
                ny[i] = my * scale;
            }
        }
        float k = Math.min(1f, width);
        float hw = Math.max(1f, width) / 2f;
        solidBatch();
        int segs = closed ? n : n - 1;
        for (int s = 0; s < segs; s++) {
            int i = s, j = (s + 1) % n;
            strip(xs, ys, i, j, -hw + 0.5f, k, hw - 0.5f, k, p);
            strip(xs, ys, i, j, hw - 0.5f, k, hw + 0.5f, 0f, p);
            strip(xs, ys, i, j, -hw - 0.5f, 0f, -hw + 0.5f, k, p);
        }
    }

    private void strip(float[] xs, float[] ys, int i, int j, float o0, float a0, float o1, float a1, Paint p) {
        if (o1 - o0 <= 1e-3f) {
            return;
        }
        float aix = xs[i] + nx[i] * o0, aiy = ys[i] + ny[i] * o0, ajx = xs[j] + nx[j] * o0, ajy = ys[j] + ny[j] * o0;
        float bix = xs[i] + nx[i] * o1, biy = ys[i] + ny[i] * o1, bjx = xs[j] + nx[j] * o1, bjy = ys[j] + ny[j] * o1;
        v(aix, aiy, Colors.fade(p.at(aix, aiy), a0));
        v(bix, biy, Colors.fade(p.at(bix, biy), a1));
        v(bjx, bjy, Colors.fade(p.at(bjx, bjy), a1));
        v(ajx, ajy, Colors.fade(p.at(ajx, ajy), a0));
    }

    /**
     * Fills a shape bounded by two polylines of the same length (screen pixels), quad by quad: an annulus sector,
     * a petal, a ribbon. {@code inner} and {@code outer} run side by side; each quad is painted per corner.
     */
    public void fillBetween(float[] ix, float[] iy, float[] ox, float[] oy, int n, Paint p) {
        solidBatch();
        for (int i = 0; i + 1 < n; i++) {
            v(ix[i], iy[i], p.at(ix[i], iy[i]));
            v(ox[i], oy[i], p.at(ox[i], oy[i]));
            v(ox[i + 1], oy[i + 1], p.at(ox[i + 1], oy[i + 1]));
            v(ix[i + 1], iy[i + 1], p.at(ix[i + 1], iy[i + 1]));
        }
    }

    /** A filled convex polygon (screen pixels) fanned from its first point, with an anti-aliased rim. */
    public void fillConvex(float[] xs, float[] ys, int n, Paint p) {
        if (n < 3) {
            return;
        }
        solidBatch();
        for (int i = 1; i + 1 < n; i++) {
            v(xs[0], ys[0], p.at(xs[0], ys[0]));
            v(xs[i], ys[i], p.at(xs[i], ys[i]));
            v(xs[i + 1], ys[i + 1], p.at(xs[i + 1], ys[i + 1]));
            v(xs[i + 1], ys[i + 1], p.at(xs[i + 1], ys[i + 1]));
        }
        // A one-pixel fade outside the rim (the normals of a clockwise outline point outwards).
        float cx = 0, cy = 0;
        for (int i = 0; i < n; i++) {
            cx += xs[i];
            cy += ys[i];
        }
        cx /= n;
        cy /= n;
        grow(n);
        for (int i = 0; i < n; i++) {
            int prev = (i + n - 1) % n, next = (i + 1) % n;
            float tx = xs[next] - xs[prev], ty = ys[next] - ys[prev];
            float l = (float) Math.sqrt(tx * tx + ty * ty);
            float mx = l > 1e-4f ? -ty / l : 0f, my = l > 1e-4f ? tx / l : 0f;
            if ((xs[i] - cx) * mx + (ys[i] - cy) * my < 0) {
                mx = -mx;
                my = -my;
            }
            nx[i] = mx;
            ny[i] = my;
        }
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            float aix = xs[i] - nx[i] * 0.5f, aiy = ys[i] - ny[i] * 0.5f, ajx = xs[j] - nx[j] * 0.5f, ajy = ys[j] - ny[j] * 0.5f;
            float bix = xs[i] + nx[i] * 0.5f, biy = ys[i] + ny[i] * 0.5f, bjx = xs[j] + nx[j] * 0.5f, bjy = ys[j] + ny[j] * 0.5f;
            v(aix, aiy, p.at(aix, aiy));
            v(bix, biy, Colors.fade(p.at(bix, biy), 0f));
            v(bjx, bjy, Colors.fade(p.at(bjx, bjy), 0f));
            v(ajx, ajy, p.at(ajx, ajy));
        }
    }

    // ------------------------------------------------------------------ textures

    /** The kit's soft round glow (premultiplied, drawn added). */
    public static SolTexture glowTexture() {
        if (glowTexture == null || glowTexture.released()) {
            // The pixel style's glow: the soft glow's alpha in four hard steps (stepped rings, not a blur).
            int[] soft = Sprites.glow(0xFFFFFFFF).toArgb(null);
            for (int i = 0; i < soft.length; i++) {
                int a = soft[i] >>> 24;
                int q = a <= 8 ? 0 : Math.min(255, (int) Math.ceil(a / 64f) * 64 - 1);
                soft[i] = (q << 24) | (soft[i] & 0xFFFFFF);
            }
            glowTexture = SolTexture.premultiplied("gui_glow", 64, 64, false, soft);
        }
        return glowTexture;
    }

    /** A glow added at (cx, cy) with radii rx, ry (design pixels), the prototypes' {@code drawImage(glow(c))}. */
    public void glowSprite(float cx, float cy, float rx, float ry, int argb) {
        if (Colors.a(argb) == 0) {
            return;
        }
        use(glowTexture(), true);
        float x0 = d.sx(cx - rx), y0 = d.sy(cy - ry), x1 = d.sx(cx + rx), y1 = d.sy(cy + ry);
        int c = a(argb);
        GuiDraw.vertex(x0, y0, 0f, 0f, c);
        GuiDraw.vertex(x0, y1, 0f, 1f, c);
        GuiDraw.vertex(x1, y1, 1f, 1f, c);
        GuiDraw.vertex(x1, y0, 1f, 0f, c);
    }

    /** A texture region drawn over a design rectangle, tinted, blended or added (a premultiplied texture). */
    public void image(SolTexture t, float x, float y, float w, float h, float u0, float v0, float u1, float v1, int argb,
                      boolean additive) {
        imageScreen(t, d.sx(x), d.sy(y), d.sx(x + w), d.sy(y + h), u0, v0, u1, v1, argb, additive);
    }

    /** A texture region drawn over a screen-pixel rectangle. */
    public void imageScreen(SolTexture t, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1,
                            int argb, boolean additive) {
        if (t == null || t.released() || Colors.a(argb) == 0) {
            return;
        }
        use(t, additive);
        int c = a(argb);
        GuiDraw.vertex(x0, y0, u0, v0, c);
        GuiDraw.vertex(x0, y1, u0, v1, c);
        GuiDraw.vertex(x1, y1, u1, v1, c);
        GuiDraw.vertex(x1, y0, u1, v0, c);
    }

    /**
     * A texture over a shape bounded by two polylines (screen pixels, as {@link #fillBetween}), each point with its
     * own place on the texture: a picture seen through an annulus sector.
     */
    public void imageBetween(SolTexture t, float[] ix, float[] iy, float[] ox, float[] oy, float[] iu, float[] iv,
                             float[] ou, float[] ov, int n, int argb) {
        if (t == null || t.released() || Colors.a(argb) == 0) {
            return;
        }
        use(t, false);
        int c = a(argb);
        for (int i = 0; i + 1 < n; i++) {
            GuiDraw.vertex(ix[i], iy[i], iu[i], iv[i], c);
            GuiDraw.vertex(ox[i], oy[i], ou[i], ov[i], c);
            GuiDraw.vertex(ox[i + 1], oy[i + 1], ou[i + 1], ov[i + 1], c);
            GuiDraw.vertex(ix[i + 1], iy[i + 1], iu[i + 1], iv[i + 1], c);
        }
    }

    /** A texture quad with free corners (rotated sprites), screen pixels. */
    public void imageQuad(SolTexture t, float[] xs, float[] ys, int argb, boolean additive) {
        if (t == null || t.released() || Colors.a(argb) == 0) {
            return;
        }
        use(t, additive);
        int c = a(argb);
        GuiDraw.vertex(xs[0], ys[0], 0f, 0f, c);
        GuiDraw.vertex(xs[1], ys[1], 0f, 1f, c);
        GuiDraw.vertex(xs[2], ys[2], 1f, 1f, c);
        GuiDraw.vertex(xs[3], ys[3], 1f, 0f, c);
    }

    // ------------------------------------------------------------------ text

    /** The font for a size in design pixels. */
    public SolFont font(SolFont.Family family, float size) {
        return SolFont.of(family, size * d.u);
    }

    /**
     * While on, every text gets a hard dark drop under it (a HUD element with no background, over a bright sky).
     */
    public boolean shadow;
    /**
     * Text over the world: a hard black drop this many design pixels under every text (whole screen pixels), never a
     * blurred one. 0 for none.
     */
    public float drop;

    /** Text with its left at {@code x} and baseline at {@code baseline} (design pixels). */
    public void text(SolFont f, String s, float x, float baseline, int argb) {
        int c = a(argb);
        if (Colors.a(c) < 8 || s == null || s.isEmpty()) {
            return;
        }
        int dropPx = drop > 0f ? px(drop) : shadow ? Math.max(1, Math.round(d.u * 0.5f)) : 0;
        int dropColour = Math.round(Colors.a(c) * (drop > 0f ? 1f : 0.75f)) << 24;
        if (f.family.pixel()) {
            if (f.pixelDraws(s)) {
                // Pixel type on whole screen pixels, batched with the shapes.
                boolean bold = f.family == SolFont.Family.PIXEL_BOLD;
                float sx = Math.round(d.sx(x)), sb = Math.round(d.sy(baseline));
                if (dropPx > 0) {
                    PixelFont.draw(this, bold, s, sx, sb + dropPx, f.k, dropColour);
                }
                PixelFont.draw(this, bold, s, sx, sb, f.k, c);
                return;
            }
            f = f.fallback();
        }
        flush();
        if (dropPx > 0) {
            f.draw(canvas, s, Math.round(d.sx(x)), Math.round(d.sy(baseline)) + dropPx, dropColour);
        }
        f.draw(canvas, s, d.sx(x), d.sy(baseline), c);
    }

    /** Text with its capitals centred on {@code cy}: what sits in the middle of a pill or a row. */
    public void textMid(SolFont f, String s, float x, float cy, int argb) {
        text(f, s, x, cy + f.cap() / 2f / d.u, argb);
    }

    /** Text centred on {@code cx}, capitals centred on {@code cy}. */
    public void textCentre(SolFont f, String s, float cx, float cy, int argb) {
        textMid(f, s, cx - width(f, s) / 2f, cy, argb);
    }

    /** Text ending at {@code right}. */
    public void textRight(SolFont f, String s, float right, float cy, int argb) {
        textMid(f, s, right - width(f, s), cy, argb);
    }

    /** Letter-spaced text ({@code spacing} in design pixels), capitals centred on {@code cy}. */
    public void textSpaced(SolFont f, String s, float x, float cy, float spacing, int argb) {
        if (f.family.pixel()) {
            textMid(f, s, x, cy, argb);
            return;
        }
        int c = a(argb);
        if (Colors.a(c) < 8 || s.isEmpty()) {
            return;
        }
        flush();
        f.drawSpaced(canvas, s, d.sx(x), d.sy(cy) + f.cap() / 2f, spacing * d.u, c);
    }

    /** Width in design pixels. */
    public float width(SolFont f, String s) {
        return f.width(s) / d.u;
    }

    public float width(SolFont f, String s, float spacing) {
        return f.width(s, spacing * d.u) / d.u;
    }

    /** Shortened with an ellipsis to fit {@code room} design pixels. */
    public String fit(SolFont f, String s, float room) {
        return f.fit(s, room * d.u);
    }

    // ------------------------------------------------------------------ clipping and the game's own drawing

    private final float[] clipStack = new float[64];
    private int clipDepth;

    /** Clips everything after this to a design rectangle (nested clips intersect) until {@link #endClip()}. */
    public void clip(float x0, float y0, float x1, float y1) {
        flush();
        float sx0 = d.sx(x0), sy0 = d.sy(y0), sx1 = d.sx(x1), sy1 = d.sy(y1);
        if (clipDepth > 0) {
            int o = (clipDepth - 1) * 4;
            sx0 = Math.max(sx0, clipStack[o]);
            sy0 = Math.max(sy0, clipStack[o + 1]);
            sx1 = Math.min(sx1, clipStack[o + 2]);
            sy1 = Math.min(sy1, clipStack[o + 3]);
        }
        sx1 = Math.max(sx0, sx1);
        sy1 = Math.max(sy0, sy1);
        if (clipDepth < clipStack.length / 4) {
            int o = clipDepth * 4;
            clipStack[o] = sx0;
            clipStack[o + 1] = sy0;
            clipStack[o + 2] = sx1;
            clipStack[o + 3] = sy1;
        }
        clipDepth++;
        canvas.clipScreen(sx0, sy0, sx1, sy1, d.gs);
    }

    public void endClip() {
        flush();
        if (clipDepth > 0) {
            clipDepth--;
            canvas.endClip();
        }
    }

    /** The current clip in design pixels into {@code out} (x0, y0, x1, y1); false when nothing is clipped. */
    public boolean clipRect(float[] out) {
        if (clipDepth == 0) {
            return false;
        }
        int o = (clipDepth - 1) * 4;
        out[0] = (clipStack[o] - d.ox) / d.u;
        out[1] = (clipStack[o + 1] - d.oy) / d.u;
        out[2] = (clipStack[o + 2] - d.ox) / d.u;
        out[3] = (clipStack[o + 3] - d.oy) / d.u;
        return true;
    }

    /** Whether a design point is inside the current clip. */
    public boolean inClip(float x, float y) {
        if (clipDepth == 0) {
            return true;
        }
        int o = (clipDepth - 1) * 4;
        float sx = d.sx(x), sy = d.sy(y);
        return sx >= clipStack[o] && sx < clipStack[o + 2] && sy >= clipStack[o + 1] && sy < clipStack[o + 3];
    }

    /** Before the game draws something itself (an item, a skin, an entity): ours so far goes under it. */
    public Canvas game() {
        flush();
        return canvas;
    }

    /** What is drawn after this goes over what was drawn before (see {@link Canvas#nextLayer()}). */
    public void layer() {
        flush();
        canvas.nextLayer();
    }
}
