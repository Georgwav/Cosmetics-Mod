package io.github.georgwav.cosmetics.sol.atmosphere;

import io.github.georgwav.cosmetics.sol.Feature;
import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.sol.render.WorldDraw;
import io.github.georgwav.cosmetics.sol.sky.Bodies;
import io.github.georgwav.cosmetics.sol.sky.BodyMaps;
import io.github.georgwav.cosmetics.sol.sky.BodyRender;
import io.github.georgwav.cosmetics.sol.sky.MoonLook;
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

    private static final float[][] HALO_RINGS = {{.975f, 0xFFFF9678, .14f, .022f}, {1f, 0xFFF0F0FF, .16f, .03f}, {1.03f, 0xFF96BEFF, .08f, .04f}};
    private static final float[] CORONA_AT = {0f, .16f, .27f, .38f, .5f, .62f, .76f, 1f};
    private static final int[] CORONA_PASTEL = {0xFFFFFAEB, 0xFFFFE2B4, 0xFFFFB08C, 0xFF96C8FF, 0xFFAAEBCD, 0xFFFFAAD6, 0xFF96BEFF, 0};
    private static final float[] CORONA_PASTEL_A = {.34f, .2f, .1f, .12f, .08f, .07f, .04f, 0f};
    private static final float[] CORONA_PINK_AT = {0f, .16f, .3f, .42f, .56f, .72f, 1f};
    private static final int[] CORONA_PINK = {0xFFFFECF5, 0xFFFFBED7, 0xFFE696D2, 0xFFBEAAFF, 0xFFFFB4DC, 0xFFC8AAFF, 0};
    private static final float[] CORONA_PINK_A = {.34f, .22f, .12f, .1f, .07f, .04f, 0f};
    private static SolTexture coronaPastel, coronaPink;

    /**
     * The moon as a pixel sphere showing its real near side: its seas, Tycho's rays, the phase from where it is
     * against the sun, a slow wobble, earthshine on a crescent once it's dark and two glows kept small so the sky
     * shows round it; dressed in tonight's look (the lab's {@code LOOKS}): a harvest moon big, flattened and orange as
     * it rises, an eclipse's copper shadow, a 22° halo with moon dogs and a pillar, a pastel corona, another map.
     */
    static void moon(ThemeSpec theme, Tokens tk, float t, float rain) {
        SkySphere sp = NightSky.SPHERE;
        MoonLook lk = NightSky.look;
        if (theme.moon() == null || !NightSky.hasMoon || sp.moonAlt < -6f) {
            return;
        }
        startMaps();
        float[] map = MAPS.get(lk.body());
        double[] me = sp.moonEq;
        eq[0] = me[0];
        eq[1] = me[1];
        eq[2] = me[2];
        if (!NightSky.project((float) me[0], (float) me[1], (float) me[2])) {
            return;
        }
        float px = NightSky.screen[0], py = NightSky.screen[1];
        float fp = NightSky.sh / 2f / NightSky.tanY;
        float harvest = lk.harvest() ? Ease.lerp(1.22f, 1f, Ease.smoothstep(0f, 22f, sp.moonAlt)) : 1f;
        float size = (float) Bodies.MOON.size() * lk.size() * harvest;
        float diam = 2f * fp * (float) Math.tan(Math.toRadians(size) / 2.0) / NightSky.depth;
        if (px < -diam * 6f || px > NightSky.sw + diam * 6f || py < -diam * 6f || py > NightSky.sh + diam * 6f) {
            return;
        }
        float glowK = NightSky.glowK, ill = sp.illum;
        float vis = Ease.smoothstep(-4f, 1f, sp.moonAlt) * (1f - rain * 0.9f);
        float[] tb = lk.tint();
        int tc = lk.eclipse() ? 0xFFC84632 : lk.harvest() && sp.moonAlt < 18f
                ? Colors.mix(0xFFFFAA5A, 0xFFD2DCFF, Ease.smoothstep(2f, 18f, sp.moonAlt))
                : Colors.rgb(Math.min(255, Math.round(tb[0] * 205)), Math.min(255, Math.round(tb[1] * 212)), Math.min(255, Math.round(tb[2] * 235)));
        WorldDraw.begin(WorldDraw.SKY_ADD, SkyDraw.glow());
        float r = diam * 1.9f;
        NightSky.box(px - r, py - r, px + r, py + r, Colors.withAlpha(tc,
                Math.min(0.6f, 0.26f * glowK * (float) Math.sqrt(ill)) * vis * (lk.eclipse() ? 0.5f : 1f)));
        r = diam * 0.95f;
        NightSky.box(px - r, py - r, px + r, py + r, Colors.withAlpha(tc, Math.min(0.7f, 0.35f * glowK * ill) * vis * (lk.eclipse() ? 0.3f : 1f)));
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
        Slot slot = slot(Bodies.find(lk.body()));
        if (!slot.pending && (n != slot.askedN || t - slot.askedAt > 0.1f || t < slot.askedAt)) {
            BodyRender.Light l = slot.light;
            double ps = Math.toRadians(sp.phaseAngle);
            l.lx = Math.sin(ps) * sdx;
            l.ly = Math.sin(ps) * sdy;
            l.lz = Math.cos(ps);
            l.pa = Math.atan2(-ndx, ndy);
            l.tilt = 0;
            l.spin = Math.sin(t * .07) * .08;
            double k = 0.82 + 0.28 * NightSky.MOON_GLOW, low = lk.harvest() ? Ease.smoothstep(1f, 20f, sp.moonAlt) : 1f;
            l.tr = (1.12 + (tb[0] - 1.12) * low) * k;
            l.tg = (.72 + (tb[1] - .72) * low) * k;
            l.tb = (.42 + (tb[2] - .42) * low) * k;
            // Earthshine on a crescent's dark part, which the twilight sky drowns until it's dark.
            l.earthshine = lk.earthshine() * Ease.smoothstep(80f, 150f, sp.phaseAngle) * .24 * NightSky.dark;
            l.ex = .3;
            l.ey = -.18;
            l.er = lk.eclipse() ? 2.6 : 0;
            l.glow = 1;
            l.t = t;
            ask(slot, n, map, t);
        }
        take(slot);
        if (slot.tex == null) {
            return;
        }
        // A harvest moon on the horizon is squashed flat by the air.
        float squash = lk.harvest() ? 1f - .13f * (1f - Ease.smoothstep(0f, 6f, sp.moonAlt)) : 1f;
        // The dark sky behind it hides the stars by night. In twilight a bright moon still stands solid against the
        // sky (a harvest moon rising orange), while a thin crescent's unlit part is lost in it.
        float solid = Math.max(NightSky.dark, Math.min(1f, ill * 1.2f));
        if (solid > 0.01f) {
            WorldDraw.begin(WorldDraw.SKY_ALPHA, SkyDraw.disc());
            float rx = diam / 2f * 0.97f, ry = rx * squash;
            int sky = Colors.scale(SkyPass.skyAt(tk, sp.moonAlt, rain), 0.55f);
            NightSky.box(px - rx, py - ry, px + rx, py + ry, Colors.withAlpha(sky, vis * solid));
            WorldDraw.end();
        }
        float side = diam * slot.s / slot.n, half = side / 2f;
        float x0 = Math.round(px - half), y0 = Math.round(py - half * squash);
        float uv = (float) slot.s / slot.texSize;
        WorldDraw.begin(WorldDraw.SKY_ADD, slot.tex);
        NightSky.box(x0, y0, x0 + side, y0 + side * squash, 0f, 0f, uv, uv, Colors.withAlpha(0xFFFFFFFF, vis));
        WorldDraw.end();
        if (lk.halo()) {
            halo(px, py, diam, tc, glowK, vis);
        }
        if (!"none".equals(lk.corona())) {
            corona("pink".equals(lk.corona()), px, py, diam, vis, t);
        }
    }

    /** A frosty night: a 22° ring in three colours, a moon dog either side, and a pillar when the moon is low. */
    private static void halo(float px, float py, float diam, int tc, float glowK, float vis) {
        SkySphere sp = NightSky.SPHERE;
        float[] m = sp.moon;
        // A point 22° straight above the moon, for the ring's size on the screen.
        float ux = -m[0] * m[1], uy = 1f - m[1] * m[1], uz = -m[2] * m[1], ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (ul < 1e-4f) {
            return;
        }
        float c22 = (float) Math.cos(Math.toRadians(22)), s22 = (float) Math.sin(Math.toRadians(22));
        float rr = worldToScreenDistance(m[0] * c22 + ux / ul * s22, m[1] * c22 + uy / ul * s22, m[2] * c22 + uz / ul * s22, px, py);
        if (rr <= 0f) {
            return;
        }
        WorldDraw.begin(WorldDraw.SKY_ADD, SkyDraw.ring());
        for (float[] ring : HALO_RINGS) {
            float h = rr * ring[0] / 0.8f;
            NightSky.box(px - h, py - h, px + h, py + h, Colors.withAlpha((int) ring[1], ring[2] * vis));
        }
        WorldDraw.end();
        // The moon dogs, 22° either side at the moon's height.
        double sh = Math.sin(Math.toRadians(sp.moonAlt)), ch2 = Math.pow(Math.cos(Math.toRadians(sp.moonAlt)), 2);
        double dA = Math.acos(Math.max(-1, Math.min(1, (c22 - sh * sh) / Math.max(.05, ch2))));
        double az = Math.atan2(m[0], m[2]);
        WorldDraw.begin(WorldDraw.SKY_ADD, SkyDraw.glow());
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            double a = az + sgn * dA, ca = Math.cos(Math.toRadians(sp.moonAlt));
            float dx = (float) (Math.sin(a) * ca), dy = (float) sh, dz = (float) (Math.cos(a) * ca);
            if (!projectWorld(dx, dy, dz)) {
                continue;
            }
            float qx = NightSky.screen[0], qy = NightSky.screen[1], r2 = diam * .55f;
            NightSky.box(qx - r2, qy - r2 * .8f, qx + r2, qy + r2 * .8f, Colors.withAlpha(0xFFFFDCC8, .4f * vis));
            float cx = qx + sgn * r2 * .3f;
            NightSky.box(cx - r2 * .6f, qy - r2 * .6f, cx + r2 * .6f, qy + r2 * .6f, Colors.withAlpha(0xFFAAC8FF, .25f * vis));
        }
        // A pillar of light over a low moon.
        if (sp.moonAlt < 10f && sp.moonAlt > -2f) {
            float a = .11f * (1f - sp.moonAlt / 10f) * Math.min(1.3f, glowK) * vis;
            NightSky.box(px - diam * .18f, py - diam * 2.6f, px + diam * .18f, py + diam * 1.4f, Colors.withAlpha(tc, a));
        }
        WorldDraw.end();
    }

    /** Thin cloud passing over the moon: pastel rings round it. */
    private static void corona(boolean pink, float px, float py, float diam, float vis, float t) {
        SolTexture tex = coronaTexture(pink);
        float rc = diam * 3f;
        WorldDraw.begin(WorldDraw.SKY_ADD, tex);
        NightSky.box(px - rc, py - rc, px + rc, py + rc, Colors.withAlpha(0xFFFFFFFF, vis));
        WorldDraw.end();
        // The cloud drifting across.
        WorldDraw.begin(WorldDraw.SKY_ALPHA, SkyDraw.glow());
        for (int i = 0; i < 7; i++) {
            float ox = (float) (((t * 4 + i * 90) % 600 + 600) % 600 - 300) * diam / 60f;
            float oy = (float) Math.sin(i * 2.3) * diam * 1.4f, r2 = diam * (1.4f + (i % 3) * .5f);
            NightSky.box(px + ox - r2, py + oy - r2 * .5f, px + ox + r2, py + oy + r2 * .5f, Colors.withAlpha(0xFF96A0BE, .1f * vis));
        }
        WorldDraw.end();
    }

    /** The corona's rings as a picture: the lab's radial gradient from inside the moon's edge outward. */
    private static SolTexture coronaTexture(boolean pink) {
        SolTexture have = pink ? coronaPink : coronaPastel;
        if (have != null && !have.released()) {
            return have;
        }
        float[] at = pink ? CORONA_PINK_AT : CORONA_AT;
        int[] cols = pink ? CORONA_PINK : CORONA_PASTEL;
        float[] as = pink ? CORONA_PINK_A : CORONA_PASTEL_A;
        int n = 128;
        int[] px = new int[n * n];
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                float dx = (i + .5f) / n * 2f - 1f, dy = (j + .5f) / n * 2f - 1f;
                // From .15 of the radius (inside the moon, which is a third of it) to its edge.
                float d = ((float) Math.sqrt(dx * dx + dy * dy) - .15f) / .85f;
                if (d < 0f || d >= 1f) {
                    continue;
                }
                int k = 0;
                while (k + 2 < at.length && d >= at[k + 1]) {
                    k++;
                }
                float f = (d - at[k]) / (at[k + 1] - at[k]);
                int c = cols[k + 1] == 0 ? cols[k] : Colors.mix(cols[k], cols[k + 1], f);
                float a = as[k] + (as[k + 1] - as[k]) * f;
                px[j * n + i] = Colors.withAlpha(c, a);
            }
        }
        SolTexture tex = SolTexture.of(pink ? "moon_corona_pink" : "moon_corona", n, n, true, px);
        if (pink) {
            coronaPink = tex;
        } else {
            coronaPastel = tex;
        }
        return tex;
    }

    /** Projects a world direction; the screen point into {@link NightSky#screen}. */
    private static boolean projectWorld(float wx, float wy, float wz) {
        // World to equatorial: the sphere's matrix is a rotation, so its transpose.
        float[] m = NightSky.SPHERE.m;
        return NightSky.project(m[0] * wx + m[3] * wy + m[6] * wz, m[1] * wx + m[4] * wy + m[7] * wz, m[2] * wx + m[5] * wy + m[8] * wz);
    }

    /** How far on the screen a world direction is from (px, py), or 0 if it's behind. */
    private static float worldToScreenDistance(float wx, float wy, float wz, float px, float py) {
        if (!projectWorld(wx, wy, wz)) {
            return 0f;
        }
        float dx = NightSky.screen[0] - px, dy = NightSky.screen[1] - py;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
