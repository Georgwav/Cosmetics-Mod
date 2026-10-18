package io.github.georgwav.cosmetics.sol.atmosphere;

import io.github.georgwav.cosmetics.sol.Feature;
import io.github.georgwav.cosmetics.sol.render.FrameInfo;
import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.sol.render.WorldDraw;
import io.github.georgwav.cosmetics.sol.sky.MilkyWayBake;
import io.github.georgwav.cosmetics.sol.sky.MoonLook;
import io.github.georgwav.cosmetics.sol.sky.MoonLooks;
import io.github.georgwav.cosmetics.sol.sky.SkySphere;
import io.github.georgwav.cosmetics.sol.sky.StarCatalog;
import io.github.georgwav.cosmetics.sol.theme.ThemeSpec;
import io.github.georgwav.cosmetics.sol.ui.kit.SolScreen;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.sol.util.Ease;
import io.github.georgwav.cosmetics.sol.util.LogOnce;
import io.github.georgwav.cosmetics.sol.util.SolExecutors;
import org.joml.Matrix4f;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The night sky of the Night Sky lab, on the one sky sphere: the Milky Way and the nebulae from a baked pixel cube;
 * every real star down to magnitude 6.5 as square pixels, the bright ones as pixel crosses with a faint four-point
 * flare and a small glow on the brightest, the same size in pixels at any zoom; and the constellations' lines and
 * names. The stars come out one by one as the sky darkens, fade near a bright moon (only within about 20° of it),
 * and dim, redden and twinkle harder low down. Render thread only; nothing allocated per frame.
 */
public final class NightSky {
    /** The sky sphere, turned once a frame before anything in the sky is drawn. */
    public static final SkySphere SPHERE = new SkySphere();
    /** Days the sky has turned through instead of the world's (the test scenario's), or NaN. */
    public static volatile double daysOverride = Double.NaN;
    /** A moon look to show instead of tonight's (the test scenario's), or null. */
    public static volatile String lookOverride;
    /** The lab's default moon glow (45%): how far the moon's light spreads into the sky. */
    static final float MOON_GLOW = 0.45f;
    private static final int LINE = 0xFFACC0FF;

    private static volatile StarCatalog catalog;
    private static volatile boolean loading;

    // The Milky Way cube: baked on the CPU thread, uploaded here.
    private record Baked(String key, int[] pixels) {
    }

    private static final AtomicReference<Baked> ready = new AtomicReference<>();
    private static SolTexture cube;
    private static String requestedKey = "", uploadedKey = "";
    /** The cube's grid: equatorial points of each face's 12 x 12 cells, on the cube (not normalised). */
    private static final int GRID = 12;
    private static final float[] gridX, gridY, gridZ;

    // This frame.
    /** 0 by day, 1 in full dark (the sun 17° down), the moon's light in the sky, the faintest star showing. */
    public static float dark, moonSky, limit;
    /** Tonight's moon look, whether there's a moon, its light on the world (0..1.25) and colour, and its glow. */
    public static MoonLook look = MoonLook.SILVER;
    public static boolean hasMoon;
    public static float moonLight, glowK = 1f;
    public static int moonColor = 0xFFB8CCFF;
    /** The colour the night lifts the world's shadows to: blue-violet, never flat black (light pass). */
    public static int floorColor = 0xFF1A2040;
    private static float mwVis, moonOn;
    /** The view's half-extents (tangents), the screen in pixels, and the sky's pixel (2 at 1080p). */
    static float tanX, tanY, sw, sh, pixel;
    private static final float[] v = new float[9];
    /** The last {@link #project}ed point: world direction, screen pixels, and its depth (cosine off the view's axis). */
    static final float[] world = new float[3];
    static final float[] screen = new float[2];
    static float depth;

    // The brightest stars' glows, drawn after the pixels.
    private static final int GLOWS = 64;
    private static final float[] glowX = new float[GLOWS], glowY = new float[GLOWS], glowR = new float[GLOWS];
    private static final int[] glowC = new int[GLOWS];
    private static int glows;

    // Constellations: how much each is in focus, and where the view pointed last frame.
    private static float[] focus = new float[0];
    private static float lastFx, lastFy, lastFz = Float.NaN;

    static {
        int n = 6 * (GRID + 1) * (GRID + 1);
        gridX = new float[n];
        gridY = new float[n];
        gridZ = new float[n];
        float[] d = new float[3];
        float k = 1f / (float) Math.sqrt(3.0);
        for (int f = 0; f < 6; f++) {
            for (int j = 0; j <= GRID; j++) {
                for (int i = 0; i <= GRID; i++) {
                    MilkyWayBake.faceDir(f, i * 2f / GRID - 1f, j * 2f / GRID - 1f, d);
                    int at = (f * (GRID + 1) + j) * (GRID + 1) + i;
                    // On the cube itself (scaled into the sky's distance), so each face is flat and its pixels exact.
                    gridX[at] = d[0] * k;
                    gridY[at] = d[1] * k;
                    gridZ[at] = d[2] * k;
                }
            }
        }
    }

    private NightSky() {
    }

    /** Turns the sky sphere to the sky's time ({@code sunAngle}, radians from noon) and the game's moon phase. */
    static void turn(ThemeSpec theme, float sunAngle, int moonPhase) {
        Atmosphere at = Atmosphere.INSTANCE;
        double lat = at != null ? at.latitude.get() : SkySphere.DEFAULT_LATITUDE;
        if (theme.sun() != null) {
            lat += theme.sun().orbitTilt();
        }
        lat = Math.max(-80.0, Math.min(80.0, lat));
        io.github.georgwav.cosmetics.sol.theme.Keyframes.latitude = (float) lat;
        int year = at != null ? (int) Math.round(at.year.get()) : SkySphere.DEFAULT_YEAR;
        double days = Double.isNaN(daysOverride) ? FrameInfo.days : daysOverride;
        float tod = sunAngle / (float) (Math.PI * 2.0);
        SPHERE.update(days, tod, moonPhase, lat, year);
        // Tonight's moon look: a special moon on its night, or the theme's own. A theme holding its showpiece night
        // holds its moon's phase too.
        String forced = lookOverride;
        look = forced != null ? MoonLooks.find(forced) : MoonLooks.tonight(SPHERE.night, moonPhase, theme.night().moon());
        if (theme.show().hold()) {
            SPHERE.update(days, tod, moonPhase, lat, year, look.elongation());
        }
        // The lab's envOf: how dark, how much moonlight is in the sky, and the faintest star that shows.
        dark = Ease.smoothstep(-4f, -17f, SPHERE.sunAlt);
        hasMoon = SPHERE.elong > 8f && SPHERE.elong < 352f && !look.hidden();
        glowK = (0.35f + 1.3f * MOON_GLOW) * look.glow();
        moonLight = hasMoon ? (float) Math.pow(SPHERE.illum, 1.6) * Ease.smoothstep(-1.5f, 12f, SPHERE.moonAlt) * look.light()
                * Math.min(1.25f, look.size()) : 0f;
        moonSky = moonLight * (0.45f + 0.55f * Math.min(1.4f, glowK));
        moonOn = hasMoon && SPHERE.moonAlt > -5f ? moonSky : 0f;
        limit = Ease.lerp(1.1f, 6.5f, dark) - 1.15f * moonSky + (look.eclipse() ? 0.3f : 0f);
        mwVis = Ease.smoothstep(-9f, -18f, SPHERE.sunAlt) * (1f - 0.62f * Math.min(1f, moonSky)) * look.milkyWay();
        // The moonlight's colour: an eclipse's copper, a harvest moon's orange low down, else the look's or the theme's.
        if (look.lightColor() != null) {
            moonColor = look.lightColor();
        } else if (look.harvest() && SPHERE.moonAlt < 15f) {
            moonColor = Colors.mix(0xFFFFB873, 0xFFB8CCFF, Ease.smoothstep(2f, 15f, SPHERE.moonAlt));
        } else {
            moonColor = theme.night().light();
        }
        // The shadows' floor: the theme's night sky's middle, a little bluer and brighter, and more under a bright moon.
        int base = theme.night().hasSky() ? theme.night().middle() : 0xFF19213B;
        floorColor = Colors.scale(Colors.mix(base, 0xFF34407A, 0.4f), 0.55f + 0.35f * Math.min(1f, moonLight));
        frame();
    }

    /**
     * Where the moon is (world direction into {@code out}) for a theme at a moment, with a look: what the test
     * scenario aims at.
     */
    public static void moonAt(ThemeSpec theme, double days, float timeOfDay, int moonPhase, String lookId, float[] out) {
        SkySphere s = new SkySphere();
        s.update(days, timeOfDay, moonPhase, SkySphere.DEFAULT_LATITUDE, SkySphere.DEFAULT_YEAR);
        if (theme.show().hold()) {
            s.update(days, timeOfDay, moonPhase, SkySphere.DEFAULT_LATITUDE, SkySphere.DEFAULT_YEAR, MoonLooks.find(lookId).elongation());
        }
        System.arraycopy(s.moon, 0, out, 0, 3);
    }

    /** Draws the night sky (the sky pass, after the dome). */
    static void render(ThemeSpec theme, Atmosphere at, float t, float rain) {
        float clear = 1f - rain;
        if (clear <= 0.01f || dark <= 0.001f && mwVis <= 0.001f) {
            SkyLabels.clear();
            focus(null, 0f);
            return;
        }
        boolean gaps = "cloudGaps".equals(theme.stars().through());
        if (at == null || at.milkyWay.get() || at.nebulae.get()) {
            cube(at, clear);
        }
        StarCatalog cat = catalog();
        SkyLabels.clear();
        if (cat == null) {
            return;
        }
        boolean names = at == null || at.names.get();
        // The theme's star density (and the player's Stars slider) moves the faintest star that shows.
        float density = theme.stars().density();
        float shift = density <= 0.01f ? -20f : 2.5f * (float) Math.log10(density);
        if (at == null || at.stars.get()) {
            stars(cat, t, clear, gaps, names, limit + shift);
        }
        String mode = at != null ? at.constellations.get() : "always";
        focus(cat, FrameInfo.frameSeconds);
        if (!"off".equals(mode) && (at == null || at.stars.get())) {
            constellations(cat, "always".equals(mode), names, clear);
        }
    }

    /** The view this frame: equatorial to view rotation, the screen and its pixel grid. */
    static void frame() {
        Matrix4f view = FrameInfo.view;
        float[] m = SPHERE.m;
        // The world-to-view rotation's rows (JOML is column-major), times the sphere's.
        rows(0, view.m00(), view.m10(), view.m20(), m);
        rows(1, view.m01(), view.m11(), view.m21(), m);
        rows(2, view.m02(), view.m12(), view.m22(), m);
        float m00 = FrameInfo.projection.m00(), m11 = FrameInfo.projection.m11();
        sw = Math.max(1, FrameInfo.width);
        sh = Math.max(1, FrameInfo.height);
        if (m00 > 0.01f && m11 > 0.01f) {
            tanX = 1f / m00;
            tanY = 1f / m11;
        } else {
            tanY = (float) Math.tan(Math.toRadians(FrameInfo.fovY) / 2.0);
            tanX = tanY * sw / sh;
        }
        // The lab's pixel: 2 up to 1080p and more above it, so the sky reads as pixel art at any resolution.
        pixel = Math.max(2f, Math.round(sh / 540f));
    }

    private static void rows(int r, float a, float b, float c, float[] m) {
        for (int col = 0; col < 3; col++) {
            v[r * 3 + col] = a * m[col] + b * m[3 + col] + c * m[6 + col];
        }
    }

    /** A vertex at screen point (x, y) in pixels, on the sky. */
    private static void vertex(float x, float y, float u, float w, int argb) {
        float d = SkyDraw.D;
        WorldDraw.vertex((x / sw * 2f - 1f) * tanX * d, (1f - y / sh * 2f) * tanY * d, -d, u, w, argb);
    }

    /** A rectangle on the screen, in pixels. */
    static void box(float x0, float y0, float x1, float y1, int argb) {
        box(x0, y0, x1, y1, 0f, 0f, 1f, 1f, argb);
    }

    /** A rectangle on the screen, in pixels, with part of the texture. */
    static void box(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, int argb) {
        vertex(x0, y0, u0, v0, argb);
        vertex(x0, y1, u0, v1, argb);
        vertex(x1, y1, u1, v1, argb);
        vertex(x1, y0, u1, v0, argb);
    }

    /** Projects an equatorial unit vector: world direction into {@link #world}, screen pixels into {@link #screen}. */
    static boolean project(float ex, float ey, float ez) {
        float[] m = SPHERE.m;
        world[0] = m[0] * ex + m[1] * ey + m[2] * ez;
        world[1] = m[3] * ex + m[4] * ey + m[5] * ez;
        world[2] = m[6] * ex + m[7] * ey + m[8] * ez;
        float vz = v[6] * ex + v[7] * ey + v[8] * ez;
        if (vz > -0.03f) {
            return false;
        }
        float vx = v[0] * ex + v[1] * ey + v[2] * ez, vy = v[3] * ex + v[4] * ey + v[5] * ez;
        depth = -vz;
        screen[0] = (vx / -vz / tanX * 0.5f + 0.5f) * sw;
        screen[1] = (0.5f - vy / -vz / tanY * 0.5f) * sh;
        return true;
    }

    // ------------------------------------------------------------------ the catalogue

    /** The star catalogue, read once on the CPU thread; null until it's there (or if it couldn't be read). */
    static StarCatalog catalog() {
        StarCatalog c = catalog;
        if (c == null && !loading) {
            loading = true;
            SolExecutors.cpu(Feature.SKY, () -> {
                try (InputStream s = NightSky.class.getResourceAsStream("/assets/cosmeticsmod/sky/stars.dat");
                     InputStream k = NightSky.class.getResourceAsStream("/assets/cosmeticsmod/sky/constellations.dat")) {
                    if (s == null || k == null) {
                        LogOnce.warn("sky-data", "The star catalogue is missing from the jar");
                        return;
                    }
                    catalog = StarCatalog.read(new java.io.BufferedInputStream(s), new java.io.BufferedInputStream(k));
                } catch (java.io.IOException e) {
                    LogOnce.warn("sky-data", "Couldn't read the star catalogue: " + e.getMessage());
                }
            });
        }
        return c;
    }

    // ------------------------------------------------------------------ the Milky Way and the nebulae

    private static void cube(Atmosphere at, float clear) {
        boolean mw = at == null || at.milkyWay.get(), neb = at == null || at.nebulae.get();
        String key = (mw ? "mw" : "") + (neb ? "+neb" : "");
        if (cube != null && cube.released()) {
            cube = null;
            uploadedKey = "";
            requestedKey = "";
        }
        if (!key.equals(requestedKey)) {
            requestedKey = key;
            MilkyWayBake.Parts parts = new MilkyWayBake.Parts(mw, neb);
            SolExecutors.cpu(Feature.SKY, () -> ready.set(new Baked(key, MilkyWayBake.bake(parts))));
        }
        Baked b = ready.get();
        if (b != null && ready.compareAndSet(b, null)) {
            if (cube == null) {
                cube = new SolTexture("milky_way", MilkyWayBake.W, MilkyWayBake.H, false);
            }
            cube.upload(b.pixels());
            uploadedKey = b.key();
        }
        float vis = mwVis * clear;
        if (cube == null || uploadedKey.isEmpty() || vis <= 0.004f) {
            return;
        }
        float[] m = SPHERE.m;
        WorldDraw.begin(WorldDraw.SKY_ADD, cube);
        int row = GRID + 1;
        for (int f = 0; f < 6; f++) {
            float u0 = (f % 3) / 3f, v0 = (f / 3) / 2f;
            for (int j = 0; j < GRID; j++) {
                for (int i = 0; i < GRID; i++) {
                    int a = (f * row + j) * row + i, b2 = a + row;
                    float y00 = up(m, a), y10 = up(m, a + 1), y01 = up(m, b2), y11 = up(m, b2 + 1);
                    // Faded out toward the horizon, so nothing shows below it.
                    if (Math.max(Math.max(y00, y10), Math.max(y01, y11)) < -0.02f) {
                        continue;
                    }
                    float ua = u0 + i / (3f * GRID), ub = u0 + (i + 1) / (3f * GRID);
                    float va = v0 + j / (2f * GRID), vb = v0 + (j + 1) / (2f * GRID);
                    corner(m, a, ua, va, y00, vis);
                    corner(m, b2, ua, vb, y01, vis);
                    corner(m, b2 + 1, ub, vb, y11, vis);
                    corner(m, a + 1, ub, va, y10, vis);
                }
            }
        }
        WorldDraw.end();
    }

    /** The sine of the altitude of grid point {@code at}. */
    private static float up(float[] m, int at) {
        float x = gridX[at], y = gridY[at], z = gridZ[at];
        return (m[3] * x + m[4] * y + m[5] * z) / (float) Math.sqrt(x * x + y * y + z * z);
    }

    private static void corner(float[] m, int at, float u, float w, float alt, float vis) {
        float x = gridX[at], y = gridY[at], z = gridZ[at];
        float a = vis * Ease.smoothstep(-0.02f, 0.3f, alt);
        SkyDraw.sphere(m[0] * x + m[1] * y + m[2] * z, m[3] * x + m[4] * y + m[5] * z, m[6] * x + m[7] * y + m[8] * z,
                u, w, Colors.withAlpha(0xFFFFFFFF, a));
    }

    // ------------------------------------------------------------------ the stars

    private static void stars(StarCatalog cat, float t, float clear, boolean gaps, boolean names, float limit) {
        float[] mo = SPHERE.moon;
        float p = pixel, margin = 6f * p;
        float tw = SolScreen.calm() ? 0f : 1f;
        glows = 0;
        WorldDraw.begin(WorldDraw.SKY_ADD, null);
        int n = cat.size();
        for (int i = 0; i < n; i++) {
            float mag = cat.mag[i];
            // Brightest first: once one is too faint to show anywhere, so is every star after it.
            if (mag >= limit) {
                break;
            }
            if (!project(cat.x[i], cat.y[i], cat.z[i]) || world[1] < -0.01f) {
                continue;
            }
            float sx = screen[0], sy = screen[1];
            if (sx < -margin || sy < -margin || sx > sw + margin || sy > sh + margin) {
                continue;
            }
            float ext = Ease.smoothstep(0f, 0.3f, world[1]);
            float lim = limit;
            if (moonOn > 0f) {
                float cd = world[0] * mo[0] + world[1] * mo[1] + world[2] * mo[2];
                if (cd > 0.94f) {
                    lim -= 2.4f * moonOn * Ease.smoothstep(0.94f, 0.995f, cd);
                }
            }
            // Low down a star is dimmer: as if a magnitude and a bit fainter on the horizon.
            float m = mag + (1f - ext) * 1.3f;
            float vis = Math.min(1f, (lim - m) / 1.3f);
            if (vis <= 0f) {
                continue;
            }
            float h = cat.seed[i];
            float b = vis * (1f + tw * (0.16f + 0.42f * (1f - ext)) * (float) Math.sin(t * (4f + h * 7f) + h * 40f)) * clear;
            if (gaps) {
                b *= cloudGap(world[0], world[1], world[2], t);
            }
            if (b <= 0.004f) {
                continue;
            }
            int c = Colors.mix(cat.color[i], 0xFFFFAA78, (1f - ext) * 0.45f);
            float x = (float) Math.floor(sx / p) * p, y = (float) Math.floor(sy / p) * p;
            box(x, y, x + p, y + p, Colors.withAlpha(Colors.mix(c, 0xFFFFFFFF, 0.4f), Math.min(1f, b * 1.05f)));
            if (m < 2.7f) {
                int a = Colors.withAlpha(c, Math.min(1f, 0.5f * b));
                box(x - p, y, x, y + p, a);
                box(x + p, y, x + 2 * p, y + p, a);
                box(x, y - p, x + p, y, a);
                box(x, y + p, x + p, y + 2 * p, a);
            }
            if (m < 1.1f) {
                int a = Colors.withAlpha(c, Math.min(1f, 0.22f * b));
                box(x - 2 * p, y, x - p, y + p, a);
                box(x + 2 * p, y, x + 3 * p, y + p, a);
                box(x, y - 2 * p, x + p, y - p, a);
                box(x, y + 2 * p, x + p, y + 3 * p, a);
            }
            if (m < 0.4f) {
                // The very brightest: a faint four-point flare.
                int a = Colors.withAlpha(c, Math.min(1f, 0.1f * b));
                box(x - 4 * p, y, x - 2 * p, y + p, a);
                box(x + 3 * p, y, x + 5 * p, y + p, a);
                box(x, y - 4 * p, x + p, y - 2 * p, a);
                box(x, y + 3 * p, x + p, y + 5 * p, a);
            }
            if (m < 1.7f && glows < GLOWS) {
                glowX[glows] = x + p * 0.5f;
                glowY[glows] = y + p * 0.5f;
                glowR[glows] = (2.4f + (1.7f - m) * 2f) * p;
                glowC[glows] = Colors.withAlpha(c, Math.min(1f, 0.3f * b));
                glows++;
            }
            String name = cat.name[i];
            if (names && name != null && mag < 1.4f && vis > 0.4f) {
                SkyLabels.add(x + p * 0.5f, y + p * 0.5f, name, SkyLabels.STAR, vis * clear);
            }
        }
        WorldDraw.end();
        if (glows > 0) {
            WorldDraw.begin(WorldDraw.SKY_ADD, SkyDraw.glow());
            for (int g = 0; g < glows; g++) {
                float r = glowR[g];
                box(glowX[g] - r, glowY[g] - r, glowX[g] + r, glowY[g] + r, glowC[g]);
            }
            WorldDraw.end();
        }
    }

    /** Monsoon: stars only through gaps in the clouds, drifting with the wind. */
    private static float cloudGap(float x, float y, float z, float t) {
        double n = fbm(x * 3 + t * 0.02, y * 3, z * 3 + t * 0.01);
        return Ease.smoothstep(0.55f, 0.7f, (float) n);
    }

    private static double fbm(double x, double y, double z) {
        double sum = 0, amp = 0.5, total = 0;
        for (int o = 0; o < 4; o++) {
            sum += amp * noise(x, y, z);
            total += amp;
            x *= 2.03;
            y *= 2.03;
            z *= 2.03;
            amp *= 0.5;
        }
        return sum / total;
    }

    private static double noise(double x, double y, double z) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y), zi = (int) Math.floor(z);
        double xf = x - xi, yf = y - yi, zf = z - zi;
        double u = xf * xf * (3 - 2 * xf), w = yf * yf * (3 - 2 * yf), s = zf * zf * (3 - 2 * zf);
        double x00 = lerp(hash(xi, yi, zi), hash(xi + 1, yi, zi), u), x10 = lerp(hash(xi, yi + 1, zi), hash(xi + 1, yi + 1, zi), u);
        double x01 = lerp(hash(xi, yi, zi + 1), hash(xi + 1, yi, zi + 1), u), x11 = lerp(hash(xi, yi + 1, zi + 1), hash(xi + 1, yi + 1, zi + 1), u);
        return lerp(lerp(x00, x10, w), lerp(x01, x11, w), s);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double hash(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (double) 0xFFFFFF;
    }

    // ------------------------------------------------------------------ constellations

    /**
     * Which constellation you're holding your view on: the nearest name within 26° of the middle of the view, while
     * the view is still (under 10° a second). It brightens over about half a second, and fades faster when you move on.
     */
    private static void focus(StarCatalog cat, float dt) {
        float fx = FrameInfo.forward.x, fy = FrameInfo.forward.y, fz = FrameInfo.forward.z;
        float speed = 0f;
        if (!Float.isNaN(lastFz) && dt > 0f) {
            float dot = Math.max(-1f, Math.min(1f, fx * lastFx + fy * lastFy + fz * lastFz));
            speed = (float) Math.toDegrees(Math.acos(dot)) / dt;
        }
        lastFx = fx;
        lastFy = fy;
        lastFz = fz;
        if (cat == null) {
            return;
        }
        List<StarCatalog.Constellation> list = cat.constellations;
        if (focus.length != list.size()) {
            focus = new float[list.size()];
        }
        int best = -1;
        float bd = (float) Math.cos(Math.toRadians(26));
        float[] m = SPHERE.m;
        for (int k = 0; k < list.size(); k++) {
            StarCatalog.Constellation c = list.get(k);
            float wx = m[0] * c.lx() + m[1] * c.ly() + m[2] * c.lz();
            float wy = m[3] * c.lx() + m[4] * c.ly() + m[5] * c.lz();
            float wz = m[6] * c.lx() + m[7] * c.ly() + m[8] * c.lz();
            if (wy < 0.02f) {
                continue;
            }
            float d = wx * fx + wy * fy + wz * fz;
            if (d > bd) {
                bd = d;
                best = k;
            }
        }
        int held = speed < 10f ? best : -1;
        for (int k = 0; k < focus.length; k++) {
            float target = k == held ? 1f : 0f;
            focus[k] = Ease.clamp01(focus[k] + (target - focus[k]) * Math.min(1f, dt * (target > 0f ? 1.8f : 3f)));
        }
    }

    private static void constellations(StarCatalog cat, boolean always, boolean names, float clear) {
        List<StarCatalog.Constellation> list = cat.constellations;
        // Drawn once the sky is properly dark, not over the twilight.
        float night = Ease.smoothstep(-7f, -16f, SPHERE.sunAlt);
        float p = pixel, dim = night * (1f - 0.3f * moonSky) * clear;
        if (dim <= 0.004f) {
            return;
        }
        WorldDraw.begin(WorldDraw.SKY_ADD, null);
        for (int k = 0; k < list.size(); k++) {
            StarCatalog.Constellation c = list.get(k);
            float f = focus[k];
            float a = ((always ? 0.2f : 0f) + f * 0.5f) * dim;
            if (a >= 0.01f) {
                int col = Colors.withAlpha(LINE, Math.min(1f, a));
                float[] l = c.lines();
                for (int s = 0; s < c.segments(); s++) {
                    int o = s * 6;
                    if (!project(l[o], l[o + 1], l[o + 2])) {
                        continue;
                    }
                    float x1 = screen[0], y1 = screen[1];
                    if (!project(l[o + 3], l[o + 4], l[o + 5])) {
                        continue;
                    }
                    line(x1, y1, screen[0], screen[1], p, col);
                }
            }
            if (f > 0.15f || names && always) {
                if (project(c.lx(), c.ly(), c.lz()) && world[1] > 0.07f) {
                    // Faded toward the horizon, like the stars round it.
                    float la = Math.max(f, names ? 0.5f : 0f) * night * clear * Ease.smoothstep(0.07f, 0.25f, world[1]);
                    SkyLabels.add(screen[0], screen[1], c.name(), SkyLabels.CONSTELLATION, la);
                }
            }
        }
        WorldDraw.end();
    }

    /** A line one sky pixel wide, stopping three pixels short of each star; none when it's under seven long. */
    private static void line(float x1, float y1, float x2, float y2, float p, int argb) {
        float dx = x2 - x1, dy = y2 - y1, len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 7f * p || len > 4f * Math.max(sw, sh)) {
            return;
        }
        float gap = 3f * p / len;
        float ax = x1 + dx * gap, ay = y1 + dy * gap, bx = x2 - dx * gap, by = y2 - dy * gap;
        // Onto the sky's pixel grid, through the middle of the pixels.
        ax = (Math.round(ax / p) + 0.5f) * p;
        ay = (Math.round(ay / p) + 0.5f) * p;
        bx = (Math.round(bx / p) + 0.5f) * p;
        by = (Math.round(by / p) + 0.5f) * p;
        float nx = -dy / len * p * 0.5f, ny = dx / len * p * 0.5f;
        vertex(ax + nx, ay + ny, 0f, 0f, argb);
        vertex(ax - nx, ay - ny, 0f, 1f, argb);
        vertex(bx - nx, by - ny, 1f, 1f, argb);
        vertex(bx + nx, by + ny, 1f, 0f, argb);
    }
}
