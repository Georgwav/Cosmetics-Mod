package io.github.georgwav.cosmetics.sol.atmosphere;

import io.github.georgwav.cosmetics.sol.render.FrameInfo;
import io.github.georgwav.cosmetics.sol.render.WorldDraw;
import io.github.georgwav.cosmetics.sol.sky.SkySphere;
import io.github.georgwav.cosmetics.sol.theme.ThemeSpec;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.sol.util.Ease;
import io.github.georgwav.cosmetics.sol.util.Mulberry32;

/**
 * What happens in the night sky (the Night Sky lab's events). Meteors all night, more on a shower night, when most
 * stream from the season's radiant; every half minute or so a burst of them; now and then a fireball that flashes the
 * sky and leaves a glowing green train drifting in the wind; rarely an earthgrazer skimming slowly along the
 * horizon. A comet with two tails in the evening sky, a straight blue ion tail and a curved golden dust tail, both
 * away from the sun. Satellites crawling across, one now and then flaring. Ships jumping to hyperspace in streaks,
 * and when the Death Star is up, its superlaser firing. Drawn in screen pixels on the sky's pixel grid, in two
 * batches. Render thread only; nothing allocated per frame.
 */
public final class SkyEvents {
    /** The seasons' meteor showers: right ascension (hours) and declination of each radiant (spring first). */
    private static final double[][] RADIANTS = {{18.07, 33.6}, {3.2, 58}, {6.33, 15.9}, {7.47, 32.5}};
    private static final int METEORS = 48, TRAINS = 6, HYPERS = 24, SATS = 3, TRAIN_POINTS = 13;

    private static Mulberry32 rnd = new Mulberry32(20251018);
    private static float lastTime = -1f;
    /** The scenario's frozen moment: which scene to set up in front of the camera (null for none), and time held. */
    private static String staging;
    private static int stageFrames;
    private static boolean held;

    // Meteors: start and end directions (world), age, duration, brightness, colour, kind.
    private static final float[] mx0 = new float[METEORS], my0 = new float[METEORS], mz0 = new float[METEORS];
    private static final float[] mx1 = new float[METEORS], my1 = new float[METEORS], mz1 = new float[METEORS];
    private static final float[] mAge = new float[METEORS], mDur = new float[METEORS], mB = new float[METEORS];
    private static final int[] mCol = new int[METEORS];
    private static final boolean[] mOn = new boolean[METEORS], mFire = new boolean[METEORS], mGraze = new boolean[METEORS];
    private static float nextMeteor = 1f, nextBurst = 6f;
    private static int burst;

    // Fireball trains: points along the path, age; and the fireball's flash.
    private static final float[][] train = new float[TRAINS][TRAIN_POINTS * 3];
    private static final float[] trainAge = new float[TRAINS];
    private static final boolean[] trainOn = new boolean[TRAINS];
    private static float fireFlash;

    // Ships jumping: start and end directions, age (negative while waiting their turn).
    private static final float[] hx0 = new float[HYPERS], hy0 = new float[HYPERS], hz0 = new float[HYPERS];
    private static final float[] hx1 = new float[HYPERS], hy1 = new float[HYPERS], hz1 = new float[HYPERS];
    private static final float[] hAge = new float[HYPERS];
    private static final boolean[] hOn = new boolean[HYPERS];
    private static float nextJump = 5f;

    // Satellites: a start direction and the way along their great circle, how far round, how fast, a flare.
    private static final float[] sx = new float[SATS], sy = new float[SATS], sz = new float[SATS];
    private static final float[] stx = new float[SATS], sty = new float[SATS], stz = new float[SATS];
    private static final float[] sU = new float[SATS], sW = new float[SATS], sFlare = new float[SATS];
    private static boolean satsMade;

    // The superlaser: age (-1 off), angle on the screen, time to the next shot.
    private static float laserAge = -1f, laserAngle, nextLaser = 7f;

    private static final float[] a = new float[3], b = new float[3], tmp = new float[3], rad = new float[3];
    private static final double[] eq = new double[3];
    private static final float[] w = new float[3];

    private SkyEvents() {
    }

    /** 0..1: how much a fireball is lighting the world this frame. */
    public static float fireball() {
        return fireFlash;
    }

    /** Draws the events (sky pass, after the planets and the moon). */
    static void render(ThemeSpec theme, Atmosphere at, float t, float rain) {
        float dt = lastTime < 0f ? 0f : Math.max(0f, Math.min(0.1f, t - lastTime));
        lastTime = t;
        float clear = 1f - rain;
        float dark = NightSky.dark * clear;
        boolean meteors = at == null || at.meteors.get(), showers = at == null || at.showers.get();
        boolean comet = (at == null || at.comet.get()) && theme.stars().comets();
        boolean ships = at == null || at.ships.get(), names = at == null || at.names.get();
        float p = NightSky.pixel, fp = NightSky.sh / 2f / NightSky.tanY;
        if (staging != null && ++stageFrames >= 5) {
            // A few frames in, once the view has turned to the shot.
            setUp(staging, fp);
            staging = null;
        }
        if (held) {
            dt = 0f;
        }
        step(theme, meteors, showers, dark, dt, fp);
        stepShips(ships, dark, dt, fp);
        fireFlash = Math.max(0f, fireFlash - dt * 1.6f);

        // Lines and the flash, untextured.
        WorldDraw.begin(WorldDraw.SKY_ADD, null);
        if (fireFlash > 0.004f) {
            NightSky.box(0f, 0f, NightSky.sw, NightSky.sh, Colors.withAlpha(0xFFFFE8C8, 0.16f * fireFlash * clear));
        }
        trains(p, clear);
        meteorLines(p, clear);
        if (comet) {
            cometTails(p, fp, clear);
        }
        if (ships && NightSky.dark > 0.4f) {
            satellites(p, clear, false);
            hyperLines(p, clear);
        }
        laser(at, dt, p, false);
        WorldDraw.end();

        // Glows.
        WorldDraw.begin(WorldDraw.SKY_ADD, SkyDraw.glow());
        meteorGlows(p, clear);
        if (comet) {
            cometHead(p, clear, names);
        }
        if (ships && NightSky.dark > 0.4f) {
            satellites(p, clear, true);
            hyperGlows(p, clear);
        }
        laser(at, 0f, p, true);
        WorldDraw.end();
    }

    // ------------------------------------------------------------------ meteors

    private static void step(ThemeSpec theme, boolean on, boolean showers, float dark, float dt, float fp) {
        for (int i = 0; i < METEORS; i++) {
            if (!mOn[i]) {
                continue;
            }
            mAge[i] += dt;
            if (mAge[i] >= mDur[i]) {
                mOn[i] = false;
                if (mFire[i]) {
                    newTrain(i);
                }
            }
        }
        for (int k = 0; k < TRAINS; k++) {
            if (trainOn[k] && (trainAge[k] += dt) >= 4.5f) {
                trainOn[k] = false;
            }
        }
        float rate = on ? (shower(theme, showers) ? 1.4f : 0.45f) * dark : 0f;
        if (rate <= 0f || dt <= 0f) {
            return;
        }
        radiant(rad);
        nextMeteor -= dt;
        if (nextMeteor <= 0f) {
            nextMeteor = (float) (-Math.log(1 - rnd.next()) / rate);
            spawn(shower(theme, showers) && rnd.next() < 0.7 ? rad : null, false, fp);
        }
        nextBurst -= dt;
        if (nextBurst <= 0f) {
            nextBurst = 20f + (float) rnd.next() * 25f;
            burst = 10 + (int) (rnd.next() * 8);
        }
        if (burst > 0 && rnd.next() < dt * 8) {
            burst--;
            spawn(rad, true, fp);
        }
    }

    /** A shower night: one night in the theme's {@code meteorNights}, or every night on a theme of shooting stars. */
    private static boolean shower(ThemeSpec theme, boolean showers) {
        if (!showers) {
            return false;
        }
        int every = theme.stars().meteorNights();
        return theme.stars().shooting() || every > 0 && Math.floorMod(NightSky.SPHERE.night, every) == 0;
    }

    /** The radiant of the season's shower (by the sun's place on the ecliptic), as a world direction. */
    private static void radiant(float[] out) {
        SkySphere sp = NightSky.SPHERE;
        int season = Math.floorMod((int) Math.round(sp.lamSun / 90.0), 4);
        double ra = Math.toRadians(RADIANTS[season][0] * 15), dec = Math.toRadians(RADIANTS[season][1]);
        sp.toWorld(Math.cos(dec) * Math.cos(ra), Math.cos(dec) * Math.sin(ra), Math.sin(dec), out);
    }

    /** A new meteor somewhere in view (the lab's spawn): from the radiant if given, sometimes an earthgrazer. */
    private static void spawn(float[] from, boolean isBurst, float fp) {
        if (from == null && rnd.next() < 0.06) {
            grazer();
        } else {
            streak(from, isBurst, fp);
        }
    }

    /** An earthgrazer: low, slow and long, skimming along the horizon near where you look. Its slot, or -1. */
    private static int grazer() {
        int i = free(mOn);
        if (i < 0) {
            return -1;
        }
        Camera cam = camera();
        float yaw = cam.yaw + (float) (rnd.next() - 0.5) * 70f, h0 = 6f + (float) rnd.next() * 8f, sg = rnd.next() < 0.5 ? -1f : 1f;
        altAz(h0, yaw, a);
        altAz(h0 + 4f + (float) rnd.next() * 6f, yaw + sg * 55f, b);
        meteor(i, a, b, 3f + (float) rnd.next() * 1.4f, 0.85f, 0xFFFFBE78, false, true);
        return i;
    }

    /** A meteor through a random point in the upper part of the view, from {@code from} if given. Its slot, or -1. */
    private static int streak(float[] from, boolean isBurst, float fp) {
        int i = free(mOn);
        if (i < 0) {
            return -1;
        }
        Camera cam = camera();
        float x = (0.05f + 0.9f * (float) rnd.next()) * NightSky.sw, y = (0.05f + 0.55f * (float) rnd.next()) * NightSky.sh;
        cam.ray(x, y, fp, tmp);
        if (tmp[1] < 0.12f) {
            return -1;
        }
        float tx, ty, tz;
        if (from != null) {
            tx = tmp[0] - from[0];
            ty = tmp[1] - from[1];
            tz = tmp[2] - from[2];
        } else {
            tx = (float) rnd.next() - 0.5f;
            ty = -(0.2f + (float) rnd.next() * 0.6f);
            tz = (float) rnd.next() - 0.5f;
        }
        float k = tx * tmp[0] + ty * tmp[1] + tz * tmp[2];
        tx -= tmp[0] * k;
        ty -= tmp[1] * k;
        tz -= tmp[2] * k;
        float n = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
        if (n < 1e-5f) {
            return -1;
        }
        float len = (float) Math.tan(Math.toRadians(isBurst ? 4 + rnd.next() * 8 : 6 + rnd.next() * 14)) / n;
        b[0] = tmp[0] + tx * len;
        b[1] = tmp[1] + ty * len;
        b[2] = tmp[2] + tz * len;
        norm(b);
        boolean fire = !isBurst && rnd.next() < 0.1;
        double c = rnd.next();
        int col = c < 0.3 ? 0xFFBEFFC8 : rnd.next() < 0.5 ? 0xFFFFD6AA : 0xFFE8EEFF;
        meteor(i, tmp, b, 0.35f + (float) rnd.next() * 0.55f + (fire ? 0.4f : 0f), fire ? 1f : 0.55f + (float) rnd.next() * 0.4f,
                col, fire, false);
        return i;
    }

    private static void meteor(int i, float[] d0, float[] d1, float dur, float bright, int col, boolean fire, boolean graze) {
        mx0[i] = d0[0];
        my0[i] = d0[1];
        mz0[i] = d0[2];
        mx1[i] = d1[0];
        my1[i] = d1[1];
        mz1[i] = d1[2];
        mAge[i] = 0f;
        mDur[i] = dur;
        mB[i] = bright;
        mCol[i] = col;
        mFire[i] = fire;
        mGraze[i] = graze;
        mOn[i] = true;
    }

    /** A burnt-out fireball leaves a glowing train along its path, and flashes the sky. */
    private static void newTrain(int i) {
        fireFlash = 1f;
        int k = free(trainOn);
        if (k < 0) {
            return;
        }
        for (int j = 0; j < TRAIN_POINTS; j++) {
            float f = j / (float) (TRAIN_POINTS - 1);
            tmp[0] = mx0[i] + (mx1[i] - mx0[i]) * f;
            tmp[1] = my0[i] + (my1[i] - my0[i]) * f;
            tmp[2] = mz0[i] + (mz1[i] - mz0[i]) * f;
            norm(tmp);
            System.arraycopy(tmp, 0, train[k], j * 3, 3);
        }
        trainAge[k] = 0f;
        trainOn[k] = true;
    }

    /** Where meteor i's head (into a) and tail (into b) are now: the head eases out, the tail a third behind. */
    private static void meteorEnds(int i) {
        float u = mAge[i] / mDur[i], e = 1f - (1f - u) * (1f - u), et = Math.max(0f, e - 0.32f);
        a[0] = mx0[i] + (mx1[i] - mx0[i]) * e;
        a[1] = my0[i] + (my1[i] - my0[i]) * e;
        a[2] = mz0[i] + (mz1[i] - mz0[i]) * e;
        b[0] = mx0[i] + (mx1[i] - mx0[i]) * et;
        b[1] = my0[i] + (my1[i] - my0[i]) * et;
        b[2] = mz0[i] + (mz1[i] - mz0[i]) * et;
        norm(a);
        norm(b);
    }

    private static void meteorLines(float p, float clear) {
        for (int i = 0; i < METEORS; i++) {
            if (!mOn[i]) {
                continue;
            }
            meteorEnds(i);
            if (!NightSky.projectWorld(a[0], a[1], a[2])) {
                continue;
            }
            float hx = NightSky.screen[0], hy = NightSky.screen[1];
            if (!NightSky.projectWorld(b[0], b[1], b[2])) {
                continue;
            }
            float u = mAge[i] / mDur[i], al = (float) Math.sin(Math.PI * u) * mB[i] * clear;
            line(NightSky.screen[0], NightSky.screen[1], hx, hy, mFire[i] ? 2f * p : p, Colors.withAlpha(mCol[i], 0f),
                    Colors.withAlpha(mCol[i], al));
            // The head: one white pixel.
            float hx0 = Math.round(hx / p) * p, hy0 = Math.round(hy / p) * p;
            NightSky.box(hx0, hy0, hx0 + p, hy0 + p, Colors.withAlpha(0xFFFFFFFF, al));
        }
    }

    private static void meteorGlows(float p, float clear) {
        for (int i = 0; i < METEORS; i++) {
            if (!mOn[i] || !mFire[i] && !mGraze[i]) {
                continue;
            }
            meteorEnds(i);
            if (!NightSky.projectWorld(a[0], a[1], a[2])) {
                continue;
            }
            float u = mAge[i] / mDur[i], al = (float) Math.sin(Math.PI * u) * mB[i] * clear;
            float r = (mFire[i] ? 7f + 5f * u : 4f) * p;
            float x = NightSky.screen[0], y = NightSky.screen[1];
            NightSky.box(x - r, y - r, x + r, y + r, Colors.withAlpha(mCol[i], 0.6f * al));
        }
    }

    /** The fireballs' trains: a faint green thread along the path, drifting and fading over four and a half seconds. */
    private static void trains(float p, float clear) {
        for (int k = 0; k < TRAINS; k++) {
            if (!trainOn[k]) {
                continue;
            }
            float age = trainAge[k], al = 0.32f * (1f - age / 4.5f) * clear, drift = age * 1.2f * p;
            int c = Colors.withAlpha(0xFFAAFFCD, al);
            float lx = 0f, ly = 0f;
            boolean have = false;
            for (int j = 0; j < TRAIN_POINTS; j++) {
                if (!NightSky.projectWorld(train[k][j * 3], train[k][j * 3 + 1], train[k][j * 3 + 2])) {
                    have = false;
                    continue;
                }
                float x = NightSky.screen[0], y = NightSky.screen[1] + (float) Math.sin(j * 0.9f + age) * drift;
                if (have) {
                    line(lx, ly, x, y, p, c, c);
                }
                lx = x;
                ly = y;
                have = true;
            }
        }
    }

    // ------------------------------------------------------------------ the comet

    /** The comet's head: 44° east of the sun along the ecliptic, 11° above it, in the evening sky. */
    private static boolean cometAt() {
        SkySphere sp = NightSky.SPHERE;
        SkySphere.ecl(sp.lamSun + 44, 11, eq);
        return NightSky.project((float) eq[0], (float) eq[1], (float) eq[2]) && NightSky.world[1] > -0.02f;
    }

    private static float cometVis() {
        return Ease.smoothstep(-6f, -14f, NightSky.SPHERE.sunAlt) * Ease.smoothstep(-0.02f, 0.1f, NightSky.world[1]);
    }

    private static void cometTails(float p, float fp, float clear) {
        if (!cometAt()) {
            return;
        }
        float vis = cometVis() * clear;
        if (vis <= 0.004f) {
            return;
        }
        float x = NightSky.screen[0], y = NightSky.screen[1], depth = NightSky.depth;
        // Away from the sun on the screen (y down).
        sunDir(x, y);
        float tx = -tmp[0], ty = tmp[1];
        float l1 = fp * (float) Math.tan(Math.toRadians(17)) / depth, l2 = fp * (float) Math.tan(Math.toRadians(11)) / depth;
        // The ion tail: straight, blue, long; six strokes of narrowing width laid on each other.
        for (int i = 0; i < 6; i++) {
            float wv = (6 - i) * 0.9f * p;
            line(x, y, x + tx * l1, y + ty * l1, wv, Colors.withAlpha(0xFF96C8FF, 0.16f * vis), Colors.withAlpha(0xFF96C8FF, 0f));
        }
        // The dust tail: golden, broader, curving away from the ion tail.
        float ang = 0.32f, c = (float) Math.cos(ang), s = (float) Math.sin(ang);
        float cx = x + (tx * c - ty * s) * l2, cy = y + (tx * s + ty * c) * l2;
        float qx = x + tx * l2 * 0.6f, qy = y + ty * l2 * 0.6f;
        int seg = 12;
        for (int i = 0; i < 8; i++) {
            float wv = (8 - i) * 1.4f * p;
            float px = x, py = y;
            for (int k = 1; k <= seg; k++) {
                float f = k / (float) seg, g = 1f - f;
                float nx = g * g * x + 2 * g * f * qx + f * f * cx, ny = g * g * y + 2 * g * f * qy + f * f * cy;
                float a0 = 0.14f * vis * (1f - (k - 1) / (float) seg), a1 = 0.14f * vis * (1f - f);
                line(px, py, nx, ny, wv, Colors.withAlpha(0xFFFFE8BE, a0), Colors.withAlpha(0xFFFFE8BE, a1));
                px = nx;
                py = ny;
            }
        }
        // Its core: one pixel.
        float hx = Math.round(x / p) * p, hy = Math.round(y / p) * p;
        NightSky.box(hx, hy, hx + p, hy + p, Colors.withAlpha(0xFFF4FFF8, vis));
    }

    private static void cometHead(float p, float clear, boolean names) {
        if (!cometAt()) {
            return;
        }
        float vis = cometVis() * clear;
        if (vis <= 0.004f) {
            return;
        }
        float x = NightSky.screen[0], y = NightSky.screen[1], r = 5f * p;
        NightSky.box(x - r, y - r, x + r, y + r, Colors.withAlpha(0xFFBEFFD7, vis));
        if (names) {
            SkyLabels.add(x, y, "Comet", SkyLabels.PLANET, vis);
        }
    }

    /** The screen direction (x right, y up) from screen point (x, y), the comet, toward the sun, into {@link #tmp}. */
    private static void sunDir(float x, float y) {
        float[] sun = NightSky.SPHERE.sun;
        float vx = NightSky.world[0], vy = NightSky.world[1], vz = NightSky.world[2];
        float k = vx * sun[0] + vy * sun[1] + vz * sun[2];
        float ux = sun[0] - vx * k, uy = sun[1] - vy * k, uz = sun[2] - vz * k;
        float n = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        tmp[0] = 0f;
        tmp[1] = 1f;
        if (n < 1e-5f) {
            return;
        }
        float sx = vx + ux / n * 0.02f, sy = vy + uy / n * 0.02f, sz = vz + uz / n * 0.02f, sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (!NightSky.projectWorld(sx / sl, sy / sl, sz / sl)) {
            return;
        }
        float dx = NightSky.screen[0] - x, dy = y - NightSky.screen[1], m = (float) Math.sqrt(dx * dx + dy * dy);
        if (m > 1e-6f) {
            tmp[0] = dx / m;
            tmp[1] = dy / m;
        }
    }

    // ------------------------------------------------------------------ satellites and ships

    private static void stepShips(boolean on, float dark, float dt, float fp) {
        if (!satsMade) {
            satsMade = true;
            for (int i = 0; i < SATS; i++) {
                newSat(i);
                sU[i] = -1f + 2f * (float) rnd.next();
            }
        }
        for (int i = 0; i < SATS; i++) {
            sU[i] += dt * sW[i];
            if (sU[i] > 1f) {
                newSat(i);
            }
            if (sFlare[i] <= 0f && dt > 0f && rnd.next() < dt / 30f) {
                sFlare[i] = 2.4f;
            }
            sFlare[i] = Math.max(0f, sFlare[i] - dt);
        }
        for (int i = 0; i < HYPERS; i++) {
            if (hOn[i] && (hAge[i] += dt) >= 0.9f) {
                hOn[i] = false;
            }
        }
        if (!on || NightSky.dark <= 0.4f || dt <= 0f) {
            return;
        }
        nextJump -= dt;
        if (nextJump <= 0f) {
            nextJump = 12f + (float) rnd.next() * 18f;
            jump(fp);
        }
    }

    /** A satellite on a new great circle, from 25° to 80° up. */
    private static void newSat(int i) {
        altAz(25f + (float) rnd.next() * 55f, (float) rnd.next() * 360f, a);
        float tx = (float) rnd.next() - 0.5f, ty = (float) rnd.next() - 0.5f, tz = (float) rnd.next() - 0.5f;
        float k = tx * a[0] + ty * a[1] + tz * a[2];
        b[0] = tx - a[0] * k;
        b[1] = ty - a[1] * k;
        b[2] = tz - a[2] * k;
        norm(b);
        sx[i] = a[0];
        sy[i] = a[1];
        sz[i] = a[2];
        stx[i] = b[0];
        sty[i] = b[1];
        stz[i] = b[2];
        sU[i] = -1f;
        sW[i] = (float) Math.toRadians(0.6 + rnd.next() * 0.9) * 1.6f;
    }

    private static void satellites(float p, float clear, boolean glows) {
        for (int i = 0; i < SATS; i++) {
            float c = (float) Math.cos(sU[i]), s = (float) Math.sin(sU[i]);
            a[0] = sx[i] * c + stx[i] * s;
            a[1] = sy[i] * c + sty[i] * s;
            a[2] = sz[i] * c + stz[i] * s;
            norm(a);
            if (a[1] < 0.1f || !NightSky.projectWorld(a[0], a[1], a[2])) {
                continue;
            }
            float va = NightSky.dark * Ease.smoothstep(0.1f, 0.25f, a[1]) * clear;
            float x = NightSky.screen[0], y = NightSky.screen[1];
            if (glows) {
                if (sFlare[i] > 0f) {
                    float f = (float) Math.sin((1f - sFlare[i] / 2.4f) * Math.PI), r = (2f + 5f * f) * p;
                    NightSky.box(x - r, y - r, x + r, y + r, Colors.withAlpha(0xFFEBF0FF, f * va));
                }
            } else {
                float x0 = Math.round(x / p) * p, y0 = Math.round(y / p) * p;
                NightSky.box(x0, y0, x0 + p, y0 + p, Colors.withAlpha(0xFFF2F4FF, 0.75f * va));
            }
        }
    }

    /** One ship (or a squadron of four) jumps: streaks shooting off from a point in the upper part of the view. */
    private static void jump(float fp) {
        Camera cam = camera();
        float x = (0.1f + 0.8f * (float) rnd.next()) * NightSky.sw, y = (0.08f + 0.45f * (float) rnd.next()) * NightSky.sh;
        cam.ray(x, y, fp, tmp);
        if (tmp[1] <= 0.15f) {
            return;
        }
        float tx = (float) rnd.next() - 0.5f, ty = ((float) rnd.next() - 0.5f) * 0.4f, tz = (float) rnd.next() - 0.5f;
        float k = tx * tmp[0] + ty * tmp[1] + tz * tmp[2];
        b[0] = tx - tmp[0] * k;
        b[1] = ty - tmp[1] * k;
        b[2] = tz - tmp[2] * k;
        norm(b);
        // Across the path: d0 × T.
        float nx = tmp[1] * b[2] - tmp[2] * b[1], ny = tmp[2] * b[0] - tmp[0] * b[2], nz = tmp[0] * b[1] - tmp[1] * b[0];
        float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        nx /= nl;
        ny /= nl;
        nz /= nl;
        int n = rnd.next() < 0.4 ? 4 : 1;
        for (int i = 0; i < n; i++) {
            int h = free(hOn);
            if (h < 0) {
                return;
            }
            float o = (i - (n - 1) / 2f) * 0.03f;
            a[0] = tmp[0] + nx * o;
            a[1] = tmp[1] + ny * o;
            a[2] = tmp[2] + nz * o;
            norm(a);
            hx0[h] = a[0];
            hy0[h] = a[1];
            hz0[h] = a[2];
            w[0] = a[0] + b[0] * 0.45f;
            w[1] = a[1] + b[1] * 0.45f;
            w[2] = a[2] + b[2] * 0.45f;
            norm(w);
            hx1[h] = w[0];
            hy1[h] = w[1];
            hz1[h] = w[2];
            hAge[h] = -i * 0.16f - (float) rnd.next() * 0.08f;
            hOn[h] = true;
        }
    }

    /** Ship h's streak this frame: from where it jumped to its growing head, into a (start) and b (head). */
    private static float hyperEnds(int h) {
        float u = hAge[h] / 0.9f, grow = Math.min(1f, u / 0.3f);
        a[0] = hx0[h];
        a[1] = hy0[h];
        a[2] = hz0[h];
        b[0] = hx0[h] + (hx1[h] - hx0[h]) * grow;
        b[1] = hy0[h] + (hy1[h] - hy0[h]) * grow;
        b[2] = hz0[h] + (hz1[h] - hz0[h]) * grow;
        norm(b);
        return u;
    }

    private static void hyperLines(float p, float clear) {
        for (int h = 0; h < HYPERS; h++) {
            if (!hOn[h] || hAge[h] < 0f) {
                continue;
            }
            float u = hyperEnds(h);
            if (!NightSky.projectWorld(a[0], a[1], a[2])) {
                continue;
            }
            float x0 = NightSky.screen[0], y0 = NightSky.screen[1];
            if (!NightSky.projectWorld(b[0], b[1], b[2])) {
                continue;
            }
            float al = (u < 0.3f ? 1f : 1f - (u - 0.3f) / 0.7f) * clear;
            line(x0, y0, NightSky.screen[0], NightSky.screen[1], p, Colors.withAlpha(0xFFAAC8FF, 0f), Colors.withAlpha(0xFFEBF2FF, al));
        }
    }

    private static void hyperGlows(float p, float clear) {
        for (int h = 0; h < HYPERS; h++) {
            if (!hOn[h] || hAge[h] < 0f) {
                continue;
            }
            float u = hyperEnds(h);
            if (u <= 0.26f || u >= 0.6f || !NightSky.projectWorld(b[0], b[1], b[2])) {
                continue;
            }
            float al = (float) Math.sin((u - 0.26f) / 0.34f * Math.PI) * clear, r = 7f * p;
            float x = NightSky.screen[0], y = NightSky.screen[1];
            NightSky.box(x - r, y - r, x + r, y + r, Colors.withAlpha(0xFFC8E1FF, al));
        }
    }

    // ------------------------------------------------------------------ the superlaser

    /** The Death Star's superlaser: eight green beams meet in front of its dish, then the main beam fires. */
    private static void laser(Atmosphere at, float dt, float p, boolean glows) {
        boolean up = (at == null || at.ships.get()) && Planets.deathA >= 0.4f;
        if (!up) {
            laserAge = -1f;
            return;
        }
        if (!glows && dt > 0f) {
            if (laserAge < 0f) {
                nextLaser -= dt;
                if (nextLaser <= 0f) {
                    laserAge = 0f;
                    laserAngle = -2.3f + ((float) rnd.next() - 0.5f) * 0.6f;
                    nextLaser = 26f + (float) rnd.next() * 30f;
                }
            } else if ((laserAge += dt) > 2.6f) {
                laserAge = -1f;
            }
        }
        if (laserAge < 0f) {
            return;
        }
        float age = laserAge, r = Planets.deathD / 2f, dx = (float) Math.cos(laserAngle), dy = (float) Math.sin(laserAngle);
        float cx = Planets.deathX + dx * r * 0.62f, cy = Planets.deathY + dy * r * 0.62f;
        float fx = Planets.deathX + dx * r * 1.7f, fy = Planets.deathY + dy * r * 1.7f;
        float a2 = Ease.smoothstep(0.9f, 1.1f, age) * (1f - Ease.smoothstep(2.1f, 2.6f, age));
        if (glows) {
            if (age > 0.9f) {
                float q = r * 1.3f;
                NightSky.box(fx - q, fy - q, fx + q, fy + q, Colors.withAlpha(0xFF8CFFA0, a2));
            }
            return;
        }
        float a1 = Ease.smoothstep(0f, 0.5f, age) * (1f - Ease.smoothstep(2.1f, 2.6f, age));
        int c = Colors.withAlpha(0xFF6EFF8C, 0.7f * a1);
        for (int i = 0; i < 8; i++) {
            double an = i / 8.0 * Math.PI * 2;
            line(cx + (float) Math.cos(an) * r * 0.24f, cy + (float) Math.sin(an) * r * 0.24f, fx, fy, p * 0.5f, c, c);
        }
        if (age > 0.9f) {
            float len = Math.max(NightSky.sw, NightSky.sh) * 1.6f * Ease.smoothstep(0.9f, 1.4f, age);
            float[] lw = {9f, 4f, 1.6f}, la = {0.12f, 0.35f, 1f};
            for (int k = 0; k < 3; k++) {
                int col = Colors.withAlpha(0xFF96FFAA, la[k] * a2);
                line(fx, fy, fx + dx * len, fy + dy * len, lw[k] * p * 0.5f, col, col);
            }
        }
    }

    // ------------------------------------------------------------------ the scenario's frozen moments

    /**
     * Sets up a scene for a reference frame and holds time ({@code "meteors"}, {@code "ships"}; any other name only
     * holds), or lets the sky run again ({@code null}).
     */
    public static void stage(String what) {
        staging = what;
        stageFrames = 0;
        held = what != null;
    }

    private static void setUp(String what, float fp) {
        rnd = new Mulberry32(7);
        java.util.Arrays.fill(mOn, false);
        java.util.Arrays.fill(trainOn, false);
        java.util.Arrays.fill(hOn, false);
        fireFlash = 0f;
        laserAge = -1f;
        if ("meteors".equals(what)) {
            // A burst from the radiant at different moments, a fireball beside the train an earlier one left, and an
            // earthgrazer low down.
            radiant(rad);
            for (int k = 0, n = 0; k < 30 && n < 7; k++) {
                int i = streak(rad, true, fp);
                if (i >= 0) {
                    mAge[i] = mDur[i] * (0.3f + 0.15f * (n++ % 4));
                }
            }
            int f = -1;
            for (int k = 0; k < 30 && f < 0; k++) {
                f = streak(null, false, fp);
            }
            if (f >= 0) {
                mFire[f] = true;
                mB[f] = 1f;
                mDur[f] += 0.4f;
                mCol[f] = 0xFFBEFFC8;
                newTrain(f);
                fireFlash = 0f;
                mAge[f] = mDur[f] * 0.55f;
                for (int k = 0; k < TRAINS; k++) {
                    if (trainOn[k]) {
                        trainAge[k] = 1.2f;
                        for (int j = 0; j < TRAIN_POINTS * 3; j += 3) {
                            train[k][j] += 0.12f;
                        }
                    }
                }
            }
            int g = grazer();
            if (g >= 0) {
                mAge[g] = mDur[g] * 0.5f;
            }
        } else if ("ships".equals(what)) {
            // A squadron jumping, a satellite flaring overhead, the superlaser firing.
            for (int k = 0; k < 20 && count(hOn) < 4; k++) {
                java.util.Arrays.fill(hOn, false);
                jump(fp);
            }
            for (int h = 0; h < HYPERS; h++) {
                hAge[h] = 0.33f + h * 0.02f;
            }
            Camera cam = camera();
            cam.ray(NightSky.sw * 0.3f, NightSky.sh * 0.25f, fp, a);
            sx[0] = a[0];
            sy[0] = a[1];
            sz[0] = a[2];
            sU[0] = 0f;
            sFlare[0] = 1.2f;
            satsMade = true;
            laserAge = 1.6f;
            laserAngle = -2.3f;
        }
    }

    private static int count(boolean[] on) {
        int n = 0;
        for (boolean b : on) {
            if (b) {
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ helpers

    /** A line {@code width} pixels wide from (x0, y0) in colour c0 to (x1, y1) in c1. */
    private static void line(float x0, float y0, float x1, float y1, float width, int c0, int c1) {
        float dx = x1 - x0, dy = y1 - y0, len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.5f || len > 6f * Math.max(NightSky.sw, NightSky.sh)) {
            return;
        }
        float nx = -dy / len * width * 0.5f, ny = dx / len * width * 0.5f;
        NightSky.vertex(x0 + nx, y0 + ny, 0f, 0f, c0);
        NightSky.vertex(x0 - nx, y0 - ny, 0f, 1f, c0);
        NightSky.vertex(x1 - nx, y1 - ny, 1f, 1f, c1);
        NightSky.vertex(x1 + nx, y1 + ny, 1f, 0f, c1);
    }

    private static int free(boolean[] on) {
        for (int i = 0; i < on.length; i++) {
            if (!on[i]) {
                return i;
            }
        }
        return -1;
    }

    private static void norm(float[] v) {
        float n = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (n > 1e-6f) {
            v[0] /= n;
            v[1] /= n;
            v[2] /= n;
        }
    }

    /** A world direction at altitude and azimuth (degrees; the game's: 0 south, 90 west). */
    private static void altAz(float alt, float az, float[] out) {
        double al = Math.toRadians(alt), zr = Math.toRadians(az);
        float h = (float) Math.cos(al);
        out[0] = (float) (-Math.sin(zr) * h);
        out[1] = (float) Math.sin(al);
        out[2] = (float) (Math.cos(zr) * h);
    }

    /** The camera this frame, from the view rotation. */
    private static final Camera CAM = new Camera();

    private static Camera camera() {
        CAM.update();
        return CAM;
    }

    private static final class Camera {
        float fx, fy, fz, rx, ry, rz, ux, uy, uz, yaw;

        void update() {
            org.joml.Matrix4f v = FrameInfo.view;
            rx = v.m00();
            ry = v.m10();
            rz = v.m20();
            ux = v.m01();
            uy = v.m11();
            uz = v.m21();
            fx = -v.m02();
            fy = -v.m12();
            fz = -v.m22();
            yaw = (float) Math.toDegrees(Math.atan2(-fx, fz));
        }

        /** The world direction through screen pixel (x, y), with focal length {@code fp} pixels. */
        void ray(float x, float y, float fp, float[] out) {
            float dx = (x - NightSky.sw / 2f) / fp, dy = (NightSky.sh / 2f - y) / fp;
            out[0] = fx + rx * dx + ux * dy;
            out[1] = fy + ry * dx + uy * dy;
            out[2] = fz + rz * dx + uz * dy;
            norm(out);
        }
    }
}
