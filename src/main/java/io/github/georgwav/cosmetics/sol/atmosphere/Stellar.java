package io.github.georgwav.cosmetics.sol.atmosphere;

import io.github.georgwav.cosmetics.sol.render.FrameInfo;
import io.github.georgwav.cosmetics.sol.render.WorldDraw;
import io.github.georgwav.cosmetics.sol.theme.ThemeSpec;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.util.Colors;

/**
 * Shooting stars, meteor nights and a rare comet. The stars, the Milky Way and the constellations are
 * {@link NightSky}'s; the planets and the moon {@link Planets}'.
 */
public final class Stellar {
    // Sky events.
    private static float shootNext = 3f, shootLife = -1f, shootX, shootY, shootZ, shootDx, shootDy, shootDz;
    private static float lastTime = -1f;

    private Stellar() {
    }

    /** Draws the sky's events (sky pass, after the stars). */
    static void render(ThemeSpec theme, Tokens tk, Atmosphere at, float sunAngle, float t, float rain) {
        float night = tk.night * (1f - rain);
        float rot = sunAngle;
        if (at == null || at.stars.get()) {
            events(theme, rot, night, t);
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
