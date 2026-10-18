package io.github.georgwav.cosmetics.sol.atmosphere;

import io.github.georgwav.cosmetics.sol.render.FrameInfo;
import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.sol.render.WorldDraw;
import io.github.georgwav.cosmetics.sol.theme.ThemeSpec;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.sol.util.Ease;

/**
 * The planets you can find on the sun's path (Saturn with its ring, Jupiter with four moons that move night to
 * night, red Mars, Venus near sunrise and sunset), shooting stars, meteor nights and a rare comet. The stars, the
 * Milky Way and the constellations are {@link NightSky}'s.
 */
public final class Stellar {
    private static SolTexture planets;

    // Sky events.
    private static float shootNext = 3f, shootLife = -1f, shootX, shootY, shootZ, shootDx, shootDy, shootDz;
    private static float lastTime = -1f;

    private Stellar() {
    }

    /** Draws the planets and the sky's events (sky pass, after the stars). */
    static void render(ThemeSpec theme, Tokens tk, Atmosphere at, float sunAngle, float t, float rain) {
        float night = tk.night * (1f - rain);
        float rot = sunAngle;
        if (at == null || at.planets.get()) {
            planets(theme, tk, rot, night, rain);
        }
        if (at == null || at.stars.get()) {
            events(theme, rot, night, t);
        }
    }

    // ------------------------------------------------------------------ planets

    private static final int SATURN = 0, JUPITER = 1, MARS = 2, VENUS = 3;

    /**
     * A planet's angle from the sun along the sun's path, in radians (D15): outer planets drift once round in their
     * period; Venus swings within 47° of the sun every 3 days. Day 0 puts Saturn opposite the sun.
     */
    public static double planetOffset(int planet, double days) {
        return switch (planet) {
            case SATURN -> Math.PI + days / 40.0 * Math.PI * 2;
            case JUPITER -> Math.PI - 0.6 + days / 24.0 * Math.PI * 2;
            case MARS -> Math.PI + 0.75 + days / 9.0 * Math.PI * 2;
            default -> Math.toRadians(47) * Math.sin(days / 3.0 * Math.PI * 2 + 0.5);
        };
    }

    private static void planets(ThemeSpec theme, Tokens tk, float sunAngle, float night, float rain) {
        ThemeSpec.Planets pl = theme.planets();
        if (!pl.enabled()) {
            return;
        }
        double days = FrameInfo.day + FrameInfo.timeOfDay;
        float px = SkyDraw.pixel();
        float sc = Math.max(1f, FrameInfo.height / 720f);
        // Zoomed in (the Zoom module) the sharper sprites show; a field of view the game didn't report is never a zoom.
        boolean zoomed = FrameInfo.fovY > 1f && FrameInfo.fovY < 30f;
        SolTexture atlas = planetAtlas(pl.style(), pl.tint());
        for (int p = 0; p < 4; p++) {
            double a = sunAngle + planetOffset(p, days);
            // Slightly off the sun's path so they don't line up exactly.
            float incl = p == SATURN ? 0.05f : p == JUPITER ? -0.03f : p == MARS ? 0.07f : 0.02f;
            float x = -(float) Math.sin(a), y = (float) Math.cos(a), z = incl;
            if (y < -0.02f) {
                continue;
            }
            String name = p == SATURN ? "saturn" : p == JUPITER ? "jupiter" : p == MARS ? "mars" : "venus";
            boolean day = pl.daytime().contains(name);
            float vis = Math.max(night, day ? (p == VENUS ? 0.9f : 0.15f) * (1f - night) : 0f) * (1f - rain);
            if (p == VENUS) {
                // Venus only shows low, near sunrise or sunset.
                vis *= Ease.smoothstep(-0.35f, -0.05f, SkyPass.sunY) * (1f - Ease.smoothstep(0.1f, 0.35f, SkyPass.sunY));
            }
            vis *= Ease.smoothstep(-0.02f, 0.06f, y);
            if (vis <= 0.01f) {
                continue;
            }
            SkyDraw.billboard(x, y, z);
            int c = Colors.withAlpha(0xFFFFFFFF, Math.min(1f, vis));
            // Fixed angular size: ~0.25° (at least 3 pixels, never more than a planet ever looks); the zoomed
            // sprite is sharper. Venus is a point of light: added, so by day it brightens the sky instead of greying it.
            float sizePx = Math.min((zoomed ? 64f : 14f) * sc,
                    Math.max(3f * sc, (0.25f / Math.max(1f, FrameInfo.fovY)) * FrameInfo.height));
            WorldDraw.begin(p == VENUS ? WorldDraw.SKY_ADD : WorldDraw.SKY_ALPHA, atlas);
            float half = sizePx * px * (p == SATURN ? 1.6f : 1f) * (zoomed ? 2.2f : 1f) / 2f;
            float u0 = p * 0.25f, u1 = u0 + 0.25f;
            float v0 = zoomed ? 0.5f : 0f, v1 = v0 + 0.5f;
            SkyDraw.rect(-half, -half, half, half, u0, v0, u1, v1, c);
            if (p == JUPITER) {
                // Four moons that move night to night.
                for (int m = 0; m < 4; m++) {
                    double phase = days * (1.8 / (m + 1)) + m * 1.7;
                    float ox = (float) Math.sin(phase) * half * (2.2f + m * 1.3f);
                    float mp = Math.max(px, half * 0.18f);
                    SkyDraw.rect(ox - mp, -mp, ox + mp, mp, 0.9375f, 0.9375f, 1f, 1f, Colors.withAlpha(0xFFFFF4DC, vis * 0.9f));
                }
            }
            WorldDraw.end();
        }
    }

    private static String planetKey = "";

    /** The planet sprites: four cells of 32 px (normal) over four (zoomed), styled by the theme. */
    private static SolTexture planetAtlas(String style, Integer tint) {
        String key = style + tint;
        if (planets != null && !planets.released() && key.equals(planetKey)) {
            return planets;
        }
        if (planets != null) {
            planets.release();
        }
        planetKey = key;
        int w = 128, h = 64;
        int[] px = new int[w * h];
        boolean mono = "mono".equals(style);
        int saturn = mono ? 0xFFE8E8EE : 0xFFE6CE8A;
        int ring = mono ? 0xFFFFFFFF : 0xFFD8C48C;
        int jupiter = mono ? 0xFFDADAE0 : 0xFFEADFC4;
        int band = mono ? 0xFFA0A0A8 : 0xFFC09A70;
        int mars = mono ? 0xFFB0B0B8 : 0xFFD8603C;
        int venus = 0xFFFFFFF4;
        if (tint != null) {
            saturn = Colors.mixRgb(saturn, tint, 0.4f);
            ring = Colors.mixRgb(ring, tint, 0.4f);
            jupiter = Colors.mixRgb(jupiter, tint, 0.4f);
            mars = Colors.mixRgb(mars, tint, 0.4f);
        }
        if ("nebula".equals(style)) {
            ring = Colors.mixRgb(ring, 0xFFFF9AD0, 0.4f);
        }
        for (int row = 0; row < 2; row++) {
            boolean big = row == 1;
            int oy = row * 32;
            // Saturn: disc with a tilted ring.
            disc(px, w, 0, oy, big ? 9f : 5f, saturn, big ? band : 0);
            ringAround(px, w, 0, oy, big ? 15f : 9f, big ? 4f : 2.2f, ring, big);
            // Jupiter: cream with bands (and the Great Red Spot zoomed).
            disc(px, w, 32, oy, big ? 12f : 6f, jupiter, band);
            if (big) {
                set(px, w, 32 + 19, oy + 19, 0xFFC8583A);
                set(px, w, 32 + 20, oy + 19, 0xFFC8583A);
            }
            // Mars: small and red, a polar cap zoomed.
            disc(px, w, 64, oy, big ? 7f : 3f, mars, 0);
            if (big) {
                for (int x = 13; x < 19; x++) {
                    set(px, w, 64 + x, oy + 10, 0xFFFFFFFF);
                }
            }
            // Venus: a bright point with a small flare.
            disc(px, w, 96, oy, big ? 4f : 2f, venus, 0);
            for (int k = 3; k < (big ? 10 : 7); k++) {
                int a = Colors.withAlpha(venus, 0.6f - k * 0.05f);
                set(px, w, 96 + 16 + k, oy + 16, a);
                set(px, w, 96 + 16 - k, oy + 16, a);
                set(px, w, 96 + 16, oy + 16 + k, a);
                set(px, w, 96 + 16, oy + 16 - k, a);
            }
        }
        // A white texel for Jupiter's moons (bottom-right corner).
        set(px, w, 127, 63, 0xFFFFFFFF);
        planets = SolTexture.of("planets", w, h, false, px);
        return planets;
    }

    private static void disc(int[] px, int w, int ox, int oy, float r, int c, int band) {
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                float dx = x - 15.5f, dy = y - 15.5f;
                if (dx * dx + dy * dy <= r * r) {
                    int col = c;
                    if (band != 0 && ((int) Math.floor((dy + r) / Math.max(1f, r / 3f))) % 2 == 1) {
                        col = Colors.mixRgb(c, band, 0.35f);
                    }
                    if (dx + dy > r * 0.6f) {
                        col = Colors.scale(col, 0.8f);
                    }
                    px[(oy + y) * w + ox + x] = col | 0xFF000000;
                }
            }
        }
    }

    private static void ringAround(int[] px, int w, int ox, int oy, float rx, float ry, int c, boolean gap) {
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                float dx = (x - 15.5f) / rx, dy = (y - 15.5f + (x - 15.5f) * 0.18f) / ry;
                float d = dx * dx + dy * dy;
                boolean onRing = d > 0.55f && d < 1f && !(gap && d > 0.74f && d < 0.8f);
                boolean behindBody = dy < 0f && Colors.a(px[(oy + y) * w + ox + x]) != 0;
                if (onRing && !behindBody) {
                    px[(oy + y) * w + ox + x] = c | 0xFF000000;
                }
            }
        }
    }

    private static void set(int[] px, int w, int x, int y, int c) {
        if (x >= 0 && y >= 0 && x < w && y * w + x < px.length) {
            px[y * w + x] = c;
        }
    }

    // ------------------------------------------------------------------ events

    private static void events(ThemeSpec theme, float rot, float night, float t) {
        float dt = lastTime < 0f ? 0f : Math.max(0f, Math.min(0.1f, t - lastTime));
        lastTime = t;
        if (night <= 0.05f) {
            return;
        }
        long nightIndex = FrameInfo.day;
        boolean shower = theme.stars().meteorNights() > 0 && nightIndex % theme.stars().meteorNights() == 0;
        float rate = theme.stars().shooting() ? 1f : 0.06f;
        if (shower) {
            rate *= 4f;
        }
        shootNext -= dt * rate;
        if (shootNext <= 0f && shootLife < 0f) {
            shootLife = 0f;
            double az = Math.random() * Math.PI * 2, alt = Math.toRadians(25 + Math.random() * 45);
            shootX = (float) (-Math.sin(az) * Math.cos(alt));
            shootY = (float) Math.sin(alt);
            shootZ = (float) (Math.cos(az) * Math.cos(alt));
            double daz = az + (shower ? 0.6 : Math.random() * 2 - 1), dalt = alt - 0.35;
            shootDx = (float) (-Math.sin(daz) * Math.cos(dalt)) - shootX;
            shootDy = (float) Math.sin(dalt) - shootY;
            shootDz = (float) (Math.cos(daz) * Math.cos(dalt)) - shootZ;
            shootNext = 2.5f + (float) Math.random() * 3f;
        }
        if (shootLife >= 0f) {
            shootLife += dt;
            float k = shootLife / 0.7f;
            if (k >= 1f) {
                shootLife = -1f;
            } else {
                float hx = shootX + shootDx * k * 0.5f, hy = shootY + shootDy * k * 0.5f, hz = shootZ + shootDz * k * 0.5f;
                SkyDraw.billboard(hx, hy, hz);
                // A streak toward where it came from, fading out.
                float len = 0.16f, w = Math.max(SkyDraw.pixel() * 1.6f, 0.002f);
                float ang = (float) Math.atan2(0.45, 1.0);
                int head = Colors.withAlpha(0xFFFFFFFF, (1f - k) * night);
                WorldDraw.begin(WorldDraw.SKY_ADD, SkyDraw.ray());
                SkyDraw.rotRect(0f, 0f, (float) Math.PI + ang, 0f, -w * 4f, len, w * 4f, head);
                WorldDraw.end();
            }
        }
        // A comet: one night in twelve it arrives and stays for three.
        long cycle = Math.floorMod(nightIndex, 12L);
        if (theme.stars().comets() && cycle < 3) {
            float a = rot + (float) Math.PI * 0.8f + cycle * 0.05f;
            float x = -(float) Math.sin(a), y = (float) Math.cos(a), z = 0.35f;
            if (y > 0.05f) {
                SkyDraw.billboard(x, y, z);
                float w = 0.012f;
                int c = Colors.withAlpha(0xFFDFF4FF, 0.55f * night);
                WorldDraw.begin(WorldDraw.SKY_ADD, SkyDraw.glow());
                SkyDraw.glowAt(0f, 0f, w * 1.6f, w * 1.6f, Colors.withAlpha(0xFFFFFFFF, 0.8f * night));
                WorldDraw.end();
                // The tail points away from the sun.
                float tail = (float) Math.atan2(-SkyPass.sunY, SkyPass.sunX) + (float) Math.PI;
                WorldDraw.begin(WorldDraw.SKY_ADD, SkyDraw.ray());
                SkyDraw.rotRect(0f, 0f, tail, 0f, -w * 3f, 0.22f, w * 3f, c);
                WorldDraw.end();
            }
        }
    }
}
