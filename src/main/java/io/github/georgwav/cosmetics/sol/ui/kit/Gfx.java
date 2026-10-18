package io.github.georgwav.cosmetics.sol.ui.kit;

import io.github.georgwav.cosmetics.sol.render.GuiDraw;
import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.sol.util.Sprites;
import io.github.georgwav.cosmetics.ui.Canvas;

/**
 * The kit's drawing: smooth shapes, glass, glows, pixel icons and text, in design pixels (see {@link Design}).
 *
 * <p>Shapes are tessellated into vertex-coloured triangles with a one-pixel fringe that fades to nothing, so their
 * edges are anti-aliased like the prototypes' CSS (and land crisp on whole pixels). Geometry is batched until the
 * texture or blend changes, or text, a clip or a game draw comes in between, so a screen is a few dozen GPU batches.
 * Everything is multiplied by {@link #alpha} (fades and staggered entrances). Render thread only.
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

    // ------------------------------------------------------------------ rounded shapes

    private static int segments(float radius) {
        return Math.max(2, Math.min(16, (int) Math.ceil(radius / 2.2f)));
    }

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

    /**
     * The contour of a rounded rectangle (screen pixels) grown by {@code off} (negative shrinks), clockwise from the
     * top-left corner, {@code seg} segments per corner, into (xs, ys); also writes the outward normals.
     * Returns the number of points.
     */
    private int contour(float x0, float y0, float x1, float y1, float r, float off, int seg, float[] xs, float[] ys) {
        int n = 0;
        float inset = Math.max(r, -off);
        float rr = Math.max(0f, r + off);
        float[] cxs = {x0 + inset, x1 - inset, x1 - inset, x0 + inset};
        float[] cys = {y0 + inset, y0 + inset, y1 - inset, y1 - inset};
        for (int c = 0; c < 4; c++) {
            float start = (float) Math.PI * (1f + 0.5f * c);
            for (int i = 0; i <= seg; i++) {
                float t = start + (float) (Math.PI / 2) * i / seg;
                float cs = (float) Math.cos(t), sn = (float) Math.sin(t);
                xs[n] = cxs[c] + cs * rr;
                ys[n] = cys[c] + sn * rr;
                nx[n] = cs;
                ny[n] = sn;
                n++;
            }
        }
        return n;
    }

    /** Fills the strip between two offsets of a rounded rectangle, each edge with its own paint and alpha. */
    private void band(float x0, float y0, float x1, float y1, float r, float offA, float alphaA, float offB, float alphaB,
                      Paint p, int seg) {
        int count = 4 * (seg + 1);
        grow(count);
        contour(x0, y0, x1, y1, r, offA, seg, ax, ay);
        contour(x0, y0, x1, y1, r, offB, seg, bx, by);
        solidBatch();
        for (int i = 0; i < count; i++) {
            int j = (i + 1) % count;
            int ca = Colors.fade(p.at(ax[i], ay[i]), alphaA), cb = Colors.fade(p.at(bx[i], by[i]), alphaB);
            int cbj = Colors.fade(p.at(bx[j], by[j]), alphaB), caj = Colors.fade(p.at(ax[j], ay[j]), alphaA);
            v(ax[i], ay[i], ca);
            v(bx[i], by[i], cb);
            v(bx[j], by[j], cbj);
            v(ax[j], ay[j], caj);
        }
    }

    private void fan(float x0, float y0, float x1, float y1, float r, float off, Paint p, int seg) {
        int count = 4 * (seg + 1);
        grow(count);
        contour(x0, y0, x1, y1, r, off, seg, ax, ay);
        float cx = (x0 + x1) / 2f, cy = (y0 + y1) / 2f;
        int cc = p.at(cx, cy);
        solidBatch();
        for (int i = 0; i < count; i++) {
            int j = (i + 1) % count;
            v(cx, cy, cc);
            v(ax[i], ay[i], p.at(ax[i], ay[i]));
            v(ax[j], ay[j], p.at(ax[j], ay[j]));
            v(ax[j], ay[j], p.at(ax[j], ay[j]));
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

    /** A filled rounded rectangle with an anti-aliased edge. */
    public void round(float x, float y, float w, float h, float r, int argb) {
        if (Colors.a(argb) == 0) {
            return;
        }
        round(x, y, w, h, r, solid(argb));
    }

    public void round(float x, float y, float w, float h, float r, Paint p) {
        box(x, y, w, h, r);
        if (bx1 - bx0 < 1 || by1 - by0 < 1) {
            return;
        }
        int seg = segments(br);
        fan(bx0, by0, bx1, by1, br, -0.5f, p, seg);
        band(bx0, by0, bx1, by1, br, -0.5f, 1f, 0.5f, 0f, p, seg);
    }

    /** A rounded rectangle filled with a vertical gradient. */
    public void roundV(float x, float y, float w, float h, float r, int top, int bottom) {
        vgrad.y0 = d.sy(y);
        vgrad.y1 = d.sy(y + h);
        vgrad.c0 = top;
        vgrad.c1 = bottom;
        round(x, y, w, h, r, vgrad);
    }

    /** A rounded outline {@code width} screen pixels wide, inside the edge (CSS borders). */
    public void stroke(float x, float y, float w, float h, float r, float width, int argb) {
        if (Colors.a(argb) == 0) {
            return;
        }
        stroke(x, y, w, h, r, width, solid(argb), 0f);
    }

    /**
     * A rounded outline {@code width} screen pixels wide whose outer edge is {@code offset} design pixels outside
     * the box (focus rings sit 2-3 px out).
     */
    public void stroke(float x, float y, float w, float h, float r, float width, Paint p, float offset) {
        box(x - offset, y - offset, w + 2 * offset, h + 2 * offset, r + (r > 0 ? offset : 0));
        if (bx1 - bx0 < 1 || by1 - by0 < 1) {
            return;
        }
        int seg = segments(br);
        float k = Math.min(1f, width);
        float s = Math.max(1f, width);
        band(bx0, by0, bx1, by1, br, 0.5f, 0f, -0.5f, k, p, seg);
        if (s > 1f) {
            band(bx0, by0, bx1, by1, br, -0.5f, k, -s + 0.5f, k, p, seg);
        }
        band(bx0, by0, bx1, by1, br, -s + 0.5f, k, -s - 0.5f, 0f, p, seg);
    }

    /** The border every Sol panel has: 1 design pixel (at least one screen pixel). */
    public float hairline() {
        return Math.max(1f, d.u);
    }

    /**
     * A soft glow round a rounded rectangle, the prototypes' {@code box-shadow: 0 0 blur spread colour}: it starts at
     * the edge with {@code argb}'s alpha and fades out over {@code extent} design pixels, like a gaussian tail.
     */
    public void glow(float x, float y, float w, float h, float r, float extent, int argb) {
        if (Colors.a(argb) == 0 || extent <= 0) {
            return;
        }
        box(x, y, w, h, r);
        int seg = segments(br + extent * d.u);
        Paint p = solid(argb);
        float e = extent * d.u;
        band(bx0, by0, bx1, by1, br, 0f, 1f, e * 0.25f, 0.55f, p, seg);
        band(bx0, by0, bx1, by1, br, e * 0.25f, 0.55f, e * 0.6f, 0.16f, p, seg);
        band(bx0, by0, bx1, by1, br, e * 0.6f, 0.16f, e, 0f, p, seg);
    }

    /**
     * The glass highlight along the top inside edge ({@code box-shadow: inset 0 1px 0 colour}): strongest where the
     * edge faces up, nothing at the sides.
     */
    public void highlight(float x, float y, float w, float h, float r, int argb) {
        if (Colors.a(argb) == 0) {
            return;
        }
        box(x, y, w, h, r);
        int seg = segments(br);
        int half = 2 * (seg + 1);
        grow(4 * (seg + 1));
        float b = hairline();
        float t = Math.max(1f, d.u);
        contour(bx0, by0, bx1, by1, br, -b, seg, ax, ay);
        contour(bx0, by0, bx1, by1, br, -b - t, seg, bx, by);
        solidBatch();
        for (int i = 0; i + 1 < half; i++) {
            float wi = Math.max(0f, -ny[i]), wj = Math.max(0f, -ny[i + 1]);
            if (wi <= 0f && wj <= 0f) {
                continue;
            }
            int ci = Colors.fade(argb, wi), cj = Colors.fade(argb, wj);
            v(ax[i], ay[i], ci);
            v(bx[i], by[i], Colors.fade(ci, 0.35f));
            v(bx[i + 1], by[i + 1], Colors.fade(cj, 0.35f));
            v(ax[i + 1], ay[i + 1], cj);
        }
    }

    /** A filled circle. */
    public void circle(float cx, float cy, float r, int argb) {
        round(cx - r, cy - r, 2 * r, 2 * r, r, argb);
    }

    public void circle(float cx, float cy, float r, Paint p) {
        round(cx - r, cy - r, 2 * r, 2 * r, r, p);
    }

    /** Ring radii (fractions of the radius) for {@link #disc}: dense toward the rim, where crescents and edges sit. */
    private static final float[] DISC_RINGS = {0.3f, 0.5f, 0.64f, 0.74f, 0.82f, 0.875f, 0.915f, 0.945f, 0.968f, 0.985f, 1f};

    /**
     * A circle filled with a paint sampled on a polar grid, so a gradient that changes inside the circle (an
     * off-centre radial with a stop at 72%, a thin lit crescent at the rim) comes out as drawn, not flattened to
     * the centre's and the rim's colours as a fan would. The rim is anti-aliased like {@link #round}.
     */
    public void disc(float cx, float cy, float r, Paint p) {
        box(cx - r, cy - r, 2 * r, 2 * r, r);
        if (bx1 - bx0 < 1 || by1 - by0 < 1) {
            return;
        }
        float scx = (bx0 + bx1) / 2f, scy = (by0 + by1) / 2f, sr = br - 0.5f;
        int seg = 72;
        solidBatch();
        float prev = 0f;
        for (float f : DISC_RINGS) {
            float r0 = sr * prev, r1 = sr * f;
            for (int k = 0; k < seg; k++) {
                double a0 = k * Math.PI * 2 / seg, a1 = (k + 1) * Math.PI * 2 / seg;
                float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
                float x00 = scx + c0 * r0, y00 = scy + s0 * r0, x01 = scx + c0 * r1, y01 = scy + s0 * r1;
                float x11 = scx + c1 * r1, y11 = scy + s1 * r1, x10 = scx + c1 * r0, y10 = scy + s1 * r0;
                v(x00, y00, p.at(x00, y00));
                v(x01, y01, p.at(x01, y01));
                v(x11, y11, p.at(x11, y11));
                v(x10, y10, p.at(x10, y10));
            }
            prev = f;
        }
        band(bx0, by0, bx1, by1, br, -0.5f, 1f, 0.5f, 0f, p, segments(br));
    }

    /** A circle outline {@code width} screen pixels wide. */
    public void ring(float cx, float cy, float r, float width, int argb) {
        stroke(cx - r, cy - r, 2 * r, 2 * r, r, width, argb);
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
            glowTexture = SolTexture.premultiplied("gui_glow", 64, 64, true, Sprites.glow(0xFFFFFFFF).toArgb(null));
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

    /** Text with its left at {@code x} and baseline at {@code baseline} (design pixels). */
    public void text(SolFont f, String s, float x, float baseline, int argb) {
        int c = a(argb);
        if (Colors.a(c) < 8 || s.isEmpty()) {
            return;
        }
        flush();
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
