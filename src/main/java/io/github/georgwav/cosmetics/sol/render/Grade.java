package io.github.georgwav.cosmetics.sol.render;

import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.sol.util.Ease;

/**
 * A theme's colour grade as the light pass's 32x32x32 LUT: 32 slices of 32x32 side by side in a 1024x32 image
 * (red across, green down, blue by slice). Contrast round the middle, saturation, highlights pulled toward the
 * theme's highlight colour and shadows lifted toward its shadow colour; {@code strength} blends from no grade (0)
 * to the full grade (1). Pure maths, so it is tested without the game.
 */
public final class Grade {
    public static final int W = 1024, H = 32, N = 32;

    private Grade() {
    }

    /** Fills {@code out} (W x H ARGB, allocated when null or the wrong size) and returns it. */
    public static int[] lut(int highlights, int shadows, float saturation, float contrast, float strength, int[] out) {
        if (out == null || out.length != W * H) {
            out = new int[W * H];
        }
        float hr = Colors.r(highlights) / 255f, hg = Colors.g(highlights) / 255f, hb = Colors.b(highlights) / 255f;
        float hl = Math.max(0.2f, 0.2126f * hr + 0.7152f * hg + 0.0722f * hb);
        hr /= hl;
        hg /= hl;
        hb /= hl;
        float sr = Colors.r(shadows) / 255f, sg = Colors.g(shadows) / 255f, sb = Colors.b(shadows) / 255f;
        float[] c = new float[3];
        for (int b = 0; b < N; b++) {
            for (int g = 0; g < N; g++) {
                for (int r = 0; r < N; r++) {
                    float r0 = r / (N - 1f), g0 = g / (N - 1f), b0 = b / (N - 1f);
                    apply(r0, g0, b0, hr, hg, hb, sr, sg, sb, saturation, contrast, c);
                    float gr = clamp(r0 + (c[0] - r0) * strength);
                    float gg = clamp(g0 + (c[1] - g0) * strength);
                    float gb = clamp(b0 + (c[2] - b0) * strength);
                    out[g * W + b * N + r] = 0xFF000000 | (Math.round(gr * 255f) << 16) | (Math.round(gg * 255f) << 8)
                            | Math.round(gb * 255f);
                }
            }
        }
        return out;
    }

    /** The LUT's entry for a colour (nearest), for tests: what the grade does to {@code rgb}. */
    public static int lookup(int[] lut, int rgb) {
        int r = Math.round(Colors.r(rgb) / 255f * (N - 1)), g = Math.round(Colors.g(rgb) / 255f * (N - 1));
        int b = Math.round(Colors.b(rgb) / 255f * (N - 1));
        return lut[g * W + b * N + r];
    }

    /** One colour through the grade (sRGB values 0..1), into {@code out}. */
    static void apply(float r, float g, float b, float hr, float hg, float hb, float sr, float sg, float sb, float sat,
                      float con, float[] out) {
        r = (r - 0.5f) * con + 0.5f;
        g = (g - 0.5f) * con + 0.5f;
        b = (b - 0.5f) * con + 0.5f;
        float l = 0.2126f * r + 0.7152f * g + 0.0722f * b;
        r = l + (r - l) * sat;
        g = l + (g - l) * sat;
        b = l + (b - l) * sat;
        l = clamp(l);
        float wh = Ease.smoothstep(0.35f, 1f, l) * 0.25f;
        r += (r * hr - r) * wh;
        g += (g * hg - g) * wh;
        b += (b * hb - b) * wh;
        float ws = (1f - Ease.smoothstep(0f, 0.5f, l)) * 0.4f;
        r += (Math.max(r, sr) - r) * ws * 0.6f + (sr - r) * ws * 0.25f;
        g += (Math.max(g, sg) - g) * ws * 0.6f + (sg - g) * ws * 0.25f;
        b += (Math.max(b, sb) - b) * ws * 0.6f + (sb - b) * ws * 0.25f;
        out[0] = clamp(r);
        out[1] = clamp(g);
        out[2] = clamp(b);
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
