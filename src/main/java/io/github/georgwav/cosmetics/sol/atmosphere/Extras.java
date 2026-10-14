package io.github.georgwav.cosmetics.sol.atmosphere;

import io.github.georgwav.cosmetics.sol.render.FrameInfo;
import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.sol.render.WorldDraw;
import io.github.georgwav.cosmetics.sol.theme.ThemeSpec;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.sol.util.PixelCanvas;
import io.github.georgwav.cosmetics.sol.util.Sprites;

/**
 * The themes' extras in the sky: aurora ribbons, nebula clouds, the Deep Field galaxy, the Grace rune with its lit
 * cloud and mist, and Monsoon's lightning. Each is placed where the theme's showpiece frame has it: the prototype
 * frame is a 70° tall view facing {@code show.azimuth}, tipped up so the horizon sits at 64% of its height.
 */
public final class Extras {
    /** How far the prototype frame looks up: the horizon at 64% of a 70° view. */
    public static final float SHOW_PITCH = (float) Math.toDegrees(Math.atan((0.64 - 0.5) * SkyDraw.T));
    private static final float ASPECT = 16f / 9f;

    /** Monsoon's sky flash, 0..0.55, read by the HUD overlay. */
    public static float flash;
    private static float flashNext = 2.5f;
    private static float lastTime = -1f;

    private static SolTexture galaxy;
    private static SolTexture sigilImg, sigilGlow;
    private static final SolTexture[] sigilCrackle = new SolTexture[3];
    private static int sigilKeyColor, sigilKeyHalo;
    private static Sprites.Sigil sigil;
    private static final SolTexture[] mistTeal = new SolTexture[3];
    private static SolTexture mistWarm;
    private static int mistKeyColor;
    private static Integer mistKeyWarm;
    private static boolean mistMade;

    private static final float[] out = new float[3];

    private Extras() {
    }

    // ------------------------------------------------------------------ placing

    /** Altitude of a prototype frame row (0 top, 1 bottom), in degrees. */
    public static float altitudeOf(float y) {
        return SHOW_PITCH + (float) Math.toDegrees(Math.atan((0.5f - y) * SkyDraw.T));
    }

    /** Azimuth of a prototype frame column (0 left, 1 right) for a theme facing {@code showAzimuth}. */
    public static float azimuthOf(float x, float showAzimuth) {
        return showAzimuth + (float) Math.toDegrees(Math.atan((x - 0.5f) * ASPECT * SkyDraw.T));
    }

    /** The world direction of a prototype frame point, turned with the night sky by {@code skyTurn} radians. */
    private static void direction(float x, float y, float showAzimuth, float skyTurn, float[] o) {
        double al = Math.toRadians(altitudeOf(y));
        double az = Math.toRadians(azimuthOf(x, showAzimuth));
        float h = (float) Math.cos(al);
        float dx = (float) (-Math.sin(az) * h);
        float dy = (float) Math.sin(al);
        float dz = (float) (Math.cos(az) * h);
        if (skyTurn != 0f) {
            float c = (float) Math.cos(skyTurn), s = (float) Math.sin(skyTurn);
            float nx = dx * c - dy * s;
            float ny = dx * s + dy * c;
            dx = nx;
            dy = ny;
        }
        o[0] = dx;
        o[1] = dy;
        o[2] = dz;
    }

    /** How far the night sky has turned from midnight (so celestial extras sit where designed at midnight). */
    private static float skyTurn() {
        return (FrameInfo.timeOfDay - 0.5f) * (float) (Math.PI * 2.0);
    }

    // ------------------------------------------------------------------ layers

    /** Behind the moon and sun: nebula, galaxy, aurora. */
    static void renderBehind(ThemeSpec theme, Tokens tk, float t, float rain) {
        float night = tk.night * (1f - rain);
        if (night <= 0.01f) {
            updateLightning(theme, t);
            return;
        }
        float az = theme.show().azimuth();
        if (!theme.nebula().isEmpty()) {
            WorldDraw.begin(WorldDraw.SKY_ADD, SkyDraw.glow());
            for (ThemeSpec.Nebula n : theme.nebula()) {
                direction(n.x(), n.y(), az, skyTurn(), out);
                SkyDraw.billboard(out[0], out[1], out[2]);
                float a = (0.38f + 0.06f * (float) Math.sin(t * 0.3f + n.x() * 9f)) * night;
                float r = n.r() * ASPECT;
                SkyDraw.glowAt(0f, 0f, r, r * 0.7f, Colors.withAlpha(n.color(), a));
            }
            WorldDraw.end();
        }
        if (theme.galaxy() != null) {
            galaxy(theme.galaxy(), az, t, night);
        }
        if (theme.aurora() != null && theme.aurora().length > 0) {
            aurora(theme.aurora(), az, t, night);
        }
        updateLightning(theme, t);
    }

    /** After the horizon band: the Grace rune. */
    static void renderFront(ThemeSpec theme, Tokens tk, float t, float rain) {
        if (theme.sigil() != null) {
            rune(theme, t, rain);
        }
    }

    /** Last in the sky: Grace's drifting fog bands, with the rune glowing through. */
    static void renderMist(ThemeSpec theme, Tokens tk, float t, float rain) {
        if (theme.mist() != null) {
            mist(theme, t);
        }
    }

    // ------------------------------------------------------------------ galaxy

    private static void galaxy(ThemeSpec.Galaxy g, float az, float t, float night) {
        if (galaxy == null || galaxy.released()) {
            galaxy = SolTexture.of("galaxy", 512, 512, true, Sprites.galaxy().toArgb(null));
        }
        direction(g.x(), g.y(), az, skyTurn(), out);
        SkyDraw.billboard(out[0], out[1], out[2]);
        float r = g.r() * ASPECT;
        // ctx.rotate(-.35); ctx.scale(1, tilt); ctx.rotate(t*.02): corners of [-r, r]^2 through that.
        float c1 = (float) Math.cos(-0.35), s1 = (float) Math.sin(-0.35);
        float c2 = (float) Math.cos(t * 0.02f), s2 = (float) Math.sin(t * 0.02f);
        int color = Colors.withAlpha(0xFFFFFFFF, 0.92f * night);
        WorldDraw.begin(WorldDraw.SKY_ADD, galaxy);
        corner(-r, -r, c1, s1, c2, s2, g.tilt(), 0f, 0f, color);
        corner(-r, r, c1, s1, c2, s2, g.tilt(), 0f, 1f, color);
        corner(r, r, c1, s1, c2, s2, g.tilt(), 1f, 1f, color);
        corner(r, -r, c1, s1, c2, s2, g.tilt(), 1f, 0f, color);
        WorldDraw.end();
    }

    private static void corner(float x, float y, float c1, float s1, float c2, float s2, float tilt, float u, float v,
                               int color) {
        float x2 = x * c2 - y * s2;
        float y2 = (x * s2 + y * c2) * tilt;
        SkyDraw.point(x2 * c1 - y2 * s1, x2 * s1 + y2 * c1, u, v, color);
    }

    // ------------------------------------------------------------------ aurora

    /** Two ribbons across 160° of sky: columns of the strip texture, waving (the prototype's formula). */
    private static void aurora(int[] colors, float az, float t, float night) {
        int columns = 140;
        float span = 160f;
        float amp = 1f + MusicSky.auroraBoost();
        float aA = Math.min(2.2f, 1f + MusicSky.auroraBoost() * 0.75f);
        WorldDraw.begin(WorldDraw.SKY_ADD, SkyDraw.strip());
        for (int ri = 0; ri < colors.length; ri++) {
            for (int i = 0; i < columns; i++) {
                float u0 = i / (float) columns;
                float u1 = (i + 1) / (float) columns;
                float y0 = 0.1f + 0.07f * ri + (float) Math.sin(u0 * 6 + t * 0.25f + ri * 2) * 0.045f * amp
                        + (float) Math.sin(u0 * 14 + t * 0.45f) * 0.014f;
                float y1n = 0.1f + 0.07f * ri + (float) Math.sin(u1 * 6 + t * 0.25f + ri * 2) * 0.045f * amp
                        + (float) Math.sin(u1 * 14 + t * 0.45f) * 0.014f;
                float len = 0.14f + 0.06f * (float) Math.sin(u0 * 9 + t * 0.35f + ri);
                float a = Math.min(1f, aA * 0.22f * (0.55f + 0.45f * (float) Math.sin(u0 * 21 + t * 0.8f + ri))) * night;
                if (a <= 0.004f) {
                    continue;
                }
                int c = Colors.withAlpha(colors[ri], a);
                float z0 = az - span / 2f + u0 * span;
                float z1 = az - span / 2f + u1 * span;
                // The ribbon hangs from y0 down to y0 + len in the frame: top of the strip texture at the top.
                SkyDraw.altAz(altitudeOf(y0), z0, 0f, 0f, c);
                SkyDraw.altAz(altitudeOf(y0 + len), z0, 0f, 1f, c);
                SkyDraw.altAz(altitudeOf(y1n + len), z1, 1f, 1f, c);
                SkyDraw.altAz(altitudeOf(y1n), z1, 1f, 0f, c);
            }
        }
        WorldDraw.end();
    }

    // ------------------------------------------------------------------ lightning

    private static void updateLightning(ThemeSpec theme, float t) {
        float dt = lastTime < 0f ? 0f : Math.max(0f, Math.min(0.1f, t - lastTime));
        lastTime = t;
        if (!theme.lightning()) {
            flash = 0f;
            return;
        }
        flashNext -= dt;
        if (flashNext <= 0f) {
            flash = 0.55f;
            flashNext = 4f + (float) Math.random() * 5f;
        }
        flash = Math.max(0f, flash - dt * 1.6f);
    }

    // ------------------------------------------------------------------ the Grace rune

    private static void ensureSigil(ThemeSpec.Sigil g) {
        if (sigil != null && sigilKeyColor == g.color() && sigilKeyHalo == g.halo() && sigilImg != null && !sigilImg.released()) {
            return;
        }
        releaseSigil();
        sigilKeyColor = g.color();
        sigilKeyHalo = g.halo();
        sigil = Sprites.sigil(g.color(), g.halo());
        sigilImg = SolTexture.of("sigil", sigil.width(), sigil.height(), false, sigil.img().toArgb(null));
        sigilGlow = SolTexture.of("sigil_glow", sigil.width(), sigil.height(), true, sigil.glow().toArgb(null));
        for (int i = 0; i < 3; i++) {
            PixelCanvas c = sigil.crackle()[i];
            sigilCrackle[i] = SolTexture.of("sigil_crackle_" + i, c.width, c.height, false, c.toArgb(null));
        }
    }

    private static void releaseSigil() {
        if (sigilImg != null) {
            sigilImg.release();
            sigilGlow.release();
            for (SolTexture c : sigilCrackle) {
                if (c != null) {
                    c.release();
                }
            }
        }
        sigilImg = null;
    }

    private static void ensureMist(ThemeSpec.Mist m) {
        if (mistMade && mistKeyColor == m.color() && java.util.Objects.equals(mistKeyWarm, m.warm())) {
            return;
        }
        for (SolTexture tx : mistTeal) {
            if (tx != null) {
                tx.release();
            }
        }
        if (mistWarm != null) {
            mistWarm.release();
            mistWarm = null;
        }
        Sprites.Mist made = Sprites.mist(m.color(), m.warm());
        for (int i = 0; i < 3; i++) {
            PixelCanvas c = made.teal()[i];
            mistTeal[i] = SolTexture.of("mist_" + i, c.width, c.height, true, c.toArgb(null));
        }
        if (made.warm() != null) {
            mistWarm = SolTexture.of("mist_warm", made.warm().width, made.warm().height, true, made.warm().toArgb(null));
        }
        mistKeyColor = m.color();
        mistKeyWarm = m.warm();
        mistMade = true;
    }

    private static void rune(ThemeSpec theme, float t, float rain) {
        ThemeSpec.Sigil g = theme.sigil();
        ensureSigil(g);
        if (theme.mist() != null) {
            ensureMist(theme.mist());
        }
        direction(g.x(), g.y(), theme.show().azimuth(), 0f, out);
        SkyDraw.billboard(out[0], out[1], out[2]);
        float vis = 1f - rain * 0.5f;
        float U = g.r();
        float k = U / sigil.unit();
        float x0 = -sigil.cx() * k;
        float y0 = -sigil.cy() * k;
        float w = sigil.width() * k;
        float h = sigil.height() * k;
        float yb = (float) Math.sqrt(3) * U;
        float hz = 0.64f - g.y();
        float pulse = (0.86f + 0.09f * (float) Math.sin(t * 1.2f) + 0.05f * (float) Math.sin(t * 3.1f)) * vis;

        WorldDraw.begin(WorldDraw.SKY_ADD, SkyDraw.glow());
        float R = U * 4.8f;
        SkyDraw.rect(-R, yb - R * 0.55f, R, yb - R * 0.55f + R * 1.05f, Colors.withAlpha(g.halo(), 0.6f * pulse));
        R = U * 2.6f;
        SkyDraw.rect(-R, U * 0.9f - R * 0.85f, R, U * 0.9f - R * 0.85f + R * 1.7f, Colors.withAlpha(g.halo(), 0.3f * pulse));
        WorldDraw.end();
        if (mistWarm != null) {
            float ww = U * 10f, wh = U * 4.2f, sx = (float) Math.sin(t * 0.09f + 1f) * U * 0.1f;
            WorldDraw.begin(WorldDraw.SKY_ALPHA, mistWarm);
            SkyDraw.rect(-ww * 0.5f - sx, yb - U * 0.75f - wh * 0.5f, ww * 0.5f - sx, yb - U * 0.75f + wh * 0.5f,
                    Colors.withAlpha(0xFFFFFFFF, 0.85f * vis));
            WorldDraw.end();
        }
        WorldDraw.begin(WorldDraw.SKY_ADD, SkyDraw.glow());
        R = U * 0.16f;
        float pillar = Math.max(U, hz - yb + 0.06f);
        SkyDraw.rect(-R, yb - U * 0.2f, R, yb - U * 0.2f + pillar, Colors.withAlpha(g.color(), 0.55f * pulse));
        WorldDraw.end();
        WorldDraw.begin(WorldDraw.SKY_ADD, sigilGlow);
        SkyDraw.rect(x0, y0, x0 + w, y0 + h, Colors.withAlpha(0xFFFFFFFF, 0.75f * pulse));
        WorldDraw.end();
        WorldDraw.begin(WorldDraw.SKY_ALPHA, sigilImg);
        SkyDraw.rect(x0, y0, x0 + w, y0 + h, Colors.withAlpha(0xFFFFFFFF, vis));
        WorldDraw.end();
        float ph = t * 2.3f;
        int j = (int) Math.floor(ph) % 3;
        float f = ph - (float) Math.floor(ph);
        WorldDraw.begin(WorldDraw.SKY_ADD, sigilCrackle[j]);
        SkyDraw.rect(x0, y0, x0 + w, y0 + h, Colors.withAlpha(0xFFFFFFFF, 0.9f * (1f - f) * vis));
        WorldDraw.end();
        WorldDraw.begin(WorldDraw.SKY_ADD, sigilCrackle[(j + 1) % 3]);
        SkyDraw.rect(x0, y0, x0 + w, y0 + h, Colors.withAlpha(0xFFFFFFFF, 0.9f * f * vis));
        WorldDraw.end();
        // Sparks running along the lines.
        int n = sigil.count();
        if (n > 0) {
            float px = Math.max(SkyDraw.pixel(), k);
            WorldDraw.begin(WorldDraw.SKY_ADD, null);
            for (int i = 0; i < 12; i++) {
                int idx = (int) ((long) Math.floor(t * 40 * (1 + (i % 3) * 0.35) + i * 977) % n);
                float x = sigil.pts()[idx * 2], y = sigil.pts()[idx * 2 + 1];
                float a = (0.5f + 0.5f * (float) Math.sin(t * 5 + i * 2.3f)) * vis;
                SkyDraw.rect(x0 + x * k, y0 + y * k, x0 + x * k + px, y0 + y * k + px, Colors.withAlpha(0xFFFFFFFF, a));
            }
            WorldDraw.end();
            WorldDraw.begin(WorldDraw.SKY_ADD, SkyDraw.glow());
            for (int i = 0; i < 12; i++) {
                int idx = (int) ((long) Math.floor(t * 40 * (1 + (i % 3) * 0.35) + i * 977) % n);
                float x = sigil.pts()[idx * 2], y = sigil.pts()[idx * 2 + 1];
                float a = (0.5f + 0.5f * (float) Math.sin(t * 5 + i * 2.3f)) * 0.7f * vis;
                float q = k * 4f;
                SkyDraw.glowAt(x0 + (x + 0.5f) * k, y0 + (y + 0.5f) * k, q, q, Colors.withAlpha(g.color(), a));
            }
            // The lower node and the centre node breathe.
            for (int nd = 0; nd < 3; nd++) {
                float ny = nd == 0 ? (float) Math.sqrt(3) : nd == 1 ? 0f : -1f;
                float s = nd == 0 ? 1f : nd == 1 ? 0.6f : 0.45f;
                float a = s * (0.35f + 0.25f * (float) Math.sin(t * 2.1f + ny * 3f)) * vis;
                float q = U * (0.14f + 0.08f * s);
                SkyDraw.glowAt(0f, ny * U, q, q, Colors.withAlpha(0xFFFFF1C8, a));
            }
            WorldDraw.end();
        }
    }

    // ------------------------------------------------------------------ mist

    private static void mist(ThemeSpec theme, float t) {
        ThemeSpec.Mist m = theme.mist();
        ensureMist(m);
        int n = Math.max(1, m.bands());
        float A = m.alpha();
        float hz = 0.64f;
        for (int b = 0; b < n; b++) {
            float f = b / (float) Math.max(1, n - 1);
            SolTexture img = mistTeal[b % 3];
            float bh = 0.18f + 0.08f * ((b * 5) % 3) / 2f;
            float y = 0.03f + f * (hz + 0.04f - 0.03f) - bh * 0.5f;
            float sp = (b % 2 == 1 ? -1 : 1) * (0.006f + b * 0.0016f);
            float a = Math.min(1f, A * (0.42f + 0.4f * f) * (0.85f + 0.15f * (float) Math.sin(t * 0.25f + b)));
            // One tile spans 1.3 (or 1.55) frame widths; the bands circle the whole sky, drifting sideways.
            float tileDeg = 360f / 2f;
            float shift = ((b * 0.37f + t * sp) % 1f) * tileDeg;
            float altTop = altitudeOf(y);
            float altBottom = altitudeOf(y + bh);
            int c = Colors.withAlpha(0xFFFFFFFF, a);
            WorldDraw.begin(WorldDraw.SKY_ALPHA, img);
            for (int tile = 0; tile < 2; tile++) {
                float start = tile * tileDeg + shift;
                for (int s = 0; s < 12; s++) {
                    float u0 = s / 12f, u1 = (s + 1) / 12f;
                    float z0 = start + u0 * tileDeg, z1 = start + u1 * tileDeg;
                    SkyDraw.altAz(altTop, z0, u0, 0f, c);
                    SkyDraw.altAz(altBottom, z0, u0, 1f, c);
                    SkyDraw.altAz(altBottom, z1, u1, 1f, c);
                    SkyDraw.altAz(altTop, z1, u1, 0f, c);
                }
            }
            WorldDraw.end();
        }
        ThemeSpec.Sigil g = theme.sigil();
        if (g == null || sigil == null || sigilImg == null) {
            return;
        }
        direction(g.x(), g.y(), theme.show().azimuth(), 0f, out);
        SkyDraw.billboard(out[0], out[1], out[2]);
        float U = g.r();
        float yb = (float) Math.sqrt(3) * U;
        if (mistWarm != null) {
            float ww = U * 8.5f, wh = U * 2f, sx = (float) Math.sin(t * 0.11f) * U * 0.12f;
            WorldDraw.begin(WorldDraw.SKY_ALPHA, mistWarm);
            SkyDraw.rect(-ww * 0.5f + sx, yb + U * 0.55f - wh * 0.5f, ww * 0.5f + sx, yb + U * 0.55f + wh * 0.5f,
                    Colors.withAlpha(0xFFFFFFFF, 0.95f));
            WorldDraw.end();
            WorldDraw.begin(WorldDraw.SKY_ADD, mistWarm);
            SkyDraw.rect(-ww * 0.36f - sx, yb + U * 0.4f - wh * 0.35f, -ww * 0.36f - sx + ww * 0.72f,
                    yb + U * 0.4f - wh * 0.35f + wh * 0.7f, Colors.withAlpha(0xFFFFFFFF, 0.22f));
            WorldDraw.end();
        }
        float k = U / sigil.unit();
        float x0 = -sigil.cx() * k, y0 = -sigil.cy() * k, w = sigil.width() * k, h = sigil.height() * k;
        WorldDraw.begin(WorldDraw.SKY_ADD, sigilGlow);
        SkyDraw.rect(x0, y0, x0 + w, y0 + h, Colors.withAlpha(0xFFFFFFFF, 0.22f));
        WorldDraw.end();
        WorldDraw.begin(WorldDraw.SKY_ADD, sigilImg);
        SkyDraw.rect(x0, y0, x0 + w, y0 + h, Colors.withAlpha(0xFFFFFFFF, 0.42f));
        WorldDraw.end();
        WorldDraw.begin(WorldDraw.SKY_ADD, sigilCrackle[(int) Math.floor(t * 2.3f + 0.5f) % 3]);
        SkyDraw.rect(x0, y0, x0 + w, y0 + h, Colors.withAlpha(0xFFFFFFFF, 0.45f));
        WorldDraw.end();
        WorldDraw.begin(WorldDraw.SKY_ADD, SkyDraw.glow());
        float R = U * 3.2f;
        SkyDraw.rect(-R, yb - R * 0.42f, R, yb - R * 0.42f + R * 0.8f, Colors.withAlpha(g.halo(), 0.4f));
        WorldDraw.end();
    }
}
