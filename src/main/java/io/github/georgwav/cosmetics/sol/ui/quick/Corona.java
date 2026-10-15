package io.github.georgwav.cosmetics.sol.ui.quick;

import io.github.georgwav.cosmetics.sol.util.Colors;

/**
 * The quick menu's total eclipse: a pixel corona round your disc, 112 x 112 cells shown at 4x. Long streamers lie
 * along the horizon, short plumes stand over the poles, a few pink prominences sit on the rim, and in the personal
 * ring a streamer reaches out to the option you point at. A port of the Sol menu prototype's {@code qCorona()},
 * with the same tables made once. Pure maths into an ARGB array, so it is tested without the game.
 */
public final class Corona {
    /** Cells across, design pixels per cell, angle bins. */
    public static final int N = 112, CELL = 4, BINS = 120;
    /** Your disc's radius in design pixels (the prototype's RD). */
    public static final float DISC = 56f;

    private static final float[] R = new float[N * N], T = new float[N * N], IN = new float[N * N], OUT = new float[N * N];
    private static final int[] BIN = new int[N * N];

    static {
        for (int j = 0; j < N; j++) {
            for (int i = 0; i < N; i++) {
                int k = j * N + i;
                float dx = (i + 0.5f - N / 2f) * CELL, dy = (j + 0.5f - N / 2f) * CELL;
                float r = (float) Math.hypot(dx, dy), t = (float) Math.atan2(dy, dx), d = Math.max(0f, r - DISC);
                R[k] = r;
                T[k] = t;
                BIN[k] = (int) Math.floor((t + Math.PI) / (2 * Math.PI) * BINS) % BINS;
                IN[k] = (float) Math.exp(-d / 10f);
                OUT[k] = (float) Math.exp(-d / 46f);
            }
        }
    }

    private final float[] lobe = new float[BINS];
    private final float[] prom = new float[BINS];
    /** The picture, ARGB, N x N. */
    public final int[] argb = new int[N * N];

    /**
     * Draws the corona at strength {@code e} (0..1.3), time {@code t} seconds, in colour {@code color}; the streamer
     * toward {@code rayDeg} at strength {@code rayA}, brightened by a fresh pick ({@code flare}).
     */
    public void draw(float e, float t, int color, float rayDeg, float rayA, float flare) {
        float cr = Colors.r(color), cg = Colors.g(color), cb = Colors.b(color);
        float ra = (float) Math.toRadians(rayDeg), rA = rayA * (1f + flare * 1.2f);
        for (int b = 0; b < BINS; b++) {
            float th = (b + 0.5f) / BINS * 2f * (float) Math.PI - (float) Math.PI;
            float eq = Math.abs((float) Math.cos(th));
            // Long streamers along the horizon, short plumes over the poles, slowly drifting.
            lobe[b] = Math.max(0f, 0.24f + 0.85f * eq * eq * eq + 0.15f * (float) Math.sin(th * 4 + t * 0.35f)
                    + 0.09f * (float) Math.sin(th * 9 - t * 0.5f + 1.3f));
            // A few small pink prominences on the rim.
            prom[b] = Math.max(0f, (float) (Math.sin(th * 5 + 1.1) * Math.sin(th * 3 - t * 0.22 + 0.4)) - 0.62f) * 4f;
        }
        for (int k = 0; k < N * N; k++) {
            float r = R[k];
            if (r < DISC - 3f || r > 222f) {
                argb[k] = 0;
                continue;
            }
            float d = r - DISC;
            int b = BIN[k];
            float in = e * (IN[k] * 1.15f + OUT[k] * 0.6f * lobe[b]);
            if (rA > 0.01f) {
                float da = T[k] - ra;
                da -= 6.2832f * Math.round(da / 6.2832f);
                if (da > -0.9f && da < 0.9f) {
                    float w = 6f + d * 0.11f, x = da * r / w;
                    float f = d < 66f ? 1f - d / 200f : (float) Math.exp(-(d - 66f) / 8f) * 0.66f;
                    in += rA * f * ((float) Math.exp(-x * x) + (x > -0.6f && x < 0.6f ? 0.35f : 0f));
                }
            }
            float p = d < 6f ? prom[b] * Math.min(1f, e) * (1f - d / 6f) : 0f;
            if (p > 0.4f) {
                argb[k] = Colors.argb(235, 255, 98, 132);
                continue;
            }
            float q = Math.min(6f, (float) Math.floor(in * 6f)) / 6f;
            if (q <= 0f) {
                argb[k] = 0;
                continue;
            }
            float w = Math.min(1f, q * 1.15f);
            argb[k] = Colors.argb(Math.round(Math.min(1f, q) * 235f), Math.round(cr + (255f - cr) * w),
                    Math.round(cg + (255f - cg) * w), Math.round(cb + (255f - cb) * w));
        }
    }

    /** The corona's colour for a theme: a cold white with a touch of the theme's glow. */
    public static int color(int glow) {
        return Colors.mix(0xFFEEF2FF, glow, 0.3f);
    }
}
