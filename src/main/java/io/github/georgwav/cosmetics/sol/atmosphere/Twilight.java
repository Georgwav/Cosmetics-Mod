package io.github.georgwav.cosmetics.sol.atmosphere;

import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.sol.render.WorldDraw;
import io.github.georgwav.cosmetics.sol.sky.LabNoise;
import io.github.georgwav.cosmetics.sol.sky.SkySphere;
import io.github.georgwav.cosmetics.sol.theme.ThemeSpec;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.sol.util.Ease;

/**
 * The sky between day and night (the Night Sky lab's twilight): the glow that stays where the sun went down, the
 * Earth's shadow rising opposite it with the pink Belt of Venus above, then on dark nights a faint airglow band
 * round the horizon in the theme's colour and the zodiacal light leaning up along the ecliptic after dusk and before
 * dawn. Drawn on the sky sphere right after the dome. Render thread only; nothing allocated per frame.
 */
final class Twilight {
    private static final int AG_W = 512, AG_H = 48, AZ = 64;
    private static SolTexture airglow;
    private static final double[] eq = new double[3];
    private static final float[] w = new float[3];

    private Twilight() {
    }

    static void render(ThemeSpec theme, Atmosphere at, float rain) {
        float clear = 1f - rain;
        if (clear <= 0.01f) {
            return;
        }
        SkySphere sp = NightSky.SPHERE;
        float sunAlt = sp.sunAlt;
        float sunAz = (float) Math.toDegrees(Math.atan2(-sp.sun[0], sp.sun[2]));
        afterglow(sp, sunAlt, clear);
        belt(sunAlt, sunAz, clear);
        if (NightSky.layer("airglow")) {
            airglow(theme, sunAlt, clear);
            zodiacal(sp, sunAlt, clear);
        }
    }

    /** The glow where the sun went down: orange low, then purple-pink, spreading wide along the horizon. */
    private static void afterglow(SkySphere sp, float sunAlt, float clear) {
        if (sunAlt <= -20f || sunAlt >= 2f) {
            return;
        }
        // Once the sun's own layers have gone below the horizon.
        float s = Ease.smoothstep(-19f, -1f, sunAlt) * (1f - Ease.smoothstep(-2f, 2f, sunAlt)) * clear;
        if (s <= 0.004f) {
            return;
        }
        float r = (0.75f + 0.5f * s) / SkyDraw.T;
        int c1 = Colors.mix(0xFFFF7846, 0xFFFFAA60, Ease.smoothstep(-4f, 4f, sunAlt));
        int c2 = Colors.mix(0xFF6E4696, 0xFFE66460, Ease.smoothstep(-10f, -2f, sunAlt));
        SkyDraw.billboard(sp.sun[0], sp.sun[1], sp.sun[2]);
        WorldDraw.begin(WorldDraw.SKY_ADD, SkyDraw.glow());
        SkyDraw.glowAt(0f, 0f, r * 1.25f, r * 0.5f, Colors.withAlpha(0xFF3C326E, 0.1f * s));
        SkyDraw.glowAt(0f, 0f, r * 1.25f * 0.65f, r * 0.5f * 0.65f, Colors.withAlpha(c2, 0.38f * s));
        SkyDraw.glowAt(0f, 0f, r * 1.25f * 0.3f, r * 0.5f * 0.3f, Colors.withAlpha(c1, 0.75f * s));
        WorldDraw.end();
    }

    /** Opposite the low sun: the Earth's dark blue shadow on the horizon, the pink Belt of Venus over it. */
    private static void belt(float sunAlt, float sunAz, float clear) {
        float s = Ease.smoothstep(-9f, -3.5f, sunAlt) * (1f - Ease.smoothstep(-0.5f, 4f, sunAlt)) * clear;
        if (s <= 0.004f) {
            return;
        }
        float az = sunAz + 180f, r = 1.25f / SkyDraw.T;
        altAzBillboard(-sunAlt + 8f, az);
        WorldDraw.begin(WorldDraw.SKY_ADD, SkyDraw.glow());
        SkyDraw.glowAt(0f, 0f, r, r * 0.2f, Colors.withAlpha(0xFFE28CAA, 0.42f * s));
        WorldDraw.end();
        altAzBillboard(Math.max(0f, -sunAlt), az);
        WorldDraw.begin(WorldDraw.SKY_ALPHA, SkyDraw.glow());
        SkyDraw.glowAt(0f, 0f, r, r * 0.16f, Colors.withAlpha(0xFF18204A, 0.55f * s));
        WorldDraw.end();
    }

    private static void altAzBillboard(float alt, float az) {
        double al = Math.toRadians(alt), zr = Math.toRadians(az);
        float h = (float) Math.cos(al);
        SkyDraw.billboard((float) (-Math.sin(zr) * h), (float) Math.sin(al), (float) (Math.cos(zr) * h));
    }

    /** On dark nights a faint band of airglow from 3° to 30° all round, in the theme's colour, waving gently. */
    private static void airglow(ThemeSpec theme, float sunAlt, float clear) {
        float night = Ease.smoothstep(-13f, -19f, sunAlt);
        float a = 0.34f * night * (1f - 0.65f * Math.min(1f, NightSky.moonSky)) * clear;
        if (a <= 0.004f) {
            return;
        }
        int c = Colors.withAlpha(theme.night().airglow(), a);
        WorldDraw.begin(WorldDraw.SKY_ADD, airglowTexture());
        for (int i = 0; i < AZ; i++) {
            float z0 = i * 360f / AZ, z1 = (i + 1) * 360f / AZ;
            // The texture runs from north round through east; the game's azimuth from south through west.
            float u0 = i / (float) AZ, u1 = (i + 1) / (float) AZ;
            SkyDraw.altAz(30f, z0, u0, 0f, c);
            SkyDraw.altAz(3f, z0, u0, 1f, c);
            SkyDraw.altAz(3f, z1, u1, 1f, c);
            SkyDraw.altAz(30f, z1, u1, 0f, c);
        }
        WorldDraw.end();
    }

    /** The lab's airglow picture: brightest a little below the middle, in slow waves and noise round the sky. */
    private static SolTexture airglowTexture() {
        if (airglow != null && !airglow.released()) {
            return airglow;
        }
        int[] px = new int[AG_W * AG_H];
        for (int y = 0; y < AG_H; y++) {
            for (int x = 0; x < AG_W; x++) {
                double a = x / (double) AG_W * Math.PI * 2;
                double wave = .55 + .45 * Math.sin(a * 7 + Math.sin(a * 3) * 2 + y * .05) * Math.sin(a * 2 + 1.3);
                double n = LabNoise.fbm(Math.cos(a) * 3, Math.sin(a) * 3, y * .08, 3);
                double v = Math.exp(-((y - 26) * (y - 26)) / (2.0 * 9 * 9)) * (wave * .6 + n * .8);
                px[y * AG_W + x] = (Math.round((float) Math.min(1, v) * 255f) << 24) | 0xFFFFFF;
            }
        }
        airglow = SolTexture.of("airglow", AG_W, AG_H, true, px);
        return airglow;
    }

    /** The zodiacal light: a faint cone leaning up along the ecliptic from the sun, after dusk and before dawn. */
    private static void zodiacal(SkySphere sp, float sunAlt, float clear) {
        float zl = Ease.smoothstep(-21f, -17.5f, sunAlt) * (1f - Ease.smoothstep(-13.5f, -10.5f, sunAlt))
                * (1f - Math.min(1f, NightSky.moonSky)) * clear;
        if (zl <= 0.004f) {
            return;
        }
        // Before dawn the sun is rising: the cone stands in the east, west of the sun on the ecliptic.
        float sg = sp.sun[0] > 0f ? -1f : 1f;
        WorldDraw.begin(WorldDraw.SKY_ADD, SkyDraw.glow());
        for (int i = 0; i < 6; i++) {
            SkySphere.ecl(sp.lamSun + sg * (18 + i * 9), 0, eq);
            sp.toWorld(eq[0], eq[1], eq[2], w);
            if (w[1] < -0.2f) {
                continue;
            }
            SkyDraw.billboard(w[0], w[1], w[2]);
            float r = (0.42f - i * 0.05f) / SkyDraw.T * 1.2f;
            SkyDraw.glowAt(0f, 0f, r, r, Colors.withAlpha(0xFFE6DCB4, 0.2f * zl * (1f - i / 7f)));
        }
        WorldDraw.end();
    }
}
