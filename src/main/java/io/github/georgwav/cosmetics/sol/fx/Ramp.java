package io.github.georgwav.cosmetics.sol.fx;

import io.github.georgwav.cosmetics.sol.util.Colors;

/**
 * The Sol FX style's four tones (C.1): every effect is drawn from these, made from one colour. Core is the colour
 * 60% toward white, main the colour itself, shade 35% toward black and deep 60% toward black. Gradients are bands of
 * these tones, never smooth; fades go in a few alpha steps. Plain arithmetic, no game classes.
 */
public final class Ramp {
    public static final int CORE = 0, MAIN = 1, SHADE = 2, DEEP = 3, TONES = 4;

    private Ramp() {
    }

    /** The four tones of {@code argb} (opaque) into {@code out}; returns it. */
    public static int[] of(int argb, int[] out) {
        int c = argb | 0xFF000000;
        out[CORE] = Colors.mixRgb(c, 0xFFFFFFFF, 0.6f) | 0xFF000000;
        out[MAIN] = c;
        out[SHADE] = Colors.mixRgb(c, 0xFF000000, 0.35f) | 0xFF000000;
        out[DEEP] = Colors.mixRgb(c, 0xFF000000, 0.6f) | 0xFF000000;
        return out;
    }

    public static int[] of(int argb) {
        return of(argb, new int[TONES]);
    }

    /**
     * An alpha from a smooth 0..1 fade as one of {@code steps} levels (1/steps, 2/steps ... 1), 0 when it's out: the
     * stepped fades of the pixel style. With {@code snap} off the fade stays smooth.
     */
    public static float steps(float alpha, int steps, boolean snap) {
        if (alpha <= 0f) {
            return 0f;
        }
        if (alpha >= 1f) {
            return 1f;
        }
        if (!snap) {
            return alpha;
        }
        return (float) Math.ceil(alpha * steps) / steps;
    }

    /** The tone a band at {@code t} (0 at the hot end, 1 at the cold end) takes: core, main, shade, then deep. */
    public static int band(float t) {
        if (t < 0.25f) {
            return CORE;
        }
        if (t < 0.5f) {
            return MAIN;
        }
        return t < 0.75f ? SHADE : DEEP;
    }

    /** A position on the texel grid (1/16 block) when {@code snap} is on. */
    public static float snap(float v, boolean snap) {
        return snap ? Math.round(v * 16f) / 16f : v;
    }

    public static double snap(double v, boolean snap) {
        return snap ? Math.round(v * 16.0) / 16.0 : v;
    }

    /** Pops overshoot (C.1): 1.25 then 1.1 then 1, in three steps over the first {@code popTime} seconds. */
    public static float pop(float age, float popTime, boolean snap) {
        if (age >= popTime) {
            return 1f;
        }
        float k = Math.max(0f, age / popTime);
        if (!snap) {
            return k < 0.33f ? k / 0.33f * 1.25f : 1.25f - 0.25f * (k - 0.33f) / 0.67f;
        }
        return k < 0.34f ? 1.25f : k < 0.67f ? 1.1f : 1f;
    }
}
