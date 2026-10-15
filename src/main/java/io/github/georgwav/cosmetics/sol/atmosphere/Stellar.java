package io.github.georgwav.cosmetics.sol.atmosphere;

import io.github.georgwav.cosmetics.sol.Feature;
import io.github.georgwav.cosmetics.sol.render.FrameInfo;
import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.sol.render.WorldDraw;
import io.github.georgwav.cosmetics.sol.theme.ThemeSpec;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.sol.util.Ease;
import io.github.georgwav.cosmetics.sol.util.Mulberry32;
import io.github.georgwav.cosmetics.sol.util.SolExecutors;

/**
 * The stellar sky (the brief's "The stellar sky"): a dense star field with real colours, the Milky Way with its
 * dust lanes and a few deep-sky smudges, baked once per theme style into a cube of six faces; about 150 bright
 * stars as twinkling pixel crosses; planets you can find on the sun's path (Saturn with its ring, Jupiter with
 * four moons that move night to night, red Mars, Venus near sunrise and sunset); shooting stars, meteor nights
 * and a rare comet. The whole sky turns with the night around the north–south axis, like the sun.
 */
public final class Stellar {
    /** Cube face size in texels (about 0.18° a texel). */
    private static final int FACE = 512;
    private static final int ATLAS_W = FACE * 3;
    private static final int ATLAS_H = FACE * 2;

    private static SolTexture field;
    private static volatile int[] baked;
    private static volatile String bakedKey = "";
    private static String uploadedKey = "";
    private static String requestedKey = "";

    // Bright stars: unit directions in the sky's own frame, size class, colour, twinkle.
    private static float[] bx = new float[0], by = new float[0], bz = new float[0], bs = new float[0], bph = new float[0];
    private static int[] bc = new int[0];
    private static String brightKey = "";
    private static ThemeSpec.Stars brightFor;

    private static SolTexture planets;

    // Sky events.
    private static float shootNext = 3f, shootLife = -1f, shootX, shootY, shootZ, shootDx, shootDy, shootDz;
    private static float lastTime = -1f;

    private Stellar() {
    }

    /** Draws the stellar sky (sky pass, after the dome). */
    static void render(ThemeSpec theme, Tokens tk, Atmosphere at, float sunAngle, float t, float rain) {
        float night = tk.night * (1f - rain);
        boolean starsOn = at == null || at.stars.get();
        float rot = sunAngle;
        if (starsOn && night > 0.01f) {
            field(theme, rot, night);
            bright(theme, rot, night, t);
        }
        if (at == null || at.planets.get()) {
            planets(theme, tk, rot, night, rain);
        }
        if (starsOn) {
            events(theme, rot, night, t);
        }
    }

    // ------------------------------------------------------------------ the baked field

    private static String styleKey(ThemeSpec theme) {
        ThemeSpec.Stars s = theme.stars();
        return s.palette() + "|" + s.tint() + "|" + s.milkyWay() + "|" + s.deepSky() + "|" + s.density();
    }

    private static ThemeSpec.Stars fieldFor;

    private static void field(ThemeSpec theme, float rot, float night) {
        if (theme.stars() != fieldFor) {
            fieldFor = theme.stars();
            String key = styleKey(theme);
            if (key.equals(requestedKey)) {
                key = null;
            }
            if (key != null) {
                bake(theme, key);
            }
        }
        if (!bakedKey.equals(uploadedKey) && baked != null) {

            if (field == null || field.released()) {
                field = new SolTexture("star_field", ATLAS_W, ATLAS_H, false);
            }
            field.upload(baked);
            uploadedKey = bakedKey;
        }
        if (field == null || uploadedKey.isEmpty()) {
            return;
        }
        float alpha = night * ("fog".equals(theme.stars().through()) ? 0.35f : 1f);
        int c = Colors.withAlpha(0xFFFFFFFF, alpha);
        float cos = (float) Math.cos(rot), sin = (float) Math.sin(rot);
        WorldDraw.begin(WorldDraw.SKY_ADD, field);
        for (int f = 0; f < 6; f++) {
            float u0 = (f % 3) / 3f, v0 = (f / 3) / 2f, u1 = u0 + 1f / 3f, v1 = v0 + 0.5f;
            face(f, -1f, -1f, u0, v0, cos, sin, c);
            face(f, -1f, 1f, u0, v1, cos, sin, c);
            face(f, 1f, 1f, u1, v1, cos, sin, c);
            face(f, 1f, -1f, u1, v0, cos, sin, c);
        }
        WorldDraw.end();
    }

    private static void bake(ThemeSpec theme, String key) {
        requestedKey = key;
        ThemeSpec.Stars s = theme.stars();
        SolExecutors.cpu(Feature.SKY, () -> {
            int[] px = bake(s);
            baked = px;
            bakedKey = key;
        });
    }

    /** A cube corner on face f at face coordinates (a, b), turned with the sky. */
    private static void face(int f, float a, float b, float u, float v, float cos, float sin, int c) {
        float[] d = faceDir(f, a, b, TMP);
        float x = d[0] * cos - d[1] * sin;
        float y = d[0] * sin + d[1] * cos;
        SkyDraw.sphere(x, y, d[2], u, v, c);
    }

    private static final float[] TMP = new float[3];

    /** The direction (not normalised) of face f at face coordinates a, b in [-1, 1]. */
    static float[] faceDir(int f, float a, float b, float[] o) {
        switch (f) {
            case 0 -> { o[0] = 1f; o[1] = -b; o[2] = -a; }
            case 1 -> { o[0] = -1f; o[1] = -b; o[2] = a; }
            case 2 -> { o[0] = a; o[1] = 1f; o[2] = b; }
            case 3 -> { o[0] = a; o[1] = -1f; o[2] = -b; }
            case 4 -> { o[0] = a; o[1] = -b; o[2] = 1f; }
            default -> { o[0] = -a; o[1] = -b; o[2] = -1f; }
        }
        return o;
    }

    /** Face and face coordinates (texel) of a unit direction, into {@code o}: {face, px, py}. */
    static void toFace(double x, double y, double z, int[] o) {
        double ax = Math.abs(x), ay = Math.abs(y), az = Math.abs(z);
        int f;
        double a, b;
        if (ax >= ay && ax >= az) {
            f = x > 0 ? 0 : 1;
            a = x > 0 ? -z / ax : z / ax;
            b = -y / ax;
        } else if (ay >= az) {
            f = y > 0 ? 2 : 3;
            a = x / ay;
            b = y > 0 ? z / ay : -z / ay;
        } else {
            f = z > 0 ? 4 : 5;
            a = z > 0 ? x / az : -x / az;
            b = -y / az;
        }
        o[0] = f;
        o[1] = (int) Math.min(FACE - 1, Math.max(0, Math.floor((a + 1) * 0.5 * FACE)));
        o[2] = (int) Math.min(FACE - 1, Math.max(0, Math.floor((b + 1) * 0.5 * FACE)));
    }

    /** The star colours of a palette: blue-white 15%, white 45%, warm yellow 28%, orange-red 12% (natural). */
    private static int starColor(String palette, Integer tint, double r) {
        int c;
        switch (palette) {
            case "mono" -> c = r < 0.5 ? 0xFFFFFFFF : r < 0.85 ? 0xFFD0D0D8 : 0xFF9A9AA3;
            case "nebula" -> c = r < 0.3 ? 0xFFFFD6F5 : r < 0.6 ? 0xFFC9B6FF : r < 0.85 ? 0xFFFFFFFF : 0xFFFF9AD0;
            case "teal" -> c = r < 0.4 ? 0xFFDFF6FF : r < 0.8 ? 0xFFB8FFE8 : 0xFFFFFFFF;
            case "silver" -> c = r < 0.6 ? 0xFFEEF3FF : 0xFFB9C8FF;
            case "gold" -> c = r < 0.6 ? 0xFFFFE0A0 : 0xFFFFC870;
            case "warm" -> c = r < 0.4 ? 0xFFFFF4E0 : r < 0.75 ? 0xFFFFE2B0 : 0xFFFFB98A;
            default -> c = r < 0.15 ? 0xFFBFD4FF : r < 0.6 ? 0xFFFFFFFF : r < 0.88 ? 0xFFFFF1C8 : 0xFFFFB88A;
        }
        return tint != null ? Colors.mixRgb(c, tint, 0.5f) : c;
    }

    /** Bakes the faint field: stars, the Milky Way's glow, stars and dust lanes, deep-sky smudges. CPU thread. */
    static int[] bake(ThemeSpec.Stars s) {
        float[] r = new float[ATLAS_W * ATLAS_H];
        float[] g = new float[r.length];
        float[] b = new float[r.length];
        Mulberry32 rnd = new Mulberry32(4242 + s.palette().hashCode());
        int[] at = new int[3];
        // The Milky Way: a great circle tilted 60° from the pole.
        double nx = 0.0, ny = Math.cos(Math.toRadians(60)), nz = Math.sin(Math.toRadians(60));
        float mw = s.milkyWay();
        int mwColor = starColor(s.palette(), s.tint(), 0.3);
        // Glow and dust per texel (band only).
        float[] dir = new float[3];
        for (int f = 0; f < 6; f++) {
            for (int py = 0; py < FACE; py++) {
                for (int px = 0; px < FACE; px++) {
                    float a = (px + 0.5f) / FACE * 2f - 1f, bb = (py + 0.5f) / FACE * 2f - 1f;
                    faceDir(f, a, bb, dir);
                    double len = Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2]);
                    double x = dir[0] / len, y = dir[1] / len, z = dir[2] / len;
                    double band = x * nx + y * ny + z * nz;
                    if (mw <= 0f || Math.abs(band) > 0.4) {
                        continue;
                    }
                    double n = fbm(x * 6, y * 6, z * 6);
                    double glow = Math.exp(-(band / 0.16) * (band / 0.16)) * (0.55 + 0.45 * n);
                    double dust = Math.abs(band) < 0.06 ? Ease.smoothstep(0.52f, 0.62f, (float) fbm(x * 11 + 7, y * 11, z * 11)) : 0;
                    double v = glow * (1 - 0.85 * dust) * mw * 0.16;
                    int i = index(f, px, py);
                    r[i] += (float) (Colors.r(mwColor) / 255.0 * v);
                    g[i] += (float) (Colors.g(mwColor) / 255.0 * v);
                    b[i] += (float) (Colors.b(mwColor) / 255.0 * v);
                }
            }
        }
        // Stars: uniform ones, and as many again crowded toward the band.
        int count = Math.round(4000 * Math.max(0.2f, s.density()));
        int bandStars = Math.round(3200 * mw * Math.max(0.2f, s.density()));
        for (int k = 0; k < count + bandStars; k++) {
            double x, y, z;
            if (k < count) {
                double zz = rnd.next() * 2 - 1, an = rnd.next() * Math.PI * 2, h = Math.sqrt(1 - zz * zz);
                x = h * Math.cos(an);
                y = h * Math.sin(an);
                z = zz;
            } else {
                // A point on the band's circle, spread by a narrow normal.
                double an = rnd.next() * Math.PI * 2;
                double off = (rnd.next() + rnd.next() + rnd.next() - 1.5) * 0.14;
                // Two axes in the band's plane.
                double ux = 1, uy = 0, uz = 0;
                double vx = ny * uz - nz * uy, vy = nz * ux - nx * uz, vz = nx * uy - ny * ux;
                x = ux * Math.cos(an) + vx * Math.sin(an) + nx * off;
                y = uy * Math.cos(an) + vy * Math.sin(an) + ny * off;
                z = uz * Math.cos(an) + vz * Math.sin(an) + nz * off;
                double l = Math.sqrt(x * x + y * y + z * z);
                x /= l;
                y /= l;
                z /= l;
            }
            // Inverse magnitude: most are faint.
            double m = Math.pow(rnd.next(), 3.2);
            float bright = (float) (0.18 + 0.82 * m);
            int c = starColor(s.palette(), s.tint(), rnd.next());
            toFace(x, y, z, at);
            add(r, g, b, index(at[0], at[1], at[2]), c, bright);
            if (m > 0.6) {
                // A few brighter ones are 2x2.
                if (at[1] + 1 < FACE) add(r, g, b, index(at[0], at[1] + 1, at[2]), c, bright * 0.6f);
                if (at[2] + 1 < FACE) add(r, g, b, index(at[0], at[1], at[2] + 1), c, bright * 0.6f);
            }
        }
        // Deep-sky: a few soft smudges and tight clusters.
        if (s.deepSky()) {
            int smudges = 7;
            for (int k = 0; k < smudges; k++) {
                double zz = rnd.next() * 1.6 - 0.8, an = rnd.next() * Math.PI * 2, h = Math.sqrt(1 - zz * zz);
                double cx = h * Math.cos(an), cy = h * Math.sin(an), cz = zz;
                int c = k % 3 == 0 ? starColor(s.palette(), s.tint(), 0.95) : starColor(s.palette(), s.tint(), 0.1);
                double radius = 0.012 + rnd.next() * 0.02;
                boolean cluster = k % 2 == 0;
                int dots = cluster ? 40 : 260;
                for (int d = 0; d < dots; d++) {
                    double ox = (rnd.next() + rnd.next() - 1) * radius, oy = (rnd.next() + rnd.next() - 1) * radius,
                            oz = (rnd.next() + rnd.next() - 1) * radius;
                    double x = cx + ox, y = cy + oy, z = cz + oz, l = Math.sqrt(x * x + y * y + z * z);
                    toFace(x / l, y / l, z / l, at);
                    add(r, g, b, index(at[0], at[1], at[2]), c, cluster ? 0.5f + (float) rnd.next() * 0.4f : 0.05f);
                }
            }
        }
        int[] out = new int[r.length];
        for (int i = 0; i < out.length; i++) {
            float rr = Math.min(1f, r[i]), gg = Math.min(1f, g[i]), bl = Math.min(1f, b[i]);
            float a = Math.max(rr, Math.max(gg, bl));
            if (a <= 0.002f) {
                continue;
            }
            // Store as colour at full strength with the brightness in alpha (additive: src·alpha).
            out[i] = Colors.argbF(a, rr / a, gg / a, bl / a);
        }
        return out;
    }

    private static int index(int face, int px, int py) {
        int ox = (face % 3) * FACE, oy = (face / 3) * FACE;
        return (oy + py) * ATLAS_W + ox + px;
    }

    private static void add(float[] r, float[] g, float[] b, int i, int c, float v) {
        r[i] += Colors.r(c) / 255f * v;
        g[i] += Colors.g(c) / 255f * v;
        b[i] += Colors.b(c) / 255f * v;
    }

    /** Cheap 3D value noise, 4 octaves, 0..1. */
    static double fbm(double x, double y, double z) {
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
        double u = xf * xf * (3 - 2 * xf), v = yf * yf * (3 - 2 * yf), w = zf * zf * (3 - 2 * zf);
        double c000 = hash(xi, yi, zi), c100 = hash(xi + 1, yi, zi), c010 = hash(xi, yi + 1, zi), c110 = hash(xi + 1, yi + 1, zi);
        double c001 = hash(xi, yi, zi + 1), c101 = hash(xi + 1, yi, zi + 1), c011 = hash(xi, yi + 1, zi + 1), c111 = hash(xi + 1, yi + 1, zi + 1);
        double x00 = c000 + (c100 - c000) * u, x10 = c010 + (c110 - c010) * u, x01 = c001 + (c101 - c001) * u, x11 = c011 + (c111 - c011) * u;
        double y0 = x00 + (x10 - x00) * v, y1 = x01 + (x11 - x01) * v;
        return y0 + (y1 - y0) * w;
    }

    private static double hash(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (double) 0xFFFFFF;
    }

    // ------------------------------------------------------------------ bright stars

    private static void bright(ThemeSpec theme, float rot, float night, float t) {
        if (theme.stars() != brightFor || !theme.id().equals(brightKey)) {
            brightFor = theme.stars();
            brightKey = theme.id();
            int n = Math.max(theme.stars().count(), Math.round(150 * theme.stars().density()));
            if ("fog".equals(theme.stars().through())) {
                n = theme.stars().count();
            }
            Mulberry32 r = new Mulberry32(theme.id().length() * 977 + theme.id().charAt(0));
            bx = new float[n];
            by = new float[n];
            bz = new float[n];
            bs = new float[n];
            bph = new float[n];
            bc = new int[n];
            for (int i = 0; i < n; i++) {
                float z = (float) (r.next() * 2.0 - 1.0);
                float a = (float) (r.next() * Math.PI * 2.0);
                float h = (float) Math.sqrt(1f - z * z);
                bx[i] = h * (float) Math.cos(a);
                by[i] = h * (float) Math.sin(a);
                bz[i] = z;
                double m = r.next();
                bs[i] = m < 0.08 ? 3f : m < 0.35 ? 2f : 1f;
                bph[i] = (float) (r.next() * Math.PI * 2.0);
                bc[i] = starColor(theme.stars().palette(), theme.stars().tint(), r.next());
            }
        }
        float px = SkyDraw.pixel();
        float sc = Math.max(1f, FrameInfo.height / 720f);
        float cos = (float) Math.cos(rot), sin = (float) Math.sin(rot);
        boolean gaps = "cloudGaps".equals(theme.stars().through());
        WorldDraw.begin(WorldDraw.SKY_ADD, null);
        for (int i = 0; i < bx.length; i++) {
            float x = bx[i] * cos - by[i] * sin;
            float y = bx[i] * sin + by[i] * cos;
            float z = bz[i];
            if (y < -0.03f) {
                continue;
            }
            // Twinkle, stronger near the horizon.
            float low = 1f - Ease.smoothstep(0f, 0.34f, y);
            float tw = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(t * (0.8f + (i % 7) * 0.3f) + bph[i]));
            tw = 1f - (1f - tw) * (1f + low * 0.8f);
            float a = Math.max(0f, tw) * night * Ease.smoothstep(-0.03f, 0.08f, y) * MusicSky.starBoost(bs[i]);
            if (gaps) {
                a *= cloudGap(x, y, z, FrameInfo.time);
            }
            if (a <= 0.01f) {
                continue;
            }
            int c = Colors.withAlpha(bc[i], Math.min(1f, a));
            SkyDraw.billboard(x, y, z);
            float p = px * sc;
            if (bs[i] >= 2f) {
                // A pixel cross; the brightest with a faint longer flare.
                SkyDraw.rect(-p * 1.5f, -p * 0.5f, p * 1.5f, p * 0.5f, c);
                SkyDraw.rect(-p * 0.5f, -p * 1.5f, p * 0.5f, -p * 0.5f, c);
                SkyDraw.rect(-p * 0.5f, p * 0.5f, p * 0.5f, p * 1.5f, c);
                if (bs[i] >= 3f) {
                    int f = Colors.withAlpha(bc[i], Math.min(1f, a) * 0.35f);
                    SkyDraw.rect(-p * 3.5f, -p * 0.5f, -p * 1.5f, p * 0.5f, f);
                    SkyDraw.rect(p * 1.5f, -p * 0.5f, p * 3.5f, p * 0.5f, f);
                    SkyDraw.rect(-p * 0.5f, -p * 3.5f, p * 0.5f, -p * 1.5f, f);
                    SkyDraw.rect(-p * 0.5f, p * 1.5f, p * 0.5f, p * 3.5f, f);
                }
            } else {
                SkyDraw.rect(-p * 0.5f, -p * 0.5f, p * 0.5f, p * 0.5f, c);
            }
        }
        WorldDraw.end();
    }

    /** Monsoon: stars only through gaps in the clouds, drifting with the wind. */
    private static float cloudGap(float x, float y, float z, float t) {
        double n = fbm(x * 3 + t * 0.02, y * 3, z * 3 + t * 0.01);
        return Ease.smoothstep(0.55f, 0.7f, (float) n);
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
        boolean zoomed = FrameInfo.fovY < 30f;
        WorldDraw.begin(WorldDraw.SKY_ALPHA, planetAtlas(pl.style(), pl.tint()));
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
            // Fixed angular size: ~0.25° (at least 3 pixels); the zoomed sprite is sharper.
            float cell = zoomed ? 24f : 12f;
            float sizePx = Math.max(3f * sc, (0.25f / FrameInfo.fovY) * FrameInfo.height);
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
        }
        WorldDraw.end();
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
