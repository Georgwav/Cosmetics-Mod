package io.github.georgwav.cosmetics.sol.util;

/** The easing curves the prototypes use for sweeps and plays. */
public final class Ease {
    private Ease() {
    }

    /** Cubic in-out (the theme sweep). */
    public static float inOutCubic(float k) {
        k = clamp01(k);
        return k < 0.5f ? 4f * k * k * k : 1f - (float) Math.pow(-2f * k + 2f, 3) / 2f;
    }

    /** Quadratic in-out (the Sun lab's "play sunset"). */
    public static float inOutQuad(float k) {
        k = clamp01(k);
        return k < 0.5f ? 2f * k * k : 1f - (float) Math.pow(-2f * k + 2f, 2) / 2f;
    }

    public static float outCubic(float k) {
        k = clamp01(k);
        return 1f - (float) Math.pow(1f - k, 3);
    }

    public static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(1f, v);
    }

    public static float smoothstep(float edge0, float edge1, float x) {
        float t = clamp01((x - edge0) / (edge1 - edge0));
        return t * t * (3f - 2f * t);
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
