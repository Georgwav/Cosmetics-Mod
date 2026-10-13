package io.github.georgwav.cosmetics.emote;

import java.util.Locale;

/**
 * How an emote value moves from one keyframe to the next. Besides the plain ones there are the usual easing
 * curves (sine, quad, ... bounce, each in, out and in-out), as named by Blockbench/GeckoLib
 * ({@code easeInOutSine}) and Emotecraft ({@code INOUTSINE}).
 */
public enum Ease {
    LINEAR, STEP, CATMULLROM,
    IN_SINE, OUT_SINE, IN_OUT_SINE,
    IN_QUAD, OUT_QUAD, IN_OUT_QUAD,
    IN_CUBIC, OUT_CUBIC, IN_OUT_CUBIC,
    IN_QUART, OUT_QUART, IN_OUT_QUART,
    IN_QUINT, OUT_QUINT, IN_OUT_QUINT,
    IN_EXPO, OUT_EXPO, IN_OUT_EXPO,
    IN_CIRC, OUT_CIRC, IN_OUT_CIRC,
    IN_BACK, OUT_BACK, IN_OUT_BACK,
    IN_ELASTIC, OUT_ELASTIC, IN_OUT_ELASTIC,
    IN_BOUNCE, OUT_BOUNCE, IN_OUT_BOUNCE;

    /**
     * The ease called {@code name} in any of the usual spellings ({@code easeInOutSine}, {@code INOUTSINE},
     * {@code in_out_sine}), or null when there is no such ease.
     */
    public static Ease byName(String name) {
        String key = name.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
        if (key.startsWith("ease") && key.length() > 4) {
            key = key.substring(4);
        }
        switch (key) {
            case "linear", "lerp":
                return LINEAR;
            case "step", "constant", "none":
                return STEP;
            case "catmullrom", "smooth":
                return CATMULLROM;
            default:
                break;
        }
        for (Ease ease : values()) {
            if (ease.name().replace("_", "").toLowerCase(Locale.ROOT).equals(key)) {
                return ease;
            }
        }
        return null;
    }

    /** Progress {@code t} (0..1) along the curve; {@link #CATMULLROM} is handled by the caller and is linear here. */
    public float apply(float t) {
        return switch (this) {
            case LINEAR, CATMULLROM -> t;
            case STEP -> t < 1f ? 0f : 1f;
            case IN_SINE -> 1f - (float) Math.cos(t * Math.PI / 2);
            case OUT_SINE -> (float) Math.sin(t * Math.PI / 2);
            case IN_OUT_SINE -> (float) (-(Math.cos(Math.PI * t) - 1) / 2);
            case IN_QUAD -> in(t, 2);
            case OUT_QUAD -> out(t, 2);
            case IN_OUT_QUAD -> inOut(t, 2);
            case IN_CUBIC -> in(t, 3);
            case OUT_CUBIC -> out(t, 3);
            case IN_OUT_CUBIC -> inOut(t, 3);
            case IN_QUART -> in(t, 4);
            case OUT_QUART -> out(t, 4);
            case IN_OUT_QUART -> inOut(t, 4);
            case IN_QUINT -> in(t, 5);
            case OUT_QUINT -> out(t, 5);
            case IN_OUT_QUINT -> inOut(t, 5);
            case IN_EXPO -> t <= 0f ? 0f : (float) Math.pow(2, 10 * t - 10);
            case OUT_EXPO -> t >= 1f ? 1f : 1f - (float) Math.pow(2, -10 * t);
            case IN_OUT_EXPO -> t <= 0f ? 0f : t >= 1f ? 1f : t < 0.5f
                    ? (float) Math.pow(2, 20 * t - 10) / 2 : (2 - (float) Math.pow(2, -20 * t + 10)) / 2;
            case IN_CIRC -> 1f - (float) Math.sqrt(1 - t * t);
            case OUT_CIRC -> (float) Math.sqrt(1 - (t - 1) * (t - 1));
            case IN_OUT_CIRC -> t < 0.5f
                    ? (1 - (float) Math.sqrt(1 - 4 * t * t)) / 2
                    : ((float) Math.sqrt(1 - (-2 * t + 2) * (-2 * t + 2)) + 1) / 2;
            case IN_BACK -> 2.70158f * t * t * t - 1.70158f * t * t;
            case OUT_BACK -> 1f + 2.70158f * (t - 1) * (t - 1) * (t - 1) + 1.70158f * (t - 1) * (t - 1);
            case IN_OUT_BACK -> {
                float c = 1.70158f * 1.525f;
                yield t < 0.5f
                        ? (4 * t * t * ((c + 1) * 2 * t - c)) / 2
                        : ((2 * t - 2) * (2 * t - 2) * ((c + 1) * (t * 2 - 2) + c) + 2) / 2;
            }
            case IN_ELASTIC -> t <= 0f ? 0f : t >= 1f ? 1f
                    : (float) (-Math.pow(2, 10 * t - 10) * Math.sin((t * 10 - 10.75) * (2 * Math.PI / 3)));
            case OUT_ELASTIC -> t <= 0f ? 0f : t >= 1f ? 1f
                    : (float) (Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * (2 * Math.PI / 3)) + 1);
            case IN_OUT_ELASTIC -> t <= 0f ? 0f : t >= 1f ? 1f : t < 0.5f
                    ? (float) (-(Math.pow(2, 20 * t - 10) * Math.sin((20 * t - 11.125) * (2 * Math.PI / 4.5))) / 2)
                    : (float) ((Math.pow(2, -20 * t + 10) * Math.sin((20 * t - 11.125) * (2 * Math.PI / 4.5))) / 2 + 1);
            case IN_BOUNCE -> 1f - bounce(1f - t);
            case OUT_BOUNCE -> bounce(t);
            case IN_OUT_BOUNCE -> t < 0.5f ? (1f - bounce(1f - 2 * t)) / 2 : (1f + bounce(2 * t - 1)) / 2;
        };
    }

    private static float in(float t, int power) {
        return (float) Math.pow(t, power);
    }

    private static float out(float t, int power) {
        return 1f - (float) Math.pow(1 - t, power);
    }

    private static float inOut(float t, int power) {
        return t < 0.5f ? (float) (Math.pow(2, power - 1) * Math.pow(t, power))
                : 1f - (float) Math.pow(-2 * t + 2, power) / 2;
    }

    private static float bounce(float t) {
        final float n = 7.5625f;
        final float d = 2.75f;
        if (t < 1 / d) {
            return n * t * t;
        } else if (t < 2 / d) {
            t -= 1.5f / d;
            return n * t * t + 0.75f;
        } else if (t < 2.5 / d) {
            t -= 2.25f / d;
            return n * t * t + 0.9375f;
        }
        t -= 2.625f / d;
        return n * t * t + 0.984375f;
    }
}
