package io.github.georgwav.cosmetics.sol.atmosphere;

import io.github.georgwav.cosmetics.sol.render.WorldDraw;
import io.github.georgwav.cosmetics.sol.theme.ThemeSpec;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.sol.util.Ease;
import io.github.georgwav.cosmetics.sol.util.Mulberry32;

/**
 * The Night Sky lab's weather at night. Monsoon's broken cloud ({@code CLOUDS}): seventy dark soft clouds drifting
 * across the stars with the wind, stars showing in the gaps, lit pale blue from inside when the lightning flashes.
 * Silver Night's haze: a silver band lying along the horizon all round. Render thread only; nothing allocated per
 * frame.
 */
final class NightClouds {
    private static final int N = 70;
    private static final float[] az = new float[N], alt = new float[N], rad = new float[N], alpha = new float[N];
    private static final int CLOUD = 0xFF0E121C, FLASH = 0xFF96AADC;
    private static final int HAZE_SEGMENTS = 48;

    static {
        for (int i = 0; i < N; i++) {
            Mulberry32 r = new Mulberry32(500 + i);
            // The lab's azimuths are from north through east; the game's from south through west.
            az[i] = (float) r.next() * 360f + 180f;
            alt[i] = 12f + (float) r.next() * 55f;
            rad[i] = 7f + (float) r.next() * 10f;
            alpha[i] = 0.72f + (float) r.next() * 0.22f;
        }
    }

    private NightClouds() {
    }

    /** The broken cloud, after the stars and nebulae and before the planets and the moon (the lab's order). */
    static void clouds(ThemeSpec theme, float t, float rain) {
        if (!theme.night().clouds()) {
            return;
        }
        // Night only: by day the theme's own overcast is the game's clouds.
        float k = Ease.smoothstep(-3f, -12f, NightSky.SPHERE.sunAlt) * (1f - rain);
        if (k <= 0.004f) {
            return;
        }
        float drift = t * 0.9f;
        WorldDraw.begin(WorldDraw.SKY_ALPHA, SkyDraw.softGlow());
        for (int i = 0; i < N; i++) {
            billboard(i, drift);
            float r = (float) Math.tan(Math.toRadians(rad[i])) / SkyDraw.T;
            SkyDraw.glowAt(0f, 0f, r, r * 0.6f, Colors.withAlpha(CLOUD, alpha[i] * k));
        }
        WorldDraw.end();
        float flash = Extras.flash / 0.55f;
        if (flash > 0.01f) {
            WorldDraw.begin(WorldDraw.SKY_ADD, SkyDraw.softGlow());
            for (int i = 0; i < N; i++) {
                billboard(i, drift);
                float r = (float) Math.tan(Math.toRadians(rad[i])) / SkyDraw.T;
                SkyDraw.glowAt(0f, 0f, r, r * 0.6f, Colors.withAlpha(FLASH, Math.min(1f, 0.55f * flash * alpha[i] * k)));
            }
            WorldDraw.end();
        }
    }

    private static void billboard(int i, float drift) {
        double al = Math.toRadians(alt[i]), zr = Math.toRadians(az[i] + drift);
        float h = (float) Math.cos(al);
        SkyDraw.billboard((float) (-Math.sin(zr) * h), (float) Math.sin(al), (float) (Math.cos(zr) * h));
    }

    /** The haze: from 9° above the horizon to 7° below, brightest just over it, stronger as night falls. */
    static void haze(ThemeSpec theme, float rain) {
        int c = theme.night().haze();
        if (c == 0) {
            return;
        }
        float a = 0.22f * (0.4f + 0.6f * NightSky.dark) * (1f - 0.6f * rain);
        if (a <= 0.004f) {
            return;
        }
        int peak = Colors.withAlpha(c, a), none = Colors.withAlpha(c, 0f);
        WorldDraw.begin(WorldDraw.SKY_ADD, SkyDraw.disc());
        for (int i = 0; i < HAZE_SEGMENTS; i++) {
            float z0 = i * 360f / HAZE_SEGMENTS, z1 = (i + 1) * 360f / HAZE_SEGMENTS;
            SkyDraw.altAz(9f, z0, 0.5f, 0.5f, none);
            SkyDraw.altAz(0.2f, z0, 0.5f, 0.5f, peak);
            SkyDraw.altAz(0.2f, z1, 0.5f, 0.5f, peak);
            SkyDraw.altAz(9f, z1, 0.5f, 0.5f, none);
            SkyDraw.altAz(0.2f, z0, 0.5f, 0.5f, peak);
            SkyDraw.altAz(-7f, z0, 0.5f, 0.5f, none);
            SkyDraw.altAz(-7f, z1, 0.5f, 0.5f, none);
            SkyDraw.altAz(0.2f, z1, 0.5f, 0.5f, peak);
        }
        WorldDraw.end();
    }
}
