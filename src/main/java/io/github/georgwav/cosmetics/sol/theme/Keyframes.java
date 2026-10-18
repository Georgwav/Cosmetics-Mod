package io.github.georgwav.cosmetics.sol.theme;

import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.sol.util.Ease;

/**
 * Blends a theme's eight times of day by the sun's height on the sky sphere, so the sky, fog, light and clouds follow
 * the world's time. The weights always add up to 1. Going down: noon above 30°, afternoon from 22° to 12°, sunset from
 * 8° to −2°, blue hour from −5° to −12°, night below −17°; coming up: pre-dawn, sunrise and morning in their places.
 * Between two times of day they blend smoothly.
 */
public final class Keyframes {
    /** One day in the quanta the blend is computed at: a new blend at most every ~94 ticks. */
    public static final int QUANTA = 256;
    /** The sky's latitude (the pole's height over the north), which the sun's height follows; set by the sky. */
    public static volatile float latitude = 34f;
    /** Each step's span of full weight, from night up to noon. */
    private static final float[] LO = {-90f, -12f, -2f, 12f, 30f};
    private static final float[] HI = {-17f, -5f, 8f, 22f, 90f};
    private static final ThemeRegistry.Phase[] EVENING = {ThemeRegistry.Phase.NIGHT, ThemeRegistry.Phase.BLUEHOUR,
            ThemeRegistry.Phase.SUNSET, ThemeRegistry.Phase.AFTERNOON, ThemeRegistry.Phase.NOON};
    private static final ThemeRegistry.Phase[] MORNING = {ThemeRegistry.Phase.NIGHT, ThemeRegistry.Phase.PREDAWN,
            ThemeRegistry.Phase.SUNRISE, ThemeRegistry.Phase.MORNING, ThemeRegistry.Phase.NOON};

    private Keyframes() {
    }

    /** Weights for the eight times of day ({@link ThemeRegistry.Phase} order) from the sun's altitude in degrees. */
    public static void weights(float altitude, boolean morning, float[] out) {
        java.util.Arrays.fill(out, 0f);
        ThemeRegistry.Phase[] steps = morning ? MORNING : EVENING;
        for (int i = 0; i < steps.length; i++) {
            if (altitude >= LO[i] && altitude <= HI[i]) {
                out[steps[i].ordinal()] = 1f;
                return;
            }
            if (i + 1 < steps.length && altitude > HI[i] && altitude < LO[i + 1]) {
                float t = Ease.smoothstep(HI[i], LO[i + 1], altitude);
                out[steps[i].ordinal()] = 1f - t;
                out[steps[i + 1].ordinal()] = t;
                return;
            }
        }
        out[ThemeRegistry.Phase.NOON.ordinal()] = 1f;
    }

    /** How much of the night-only sky shows for these weights: the night, and half of blue hour and pre-dawn. */
    public static float night(float[] w) {
        return w[ThemeRegistry.Phase.NIGHT.ordinal()]
                + 0.5f * (w[ThemeRegistry.Phase.BLUEHOUR.ordinal()] + w[ThemeRegistry.Phase.PREDAWN.ordinal()]);
    }

    /**
     * The sun's altitude in degrees for a vanilla time of day fraction (0 = noon, 0.25 = sunset, 0.5 = midnight,
     * 0.75 = sunrise) on the sky sphere at the sky's latitude (at 0° it is the game's own path).
     */
    public static float altitude(float timeOfDay) {
        double c = Math.cos(Math.toRadians(latitude));
        return (float) Math.toDegrees(Math.asin(c * Math.cos(timeOfDay * Math.PI * 2.0)));
    }

    /** Whether a time of day fraction is in the morning half (from midnight to noon). */
    public static boolean morning(float timeOfDay) {
        float t = timeOfDay - (float) Math.floor(timeOfDay);
        return t >= 0.5f;
    }

    /** Rounds a time of day fraction to the blend quantum. */
    public static float quantise(float timeOfDay) {
        float t = timeOfDay - (float) Math.floor(timeOfDay);
        return Math.round(t * QUANTA) / (float) QUANTA;
    }

    /**
     * The world fields of {@code out} (sky, fog, light, clouds, particle scale, night) from the theme's versions
     * blended by {@code w}. The UI fields come from the theme's own values, which don't change with the time.
     */
    public static void blend(ThemeRegistry.Entry e, float[] w, Integer accentOverride, Tokens out) {
        ThemeSpec base = e.base();
        out.themeId = e.id();
        out.ui(base, accentOverride);
        out.glow = base.sky().glow();
        out.band = Colors.withAlpha(out.glow, 0.2f);

        int top = 0, mid = 0, hor = 0, skyGlow = 0, fogColor = 0, tint = 0, cloud = 0, sunLight = 0, shadow = 0;
        float warm = 0, density = 0, start = 0, end = 0, thin = 0, strength = 0, scale = 0;
        float near = 0, far = 0, scatter = 0, haze = 0, boost = 0;
        boolean fixed = false;
        boolean first = true;
        float acc = 0f;
        for (ThemeRegistry.Phase p : ThemeRegistry.Phase.values()) {
            float k = w[p.ordinal()];
            if (k <= 0f) {
                continue;
            }
            ThemeSpec s = e.phase(p);
            if (first) {
                top = s.sky().top();
                mid = s.sky().middle();
                hor = s.sky().horizon();
                skyGlow = s.sky().glow();
                fogColor = s.fog().color();
                tint = s.light().tint();
                cloud = s.clouds().tint() != null ? s.clouds().tint() : 0;
                sunLight = s.light().sun();
                shadow = shadowOf(s);
                first = false;
                acc = k;
            } else {
                // Running blend: the new colour's share of everything blended so far.
                float t = k / (acc + k);
                top = Colors.mix(top, s.sky().top(), t);
                mid = Colors.mix(mid, s.sky().middle(), t);
                hor = Colors.mix(hor, s.sky().horizon(), t);
                skyGlow = Colors.mix(skyGlow, s.sky().glow(), t);
                fogColor = Colors.mix(fogColor, s.fog().color(), t);
                tint = Colors.mix(tint, s.light().tint(), t);
                int c = s.clouds().tint() != null ? s.clouds().tint() : 0;
                cloud = cloud == 0 || c == 0 ? (t >= 0.5f ? c : cloud) : Colors.mix(cloud, c, t);
                sunLight = Colors.mix(sunLight, s.light().sun(), t);
                shadow = Colors.mix(shadow, shadowOf(s), t);
                acc += k;
            }
            warm += s.sky().sunWarmth() * k;
            density += s.fog().density() * k;
            start += s.fog().start() * k;
            end += s.fog().end() * k;
            thin += s.fog().voxyThinning() * k;
            strength += s.light().strength() * k;
            scale += e.particleScale()[p.ordinal()] * k;
            // Fixed fog distances blend with the fractions of a phase without them (at a 12-chunk view).
            if (s.fog().far() > 0f) {
                fixed = true;
            }
            near += (s.fog().far() > 0f ? s.fog().near() : s.fog().start() * 192f) * k;
            far += (s.fog().far() > 0f ? s.fog().far() : s.fog().end() * 192f) * k;
            scatter += s.fog().scatter() * k;
            haze += s.fog().haze() * k;
            boost += s.light().boost() * k;
        }
        out.skyTop = top;
        out.skyMiddle = mid;
        out.skyHorizon = hor;
        out.skyGlow = skyGlow;
        out.sunWarmth = warm;
        out.fogColor = fogColor;
        out.fogDensity = density;
        out.fogStart = start;
        out.fogEnd = end;
        out.voxyThinning = thin;
        out.fogBands = base.fog().bands();
        out.lightTint = tint;
        out.lightStrength = strength;
        out.cloudTint = cloud;
        out.cloudsLitBySun = base.clouds().litBySun();
        out.cloudsVisible = base.clouds().visible();
        out.particleScale = scale;
        out.night = night(w);
        out.fogNear = fixed ? near : 0f;
        out.fogFar = fixed ? far : 0f;
        out.scatter = scatter;
        out.haze = haze;
        out.lightBoost = boost;
        out.lightSun = sunLight;
        out.lightShadow = shadow;
        ThemeSpec.Grade g = base.grade();
        out.gradeHighlights = g.highlights();
        out.gradeShadows = g.shadows();
        out.saturation = g.saturation();
        out.contrast = g.contrast();
        out.bloomTint = g.bloomTint();
        out.bloom = g.bloom();
        out.bloomThreshold = g.threshold();
        out.vignette = g.vignette();
        out.lut = g.lut();
    }

    /** The shadow side's colour: the theme's, or the sky's middle lifted toward its top (sky light from above). */
    static int shadowOf(ThemeSpec s) {
        if (s.light().shadow() != 0) {
            return s.light().shadow();
        }
        return Colors.mix(Colors.scale(s.sky().middle(), 1.6f), s.sky().top(), 0.25f) | 0xFF000000;
    }
}
