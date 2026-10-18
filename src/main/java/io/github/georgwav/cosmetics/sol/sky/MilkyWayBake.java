package io.github.georgwav.cosmetics.sol.sky;

import io.github.georgwav.cosmetics.sol.util.Mulberry32;

/**
 * Bakes the Milky Way and the nebulae into a pixel sky cube (the Night Sky lab's {@code MWB}, {@code DUST},
 * {@code GRAIN} and {@code NEB}, made with the same seeds so the band lands where it does in the lab): soft clouds of
 * light along the galactic plane, warmest and brightest at the core in Sagittarius, with star clouds in Cygnus and
 * Scutum; the Great Rift and dust lanes cut out of them; fifteen thousand faint stars giving it its grain; and the
 * lab's thirteen nebulae and galaxies at their real places, turned to celestial north.
 *
 * <p>The cube is six {@link #FACE}-texel faces in a 3 x 2 atlas, in equatorial axes (the sky sphere turns it). At
 * 512 texels a face a texel is about the lab's pixel at the default view, so with nearest filtering it stays pixel
 * art. Each texel is the colour at full strength with its brightness in alpha (drawn added). Pure Java: bakes once,
 * on Sol's CPU thread and in tests.
 */
public final class MilkyWayBake {
    public static final int FACE = 512;
    public static final int W = FACE * 3, H = FACE * 2;
    /** The lab's Milky Way colours: warm core, white, blue, and a rare pink. */
    private static final int[][] MWCOL = {{255, 222, 186}, {226, 228, 244}, {196, 208, 255}, {255, 140, 176}};
    /** One lab pixel at its default view (70° over 290 pixels), in degrees: the grain's brightness is per pixel. */
    private static final double LAB_PIXEL = 70.0 / 290.0;

    /** The parts to bake. */
    public record Parts(boolean milkyWay, boolean nebulae) {
    }

    private MilkyWayBake() {
    }

    // ------------------------------------------------------------------ the lab's sky, regenerated

    /** A soft cloud of light (or of dust): centre (equatorial unit vector), angular radius, strength, colour. */
    record Blob(double x, double y, double z, double s, double a, int col) {
    }

    record Grain(double x, double y, double z, double a, int rgb) {
    }

    record Nebula(double x, double y, double z, double s, String type, double rot, double a, int seed) {
    }

    static final Blob[] MWB, DUST;
    static final Grain[] GRAIN;
    static final Nebula[] NEB;

    static {
        Mulberry32 r = new Mulberry32(23);
        double[] v = new double[3];
        MWB = new Blob[1150];
        for (int i = 0; i < MWB.length; i++) {
            double l = galL(r), k = bulge(l), b = gauss(r) * (2.6 + 5.2 * k), size = Math.toRadians((1.5 + r.next() * 3.4) * (1 + 0.9 * k));
            int col = r.next() < 0.07 ? 3 : k > 0.45 ? 0 : r.next() < 0.55 ? 2 : 1;
            double a = (0.024 + 0.034 * r.next()) * (0.5 + 0.85 * k);
            SkySphere.gal(l, b, v);
            MWB[i] = new Blob(v[0], v[1], v[2], size, a, col);
        }
        DUST = new Blob[300 + 150 + 26];
        int d = 0;
        for (int i = 0; i < 300; i++) {
            double l = -8 + r.next() * 92, b = 1.1 + gauss(r) * 1.5 + (l < 14 ? 3 * (1 - l / 14) : 0);
            SkySphere.gal(l, b, v);
            double s = Math.toRadians(0.9 + r.next() * 1.8), a = 0.3 + r.next() * 0.35;
            DUST[d++] = new Blob(v[0], v[1], v[2], s, a, 0);
        }
        for (int i = 0; i < 150; i++) {
            double l = galL(r), b = gauss(r) * 2.4;
            SkySphere.gal(l, b, v);
            double s = Math.toRadians(0.5 + r.next() * 1.3), a = 0.2 + r.next() * 0.3;
            DUST[d++] = new Blob(v[0], v[1], v[2], s, a, 0);
        }
        for (int i = 0; i < 26; i++) {
            double l = -6 + r.next() * 8, b = 4 + r.next() * 4;
            SkySphere.gal(l, b, v);
            double s = Math.toRadians(0.6 + r.next() * 0.9);
            DUST[d++] = new Blob(v[0], v[1], v[2], s, 0.45, 0);
        }
        GRAIN = new Grain[15000];
        for (int i = 0; i < GRAIN.length; i++) {
            double l = galL(r), k = bulge(l), b = gauss(r) * (1.8 + 4.2 * k);
            SkySphere.gal(l, b, v);
            boolean warm = k > 0.5 && r.next() < 0.7;
            double a = (0.05 + 0.11 * r.next()) * (0.45 + 0.9 * k);
            int c = warm ? 0xFFE2BE : r.next() < 0.5 ? 0xCDD6FF : 0xECECF6;
            GRAIN[i] = new Grain(v[0], v[1], v[2], a, c);
        }
        // RA (hours), Dec, size (degrees across, drawn larger than life), sprite, turn from north, brightness.
        Object[][] neb = {
                {5.588, -5.39, 2.6, "orion", 0, 1.0}, {6.533, 4.95, 2.6, "ring", 0, 0.75},
                {18.06, -24.38, 2.4, "pink", 0, 1.0}, {18.04, -23.03, 1.2, "trifid", 0, 0.9},
                {20.98, 44.3, 3.6, "red", 0, 0.65}, {20.85, 31.0, 3.4, "veil", 0, 0.7},
                {4.0, 36.4, 3.2, "red", 50, 0.5}, {2.55, 61.5, 2.4, "pink", 30, 0.45},
                {3.79, 24.1, 2.2, "blue", 0, 0.6}, {16.43, -24.0, 6.0, "rho", 0, 0.55},
                {5.6, -3.0, 18.0, "loop", 0, 0.42}, {0.712, 41.27, 4.2, "galaxy", -55, 0.9},
                {1.564, 30.66, 1.4, "faint", 20, 0.6}};
        NEB = new Nebula[neb.length];
        for (int i = 0; i < neb.length; i++) {
            Object[] n = neb[i];
            SkySphere.eq(((Number) n[0]).doubleValue(), ((Number) n[1]).doubleValue(), v);
            NEB[i] = new Nebula(v[0], v[1], v[2], Math.toRadians(((Number) n[2]).doubleValue()), (String) n[3],
                    Math.toRadians(((Number) n[4]).doubleValue()), ((Number) n[5]).doubleValue(), 50 + i);
        }
    }

    /** The lab's galactic longitude draw: the core, the Cygnus and Scutum clouds, and the rest of the band. */
    static double galL(Mulberry32 r) {
        double u = r.next();
        return u < 0.36 ? gauss(r) * 26 : u < 0.5 ? 78 + gauss(r) * 11 : u < 0.6 ? 27 + gauss(r) * 6 : r.next() * 360 - 180;
    }

    /** How bright the band is at a galactic longitude: the core, Cygnus, Scutum. */
    static double bulge(double l) {
        l = adiff(l, 0);
        return Math.exp(-(l * l) / (2 * 24 * 24)) + 0.55 * Math.exp(-((l - 78) * (l - 78)) / (2 * 12 * 12))
                + 0.4 * Math.exp(-((l - 27) * (l - 27)) / (2 * 7 * 7));
    }

    static double gauss(Mulberry32 r) {
        double u = 0;
        while (u == 0) {
            u = r.next();
        }
        return Math.sqrt(-2 * Math.log(u)) * Math.cos(Math.PI * 2 * r.next());
    }

    private static double adiff(double a, double b) {
        double d = ((a - b + 540) % 360 + 360) % 360;
        return d - 180;
    }

    // ------------------------------------------------------------------ the bake

    /** Bakes the cube atlas ({@link #W} x {@link #H}). */
    public static int[] bake(Parts parts) {
        int[] out = new int[W * H];
        float[][] neb = parts.nebulae() ? nebulaSprites() : null;
        float[] mw = new float[FACE * FACE * 3];
        float[] sum = new float[FACE * FACE * 3];
        for (int f = 0; f < 6; f++) {
            java.util.Arrays.fill(mw, 0f);
            java.util.Arrays.fill(sum, 0f);
            if (parts.milkyWay()) {
                for (Blob b : MWB) {
                    int[] c = MWCOL[b.col()];
                    splat(f, b, mw, c[0] / 255f, c[1] / 255f, c[2] / 255f, (float) b.a(), false);
                }
                for (Blob b : DUST) {
                    splat(f, b, mw, 0f, 0f, 0f, (float) b.a(), true);
                }
                for (int i = 0; i < mw.length; i++) {
                    sum[i] = mw[i] * 0.9f;
                }
                grain(f, sum);
            }
            if (neb != null) {
                for (int i = 0; i < NEB.length; i++) {
                    nebula(f, NEB[i], neb[i], sum);
                }
            }
            int ox = (f % 3) * FACE, oy = (f / 3) * FACE;
            for (int py = 0; py < FACE; py++) {
                for (int px = 0; px < FACE; px++) {
                    int i = (py * FACE + px) * 3;
                    float rr = Math.min(1f, sum[i]), gg = Math.min(1f, sum[i + 1]), bb = Math.min(1f, sum[i + 2]);
                    float a = Math.max(rr, Math.max(gg, bb));
                    if (a < 0.5f / 255f) {
                        continue;
                    }
                    out[(oy + py) * W + ox + px] = (Math.round(a * 255f) << 24) | (Math.round(rr / a * 255f) << 16)
                            | (Math.round(gg / a * 255f) << 8) | Math.round(bb / a * 255f);
                }
            }
        }
        return out;
    }

    /** The lab's soft glow sprite: full at the centre, .45 at .45 of the radius, gone at the edge. */
    static float soft(double rn) {
        if (rn >= 1.0) {
            return 0f;
        }
        return rn <= 0.45 ? (float) (1.0 - rn * (0.55 / 0.45)) : (float) (0.45 * (1.0 - (rn - 0.45) / 0.55));
    }

    /**
     * Adds a blob of light (canvas "lighter", clamped) to face f, or cuts dust out of it ("destination-out").
     */
    private static void splat(int f, Blob b, float[] buf, float cr, float cg, float cb, float alpha, boolean cut) {
        int[] box = new int[4];
        if (!bounds(f, b.x(), b.y(), b.z(), b.s(), box)) {
            return;
        }
        double cosS = Math.cos(b.s());
        float[] dir = new float[3];
        for (int py = box[2]; py <= box[3]; py++) {
            for (int px = box[0]; px <= box[1]; px++) {
                texelDir(f, px, py, dir);
                double dot = dir[0] * b.x() + dir[1] * b.y() + dir[2] * b.z();
                if (dot <= cosS) {
                    continue;
                }
                double rn = Math.acos(Math.min(1.0, dot)) / b.s();
                float k = soft(rn) * alpha;
                if (k <= 0f) {
                    continue;
                }
                int i = (py * FACE + px) * 3;
                if (cut) {
                    buf[i] *= 1f - k;
                    buf[i + 1] *= 1f - k;
                    buf[i + 2] *= 1f - k;
                } else {
                    buf[i] = Math.min(1f, buf[i] + cr * k);
                    buf[i + 1] = Math.min(1f, buf[i + 1] + cg * k);
                    buf[i + 2] = Math.min(1f, buf[i + 2] + cb * k);
                }
            }
        }
    }

    /** The grain: each faint star into its texel, as bright as it was in a lab pixel. */
    private static void grain(int f, float[] sum) {
        int[] at = new int[3];
        for (Grain g : GRAIN) {
            toFace(g.x(), g.y(), g.z(), at);
            if (at[0] != f) {
                continue;
            }
            double a = (at[1] + 0.5) / FACE * 2 - 1, b = (at[2] + 0.5) / FACE * 2 - 1;
            // The texel's size in degrees (smaller toward the face's edges), against the lab's pixel.
            double texel = Math.toDegrees(2.0 / FACE) / Math.pow(1 + a * a + b * b, 0.75);
            float k = (float) Math.min(1.0, g.a() * Math.min(2.5, (LAB_PIXEL / texel) * (LAB_PIXEL / texel)));
            int i = (at[2] * FACE + at[1]) * 3;
            sum[i] += ((g.rgb() >> 16) & 255) / 255f * k;
            sum[i + 1] += ((g.rgb() >> 8) & 255) / 255f * k;
            sum[i + 2] += (g.rgb() & 255) / 255f * k;
        }
    }

    /**
     * A nebula's sprite laid on the sky round its place: its top to celestial north, its right to the west (as you
     * see it from below), turned by its own angle, half as wide again as its size, added.
     */
    private static void nebula(int f, Nebula n, float[] sprite, float[] sum) {
        double half = Math.atan(Math.tan(n.s() / 2) * 1.25);
        int[] box = new int[4];
        if (!bounds(f, n.x(), n.y(), n.z(), half * 1.45, box)) {
            return;
        }
        // East = pole × centre, north = centre × east.
        double ex = -n.y(), ey = n.x(), ez = 0;
        double el = Math.sqrt(ex * ex + ey * ey);
        if (el < 1e-9) {
            ex = 1;
            ey = 0;
            el = 1;
        }
        ex /= el;
        ey /= el;
        double nx = n.y() * ez - n.z() * ey, ny = n.z() * ex - n.x() * ez, nz = n.x() * ey - n.y() * ex;
        double tanR = Math.tan(half), cr = Math.cos(n.rot()), sr = Math.sin(n.rot());
        float[] dir = new float[3];
        int s = NEB_SIZE;
        for (int py = box[2]; py <= box[3]; py++) {
            for (int px = box[0]; px <= box[1]; px++) {
                texelDir(f, px, py, dir);
                double dc = dir[0] * n.x() + dir[1] * n.y() + dir[2] * n.z();
                if (dc <= 0.2) {
                    continue;
                }
                // On the screen: right is west (−east), down is south (−north).
                double sx = -(dir[0] * ex + dir[1] * ey + dir[2] * ez) / dc / tanR;
                double sy = -(dir[0] * nx + dir[1] * ny + dir[2] * nz) / dc / tanR;
                double u = sx * cr + sy * sr, w = -sx * sr + sy * cr;
                if (u <= -1 || u >= 1 || w <= -1 || w >= 1) {
                    continue;
                }
                double fx = (u + 1) * 0.5 * s - 0.5, fy = (w + 1) * 0.5 * s - 0.5;
                int i = (py * FACE + px) * 3;
                for (int ch = 0; ch < 3; ch++) {
                    sum[i + ch] += (float) (bilinear(sprite, s, fx, fy, ch) * n.a());
                }
            }
        }
    }

    private static double bilinear(float[] img, int s, double x, double y, int ch) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        double tx = x - x0, ty = y - y0;
        double a = px(img, s, x0, y0, ch), b = px(img, s, x0 + 1, y0, ch), c = px(img, s, x0, y0 + 1, ch), d = px(img, s, x0 + 1, y0 + 1, ch);
        return (a + (b - a) * tx) * (1 - ty) + (c + (d - c) * tx) * ty;
    }

    private static float px(float[] img, int s, int x, int y, int ch) {
        if (x < 0 || y < 0 || x >= s || y >= s) {
            return 0f;
        }
        return img[(y * s + x) * 4 + ch];
    }

    // ------------------------------------------------------------------ the nebula sprites

    static final int NEB_SIZE = 160;

    /** The lab's {@code nebSprite} for each nebula: premultiplied RGBA floats, {@link #NEB_SIZE} square. */
    static float[][] nebulaSprites() {
        float[][] out = new float[NEB.length][];
        for (int i = 0; i < NEB.length; i++) {
            out[i] = nebSprite(NEB[i].type(), NEB[i].seed());
        }
        return out;
    }

    /** A small canvas of glow blobs, composited as the lab's 2D canvas does ("lighter" and "destination-out"). */
    static final class Sprite {
        final int s;
        final float[] px;
        boolean cut;
        float sy = 1f;
        float ox, oy;

        Sprite(int s) {
            this.s = s;
            this.px = new float[s * s * 4];
        }

        /** The lab's {@code blob}: the soft glow sprite drawn at (x, y), radius rad, colour c, alpha a. */
        void blob(double x, double y, double rad, int[] c, double a) {
            double cx = ox + x, cy = oy + y * sy, rx = rad, ry = rad * sy;
            int x0 = Math.max(0, (int) Math.floor(cx - rx)), x1 = Math.min(s - 1, (int) Math.ceil(cx + rx));
            int y0 = Math.max(0, (int) Math.floor(cy - ry)), y1 = Math.min(s - 1, (int) Math.ceil(cy + ry));
            for (int j = y0; j <= y1; j++) {
                for (int i = x0; i <= x1; i++) {
                    double dx = (i + 0.5 - cx) / rx, dy = (j + 0.5 - cy) / ry;
                    float k = soft(Math.sqrt(dx * dx + dy * dy)) * (float) a;
                    if (k <= 0f) {
                        continue;
                    }
                    int q = (j * s + i) * 4;
                    if (cut) {
                        px[q] *= 1f - k;
                        px[q + 1] *= 1f - k;
                        px[q + 2] *= 1f - k;
                        px[q + 3] *= 1f - k;
                    } else {
                        px[q] = Math.min(1f, px[q] + c[0] / 255f * k);
                        px[q + 1] = Math.min(1f, px[q + 1] + c[1] / 255f * k);
                        px[q + 2] = Math.min(1f, px[q + 2] + c[2] / 255f * k);
                        px[q + 3] = Math.min(1f, px[q + 3] + k);
                    }
                }
            }
        }
    }

    private static final int[] PINKRED = {255, 96, 132}, PINK2 = {255, 150, 170}, TEAL = {140, 236, 226}, WHITE = {255, 255, 255};

    /** The lab's {@code nebSprite}, blob for blob. */
    static float[] nebSprite(String type, int seed) {
        int S = NEB_SIZE;
        Sprite g = new Sprite(S);
        Mulberry32 r = new Mulberry32(seed);
        double C = S / 2.0;
        switch (type) {
            case "orion" -> {
                for (int i = 0; i < 70; i++) {
                    double a = r.next() * Math.PI * 2, d = Math.pow(r.next(), 0.7) * 44;
                    double rad = 8 + r.next() * 22;
                    g.blob(C + Math.cos(a) * d * 1.1, C + Math.sin(a) * d, rad, r.next() < 0.7 ? PINKRED : PINK2, 0.16);
                }
                for (int i = 0; i < 14; i++) {
                    double x = C + gauss(r) * 7, y = C - 4 + gauss(r) * 6;
                    g.blob(x, y, 6 + r.next() * 9, TEAL, 0.22);
                }
                g.blob(C, C - 4, 9, WHITE, 0.5);
            }
            case "ring" -> {
                for (int i = 0; i < 90; i++) {
                    double a = r.next() * Math.PI * 2, d = 30 + gauss(r) * 9;
                    g.blob(C + Math.cos(a) * d, C + Math.sin(a) * d, 9 + r.next() * 16, new int[]{255, 92, 128}, 0.12);
                }
                g.cut = true;
                g.blob(C, C, 26, WHITE, 0.8);
            }
            case "pink" -> {
                for (int i = 0; i < 60; i++) {
                    double a = r.next() * Math.PI * 2, d = Math.pow(r.next(), 0.8) * 42;
                    double rad = 8 + r.next() * 20;
                    g.blob(C + Math.cos(a) * d, C + Math.sin(a) * d * 0.75, rad, r.next() < 0.8 ? new int[]{255, 100, 140} : new int[]{180, 170, 255}, 0.14);
                }
                g.cut = true;
                for (int i = 0; i < 6; i++) {
                    double x = C + gauss(r) * 14, y = C + gauss(r) * 6;
                    g.blob(x, y, 5 + r.next() * 7, WHITE, 0.5);
                }
            }
            case "trifid" -> {
                for (int i = 0; i < 30; i++) {
                    double a = r.next() * Math.PI * 2, d = Math.pow(r.next(), 0.8) * 24;
                    g.blob(C + Math.cos(a) * d, C + 8 + Math.sin(a) * d, 6 + r.next() * 12, new int[]{255, 100, 140}, 0.16);
                }
                for (int i = 0; i < 20; i++) {
                    double x = C + gauss(r) * 14, y = C - 22 + gauss(r) * 9;
                    g.blob(x, y, 8 + r.next() * 12, new int[]{130, 160, 255}, 0.13);
                }
                g.cut = true;
                for (double a : new double[]{0, 2.1, 4.2}) {
                    for (int k = 4; k < 30; k += 3) {
                        g.blob(C + Math.cos(a) * k, C + 8 + Math.sin(a) * k, 2.6, WHITE, 0.6);
                    }
                }
            }
            case "red" -> {
                for (int i = 0; i < 80; i++) {
                    double a = r.next() * Math.PI * 2, d = Math.pow(r.next(), 0.6) * 50;
                    g.blob(C + Math.cos(a) * d, C + Math.sin(a) * d * 0.7, 8 + r.next() * 20, new int[]{255, 80, 110}, 0.1);
                }
                g.cut = true;
                for (int i = 0; i < 10; i++) {
                    double x = C + 18 + gauss(r) * 10, y = C + gauss(r) * 14;
                    g.blob(x, y, 8 + r.next() * 10, WHITE, 0.45);
                }
            }
            case "veil" -> {
                double[][] arcs = {{-16, 48, -0.9, 0.9, 0}, {-12, 46, -0.8, 0.7, 1}, {28, 50, 2.3, 3.9, 0}, {26, 47, 2.4, 3.8, 1}};
                for (double[] arc : arcs) {
                    int[] c = arc[4] == 0 ? new int[]{120, 236, 220} : new int[]{255, 110, 140};
                    for (int i = 0; i < 70; i++) {
                        double a = arc[2] + (arc[3] - arc[2]) * r.next();
                        g.blob(C + arc[0] + Math.cos(a) * arc[1], C + Math.sin(a) * arc[1], 2 + r.next() * 4, c, 0.32);
                    }
                }
            }
            case "blue" -> {
                for (int i = 0; i < 40; i++) {
                    double x = C + gauss(r) * 20, y = C + gauss(r) * 16;
                    g.blob(x, y, 10 + r.next() * 20, new int[]{140, 175, 255}, 0.12);
                }
            }
            case "loop" -> {
                for (int i = 0; i < 160; i++) {
                    double a = -1.6 + 3.2 * r.next(), rad = 62 + gauss(r) * 4;
                    g.blob(C - 30 + Math.cos(a) * rad, C + Math.sin(a) * rad, 5 + r.next() * 9, new int[]{255, 90, 120}, 0.09);
                }
            }
            case "rho" -> {
                g.blob(C - 20, C + 18, 30, new int[]{255, 206, 120}, 0.28);
                g.blob(C + 14, C - 20, 26, new int[]{130, 160, 255}, 0.24);
                g.blob(C + 24, C + 4, 18, new int[]{255, 100, 130}, 0.2);
                for (int i = 0; i < 40; i++) {
                    double x = C + gauss(r) * 30, y = C + gauss(r) * 30;
                    g.blob(x, y, 6 + r.next() * 14, new int[]{255, 170, 150}, 0.06);
                }
                g.cut = true;
                for (int i = 0; i < 26; i++) {
                    double t = r.next();
                    double x = C - 40 + t * 90, y = C + 30 - t * 50 + gauss(r) * 5;
                    g.blob(x, y, 5 + r.next() * 6, WHITE, 0.5);
                }
            }
            case "galaxy" -> {
                g.ox = (float) C;
                g.oy = (float) C;
                g.sy = 0.3f;
                for (int i = 0; i < 26; i++) {
                    double x = gauss(r) * 24, y = gauss(r) * 10;
                    g.blob(x, y, 14 + r.next() * 24, new int[]{200, 212, 255}, 0.12);
                }
                g.blob(0, 0, 30, new int[]{255, 230, 200}, 0.55);
                g.blob(0, 0, 9, new int[]{255, 248, 236}, 0.9);
                g.cut = true;
                for (int x = -52; x <= 52; x += 4) {
                    g.blob(x, 10 + x * 0.05, 3.4, WHITE, 0.55);
                }
            }
            default -> {
                // "faint": the Triangulum Galaxy.
                g.ox = (float) C;
                g.oy = (float) C;
                g.sy = 0.65f;
                for (int i = 0; i < 30; i++) {
                    double x = gauss(r) * 20, y = gauss(r) * 20;
                    g.blob(x, y, 10 + r.next() * 14, new int[]{200, 212, 255}, 0.08);
                }
                g.blob(0, 0, 14, new int[]{240, 240, 255}, 0.25);
            }
        }
        return g.px;
    }

    // ------------------------------------------------------------------ cube geometry

    /** The direction (not normalised) of face f at face coordinates a, b in [-1, 1]. */
    public static float[] faceDir(int f, float a, float b, float[] o) {
        switch (f) {
            case 0 -> { o[0] = 1f; o[1] = -b; o[2] = -a; }
            case 1 -> { o[0] = -1f; o[1] = -b; o[2] = a; }
            case 2 -> { o[0] = a; o[1] = 1f; o[2] = b; }
            case 3 -> { o[0] = a; o[1] = -1f; o[2] = -b; }
            case 4 -> { o[0] = a; o[1] = -b; o[2] = 1f; }
            default -> { o[0] = -a; o[1] = -b; o[2] = -1f; }
        }
        return o;
    }

    /** The unit direction through texel (px, py) of face f. */
    static void texelDir(int f, int px, int py, float[] o) {
        faceDir(f, (px + 0.5f) / FACE * 2f - 1f, (py + 0.5f) / FACE * 2f - 1f, o);
        float l = (float) Math.sqrt(o[0] * o[0] + o[1] * o[1] + o[2] * o[2]);
        o[0] /= l;
        o[1] /= l;
        o[2] /= l;
    }

    /** Face and texel of a unit direction, into {@code o}: {face, px, py}. */
    public static void toFace(double x, double y, double z, int[] o) {
        double ax = Math.abs(x), ay = Math.abs(y), az = Math.abs(z);
        int f;
        double a, b;
        if (ax >= ay && ax >= az) {
            f = x > 0 ? 0 : 1;
            a = x > 0 ? -z / ax : z / ax;
            b = -y / ax;
        } else if (ay >= az) {
            f = y > 0 ? 2 : 3;
            a = x / ay;
            b = y > 0 ? z / ay : -z / ay;
        } else {
            f = z > 0 ? 4 : 5;
            a = z > 0 ? x / az : -x / az;
            b = -y / az;
        }
        o[0] = f;
        o[1] = (int) Math.min(FACE - 1, Math.max(0, Math.floor((a + 1) * 0.5 * FACE)));
        o[2] = (int) Math.min(FACE - 1, Math.max(0, Math.floor((b + 1) * 0.5 * FACE)));
    }

    /**
     * The texels of face f a cap of angular radius {@code s} round (x, y, z) can cover: {x0, x1, y0, y1}. False if
     * it misses the face.
     */
    static boolean bounds(int f, double x, double y, double z, double s, int[] box) {
        // Points round the cap's edge and its centre, onto the face's plane.
        double ux, uy, uz;
        if (Math.abs(z) < 0.9) {
            ux = -y;
            uy = x;
            uz = 0;
        } else {
            ux = 0;
            uy = -z;
            uz = y;
        }
        double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= ul;
        uy /= ul;
        uz /= ul;
        double vx = y * uz - z * uy, vy = z * ux - x * uz, vz = x * uy - y * ux;
        double minA = 2, maxA = -2, minB = 2, maxB = -2;
        boolean any = false;
        double cs = Math.cos(s), sn = Math.sin(s);
        double[] ab = new double[2];
        for (int k = -1; k < 24; k++) {
            double px, py, pz;
            if (k < 0) {
                px = x;
                py = y;
                pz = z;
            } else {
                double t = k * Math.PI * 2 / 24, c = Math.cos(t), si = Math.sin(t);
                px = x * cs + (ux * c + vx * si) * sn;
                py = y * cs + (uy * c + vy * si) * sn;
                pz = z * cs + (uz * c + vz * si) * sn;
            }
            if (!onFace(f, px, py, pz, ab)) {
                continue;
            }
            any = true;
            minA = Math.min(minA, ab[0]);
            maxA = Math.max(maxA, ab[0]);
            minB = Math.min(minB, ab[1]);
            maxB = Math.max(maxB, ab[1]);
        }
        if (!any) {
            return false;
        }
        // A cap that runs off the face's edge: its points beyond it projected past ±1, so clamp.
        box[0] = clampTexel(minA - 0.01);
        box[1] = clampTexel(maxA + 0.01);
        box[2] = clampTexel(minB - 0.01);
        box[3] = clampTexel(maxB + 0.01);
        return true;
    }

    private static int clampTexel(double a) {
        return (int) Math.min(FACE - 1, Math.max(0, Math.floor((a + 1) * 0.5 * FACE)));
    }

    /** The face coordinates of a direction on face f's plane, if it points into that face's half of the sky. */
    static boolean onFace(int f, double x, double y, double z, double[] ab) {
        double n;
        switch (f) {
            case 0 -> { n = x; ab[0] = -z; ab[1] = -y; }
            case 1 -> { n = -x; ab[0] = z; ab[1] = -y; }
            case 2 -> { n = y; ab[0] = x; ab[1] = z; }
            case 3 -> { n = -y; ab[0] = x; ab[1] = -z; }
            case 4 -> { n = z; ab[0] = x; ab[1] = -y; }
            default -> { n = -z; ab[0] = -x; ab[1] = -y; }
        }
        if (n <= 0.05) {
            return false;
        }
        ab[0] = Math.max(-3, Math.min(3, ab[0] / n));
        ab[1] = Math.max(-3, Math.min(3, ab[1] / n));
        return true;
    }
}
