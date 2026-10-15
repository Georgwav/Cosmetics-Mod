package io.github.georgwav.cosmetics.sol.atmosphere;

import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.sol.util.PixelCanvas;

/**
 * The atmosphere particles' sprites, white, baked once on the CPU at several sizes (a hand-made mip chain, so a
 * particle a few pixels wide reads as smoothly as a big one on every version, whatever the game's own texture
 * sampling does):
 *
 * <ul>
 *   <li>{@link #glow()}: the glowing particles. A soft light, a bright core in a wide halo, with nothing that
 *   could read as a square or an edge; and an out-of-focus bokeh disc for the big ones close to the camera.</li>
 *   <li>{@link #shapes()}: petal, ash, blossom, leaf, snow, flake, a four-point star and a music note, drawn with
 *   4×4 samples per pixel at four times the size and box-filtered down, so their edges are smooth.</li>
 * </ul>
 */
final class ParticleSprites {
    /** Glow atlas: levels of {@link #GLOW_SIZES} side by side, the glow in the top row, the bokeh disc below. */
    static final int[] GLOW_SIZES = {128, 64, 32, 16, 8};
    static final int GLOW_W = 256, GLOW_H = 256;
    /** Shape atlas: eight cells per level, one row per level of {@link #SHAPE_SIZES}. */
    static final int[] SHAPE_SIZES = {64, 32, 16};
    static final int SHAPE_W = 512, SHAPE_H = 128;
    static final int PETAL = 0, ASH = 1, BLOSSOM = 2, LEAF = 3, SNOW = 4, FLAKE = 5, STAR = 6, NOTE = 7;

    private static SolTexture glow;
    private static SolTexture shapes;

    private ParticleSprites() {
    }

    /** Where level {@code level} of the glow (row 0) or the bokeh disc (row 1) is: {u0, v0, u1, v1}. */
    static void glowUv(int row, int level, float[] out) {
        int x = 0;
        for (int i = 0; i < level; i++) {
            x += GLOW_SIZES[i];
        }
        int s = GLOW_SIZES[level];
        out[0] = x / (float) GLOW_W;
        out[1] = row * 128f / GLOW_H;
        out[2] = (x + s) / (float) GLOW_W;
        out[3] = (row * 128f + s) / GLOW_H;
    }

    /** The level whose size covers {@code pixels} on screen (the largest when it's big). */
    static int glowLevel(float pixels) {
        for (int i = GLOW_SIZES.length - 1; i > 0; i--) {
            if (GLOW_SIZES[i] >= pixels) {
                return i;
            }
        }
        return 0;
    }

    static void shapeUv(int shape, int level, float[] out) {
        int s = SHAPE_SIZES[level];
        int y = 0;
        for (int i = 0; i < level; i++) {
            y += SHAPE_SIZES[i];
        }
        out[0] = shape * s / (float) SHAPE_W;
        out[1] = y / (float) SHAPE_H;
        out[2] = (shape + 1) * s / (float) SHAPE_W;
        out[3] = (y + s) / (float) SHAPE_H;
    }

    static int shapeLevel(float pixels) {
        for (int i = SHAPE_SIZES.length - 1; i > 0; i--) {
            if (SHAPE_SIZES[i] >= pixels) {
                return i;
            }
        }
        return 0;
    }

    static SolTexture glow() {
        if (glow == null || glow.released()) {
            glow = SolTexture.of("particle_glow", GLOW_W, GLOW_H, true, bakeGlow());
        }
        return glow;
    }

    static SolTexture shapes() {
        if (shapes == null || shapes.released()) {
            shapes = SolTexture.of("particle_shapes", SHAPE_W, SHAPE_H, true, bakeShapes());
        }
        return shapes;
    }

    // ------------------------------------------------------------------ glow

    /** The glow's brightness at a distance r (0 centre, 1 the sprite's edge): a core, a halo and a wide skirt. */
    static float glowProfile(float r) {
        if (r >= 1f) {
            return 0f;
        }
        // The prototypes' glow (full at the centre, .55 a quarter of the way out, nothing at the edge), with a
        // smooth core and a tail that reaches zero gently, so no edge or square ever shows.
        double inner = r < 0.25 ? 1.0 - 0.45 * smooth(0.0, 0.25, r) : 0.55 * Math.pow(1.0 - smooth(0.25, 1.0, r), 1.15);
        double core = 0.35 * Math.exp(-Math.pow(r / 0.08, 2));
        return (float) Math.min(1.0, inner + core);
    }

    /** The out-of-focus disc: even light, a slightly brighter rim, soft all the way round. */
    static float bokehProfile(float r) {
        if (r >= 1f) {
            return 0f;
        }
        double disc = 1.0 - smooth(0.62, 0.86, r);
        double rim = 0.25 * Math.exp(-Math.pow((r - 0.74) / 0.07, 2));
        return (float) Math.min(1.0, 0.42 * disc + rim * (1.0 - smooth(0.86, 1.0, r)));
    }

    private static double smooth(double e0, double e1, double x) {
        double t = Math.max(0, Math.min(1, (x - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }

    private static int[] bakeGlow() {
        int[] out = new int[GLOW_W * GLOW_H];
        for (int row = 0; row < 2; row++) {
            int x0 = 0;
            for (int s : GLOW_SIZES) {
                for (int j = 0; j < s; j++) {
                    for (int i = 0; i < s; i++) {
                        // 4×4 samples per pixel.
                        float sum = 0f;
                        for (int sy = 0; sy < 4; sy++) {
                            for (int sx = 0; sx < 4; sx++) {
                                float dx = (i + (sx + 0.5f) / 4f) / s * 2f - 1f;
                                float dy = (j + (sy + 0.5f) / 4f) / s * 2f - 1f;
                                float r = (float) Math.sqrt(dx * dx + dy * dy);
                                sum += row == 0 ? glowProfile(r) : bokehProfile(r);
                            }
                        }
                        int a = Math.round(sum / 16f * 255f);
                        out[(row * 128 + j) * GLOW_W + x0 + i] = (a << 24) | 0xFFFFFF;
                    }
                }
                x0 += s;
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ shapes

    private static int[] bakeShapes() {
        int big = SHAPE_SIZES[0] * 4;
        int[] out = new int[SHAPE_W * SHAPE_H];
        for (int shape = 0; shape < 8; shape++) {
            PixelCanvas c = new PixelCanvas(big, big);
            draw(c, shape, big);
            float[] alpha = new float[big * big];
            int[] argb = c.toArgb(null);
            for (int k = 0; k < alpha.length; k++) {
                alpha[k] = (argb[k] >>> 24) / 255f;
            }
            // Box-filter down to each level.
            int y0 = 0;
            for (int s : SHAPE_SIZES) {
                int f = big / s;
                for (int j = 0; j < s; j++) {
                    for (int i = 0; i < s; i++) {
                        float sum = 0f;
                        for (int v = 0; v < f; v++) {
                            for (int u = 0; u < f; u++) {
                                sum += alpha[(j * f + v) * big + i * f + u];
                            }
                        }
                        int a = Math.round(sum / (f * f) * 255f);
                        out[(y0 + j) * SHAPE_W + shape * s + i] = (a << 24) | 0xFFFFFF;
                    }
                }
                y0 += s;
            }
        }
        return out;
    }

    /** One shape, white, filling a {@code n} x {@code n} canvas with a little margin. */
    private static void draw(PixelCanvas c, int shape, int n) {
        float m = n / 2f, u = n / 64f;
        int white = 0xFFFFFFFF;
        switch (shape) {
            case PETAL -> {
                // A cherry petal: an oval, rounder at the base, with the notch at its tip.
                c.fillEllipse(m, m + 2 * u, 15 * u, 25 * u, white);
                c.fillEllipse(m, m - 8 * u, 12 * u, 18 * u, white);
                c.save();
                c.setGlobalAlpha(1f);
                cut(c, new float[]{m - 5 * u, m, m + 5 * u}, new float[]{m - 30 * u, m - 21 * u, m - 30 * u});
                c.restore();
            }
            case ASH -> {
                float[] xs = new float[9], ys = new float[9];
                double[] r = {0.95, 0.7, 1.0, 0.62, 0.9, 0.75, 1.0, 0.58, 0.85};
                for (int i = 0; i < 9; i++) {
                    double a = i / 9.0 * Math.PI * 2;
                    xs[i] = m + (float) (Math.cos(a) * r[i] * 24 * u);
                    ys[i] = m + (float) (Math.sin(a) * r[i] * 18 * u);
                }
                c.fillPolygon(xs, ys, white);
            }
            case BLOSSOM -> {
                for (int i = 0; i < 5; i++) {
                    double a = i * Math.PI * 2 / 5 - Math.PI / 2;
                    c.save();
                    c.translate(m + (float) Math.cos(a) * 13 * u, m + (float) Math.sin(a) * 13 * u);
                    c.rotate((float) (a + Math.PI / 2));
                    c.fillEllipse(0, 0, 9.5f * u, 12.5f * u, white);
                    c.restore();
                }
                c.fillEllipse(m, m, 8 * u, 8 * u, white);
            }
            case LEAF -> {
                // A pointed leaf with a stalk.
                float[] xs = new float[24], ys = new float[24];
                for (int i = 0; i < 24; i++) {
                    double t = i / 23.0;
                    double y = -26 + 50 * t;
                    double w = Math.sin(Math.PI * Math.pow(t, 0.8)) * 15;
                    xs[i] = m + (float) (w * u);
                    ys[i] = m + (float) (y * u);
                }
                float[] px = new float[48], py = new float[48];
                for (int i = 0; i < 24; i++) {
                    px[i] = xs[i];
                    py[i] = ys[i];
                    px[47 - i] = 2 * m - xs[i];
                    py[47 - i] = ys[i];
                }
                c.fillPolygon(px, py, white);
                c.line(m, m + 22 * u, m, m + 30 * u, 2.4f * u, PixelCanvas.solid(white));
            }
            case SNOW -> {
                // A soft round flake: solid in the middle, fading out.
                for (int k = 10; k >= 1; k--) {
                    float f = k / 10f;
                    c.fillEllipse(m, m, f * 26 * u, f * 26 * u, ((int) (255 * (1 - f) * 0.22f + 30) << 24) | 0xFFFFFF);
                }
                c.fillEllipse(m, m, 14 * u, 14 * u, white);
            }
            case FLAKE -> {
                for (int i = 0; i < 6; i++) {
                    double a = i * Math.PI / 3;
                    float ex = m + (float) Math.cos(a) * 28 * u, ey = m + (float) Math.sin(a) * 28 * u;
                    c.line(m, m, ex, ey, 3.2f * u, PixelCanvas.solid(white));
                    for (int b = 1; b <= 2; b++) {
                        float bx = m + (float) Math.cos(a) * (9 + b * 7) * u, by = m + (float) Math.sin(a) * (9 + b * 7) * u;
                        for (int sgn = -1; sgn <= 1; sgn += 2) {
                            double ba = a + sgn * Math.PI / 4;
                            c.line(bx, by, bx + (float) Math.cos(ba) * 8 * u, by + (float) Math.sin(ba) * 8 * u, 2.4f * u,
                                    PixelCanvas.solid(white));
                        }
                    }
                }
                c.fillEllipse(m, m, 5 * u, 5 * u, white);
            }
            case STAR -> {
                // Four soft points: concave sides, like light catching on a crystal.
                float[] xs = new float[64], ys = new float[64];
                for (int i = 0; i < 64; i++) {
                    double a = i / 64.0 * Math.PI * 2;
                    double r = 4 + 26 * Math.pow(Math.abs(Math.cos(2 * a)), 7);
                    xs[i] = m + (float) (Math.cos(a) * r * u);
                    ys[i] = m + (float) (Math.sin(a) * r * u);
                }
                c.fillPolygon(xs, ys, white);
                c.fillEllipse(m, m, 6 * u, 6 * u, white);
            }
            default -> {
                // A quaver: an oval head, a stem and a flag.
                c.save();
                c.translate(m - 8 * u, m + 16 * u);
                c.rotate(-0.45f);
                c.fillEllipse(0, 0, 10 * u, 7 * u, white);
                c.restore();
                c.line(m + 1 * u, m + 14 * u, m + 1 * u, m - 24 * u, 3.6f * u, PixelCanvas.solid(white));
                float[] fx = {m + 1 * u, m + 16 * u, m + 14 * u, m + 1 * u};
                float[] fy = {m - 24 * u, m - 12 * u, m - 6 * u, m - 16 * u};
                c.fillPolygon(fx, fy, white);
            }
        }
    }

    /** Clears a triangle out of the canvas (the petal's notch). */
    private static void cut(PixelCanvas c, float[] xs, float[] ys) {
        PixelCanvas mask = new PixelCanvas(c.width, c.height);
        mask.fillPolygon(xs, ys, 0xFFFFFFFF);
        int[] m = mask.toArgb(null);
        for (int y = 0; y < c.height; y++) {
            for (int x = 0; x < c.width; x++) {
                float k = (m[y * c.width + x] >>> 24) / 255f;
                if (k > 0f) {
                    int p = c.getPixel(x, y);
                    int a = Math.round((p >>> 24) * (1f - k));
                    c.setPixel(x, y, (a << 24) | (p & 0xFFFFFF));
                }
            }
        }
    }
}
