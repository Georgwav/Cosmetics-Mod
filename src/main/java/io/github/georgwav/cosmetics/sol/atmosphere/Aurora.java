package io.github.georgwav.cosmetics.sol.atmosphere;

import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.sol.render.WorldDraw;
import io.github.georgwav.cosmetics.sol.sky.LabNoise;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.sol.util.Ease;
import io.github.georgwav.cosmetics.sol.util.Mulberry32;

/**
 * The aurora of the Night Sky lab ({@code AURIB}, {@code AURRAY}): three curtains of light hanging over the northern
 * sky, the nearest lowest and widest. Each winds and folds as it drifts, light running along it in waves. Its lower
 * edge is sharp, a thin pink fringe under a white-green line, and it rises through green into violet and fades out at
 * the top. Fine vertical rays come from a texture of hundreds of streaks laid twice along each curtain, drifting
 * opposite ways, and a soft green glow inside the curtains lights the sky round them. All added to the sky, so the
 * stars shine through. Render thread only; nothing is allocated per frame.
 */
final class Aurora {
    /** The curtain texture: rays tiling along a curtain (u), its lower edge at the bottom (v = 1). */
    private static final int TEX_W = 512, TEX_H = 256;
    private static final int COLUMNS = 200;
    /** How many times each layer of rays repeats along a curtain, how fast it drifts (tiles a second), its share. */
    private static final float[] REPEAT = {2.6f, 4.3f};
    private static final float[] DRIFT = {0.018f, -0.031f};
    private static final float[] LAYER_ALPHA = {0.62f, 0.5f};
    /**
     * The lab's curtains: azimuth span (degrees from the theme's facing), lower edge and ray length (degrees), how far
     * they fold and at what frequencies, how fast light runs along them, and how bright they are.
     */
    private static final float[] A0 = {-75f, -40f, -15f}, A1 = {95f, 70f, 45f};
    private static final float[] BASE = {10f, 19f, 30f}, TALL = {24f, 20f, 15f};
    private static final float[] FOLD = {9f, 7f, 5f}, F1 = {1.6f, 2.3f, 1.2f}, F2 = {3.7f, 4.4f, 5.1f};
    private static final float[] SPEED = {.12f, -.1f, .08f}, WAVE = {.6f, -.45f, .8f}, BRIGHT = {1f, .75f, .55f};
    private static final int PINK = 0xFFFF6EBE, TOP = 0xFFAA3CC8;

    private static SolTexture tex;
    private static int texLow, texHigh;
    private static float[] ray, reach;

    // One curtain's columns this frame: azimuth, lower edge and top altitude, brightness.
    private static final float[] colAz = new float[COLUMNS + 1];
    private static final float[] colAlt = new float[COLUMNS + 1];
    private static final float[] colTop = new float[COLUMNS + 1];
    private static final float[] colA = new float[COLUMNS + 1];

    private Aurora() {
    }

    /** Draws the curtains facing {@code az} (degrees, the game's: 180 is north), at time {@code t} (seconds). */
    static void render(int[] colors, float az, float t, float rain) {
        float k = Ease.smoothstep(-7f, -15f, NightSky.SPHERE.sunAlt) * (1f - rain);
        if (k <= 0.004f) {
            return;
        }
        float boost = MusicSky.auroraBoost();
        float tall = 1f + boost;
        k *= Math.min(2.2f, 1f + boost * 0.75f);
        int low = colors[0];
        int high = colors.length > 1 ? colors[1] : Colors.mix(colors[0], PINK, 0.5f);
        SolTexture rays = texture(low, high);
        for (int c = 0; c < A0.length; c++) {
            columns(c, az, t, tall);
            WorldDraw.begin(WorldDraw.SKY_ADD, rays);
            for (int layer = 0; layer < REPEAT.length; layer++) {
                strips(REPEAT[layer], c * 0.37f + layer * 0.21f + t * DRIFT[layer], LAYER_ALPHA[layer] * k);
            }
            WorldDraw.end();
            glow(low, k);
        }
    }

    /** Curtain c's path this frame (the lab's AURIB maths). */
    private static void columns(int c, float az, float t, float tall) {
        double tau = Math.PI * 2;
        for (int i = 0; i <= COLUMNS; i++) {
            float u = i / (float) COLUMNS;
            colAz[i] = az + Ease.lerp(A0[c], A1[c], u) + FOLD[c] * (float) Math.sin(u * tau * F1[c] + t * SPEED[c])
                    + FOLD[c] * 0.45f * (float) Math.sin(u * tau * F2[c] - t * SPEED[c] * 1.7f + 1.3f);
            float b0 = BASE[c] + 2.2f * (float) Math.sin(u * tau * 1.3f + t * 0.15f) + 1.2f * (float) LabNoise.vn1(u * 9 + t * 0.2f);
            colAlt[i] = b0;
            colTop[i] = b0 + TALL[c] * tall * (0.65f + 0.35f * (float) LabNoise.vn1(u * 16 + t * 0.3f));
            colA[i] = Ease.smoothstep(0f, 0.12f, u) * Ease.smoothstep(1f, 0.88f, u)
                    * (0.35f + 0.65f * (float) LabNoise.vn1(u * 11 - t * WAVE[c]))
                    * (0.55f + 0.45f * (float) LabNoise.vn1(u * 70 + t * 1.6f)) * BRIGHT[c];
        }
    }

    /** The curtain's quads, the texture repeating {@code repeat} times along it from {@code offset}. */
    private static void strips(float repeat, float offset, float alpha) {
        float du = repeat / COLUMNS;
        for (int i = 0; i < COLUMNS; i++) {
            if (colA[i] <= 0.004f && colA[i + 1] <= 0.004f) {
                continue;
            }
            float u0 = offset + i * du;
            u0 -= (float) Math.floor(u0);
            float u1 = u0 + du;
            if (u1 <= 1f) {
                quad(i, 0f, 1f, u0, u1, alpha);
            } else {
                // The texture's seam falls inside this column: two quads, one each side of it.
                float f = (1f - u0) / du;
                quad(i, 0f, f, u0, 1f, alpha);
                quad(i, f, 1f, 0f, u1 - 1f, alpha);
            }
        }
    }

    /** The curtain from fraction {@code f0} to {@code f1} of the way from column i to i + 1. */
    private static void quad(int i, float f0, float f1, float u0, float u1, float alpha) {
        float az0 = Ease.lerp(colAz[i], colAz[i + 1], f0), az1 = Ease.lerp(colAz[i], colAz[i + 1], f1);
        float b0 = Ease.lerp(colAlt[i], colAlt[i + 1], f0), b1 = Ease.lerp(colAlt[i], colAlt[i + 1], f1);
        float t0 = Ease.lerp(colTop[i], colTop[i + 1], f0), t1 = Ease.lerp(colTop[i], colTop[i + 1], f1);
        int c0 = Colors.withAlpha(0xFFFFFFFF, Math.min(1f, alpha * Ease.lerp(colA[i], colA[i + 1], f0)));
        int c1 = Colors.withAlpha(0xFFFFFFFF, Math.min(1f, alpha * Ease.lerp(colA[i], colA[i + 1], f1)));
        SkyDraw.altAz(t0, az0, u0, 0f, c0);
        SkyDraw.altAz(b0, az0, u0, 1f, c0);
        SkyDraw.altAz(b1, az1, u1, 1f, c1);
        SkyDraw.altAz(t1, az1, u1, 0f, c1);
    }

    /** The lab's soft glow inside the curtain every ninth step, lighting the sky round it. */
    private static void glow(int low, float k) {
        WorldDraw.begin(WorldDraw.SKY_ADD, SkyDraw.glow());
        int c = Colors.mix(low, 0xFF3CDC8C, 0.5f);
        for (int i = 6; i <= COLUMNS; i += 7) {
            float a = 0.07f * colA[i] * k;
            if (a <= 0.003f) {
                continue;
            }
            float h = colTop[i] - colAlt[i];
            double al = Math.toRadians(colAlt[i] + 0.65f * h), zr = Math.toRadians(colAz[i]);
            float ch = (float) Math.cos(al);
            SkyDraw.billboard((float) (-Math.sin(zr) * ch), (float) Math.sin(al), (float) (Math.cos(zr) * ch));
            float r = 0.7f * (float) Math.toRadians(h) / SkyDraw.T;
            SkyDraw.glowAt(0f, 0f, r, r * 0.6f, Colors.withAlpha(c, Math.min(1f, a)));
        }
        WorldDraw.end();
    }

    // ------------------------------------------------------------------ the curtain texture

    private static SolTexture texture(int low, int high) {
        if (tex != null && !tex.released() && texLow == low && texHigh == high) {
            return tex;
        }
        int[] px = bake(low, high);
        if (tex == null || tex.released()) {
            tex = SolTexture.of("aurora_rays", TEX_W, TEX_H, true, px);
        } else {
            tex.upload(px);
        }
        texLow = low;
        texHigh = high;
        return tex;
    }

    /**
     * The lab's {@code AURRAY} colours and brightness up the curtain (a pink fringe on the lower edge, a white-green
     * line, green, then violet and purple fading out at the top), through hundreds of streaks side by side that tile
     * left to right, the bright ones reaching highest.
     */
    static int[] bake(int low, int high) {
        if (ray == null) {
            rays();
        }
        int edge = Colors.mix(low, 0xFFFFFFFF, 0.62f), top = Colors.mix(high, TOP, 0.7f);
        int[] out = new int[TEX_W * TEX_H];
        for (int j = 0; j < TEX_H; j++) {
            // t as in the lab: 0 at the top, 1 at the lower edge.
            float t = j / (float) (TEX_H - 1);
            int col;
            float a;
            if (t > 0.975f) {
                col = PINK;
                a = (1f - t) / 0.025f * 0.9f;
            } else {
                a = (float) Math.pow(t, 1.7) * (1f + 1.2f * (float) LabNoise.sstep(.86, .96, t));
                col = t < 0.35f ? Colors.mix(top, high, t / 0.35f)
                        : t < 0.62f ? Colors.mix(high, low, (t - 0.35f) / 0.27f)
                        : Colors.mix(low, edge, (float) LabNoise.sstep(.8, .97, t));
            }
            a = Math.min(1f, a);
            float up = 1f - t;
            for (int i = 0; i < TEX_W; i++) {
                float rr = ray[i];
                // Faint rays fade before they reach the top; bright ones go all the way.
                float fade = 1f - up * (1f - reach[i]) * (1.2f - rr) * 0.7f;
                float v = a * (0.55f + 0.45f * rr) * Math.max(0f, fade);
                out[j * TEX_W + i] = (Math.round(Math.min(1f, v) * 255f) << 24) | (col & 0xFFFFFF);
            }
        }
        return out;
    }

    /** Hundreds of streaks over a fainter glow between them: brightness and reach across the texture. */
    private static void rays() {
        Mulberry32 r = new Mulberry32(2718);
        int n = 220;
        float[] px = new float[n], pw = new float[n], pb = new float[n], ph = new float[n];
        for (int k = 0; k < n; k++) {
            px[k] = (float) r.next();
            pw[k] = 0.0016f + (float) Math.pow(r.next(), 2) * 0.009f;
            pb[k] = 0.25f + 0.75f * (float) r.next();
            ph[k] = 0.35f + 0.65f * (float) r.next();
        }
        float[] ry = new float[TEX_W], re = new float[TEX_W];
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
            ry[i] = Math.min(1f, sum);
            re[i] = wsum > 1e-3f ? hsum / wsum : 0.5f;
        }
        reach = re;
        ray = ry;
    }
}
