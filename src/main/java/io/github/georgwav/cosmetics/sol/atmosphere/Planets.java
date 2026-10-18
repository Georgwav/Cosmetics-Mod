package io.github.georgwav.cosmetics.sol.atmosphere;

import io.github.georgwav.cosmetics.sol.Feature;
import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.sol.render.WorldDraw;
import io.github.georgwav.cosmetics.sol.sky.Bodies;
import io.github.georgwav.cosmetics.sol.sky.BodyMaps;
import io.github.georgwav.cosmetics.sol.sky.BodyRender;
import io.github.georgwav.cosmetics.sol.sky.SkySphere;
import io.github.georgwav.cosmetics.sol.theme.ThemeSpec;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.sol.util.Ease;
import io.github.georgwav.cosmetics.sol.util.SolExecutors;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The planets, the wild worlds and the moon of the Night Sky lab, as little 3D balls in pixels: each is ray-traced
 * on Sol's CPU thread into a small picture the size it is on the screen ({@link BodyRender}), about ten times a
 * second while it turns, lit from the sun's side at its real phase, and drawn added over a disc of darker sky (so
 * the stars behind it are hidden), with a soft glow round it. Every planet is up every night at the same hours, set
 * from the sun on the ecliptic; the wild worlds from the point opposite the sun. Render thread only, apart from the
 * pictures' making.
 */
final class Planets {
    /** The lab's default moon glow (45%). */
    private static final float MOON_GLOW = 0.45f;
    private static final double[][] JUPITER_MOONS = {{5.9, 2.1, .9}, {9.4, 1.2, 2.4}, {15, .6, 4.1}, {26, .26, 5.5}};

    private static final Map<String, float[]> MAPS = new ConcurrentHashMap<>();
    private static volatile boolean mapsStarted;
    private static final Map<String, Slot> SLOTS = new HashMap<>();

    /** A body's picture: what was asked for, what's ready, and the texture it's drawn from. */
    private static final class Slot {
        final Bodies.Body body;
        final BodyRender.Light light = new BodyRender.Light();
        final AtomicReference<Picture> ready = new AtomicReference<>();
        volatile boolean pending;
        SolTexture tex;
        int texSize, n, s;
        int askedN = -1;
        float askedAt = -99f;

        Slot(Bodies.Body body) {
            this.body = body;
        }
    }

    private record Picture(int n, int s, int[] px) {
    }

    // This frame's bodies, drawn in four passes.
    private static final int MAX = 24;
    private static final Slot[] seen = new Slot[MAX];
    private static final float[] seenX = new float[MAX], seenY = new float[MAX], seenD = new float[MAX], seenA = new float[MAX];
    private static final float[] seenNx = new float[MAX], seenNy = new float[MAX];
    private static final int[] seenSky = new int[MAX];
    private static int seenCount;

    private static final double[] eq = new double[3], sunEq = new double[3], POLE = {0, 0, 1};
    private static final float[] dir = new float[2];

    private Planets() {
    }

    /** Makes every surface map once, on the CPU thread. */
    private static void startMaps() {
        if (mapsStarted) {
            return;
        }
        mapsStarted = true;
        SolExecutors.cpu(Feature.SKY, () -> {
            for (String id : new String[]{"moon", "jupiter", "saturn", "venus", "mars", "mercury", "uranus", "neptune", "polyphemus",
                    "deathstar", "shattered", "coruscant", "mustafar", "tatooine", "hoth", "endor", "kamino", "arrakis", "carian", "majora"}) {
                MAPS.put(id, BodyMaps.make(id));
            }
        });
    }

    private static Slot slot(Bodies.Body b) {
        return SLOTS.computeIfAbsent(b.id(), k -> new Slot(b));
    }

    private static boolean needsMap(Bodies.Body b) {
        return b.kind() != Bodies.Kind.BLACK_HOLE && b.kind() != Bodies.Kind.RING_WORLD;
    }

    // ------------------------------------------------------------------ planets

    /** Draws the planets and the wild worlds (sky pass, after the stars). */
    static void render(Tokens tk, Atmosphere at, float t, float rain) {
        boolean planets = at == null || at.planets.get(), wild = at == null || at.wildWorlds.get();
        float clear = 1f - rain;
        seenCount = 0;
        if (!planets && !wild || clear <= 0.01f) {
            return;
        }
        startMaps();
        SkySphere sp = NightSky.SPHERE;
        double raS = Math.toRadians(sp.lamSun);
        sunEq[0] = Math.cos(raS);
        sunEq[1] = Math.sin(raS);
        sunEq[2] = 0;
        float fp = NightSky.sh / 2f / NightSky.tanY;
        boolean names = at == null || at.names.get();
        for (Bodies.Body b : Bodies.PLANETS) {
            if (seenCount >= MAX || (b.wild() != null ? !wild : !planets)) {
                continue;
            }
            Bodies.place(b, sp.lamSun, eq);
            if (!NightSky.project((float) eq[0], (float) eq[1], (float) eq[2])) {
                continue;
            }
            float alt = (float) Math.toDegrees(Math.asin(Math.max(-1f, Math.min(1f, NightSky.world[1]))));
            if (alt < -1.5f) {
                continue;
            }
            float a = Ease.smoothstep((float) b.vis(), (float) b.vis() - 4f, sp.sunAlt) * Ease.smoothstep(-1.5f, 3f, alt) * clear;
            if (a <= 0.004f) {
                continue;
            }
            float px = NightSky.screen[0], py = NightSky.screen[1];
            float diam = 2f * fp * (float) Math.tan(Math.toRadians(b.size()) / 2.0) / NightSky.depth;
            if (px < -diam * 2f || px > NightSky.sw + diam * 2f || py < -diam * 2f || py > NightSky.sh + diam * 2f) {
                continue;
            }
            int n = Math.max(needsMap(b) ? 4 : 28, Math.min(128, Math.round(diam / (NightSky.pixel * 0.9f))));
            float[] map = needsMap(b) ? MAPS.get(b.id()) : null;
            if (needsMap(b) && map == null) {
                continue;
            }
            // Which way is the sun on the screen from here, and which way is north.
            screenDir(sunEq, dir);
            float sdx = dir[0], sdy = dir[1];
            screenDir(POLE, dir);
            float ndx = dir[0], ndy = dir[1];
            Slot slot = slot(b);
            if (!slot.pending && (n != slot.askedN || t - slot.askedAt > 0.1f || t < slot.askedAt)) {
                BodyRender.Light l = slot.light;
                double ps = Math.toRadians(b.phase());
                l.lx = Math.sin(ps) * sdx;
                l.ly = Math.sin(ps) * sdy;
                l.lz = Math.cos(ps);
                l.pa = Math.atan2(-ndx, ndy) + Math.toRadians(b.turn());
                l.tilt = Math.toRadians(b.tilt());
                l.spin = t * b.spin();
                horizonTint(alt, l);
                l.earthshine = 0;
                l.er = 0;
                l.t = t;
                l.glow = b.emPulse() ? .2 + .8 * Math.pow(Math.max(0, Math.sin(t * .9)), 6) : 1;
                ask(slot, n, map, t);
            }
            take(slot);
            if (slot.tex == null) {
                continue;
            }
            int k = seenCount++;
            seen[k] = slot;
            seenX[k] = px;
            seenY[k] = py;
            seenD[k] = diam;
            seenA[k] = a;
            seenNx[k] = ndx;
            seenNy[k] = ndy;
            seenSky[k] = Colors.scale(SkyPass.skyAt(tk, alt, rain), 0.55f);
            if (names && alt > 2f) {
                SkyLabels.add(px + diam * (float) b.box() / 2f, py, b.name(), SkyLabels.PLANET, a);
            }
        }
        draw(t);
    }

    private static void draw(float t) {
        if (seenCount == 0) {
            return;
        }
        // A soft glow round each.
        WorldDraw.begin(WorldDraw.SKY_ADD, SkyDraw.glow());
        for (int i = 0; i < seenCount; i++) {
            Bodies.Body b = seen[i].body;
            float r = seenD[i] * (1.6f + (float) b.glow());
            int c = "venus".equals(b.id()) ? 0xFFFFF6DC : 0xFFE6E8FF;
            NightSky.box(seenX[i] - r, seenY[i] - r, seenX[i] + r, seenY[i] + r, Colors.withAlpha(c, 0.3f * seenA[i] * (float) b.glow()));
        }
        WorldDraw.end();
        // The sky behind each, darker, so the stars behind it don't show through.
        WorldDraw.begin(WorldDraw.SKY_ALPHA, SkyDraw.disc());
        for (int i = 0; i < seenCount; i++) {
            Bodies.Body b = seen[i].body;
            if (b.kind() == Bodies.Kind.RING_WORLD) {
                continue;
            }
            boolean hole = b.kind() == Bodies.Kind.BLACK_HOLE;
            float r = seenD[i] / 2f * (hole ? 0.27f : 0.96f);
            // Only once it's dark: in twilight the sky is in front of a world's dark side, so nothing is hidden.
            NightSky.box(seenX[i] - r, seenY[i] - r, seenX[i] + r, seenY[i] + r,
                    Colors.withAlpha(hole ? 0xFF000000 : seenSky[i], seenA[i] * (hole ? 1f : NightSky.dark)));
        }
        WorldDraw.end();
        for (int i = 0; i < seenCount; i++) {
            sprite(seen[i], seenX[i], seenY[i], seenD[i], seenA[i]);
        }
        // Jupiter's four moons in a line, moving night to night.
        WorldDraw.begin(WorldDraw.SKY_ALPHA, SkyDraw.disc());
        for (int i = 0; i < seenCount; i++) {
            if (!seen[i].body.moons() || seenD[i] <= 8f) {
                continue;
            }
            float ex = seenNy[i], ey = -seenNx[i];
            for (double[] mo : JUPITER_MOONS) {
                double ph = t * mo[1] * .15 + mo[2];
                float off = (float) (mo[0] * seenD[i] / 2 * Math.sin(ph));
                if (Math.abs(off) < seenD[i] / 2f && Math.cos(ph) < 0) {
                    continue;
                }
                float m = Math.max(NightSky.pixel, seenD[i] / 16f);
                float cx = seenX[i] + ex * off, cy = seenY[i] - ey * off;
                NightSky.box(cx - m / 2f, cy - m / 2f, cx + m / 2f, cy + m / 2f, Colors.withAlpha(0xFFECE6DA, seenA[i]));
            }
        }
        WorldDraw.end();
    }

    /** A body's picture, added, its pixels on whole screen pixels. */
    private static void sprite(Slot slot, float x, float y, float diam, float a) {
        float size = diam * slot.s / slot.n, half = size / 2f;
        float x0 = Math.round(x - half), y0 = Math.round(y - half);
        float uv = (float) slot.s / slot.texSize;
        WorldDraw.begin(WorldDraw.SKY_ADD, slot.tex);
        NightSky.box(x0, y0, x0 + size, y0 + size, 0f, 0f, uv, uv, Colors.withAlpha(0xFFFFFFFF, a));
        WorldDraw.end();
    }

    /** Low down, a body is reddened like the stars. */
    private static void horizonTint(float alt, BodyRender.Light l) {
        double k = 1 - Ease.smoothstep(1f, 18f, alt);
        l.tr = 1;
        l.tg = 1 + (.72 - 1) * k;
        l.tb = 1 + (.5 - 1) * k;
    }

    /** The screen direction (x right, y up) from the last projected point toward an equatorial direction. */
    private static void screenDir(double[] target, float[] out) {
        double vx = eq[0], vy = eq[1], vz = eq[2];
        double k = vx * target[0] + vy * target[1] + vz * target[2];
        double tx = target[0] - vx * k, ty = target[1] - vy * k, tz = target[2] - vz * k;
        double n = Math.sqrt(tx * tx + ty * ty + tz * tz);
        out[0] = 0f;
        out[1] = 1f;
        if (n < 1e-5) {
            return;
        }
        if (!NightSky.project((float) vx, (float) vy, (float) vz)) {
            return;
        }
        float x1 = NightSky.screen[0], y1 = NightSky.screen[1];
        double sx = vx + tx / n * .02, sy = vy + ty / n * .02, sz = vz + tz / n * .02, sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (!NightSky.project((float) (sx / sl), (float) (sy / sl), (float) (sz / sl))) {
            return;
        }
        float dx = NightSky.screen[0] - x1, dy = y1 - NightSky.screen[1], m = (float) Math.sqrt(dx * dx + dy * dy);
        if (m > 1e-6f) {
            out[0] = dx / m;
            out[1] = dy / m;
        }
    }

    private static void ask(Slot slot, int n, float[] map, float t) {
        slot.pending = true;
        slot.askedN = n;
        slot.askedAt = t;
        Bodies.Body b = slot.body;
        BodyRender.Light l = slot.light;
        SolExecutors.cpu(Feature.SKY, () -> {
            int s = BodyRender.size(b, n);
            int[] px = new int[s * s];
            BodyRender.render(b, map, n, l, px);
            slot.ready.set(new Picture(n, s, px));
        });
    }

    /** Uploads a body's new picture if one is ready. */
    private static void take(Slot slot) {
        Picture p = slot.ready.getAndSet(null);
        if (p == null) {
            if (slot.tex != null && slot.tex.released()) {
                slot.tex = null;
                slot.askedN = -1;
            }
            return;
        }
        int need = 8;
        while (need < p.s()) {
            need *= 2;
        }
        if (slot.tex == null || slot.tex.released() || slot.texSize < need) {
            if (slot.tex != null) {
                slot.tex.release();
            }
            slot.tex = new SolTexture("sky_body_" + slot.body.id(), need, need, false);
            slot.texSize = need;
        }
        slot.tex.upload(p.px(), p.s(), p.s());
        slot.n = p.n();
        slot.s = p.s();
        slot.pending = false;
    }

    // ------------------------------------------------------------------ the moon

    /**
     * The moon as a pixel sphere showing its real near side: its seas, Tycho's rays, the phase from where it is
     * against the sun, a slow wobble, earthshine on a thin crescent, and two glows kept small so the sky shows round it.
     */
    static void moon(ThemeSpec theme, Tokens tk, float t, float rain) {
        SkySphere sp = NightSky.SPHERE;
        boolean hasMoon = sp.elong > 8f && sp.elong < 352f;
        if (theme.moon() == null || !hasMoon || sp.moonAlt < -6f) {
            return;
        }
        startMaps();
        float[] map = MAPS.get("moon");
        double[] me = sp.moonEq;
        eq[0] = me[0];
        eq[1] = me[1];
        eq[2] = me[2];
        if (!NightSky.project((float) me[0], (float) me[1], (float) me[2])) {
            return;
        }
        float px = NightSky.screen[0], py = NightSky.screen[1];
        float fp = NightSky.sh / 2f / NightSky.tanY;
        float diam = 2f * fp * (float) Math.tan(Math.toRadians(Bodies.MOON.size()) / 2.0) / NightSky.depth;
        if (px < -diam * 6f || px > NightSky.sw + diam * 6f || py < -diam * 6f || py > NightSky.sh + diam * 6f) {
            return;
        }
        float glowK = 0.35f + 1.3f * MOON_GLOW, ill = sp.illum;
        float vis = Ease.smoothstep(-4f, 1f, sp.moonAlt) * (1f - rain * 0.9f);
        int tc = 0xFFC7D0F0;
        WorldDraw.begin(WorldDraw.SKY_ADD, SkyDraw.glow());
        float r = diam * 1.9f;
        NightSky.box(px - r, py - r, px + r, py + r, Colors.withAlpha(tc, Math.min(0.6f, 0.26f * glowK * (float) Math.sqrt(ill)) * vis));
        r = diam * 0.95f;
        NightSky.box(px - r, py - r, px + r, py + r, Colors.withAlpha(tc, Math.min(0.7f, 0.35f * glowK * ill) * vis));
        WorldDraw.end();
        if (map == null) {
            return;
        }
        sunEq[0] = Math.cos(Math.toRadians(sp.lamSun));
        sunEq[1] = Math.sin(Math.toRadians(sp.lamSun));
        sunEq[2] = 0;
        screenDir(sunEq, dir);
        float sdx = dir[0], sdy = dir[1];
        screenDir(POLE, dir);
        float ndx = dir[0], ndy = dir[1];
        int n = Math.max(10, Math.min(160, Math.round(diam / (NightSky.pixel * 1.1f))));
        Slot slot = slot(Bodies.MOON);
        if (!slot.pending && (n != slot.askedN || t - slot.askedAt > 0.1f || t < slot.askedAt)) {
            BodyRender.Light l = slot.light;
            double ps = Math.toRadians(sp.phaseAngle);
            l.lx = Math.sin(ps) * sdx;
            l.ly = Math.sin(ps) * sdy;
            l.lz = Math.cos(ps);
            l.pa = Math.atan2(-ndx, ndy);
            l.tilt = 0;
            l.spin = Math.sin(t * .07) * .08;
            double k = 0.82 + 0.28 * MOON_GLOW;
            l.tr = .97 * k;
            l.tg = .98 * k;
            l.tb = 1.02 * k;
            // Earthshine on a crescent's dark part, which the twilight sky drowns until it's dark.
            l.earthshine = .45 * Ease.smoothstep(80f, 150f, sp.phaseAngle) * .24 * NightSky.dark;
            l.er = 0;
            l.glow = 1;
            l.t = t;
            ask(slot, n, map, t);
        }
        take(slot);
        if (slot.tex == null) {
            return;
        }
        // The dark sky behind it hides the stars by night; by day and in twilight the sky stays in front of the
        // unlit part, so it isn't there.
        if (NightSky.dark > 0.01f) {
            WorldDraw.begin(WorldDraw.SKY_ALPHA, SkyDraw.disc());
            r = diam / 2f * 0.97f;
            int sky = Colors.scale(SkyPass.skyAt(tk, sp.moonAlt, rain), 0.55f);
            NightSky.box(px - r, py - r, px + r, py + r, Colors.withAlpha(sky, vis * NightSky.dark));
            WorldDraw.end();
        }
        sprite(slot, px, py, diam, vis);
    }
}
