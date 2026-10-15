package io.github.georgwav.cosmetics.sol.atmosphere;

import io.github.georgwav.cosmetics.sol.Feature;
import io.github.georgwav.cosmetics.sol.Guard;
import io.github.georgwav.cosmetics.sol.render.FrameInfo;
import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.sol.render.WorldDraw;
import io.github.georgwav.cosmetics.sol.theme.ThemeSpec;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.sol.util.Ease;

/**
 * The Sol sky, drawn in the game's sky pass in place of its own: the dome (top, middle and horizon colours, warmer
 * toward the sun), the stars, the theme's extras, the moon, the sun with its eight layers (the brief's Sun lab, see
 * plan §9.2), and the horizon glow band. Everything is behind the world: terrain is drawn after it.
 */
public final class SkyPass {
    private static final float[] RINGS = {-90f, -12f, -3f, 0f, 2f, 5f, 9f, 14f, 22f, 30f, 38f, 46f, 60f, 75f, 90f};
    private static final int AZ = 32;
    private static final int[] colors = new int[RINGS.length * (AZ + 1)];
    private static final float[][] CORONA_SQUARES = {{1.4f, 0.22f}, {1.85f, 0.12f}, {2.45f, 0.06f}};
    private static final int[] DOG_RINGS = {0xFFFF8A5C, 0xFFFFE28A, 0xFF8AB8FF};
    private static final int[] DOG_BARS = {0xFFFF9A6A, 0xFFFFFFFF, 0xFF9CC4FF};

    // The sun this frame, for the lens flare and the HUD.
    public static float sunX, sunY, sunZ;
    public static float sunAltitude;
    /** The prototype's sun height e (0 on the horizon, 1 at 38° and up), quantised to 1/20. */
    public static float sunE;
    /** Half-size of the sun in H units. */
    public static float sunS;
    public static int sunCore = 0xFFFFF8EA;
    public static int sunHalo = 0xFFFFB347;
    /** 0..1: how much of the sun shows (below the horizon, behind rain). */
    public static float sunVisible;
    public static boolean drewThisFrame;
    /** The frame number the Sol sky was last drawn in (the fog follows it). */
    public static long lastDrawFrame = -100L;

    private static SolTexture plasma;
    private static SolTexture moons;
    private static final int[] plasmaPixels = new int[256];
    private static float plasmaTime = -1f;


    private SkyPass() {
    }

    /**
     * Draws the Sol sky if Atmosphere is on. Returns true when it drew (the game's sky is then skipped).
     * {@code sunAngle} is the game's sun angle in radians; {@code moonPhase} 0 (full) to 7.
     */
    public static boolean render(float sunAngle, int moonPhase, float rainBrightness) {
        drewThisFrame = false;
        if (!Atmosphere.active() || !Guard.on(Feature.SKY)) {
            KeyLight.none();
            return false;
        }
        // A theme holding its showpiece shows its own time (a night theme keeps its night sky by day).
        float shownAngle = SkyTime.angle(sunAngle);
        boolean ok = Guard.run(Feature.SKY, () -> draw(shownAngle, moonPhase, rainBrightness));
        if (!ok) {
            KeyLight.none();
        }
        drewThisFrame = ok;
        if (ok) {
            lastDrawFrame = FrameInfo.frame;
        }
        return ok;
    }

    private static void draw(float sunAngle, int moonPhase, float rainBrightness) {
        io.github.georgwav.cosmetics.sol.music.Visualizers.frame();
        Tokens tk = ThemeState.tokens();
        ThemeSpec theme = ThemeState.entry().base();
        Atmosphere at = Atmosphere.INSTANCE;
        WorldDraw.pixel = at == null || !"smooth".equals(at.lightStyle.get());
        float t = FrameInfo.time;
        float rain = Ease.clamp01(1f - rainBrightness);

        // Sun direction: the game's path, tilted by the theme's orbit tilt.
        float sx = -(float) Math.sin(sunAngle);
        float sy = (float) Math.cos(sunAngle);
        float sz = 0f;
        float tilt = theme.sun() != null ? (float) Math.toRadians(theme.sun().orbitTilt()) : 0f;
        if (tilt != 0f) {
            float y2 = sy * (float) Math.cos(tilt);
            float z2 = sy * (float) Math.sin(tilt);
            sy = y2;
            sz = z2;
        }
        sunX = sx;
        sunY = sy;
        sunZ = sz;
        sunAltitude = (float) Math.toDegrees(Math.asin(Math.max(-1f, Math.min(1f, sy))));
        float e = Ease.clamp01(sunAltitude / 38f);
        float q = Math.round(e * 20f) / 20f;
        sunE = q;
        float sunAz = (float) Math.toDegrees(Math.atan2(-sx, sz));

        dome(tk, sunAz, q, rain);
        Stellar.render(theme, tk, at, sunAngle, t, rain);
        Extras.renderBehind(theme, tk, t, rain);
        moon(theme, tk, sunAngle, moonPhase, rain);
        if (theme.eclipse() != null) {
            eclipse(theme, tk, rain);
        } else if (theme.sun() != null && Guard.on(Feature.SUN)) {
            Guard.run(Feature.SUN, () -> sun(theme, at, q, t, rain));
        } else {
            sunVisible = 0f;
        }
        band(tk, sunAz, q, rain);
        eq(tk, sunAz, rain);
        Extras.renderFront(theme, tk, t, rain);
        Extras.renderMist(theme, tk, t, rain);
        KeyLight.update(theme, tk, t, rain);
    }

    // ------------------------------------------------------------------ dome

    private static void dome(Tokens tk, float sunAz, float q, float rain) {
        int fog = tk.fogColor;
        int top = tk.skyTop;
        int mid = tk.skyMiddle;
        int hor = tk.skyHorizon;
        int glow = tk.skyGlow;
        float warmBase = (1f - q) * tk.sunWarmth * Ease.smoothstep(-12f, 0f, sunAltitude);
        int grey = Colors.scale(Colors.mix(fog, 0xFF8A9099, 0.5f), 0.85f);
        for (int r = 0; r < RINGS.length; r++) {
            float alt = RINGS[r];
            int base;
            if (alt < 0f) {
                base = fog;
            } else if (alt <= 22f) {
                base = Colors.mix(hor, mid, alt / 22f);
            } else if (alt <= 46f) {
                base = Colors.mix(mid, top, (alt - 22f) / 24f);
            } else {
                base = Colors.mix(top, Colors.scale(top, 0.9f), (alt - 46f) / 44f);
            }
            for (int i = 0; i <= AZ; i++) {
                int c = base;
                if (alt >= 0f && alt < 30f && warmBase > 0f) {
                    float az = i * 360f / AZ;
                    float cos = (float) Math.cos(Math.toRadians(az - sunAz));
                    float w = Math.max(0f, cos) * warmBase * (1f - alt / 30f);
                    if (w > 0f) {
                        c = Colors.mix(c, glow, Math.min(1f, w));
                    }
                }
                if (rain > 0f) {
                    c = Colors.mix(c, grey, rain * 0.6f);
                }
                colors[r * (AZ + 1) + i] = c | 0xFF000000;
            }
        }
        WorldDraw.begin(WorldDraw.SKY_ALPHA, null);
        for (int r = 0; r < RINGS.length - 1; r++) {
            float a0 = RINGS[r];
            float a1 = RINGS[r + 1];
            for (int i = 0; i < AZ; i++) {
                float z0 = i * 360f / AZ;
                float z1 = (i + 1) * 360f / AZ;
                SkyDraw.altAz(a0, z0, 0f, 0f, colors[r * (AZ + 1) + i]);
                SkyDraw.altAz(a1, z0, 0f, 1f, colors[(r + 1) * (AZ + 1) + i]);
                SkyDraw.altAz(a1, z1, 1f, 1f, colors[(r + 1) * (AZ + 1) + i + 1]);
                SkyDraw.altAz(a0, z1, 1f, 0f, colors[r * (AZ + 1) + i + 1]);
            }
        }
        WorldDraw.end();
    }

    // ------------------------------------------------------------------ band

    /** The horizon glow: clear at +10°, the glow at .75 on the horizon, clear again at −4° (the prototype's band). */
    private static void band(Tokens tk, float sunAz, float q, float rain) {
        int glow = tk.skyGlow;
        // A little over the prototype's .75: in the game the band also has to read through the terrain's fog edge.
        float peak = 0.84f * (1f - rain * 0.5f) + MusicSky.bandBoost();
        WorldDraw.begin(WorldDraw.SKY_ALPHA, null);
        for (int i = 0; i < AZ; i++) {
            float z0 = i * 360f / AZ;
            float z1 = (i + 1) * 360f / AZ;
            float b0 = toward(z0, sunAz, q);
            float b1 = toward(z1, sunAz, q);
            int top0 = Colors.withAlpha(glow, 0f);
            int mid0 = Colors.withAlpha(glow, Math.min(1f, peak * b0));
            int mid1 = Colors.withAlpha(glow, Math.min(1f, peak * b1));
            SkyDraw.altAz(10f, z0, 0f, 0f, top0);
            SkyDraw.altAz(0f, z0, 0f, 1f, mid0);
            SkyDraw.altAz(0f, z1, 1f, 1f, mid1);
            SkyDraw.altAz(10f, z1, 1f, 0f, top0);
            SkyDraw.altAz(0f, z0, 0f, 0f, mid0);
            SkyDraw.altAz(-4f, z0, 0f, 1f, top0);
            SkyDraw.altAz(-4f, z1, 1f, 1f, top0);
            SkyDraw.altAz(0f, z1, 1f, 0f, mid1);
        }
        WorldDraw.end();
    }

    /**
     * The music's horizon EQ (a visualizer): 40 columns of block-sized steps standing on the horizon either side of the
     * sun's bearing, the low notes in the middle, in the accent fading to the glow as they climb; terrain in front cuts them, as it does the band.
     */
    private static void eq(Tokens tk, float sunAz, float rain) {
        float[] v = MusicSky.eq;
        if (v == null) {
            return;
        }
        // 40 columns across 120° of horizon centred on the sun: about the view's width, narrow enough to read as bars.
        int n = v.length;
        float span = 120f / n, gap = span * 0.28f, step = 0.8f, fill = 0.75f;
        WorldDraw.begin(WorldDraw.SKY_ADD, null);
        for (int i = 0; i < n; i++) {
            float z0 = sunAz + (i - n / 2f) * span + gap / 2f, z1 = z0 + span - gap;
            int blocks = Math.round(Math.max(0f, Math.min(1f, v[i])) * 8f);
            for (int b = 0; b < blocks; b++) {
                float a0 = b * step, a1 = a0 + step * fill;
                int c = Colors.withAlpha(Colors.mix(tk.accent, tk.skyGlow, b / 8f), 0.55f * (1f - b / 11f) * (1f - rain * 0.6f));
                SkyDraw.altAz(a0, z0, 0f, 1f, c);
                SkyDraw.altAz(a1, z0, 0f, 0f, c);
                SkyDraw.altAz(a1, z1, 1f, 0f, c);
                SkyDraw.altAz(a0, z1, 1f, 1f, c);
            }
        }
        WorldDraw.end();
    }

    /** The band is a little brighter toward a low sun. */
    private static float toward(float az, float sunAz, float q) {
        float cos = (float) Math.cos(Math.toRadians(az - sunAz));
        return 1f + 0.3f * Math.max(0f, cos) * (1f - q) * Ease.smoothstep(-10f, 0f, sunAltitude);
    }

    // ------------------------------------------------------------------ moon

    private static void moon(ThemeSpec theme, Tokens tk, float sunAngle, int phase, float rain) {
        ThemeSpec.Moon m = theme.moon();
        if (m == null) {
            return;
        }
        float x = (float) Math.sin(sunAngle);
        float y = -(float) Math.cos(sunAngle);
        if (y < -0.12f) {
            return;
        }
        // A pale moon by day.
        float alpha = (0.35f + 0.65f * tk.night) * (1f - rain * 0.9f) * Ease.smoothstep(-0.12f, 0.02f, y);
        if (alpha <= 0.01f) {
            return;
        }
        SkyDraw.billboard(x, y, 0f);
        WorldDraw.begin(WorldDraw.SKY_ADD, SkyDraw.glow());
        float hr = m.haloRadius();
        SkyDraw.glowAt(0f, 0f, hr, hr, Colors.withAlpha(m.halo(), m.haloAlpha() * alpha * (0.4f + 0.6f * tk.night)));
        WorldDraw.end();
        if (m.ring() && tk.night > 0.5f) {
            WorldDraw.begin(WorldDraw.SKY_ADD, null);
            SkyDraw.ring(0f, 0f, 0.3f, Math.max(SkyDraw.pixel() * 1.5f, m.size() * 0.12f), 96,
                    Colors.withAlpha(0xFFCFE4FF, 0.08f * alpha));
            WorldDraw.end();
        }
        float s = m.size();
        int p = Math.floorMod(phase, 8);
        WorldDraw.begin(WorldDraw.SKY_ALPHA, moonTexture(m.color()));
        SkyDraw.rect(-s, -s, s, s, 0f, p / 8f, 1f, (p + 1) / 8f, Colors.withAlpha(0xFFFFFFFF, alpha));
        WorldDraw.end();
    }

    private static int moonColor;

    /** The square moon's eight phases, 16x16 each, stacked (phase 0 is full, 4 is new, as in the game). */
    private static SolTexture moonTexture(int color) {
        if (moons != null && !moons.released() && moonColor == color) {
            return moons;
        }
        if (moons != null) {
            moons.release();
        }
        moonColor = color;
        int[] px = new int[16 * 16 * 8];
        int dark = Colors.withAlpha(Colors.scale(color, 0.32f), 0.55f);
        int crater = Colors.mix(color, 0xFF78829F, 0.4f);
        for (int ph = 0; ph < 8; ph++) {
            for (int j = 0; j < 16; j++) {
                for (int i = 0; i < 16; i++) {
                    boolean corner = (i == 0 || i == 15) && (j == 0 || j == 15);
                    int c = corner ? 0 : color;
                    if (!corner && (inBox(i, j, 3, 4, 7, 7) || inBox(i, j, 9, 9, 13, 12) || inBox(i, j, 7, 12, 9, 14)
                            || inBox(i, j, 11, 3, 12, 4))) {
                        c = crater;
                    }
                    if (!corner && !lit(ph, i, j)) {
                        c = dark;
                    }
                    px[(ph * 16 + j) * 16 + i] = c;
                }
            }
        }
        moons = SolTexture.of("moon_phases", 16, 128, false, px);
        return moons;
    }

    private static boolean inBox(int i, int j, int x0, int y0, int x1, int y1) {
        return i >= x0 && i <= x1 && j >= y0 && j <= y1;
    }

    /** Whether pixel (i, j) of the moon is lit in a phase; the terminator bows a little like a real one. */
    private static boolean lit(int phase, int i, int j) {
        if (phase == 0) {
            return true;
        }
        if (phase == 4) {
            return false;
        }
        float bow = (float) (Math.abs(j - 7.5) / 7.5);
        float edge;
        boolean waning = phase < 4;
        int k = waning ? phase : 8 - phase;
        // k = 1: gibbous (12 columns), 2: quarter (8), 3: crescent (4).
        edge = 16f - k * 4f + (k == 2 ? 0f : (k == 1 ? -1.5f : 1.5f) * bow * bow);
        return waning ? i < edge : i >= 16f - edge;
    }

    // ------------------------------------------------------------------ eclipse

    private static void eclipse(ThemeSpec theme, Tokens tk, float rain) {
        if (sunY < -0.1f) {
            sunVisible = 0f;
            return;
        }
        ThemeSpec.Eclipse ec = theme.eclipse();
        float vis = Ease.smoothstep(-0.1f, 0.05f, sunY) * (1f - rain * 0.8f);
        sunVisible = vis;
        float s = ec.size();
        sunS = s;
        sunCore = 0xFF050303;
        sunHalo = ec.corona();
        SkyDraw.billboard(sunX, sunY, sunZ);
        WorldDraw.begin(WorldDraw.SKY_ADD, SkyDraw.glow());
        SkyDraw.glowAt(0f, 0f, s * 4.5f, s * 4.5f, Colors.withAlpha(ec.corona(), 0.9f * vis));
        SkyDraw.glowAt(0f, 0f, s * 2f, s * 2f, Colors.withAlpha(0xFFFFD0A0, 0.7f * vis));
        WorldDraw.end();
        WorldDraw.begin(WorldDraw.SKY_ALPHA, null);
        SkyDraw.rect(-s, -s, s, s, Colors.withAlpha(0xFF050303, vis));
        float w = Math.max(SkyDraw.pixel(), 1.5f * SkyDraw.pixel() * Math.max(1f, FrameInfo.height / 560f));
        SkyDraw.hollowSquare(0f, 0f, s, w, Colors.withAlpha(0xFFFFAA78, 0.85f * vis));
        WorldDraw.end();
    }

    // ------------------------------------------------------------------ sun

    /** The prototype's sunState(): colour and size by height. */
    private static void sunState(ThemeSpec.Sun spec, float q) {
        sunCore = q < 0.35f ? Colors.mix(0xFFFF6A2A, 0xFFFFD36B, q / 0.35f)
                : Colors.mix(0xFFFFD36B, 0xFFFFF8EA, (q - 0.35f) / 0.65f);
        sunHalo = Colors.mix(spec.halo(), 0xFFFF5A1F, (1f - q) * 0.45f);
        sunS = spec.size() * (1.75f - 0.75f * q) * MusicSky.sunPulse();
    }

    private static void sun(ThemeSpec theme, Atmosphere at, float q, float t, float rain) {
        ThemeSpec.Sun spec = theme.sun();
        sunState(spec, q);
        float s = sunS;
        float sDeg = s * 70f;
        float vis = Ease.smoothstep(-(sDeg + 1.5f), sDeg * 0.4f, sunAltitude) * (1f - rain * 0.85f);
        sunVisible = vis;
        if (vis <= 0.005f) {
            return;
        }
        float low = 1f - q;
        float px = s * 2f / 16f;
        float pix = SkyDraw.pixel();
        int halo = sunHalo;
        int core = sunCore;
        boolean plasmaOn = spec.plasma() && (at == null || at.plasma.get());
        boolean coronaOn = spec.corona() && (at == null || at.corona.get());
        boolean raysOn = spec.rays() > 0 && (at == null || at.rays.get());
        boolean dogsOn = spec.sunDogs() && (at == null || at.dogs.get());
        boolean pillarOn = spec.pillar() && (at == null || at.pillar.get());
        boolean shimmerOn = spec.shimmer() && (at == null || at.shimmer.get());
        SkyDraw.billboard(sunX, sunY, sunZ);

        // Halo and pillar.
        WorldDraw.begin(WorldDraw.SKY_ADD, SkyDraw.glow());
        // The theme's halo (Ember's is .36 at .85: the Sun lab's .3 + .18·low at .45 + .4·low), widest and strongest
        // with the sun low; a high sun keeps a smaller, fainter one so a day sky isn't one big disc.
        float high = Math.min(1f, low * 2f);
        float r = spec.haloRadius() * (0.83f + 0.5f * low) * (0.7f + 0.3f * high);
        float ha = spec.haloAlpha() * (0.53f + 0.47f * low) * (0.75f + 0.25f * high);
        SkyDraw.glowAt(0f, 0f, r, r, Colors.withAlpha(halo, ha * vis));
        if (pillarOn && low > 0.3f) {
            float k = (low - 0.3f) / 0.7f;
            // As strong as Ember's with Ember's halo; a theme with a fainter halo gets a fainter pillar, so it
            // doesn't stand above its sun like a tower.
            float f = Math.min(1f, spec.haloAlpha() / 0.85f);
            float pa = k * k * 0.6f * f * f;
            SkyDraw.rect(-s * 1.2f, -0.42f, s * 1.2f, 0.42f, Colors.withAlpha(halo, pa * vis));
        }
        // Sun dogs: the spots' glows.
        float dA = dogsOn ? Math.max(0f, 1f - Math.abs(q - 0.22f) / 0.4f) : 0f;
        float dogD = 0.3f;
        if (dA > 0f) {
            for (int dir = -1; dir <= 1; dir += 2) {
                float dx = dir * dogD;
                SkyDraw.glowAt(dx, 0f, s * 1.5f, s * 1.5f, Colors.withAlpha(core, 0.7f * dA * vis));
                float x0 = dx;
                float x1 = dx + dir * s * 4.5f;
                SkyDraw.rect(Math.min(x0, x1), -s * 0.22f, Math.max(x0, x1), s * 0.22f, Colors.withAlpha(halo, 0.45f * dA * vis));
            }
        }
        WorldDraw.end();

        // Rays.
        if (raysOn) {
            float len0 = (0.3f + 0.22f * low) * spec.rayLength();
            int n = spec.rays();
            WorldDraw.begin(WorldDraw.SKY_ADD, SkyDraw.ray());
            for (int i = 0; i < n; i++) {
                float a = (float) (i / (double) n * 6.283) + t * 0.035f;
                float lng = i % 2 == 1 ? 0.6f : 1f;
                float len = len0 * lng * (1f + 0.14f * (float) Math.sin(t * 0.9f + i * 1.7f));
                float w = s * (i % 2 == 1 ? 1.3f : 2f);
                float al = (0.2f + 0.16f * low) * (0.75f + 0.25f * (float) Math.sin(t * 1.3f + i)) * vis;
                SkyDraw.rotRect(0f, 0f, a, s * 0.5f, -w / 2f, s * 0.5f + len, w / 2f, Colors.withAlpha(halo, al));
            }
            int m = spec.coreRays();
            for (int i = 0; i < m; i++) {
                float a = (float) (i / (double) m * 6.283) - t * 0.022f + 0.2f;
                float len = len0 * 0.55f * (1f + 0.2f * (float) Math.sin(t * 0.7f + i * 2.3f));
                float w = s * 0.9f;
                SkyDraw.rotRect(0f, 0f, a, s * 0.4f, -w / 2f, s * 0.4f + len, w / 2f, Colors.withAlpha(core, 0.16f * vis));
            }
            WorldDraw.end();
        }

        WorldDraw.begin(WorldDraw.SKY_ADD, null);
        // Sun dogs: the 22° rings and the pixel bars.
        if (dA > 0f) {
            float lw = Math.max(pix, px * 0.7f);
            for (int o = -1; o <= 1; o++) {
                SkyDraw.ring(0f, 0f, dogD + o * Math.max(1.5f * pix, px * 0.8f), lw, 96,
                        Colors.withAlpha(DOG_RINGS[o + 1], 0.13f * dA * vis));
            }
            for (int dir = -1; dir <= 1; dir += 2) {
                float dx = dir * dogD;
                for (int k = 0; k < 3; k++) {
                    float bx = dx + dir * (k - 1) * px - px / 2f;
                    SkyDraw.rect(bx, -px * 1.5f, bx + px, px * 1.5f, Colors.withAlpha(DOG_BARS[k], 0.9f * dA * vis));
                }
            }
        }
        // Corona: three squares snapped to the sun's pixels, and two square rings pulsing out.
        if (coronaOn) {
            for (float[] sq : CORONA_SQUARES) {
                float h = Math.round(s * sq[0] / px) * px;
                SkyDraw.rect(-h, -h, h, h, Colors.withAlpha(halo, sq[1] * vis));
            }
            float lw = Math.max(pix, px * 0.6f);
            for (int ring = 0; ring < 2; ring++) {
                float ph = ((t * 0.4f + ring * 0.5f) % 1f + 1f) % 1f;
                float h = Math.round(s * (1.15f + ph * 2.4f) / px) * px;
                SkyDraw.hollowSquare(0f, 0f, h, lw, Colors.withAlpha(core, (1f - ph) * (1f - ph) * 0.24f * vis));
            }
        }
        // Music: a square ring of the accent spreading from the sun on each beat (a visualizer).
        float beatRing = MusicSky.ring();
        if (beatRing >= 0f) {
            float h = Math.round(s * (1.2f + beatRing * 3.4f) / px) * px;
            SkyDraw.hollowSquare(0f, 0f, h, Math.max(pix, px * 0.8f),
                    Colors.withAlpha(ThemeState.tokens().accent, (1f - beatRing) * (1f - beatRing) * 0.5f * vis));
        }
        WorldDraw.end();

        // The body: 16 rows of the plasma texture, shifted by the heat shimmer near the horizon.
        updatePlasma(plasmaOn, core, halo, t);
        float shim = shimmerOn ? low * low * low * 1.4f : 0f;
        WorldDraw.begin(WorldDraw.SKY_ALPHA, plasma);
        int body = Colors.withAlpha(0xFFFFFFFF, vis);
        for (int j = 0; j < 16; j++) {
            float off = shim > 0f ? Math.round((float) Math.sin(j * 0.9f + t * 7f) * shim * 2f) / 2f * px : 0f;
            float y0 = -s + j * px;
            SkyDraw.rect(-s + off, y0, s + off, y0 + px + px * 0.03f, 0f, j / 16f, 1f, (j + 1) / 16f, body);
        }
        WorldDraw.end();

        // The body burns brighter than white, as the Sun lab's glare makes it: a warm wash on the disc and just round
        // it (a white core, a yellow rim). Against a pale sky the sun already reads white and the wash would only
        // blow the sky out, so it follows how dark the sky is. With the full-screen glare on, the glare does it.
        boolean glareOn = spec.glare() && at != null && at.glare.get();
        float dark = 1f - Colors.luma(ThemeState.tokens().skyMiddle);
        float hot = 0.6f * dark * dark * vis;
        if (!glareOn && hot > 0.01f) {
            WorldDraw.begin(WorldDraw.SKY_ADD, SkyDraw.glow());
            SkyDraw.glowAt(0f, 0f, s * 2.2f, s * 2.2f, Colors.withAlpha(halo, hot));
            WorldDraw.end();
        }
    }

    private static int[] albumShown;

    /** The prototype's pixel plasma, rewritten at most 20 times a second. */
    private static void updatePlasma(boolean plasmaOn, int core, int halo, float t) {
        if (plasma == null || plasma.released()) {
            plasma = new SolTexture("sun_plasma", 16, 16, false);
            plasmaTime = -1f;
        }
        int[] album = MusicSky.albumSun();
        if (album != null) {
            if (plasmaTime != -2f || album != albumShown) {
                plasma.upload(album);
                plasmaTime = -2f;
                albumShown = album;
            }
            return;
        }
        if (plasmaTime >= 0f && t - plasmaTime < 0.05f && t >= plasmaTime) {
            return;
        }
        plasmaTime = t;
        int white = 0xFFFFFFFF;
        int rim = Colors.mix(core, white, 0.55f);
        for (int j = 0; j < 16; j++) {
            for (int i = 0; i < 16; i++) {
                float d = Math.max(Math.abs(i - 7.5f), Math.abs(j - 7.5f));
                int c;
                if (!plasmaOn) {
                    c = d < 4f ? white : d > 7f ? rim : core;
                } else {
                    float v = 0.5f + 0.5f * ((float) (Math.sin(i * 0.9 + t * 1.9) * Math.sin(j * 1.1 - t * 1.4)) * 0.6f
                            + (float) Math.sin((i + j) * 0.55 + t * 2.3) * 0.4f);
                    c = d < 3.5f ? Colors.mix(white, core, v * 0.25f)
                            : d > 7f ? rim : Colors.mix(Colors.mix(core, white, 0.25f), halo, v * 0.35f * (d / 7f));
                    float spotX = ((t * 0.18f) % 1f) * 10f + 3f;
                    if (Math.abs(i - spotX) < 0.6f && Math.abs(j - 9.5f) < 0.6f) {
                        c = Colors.mix(c, halo, 0.45f);
                    }
                }
                plasmaPixels[j * 16 + i] = c | 0xFF000000;
            }
        }
        plasma.upload(plasmaPixels);
    }
}
