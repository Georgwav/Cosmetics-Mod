package io.github.georgwav.cosmetics.sol.atmosphere;

import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.sol.render.WorldDraw;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.sol.util.Ease;
import io.github.georgwav.cosmetics.sol.util.Mulberry32;

/**
 * The aurora: three curtains of light hanging across the sky the theme faces. Each curtain winds and folds back on
 * itself as it drifts, with a bright, sharp lower edge in the theme's first colour that rises through fine vertical
 * rays into its second colour and a pink-red top that fades out. The rays come from a texture of hundreds of
 * streaks laid twice along each curtain, drifting opposite ways, so they shimmer and race along the folds; pulses of
 * brightness run along the curtains, and a soft glow under them lights the sky. All added to the sky, so the stars
 * shine through. Render thread only; nothing is allocated per frame.
 */
final class Aurora {
    /** The rays texture: tiles along a curtain (u), lower edge at the bottom (v = 1). */
    private static final int TEX_W = 512, TEX_H = 128;
    /** Columns along a curtain, and the heights of its rows (0 lower edge, 1 top). */
    private static final int COLUMNS = 128;
    private static final float[] ROWS = {0f, 0.1f, 0.42f, 1f};
    /** How many times each layer of rays repeats along a curtain, and how fast it drifts (tiles a second). */
    private static final float[] REPEAT = {2.6f, 4.3f};
    private static final float[] DRIFT = {0.018f, -0.031f};
    private static final float[] LAYER_ALPHA = {0.62f, 0.46f};
    /** The curtains: how bright, how high their lower edge, how tall, and their turn in the sky. */
    private static final float[] BRIGHT = {0.8f, 0.56f, 0.36f};
    private static final float[] BASE = {8f, 14f, 22f};
    private static final float[] TALL = {20f, 17f, 13f};
    private static final float[] OFFSET = {0f, 14f, -18f};
    private static final int PINK = 0xFFFF5A9E;

    private static SolTexture rays;

    // One curtain's columns this frame: azimuth, lower edge altitude, height and brightness.
    private static final float[] colAz = new float[COLUMNS + 1];
    private static final float[] colAlt = new float[COLUMNS + 1];
    private static final float[] colTall = new float[COLUMNS + 1];
    private static final float[] colA = new float[COLUMNS + 1];
    private static final float[] split = new float[4];
    private static final int[] rowColor = new int[ROWS.length];

    private Aurora() {
    }

    /** Draws the curtains facing {@code az} (degrees), {@code night} 0..1, at time {@code t} (seconds). */
    static void render(int[] colors, float az, float t, float night) {
        float boost = MusicSky.auroraBoost();
        float tall = 1f + boost;
        float bright = Math.min(2.2f, 1f + boost * 0.75f) * night;
        int low = colors[0];
        int high = colors.length > 1 ? colors[1] : Colors.mix(colors[0], PINK, 0.5f);
        glow(low, high, az, t, bright);
        WorldDraw.begin(WorldDraw.SKY_ADD, rays());
        for (int c = 0; c < BRIGHT.length; c++) {
            columns(c, az + OFFSET[c], t, tall);
            // Further curtains lean toward the second colour; every top burns pink-red before it fades.
            int edge = Colors.mix(low, high, c * 0.3f);
            int top = Colors.mix(high, PINK, 0.55f + c * 0.15f);
            for (int layer = 0; layer < REPEAT.length; layer++) {
                float a = BRIGHT[c] * LAYER_ALPHA[layer] * bright;
                rowColor[0] = Colors.withAlpha(Colors.mix(edge, 0xFFFFFFFF, 0.25f), a);
                rowColor[1] = Colors.withAlpha(edge, a);
                rowColor[2] = Colors.withAlpha(Colors.mix(edge, high, 0.35f), a * 0.9f);
                rowColor[3] = Colors.withAlpha(top, a);
                float offset = c * 0.37f + layer * 0.21f + t * DRIFT[layer];
                strips(REPEAT[layer], offset);
            }
        }
        WorldDraw.end();
    }

    /** The curtain's path this frame: winding and folding back, low at its ends as it goes away from you. */
    private static void columns(int c, float centre, float t, float tall) {
        float span = 170f;
        for (int i = 0; i <= COLUMNS; i++) {
            float s = i / (float) COLUMNS;
            column(c, s, centre, span, t, tall, i);
        }
    }

    private static void column(int c, float s, float centre, float span, float t, float tall, int at) {
        double tau = Math.PI * 2;
        float fold = 13f * (float) Math.sin(tau * 1.3 * s + t * 0.04f + c * 2f) + 7f * (float) Math.sin(tau * 3.1 * s - t * 0.07f + c);
        float away = 0.68f + 0.32f * (float) Math.sin(Math.PI * s);
        float wave = 4f * (float) Math.sin(tau * 0.9 * s + t * 0.06f + c) + 2.5f * (float) Math.sin(tau * 2.3 * s - t * 0.09f);
        colAz[at] = centre + (s - 0.5f) * span + fold;
        colAlt[at] = (BASE[c] + wave) * away;
        colTall[at] = (TALL[c] + 6f * (float) Math.sin(tau * 1.7 * s + t * 0.11f + c)) * away * tall;
        // Pulses running along the curtain, and its ends faded rather than cut.
        float pulse = 0.55f + 0.45f * (0.5f + 0.5f * (float) Math.sin(tau * 2.2 * s - t * 0.35f + c))
                * (0.6f + 0.4f * (float) Math.sin(tau * 5.3 * s + t * 0.9f));
        colA[at] = pulse * Ease.smoothstep(0f, 0.12f, s) * Ease.smoothstep(1f, 0.88f, s);
    }

    /** The curtain's quads, the rays texture repeating {@code repeat} times along it from {@code offset}. */
    private static void strips(float repeat, float offset) {
        float du = repeat / COLUMNS;
        for (int i = 0; i < COLUMNS; i++) {
            if (colA[i] <= 0.004f && colA[i + 1] <= 0.004f) {
                continue;
            }
            float u0 = offset + i * du;
            u0 -= (float) Math.floor(u0);
            float u1 = u0 + du;
            if (u1 <= 1f) {
                quads(i, 0f, 1f, u0, u1);
            } else {
                // The texture's seam falls inside this column: two quads, one each side of it.
                float f = (1f - u0) / du;
                quads(i, 0f, f, u0, 1f);
                quads(i, f, 1f, 0f, u1 - 1f);
            }
        }
    }

    /** The rows of quads between fractions {@code f0} and {@code f1} of column i to i + 1. */
    private static void quads(int i, float f0, float f1, float u0, float u1) {
        at(i, f0, split, 0);
        at(i, f1, split, 2);
        float az0 = split[0], az1 = split[2];
        float alt0 = Ease.lerp(colAlt[i], colAlt[i + 1], f0), alt1 = Ease.lerp(colAlt[i], colAlt[i + 1], f1);
        float h0 = Ease.lerp(colTall[i], colTall[i + 1], f0), h1 = Ease.lerp(colTall[i], colTall[i + 1], f1);
        float a0 = split[1], a1 = split[3];
        for (int r = 0; r + 1 < ROWS.length; r++) {
            float ra = ROWS[r], rb = ROWS[r + 1];
            int ca = rowColor[r], cb = rowColor[r + 1];
            SkyDraw.altAz(alt0 + h0 * rb, az0, u0, 1f - rb, Colors.withAlpha(cb, Colors.a(cb) / 255f * a0));
            SkyDraw.altAz(alt0 + h0 * ra, az0, u0, 1f - ra, Colors.withAlpha(ca, Colors.a(ca) / 255f * a0));
            SkyDraw.altAz(alt1 + h1 * ra, az1, u1, 1f - ra, Colors.withAlpha(ca, Colors.a(ca) / 255f * a1));
            SkyDraw.altAz(alt1 + h1 * rb, az1, u1, 1f - rb, Colors.withAlpha(cb, Colors.a(cb) / 255f * a1));
        }
    }

    /** Azimuth and brightness a fraction {@code f} of the way from column i to i + 1. */
    private static void at(int i, float f, float[] out, int o) {
        out[o] = Ease.lerp(colAz[i], colAz[i + 1], f);
        out[o + 1] = Ease.lerp(colA[i], colA[i + 1], f);
    }

    /** The light the curtains throw on the sky under them: wide soft glows along the nearest one. */
    private static void glow(int low, int high, float az, float t, float bright) {
        WorldDraw.begin(WorldDraw.SKY_ADD, SkyDraw.glow());
        for (int k = 0; k < 7; k++) {
            float s = (k + 0.5f) / 7f;
            float z = az + (s - 0.5f) * 150f;
            float alt = 10f + 3f * (float) Math.sin(s * 5f + t * 0.05f);
            double al = Math.toRadians(alt), zr = Math.toRadians(z);
            float h = (float) Math.cos(al);
            SkyDraw.billboard((float) (-Math.sin(zr) * h), (float) Math.sin(al), (float) (Math.cos(zr) * h));
            float a = 0.05f * bright * (0.7f + 0.3f * (float) Math.sin(s * 9f + t * 0.3f)) * (float) Math.sin(Math.PI * s);
            SkyDraw.glowAt(0f, -0.1f, 0.4f, 0.3f, Colors.withAlpha(Colors.mix(low, high, 0.25f), a));
        }
        WorldDraw.end();
    }

    // ------------------------------------------------------------------ the rays texture

    static SolTexture rays() {
        if (rays == null || rays.released()) {
            rays = SolTexture.of("aurora_rays", TEX_W, TEX_H, true, bakeRays());
        }
        return rays;
    }

    /**
     * Hundreds of streaks side by side, tiling left to right: each with its own brightness, width and reach, over a
     * fainter glow between them; all rising from a sharp, bright lower edge and fading upward, the bright ones
     * reaching highest.
     */
    static int[] bakeRays() {
        Mulberry32 r = new Mulberry32(2718);
        int n = 220;
        float[] px = new float[n], pw = new float[n], pb = new float[n], ph = new float[n];
        for (int k = 0; k < n; k++) {
            px[k] = (float) r.next();
            pw[k] = 0.0016f + (float) Math.pow(r.next(), 2) * 0.009f;
            pb[k] = 0.25f + 0.75f * (float) r.next();
            ph[k] = 0.35f + 0.65f * (float) r.next();
        }
        float[] ray = new float[TEX_W], reach = new float[TEX_W];
        for (int i = 0; i < TEX_W; i++) {
            float x = (i + 0.5f) / TEX_W;
            float sum = 0f, wsum = 0f, hsum = 0f;
            for (int k = 0; k < n; k++) {
                float dx = Math.abs(x - px[k]);
                dx = Math.min(dx, 1f - dx);
                float g = (float) Math.exp(-(dx / pw[k]) * (dx / pw[k]));
                sum += pb[k] * g;
                wsum += g;
                hsum += ph[k] * g;
            }
            ray[i] = Math.min(1f, sum);
            reach[i] = wsum > 1e-3f ? hsum / wsum : 0.5f;
        }
        int[] out = new int[TEX_W * TEX_H];
        for (int j = 0; j < TEX_H; j++) {
            // t: 0 at the lower edge (the texture's bottom), 1 at the top.
            float t = 1f - (j + 0.5f) / TEX_H;
            for (int i = 0; i < TEX_W; i++) {
                float rr = ray[i];
                float h = 0.3f + 0.7f * reach[i] * (0.5f + 0.5f * rr);
                float edge = Ease.smoothstep(0f, 0.05f, t);
                float fall = (float) Math.exp(-Math.max(0f, t - 0.06f) * 1.7f / h);
                float line = 0.4f * (float) Math.exp(-Math.pow((t - 0.07f) / 0.03f, 2)) * (0.5f + 0.5f * rr);
                float v = edge * fall * (0.22f + 0.78f * rr) + line;
                int a = Math.round(Math.min(1f, v) * 255f);
                out[j * TEX_W + i] = (a << 24) | 0xFFFFFF;
            }
        }
        return out;
    }
}
