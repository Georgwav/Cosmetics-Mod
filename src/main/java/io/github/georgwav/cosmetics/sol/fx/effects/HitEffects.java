package io.github.georgwav.cosmetics.sol.fx.effects;

import io.github.georgwav.cosmetics.client.Setting;
import io.github.georgwav.cosmetics.sol.fx.Fx;
import io.github.georgwav.cosmetics.sol.fx.FxColour;
import io.github.georgwav.cosmetics.sol.fx.FxDraw;
import io.github.georgwav.cosmetics.sol.fx.FxEvents;
import io.github.georgwav.cosmetics.sol.fx.FxKind;
import io.github.georgwav.cosmetics.sol.fx.FxModule;
import io.github.georgwav.cosmetics.sol.fx.FxOut;
import io.github.georgwav.cosmetics.sol.fx.FxPool;
import io.github.georgwav.cosmetics.sol.fx.FxPreview;
import io.github.georgwav.cosmetics.sol.fx.FxSettings;
import io.github.georgwav.cosmetics.sol.fx.FxSprites;
import io.github.georgwav.cosmetics.sol.fx.FxText;
import io.github.georgwav.cosmetics.sol.fx.FxWorld;
import io.github.georgwav.cosmetics.sol.fx.PoseSnapshot;
import io.github.georgwav.cosmetics.sol.fx.Ramp;
import io.github.georgwav.cosmetics.sol.fx.SolPicks;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

/**
 * Hit Effects (C.4): what a hit looks like, only on your screen. Hit particles off the exact point you hit (Pixels,
 * Stars, Hearts, Crystals, Orbs or Sparks; twice as many on a crit; they fall and come to rest), a bubble that swells
 * and pops into droplets, word hits (HIT, CRIT!, KILL, your own words) that pop up and float away, a hitmarker round
 * the crosshair (red on a kill, with its own sound), and damage numbers rising off what you hit (as health or hearts;
 * crits in gold). The hit sounds stay in PvP Effects. While the particles are on they take the place of the game's
 * sharpness sparks (Particles).
 */
public final class HitEffects extends FxModule {
    public static final List<String> PARTICLE_STYLES = List.of("pixels", "stars", "hearts", "crystals", "orbs", "sparks");
    private static final List<String> ONE = List.of("one");
    /** The hitmarker's red on a kill. */
    public static final int KILL_RED = 0xFFFF3B3B;
    /** Crits' gold, for the damage numbers. */
    public static final int CRIT_GOLD = 0xFFFFC23D;

    public final FxSettings particles = fx("hit_particles", PARTICLE_STYLES, new FxSettings.Spec().toggle(true).random().who(false));
    public final FxSettings bubbles = fx("bubbles", ONE, new FxSettings.Spec().toggle(false).who(false));
    public final FxSettings words = fx("words", ONE, new FxSettings.Spec().toggle(false).noAmount().who(false));
    public final Setting.Text wordHit = add(new Setting.Text("words_hit", "HIT", 12).group("words"));
    public final Setting.Text wordCrit = add(new Setting.Text("words_crit", "CRIT!", 12).group("words"));
    public final Setting.Text wordKill = add(new Setting.Text("words_kill", "KILL", 12).group("words"));
    public final FxSettings marker = fx("hitmarker", ONE, new FxSettings.Spec().toggle(true).noAmount().noSpeed().sound("pop"));
    public final Setting.Toggle markerKill = add(new Setting.Toggle("hitmarker_kill", true).group("hitmarker"));
    public final FxSettings damage = fx("damage", ONE, new FxSettings.Spec().toggle(false).noAmount().noGlow().who(false));
    public final Setting.Choice damageAs = add(new Setting.Choice("damage_as", "hp", List.of("hp", "hearts")).group("damage"));
    public final Setting.Toggle critGold = add(new Setting.Toggle("damage_crit_gold", true).group("damage"));

    /** The last few hitmarkers' start times (the effects' clock; Try it queues three) and whether each was a kill. */
    private final float[] markerAt = new float[4];
    private final boolean[] markerKilled = new boolean[4];
    private int markerNext;

    public HitEffects() {
        super("hit_effects", Category.VIEW, "burst");
        for (Setting.Text t : List.of(wordHit, wordCrit, wordKill)) {
            t.shownWhen(words.on::get);
        }
        markerKill.shownWhen(marker.on::get);
        damageAs.shownWhen(damage.on::get);
        critGold.shownWhen(damage.on::get);
        FxEvents.HIT.add(new FxEvents.Hit() {
            @Override
            public boolean listening() {
                return enabled();
            }

            @Override
            public void hit(LivingEntity target, double x, double y, double z, boolean crit, float healthBefore) {
                Player you = Minecraft.getInstance().player;
                Vec3 eye = you != null ? you.getEyePosition() : new Vec3(x, y, z - 1);
                double dx = x - eye.x, dz = z - eye.z;
                // Toward you a little, so nothing starts inside the body it hit.
                double l = Math.max(1e-4, Math.sqrt(dx * dx + dz * dz));
                double fx = x - dx / l * 0.12, fz = z - dz / l * 0.12;
                played(FxWorld.POOL, target, fx, y, fz, (float) (dx / l), (float) (dz / l), crit, 0f);
                if (marker.on()) {
                    marker(false, 0f);
                }
            }
        });
        FxEvents.DAMAGE.add(new FxEvents.Damage() {
            @Override
            public boolean listening() {
                return enabled() && damage.on();
            }

            @Override
            public void damaged(LivingEntity target, float amount, boolean crit) {
                if (damage.allows(target)) {
                    number(FxWorld.POOL, target.getX(), target.getY() + target.getBbHeight() + 0.3, target.getZ(), amount, crit, 0f);
                }
            }
        });
        FxEvents.KILL.add(new FxEvents.Kill() {
            @Override
            public boolean listening() {
                return enabled() && (marker.on() || words.on());
            }

            @Override
            public void killed(LivingEntity dead, PoseSnapshot copy) {
                if (marker.on()) {
                    marker(markerKill.get(), 0f);
                    marker.play("kill", 1f);
                }
                if (words.on() && words.allows(dead)) {
                    word(FxWorld.POOL, dead.getX(), dead.getY() + dead.getBbHeight() * 0.75, dead.getZ(), 2, 0f);
                }
            }
        });
        SolPicks.module(this);
        SolPicks.set(particles.on, true);
        SolPicks.set(particles.style, "pixels");
        SolPicks.set(particles.random, false);
        SolPicks.set(marker.on, true);
    }

    /** Forgets the hitmarkers (a new world, or the module switched off). */
    @Override
    protected void onDisable(Minecraft mc) {
        java.util.Arrays.fill(markerAt, 0f);
    }

    /** Whether the hit particles are showing (then the game's sharpness sparks aren't: Particles). */
    public boolean particlesOn() {
        return enabled() && particles.on();
    }

    // ------------------------------------------------------------------ playing

    /** One hit's world effects at (x, y, z), {@code delay} seconds from now; ({@code dx}, {@code dz}) the way it went. */
    private void played(FxPool pool, LivingEntity target, double x, double y, double z, float dx, float dz, boolean crit, float delay) {
        if (particles.on() && (target == null || particles.allows(target))) {
            int style = particles.pick((int) (System.nanoTime() >>> 10));
            Fx f = start(pool, particles, PARTICLES, x, y, z, style == 5 ? 0.6f : style == 4 ? 1.1f : 0.85f);
            particles.apply(f, style);
            f.i0 = Math.max(6, Math.min(20, Math.round(10 * f.amount))) * (crit ? 2 : 1);
            f.i1 = crit ? 1 : 0;
            f.tx = dx;
            f.tz = dz;
            f.c = Math.min(-0.05f, ground(pool, x, z, y) - (float) y);
            FxPool.delay(f, delay);
        }
        if (bubbles.on() && (target == null || bubbles.allows(target))) {
            Fx f = start(pool, bubbles, BUBBLE, x, y, z, 0.6f);
            bubbles.apply(f, 0);
            f.c = Math.min(-0.05f, ground(pool, x, z, y) - (float) y);
            FxPool.delay(f, delay);
        }
        if (words.on() && (target == null || words.allows(target))) {
            word(pool, x, y + 0.2, z, crit ? 1 : 0, delay);
        }
    }

    /** A word: 0 a hit, 1 a crit, 2 a kill. */
    private void word(FxPool pool, double x, double y, double z, int which, float delay) {
        String w = (which == 2 ? wordKill : which == 1 ? wordCrit : wordHit).get().trim();
        if (w.isEmpty()) {
            return;
        }
        Fx f = start(pool, words, WORD, x, y, z, 1f);
        words.apply(f, 0);
        f.text = w.toUpperCase(Locale.ROOT);
        f.i1 = which;
        f.a = FxDraw.signed(f.seed, 3) * 0.2f;
        FxPool.delay(f, delay);
    }

    /** A damage number over a head at (x, y, z). */
    private void number(FxPool pool, double x, double y, double z, float amount, boolean crit, float delay) {
        boolean hearts = damageAs.get().equals("hearts");
        float v = hearts ? amount / 2f : amount;
        // Halves for hearts, tenths for health; a whole number shows no point.
        float r = hearts ? Math.round(v * 4f) / 4f : Math.round(v * 10f) / 10f;
        if (r <= 0f) {
            return;
        }
        Fx f = start(pool, damage, DAMAGE, x, y, z, 1f);
        damage.apply(f, 0);
        if (crit && critGold.get()) {
            FxColour.fixed(f, CRIT_GOLD);
        }
        f.text = r == Math.floor(r) ? String.valueOf((int) r) : String.format(Locale.ROOT, hearts ? "%.2f" : "%.1f", r)
                .replaceAll("0+$", "");
        f.i1 = hearts ? 1 : 0;
        f.a = FxDraw.signed(f.seed, 1) * 0.35f;
        f.b = FxDraw.signed(f.seed, 2) * 0.35f;
        FxPool.delay(f, delay);
    }

    /** Starts a hitmarker {@code delay} seconds from now. */
    private void marker(boolean kill, float delay) {
        int i = markerNext;
        markerNext = (markerNext + 1) % markerAt.length;
        markerAt[i] = FxWorld.now() + delay;
        markerKilled[i] = kill;
    }

    @Override
    public float tryIt(FxPool pool, FxPreview.Dummy dummy) {
        double a = Math.toRadians(dummy.yaw);
        // The dummy faces whoever hits it: the hits go the other way.
        float dx = (float) Math.sin(a), dz = (float) -Math.cos(a);
        double fx = dummy.x - dx * 0.32, fz = dummy.z - dz * 0.32;
        float[] at = {0f, 0.55f, 1.1f};
        boolean[] crit = {false, true, false};
        float[] dmg = {3f, 4.5f, 3f};
        for (int k = 0; k < 3; k++) {
            double y = dummy.chestY() + (k - 1) * 0.12;
            double side = (k - 1) * 0.12;
            played(pool, null, fx - dz * side, y, fz + dx * side, dx, dz, crit[k], at[k]);
            if (damage.on()) {
                number(pool, dummy.x, dummy.y + dummy.body.height + 0.3, dummy.z, dmg[k], crit[k], at[k]);
            }
            if (marker.on() && pool == FxWorld.POOL) {
                marker(false, at[k]);
            }
        }
        return 2f;
    }

    // ------------------------------------------------------------------ the hitmarker (drawn with the crosshair)

    /**
     * The hitmarker round the crosshair at (cx, cy) in screen pixels: four diagonal strokes of whole pixels that open
     * over 0.12 s and fade by 0.35 s; red on a kill. Drawn by Crosshair's renderer, over its crosshair or the game's.
     */
    public void drawMarker(Canvas g, int cx, int cy, float guiScale) {
        if (!enabled() || !marker.on()) {
            return;
        }
        float now = FxWorld.now();
        int pick = -1;
        for (int i = 0; i < markerAt.length; i++) {
            if (markerAt[i] != 0f && markerAt[i] <= now && (pick < 0 || markerAt[i] > markerAt[pick])) {
                pick = i;
            }
        }
        if (pick < 0) {
            return;
        }
        float dur = (float) (marker.duration.get() / 100.0);
        float age = (now - markerAt[pick]) / dur;
        if (age >= 0.35f) {
            return;
        }
        boolean snap = marker.snap.get();
        boolean calm = FxWorld.calm();
        float open = calm ? 1f : Ramp.steps(Math.min(1f, age / 0.12f), 3, snap);
        float alpha = age < 0.2f ? 1f : Ramp.steps(1f - (age - 0.2f) / 0.15f, 3, snap);
        // One block of a stroke: two screen pixels at GUI scale 2 and 3, more as the scale or the size grows.
        float size = (float) (marker.size.get() / 100.0);
        int u = Math.max(1, Math.round(Math.max(2f, guiScale * 0.75f) * size));
        int d = Math.round((4f + 2f * open) * u);
        int c1, c2;
        if (markerKilled[pick]) {
            c1 = c2 = KILL_RED;
        } else {
            switch (marker.colourMode.get()) {
                case "custom" -> c1 = c2 = marker.colour.argb();
                case "two" -> {
                    c1 = marker.colour.argb();
                    c2 = marker.colour2.argb();
                }
                case "rainbow" -> {
                    float hue = (now % 3f) / 3f;
                    c1 = c2 = Colors.fromHsv(Math.round(hue * 12f) / 12f, 0.75f, 1f);
                }
                default -> c1 = c2 = FxColour.accent();
            }
        }
        int glow = marker.glow.get().equals("off") ? 0 : marker.glow.get().equals("strong") ? 2 : 1;
        int glowColour = markerKilled[pick] ? KILL_RED : FxColour.glow();
        // Glow, then a dark outline, then the strokes.
        if (glow > 0) {
            strokes(g, cx, cy, d, u, u, Colors.withAlpha(glowColour, alpha * (glow == 2 ? 0.4f : 0.22f)), 0);
        }
        strokes(g, cx, cy, d, u, 1, Colors.withAlpha(0xFF000000, alpha * 0.55f), 0);
        strokes(g, cx, cy, d, u, 0, Colors.withAlpha(c1, alpha), Colors.withAlpha(c2, alpha));
    }

    /**
     * The four strokes: diagonal lines {@code u} thick and four {@code u} long, from {@code d} out, one solid stroke each
     * (a run of pixels per row, so a see-through colour never doubles up), grown by {@code grow} on every side (the
     * outline and the glow).
     */
    private static void strokes(Canvas g, int cx, int cy, int d, int u, int grow, int argb, int second) {
        int len = 3 * u;
        for (int q = 0; q < 4; q++) {
            int sx = (q & 1) == 0 ? -1 : 1, sy = q < 2 ? -1 : 1;
            int c = second != 0 && (q == 1 || q == 2) ? second : argb;
            // In the quadrant's own frame (out from the middle is positive): the squares of side u at (d + t, d + t),
            // t = 0 .. len, joined row by row.
            for (int y = d - grow; y < d + len + u + grow; y++) {
                int t0 = Math.max(0, y - d - u - grow + 1), t1 = Math.min(len, y - d + grow);
                if (t0 > t1) {
                    continue;
                }
                int x0 = d + t0 - grow, x1 = d + t1 + u + grow;
                int left = sx > 0 ? cx + x0 : cx - x1, right = sx > 0 ? cx + x1 : cx - x0;
                int top = sy > 0 ? cy + y : cy - y - 1;
                g.fill(left, top, right, top + 1, c);
            }
        }
    }

    // ------------------------------------------------------------------ the kinds

    private static final float T = FxDraw.TEXEL;
    private static final float[] RR = new float[3], UU = new float[3];
    private static final FxSprites.Sprite[] SPRITES = {FxSprites.SQUARE, FxSprites.STAR, FxSprites.HEART, FxSprites.CRYSTAL,
            FxSprites.ORB, FxSprites.SQUARE};
    /** Each style's sprite pixel (in texels), gravity (blocks/s²) and speed. */
    private static final float[] PIXEL = {1f, 0.5f, 0.45f, 0.5f, 0.55f, 1f};
    private static final float[] GRAVITY = {7f, 6f, 4.5f, 7f, 1.6f, 8f};
    private static final float[] SPEED = {1f, 0.9f, 0.75f, 1f, 0.55f, 1.7f};

    /** Where particle {@code i} is at {@code t} (into {@code p}), resting where it lands. */
    static void at(Fx fx, int i, float t, float[] p) {
        int s = fx.style;
        float h1 = FxDraw.hash(fx.seed, i * 4), h2 = FxDraw.signed(fx.seed, i * 4 + 1), h3 = FxDraw.hash(fx.seed, i * 4 + 2);
        float k = SPEED[s] * (fx.i1 == 1 ? 1.2f : 1f);
        // Off the body's surface: back toward you a little, out to the sides and up.
        float along = -(0.2f + 0.7f * h1) * k, side = h2 * 1.3f * k, up = (s == 4 ? 0.35f + 0.6f * h3 : 1.0f + 1.4f * h3) * k;
        float tx = (float) fx.tx, tz = (float) fx.tz;
        float vx = tx * along - tz * side, vz = tz * along + tx * side;
        float g = GRAVITY[s];
        float c = fx.c;
        float land = (up + (float) Math.sqrt(up * up - 2f * g * c)) / g;
        float tt = Math.min(t, land);
        p[0] = vx * tt;
        p[1] = Math.max(c, up * tt - 0.5f * g * tt * tt);
        p[2] = vz * tt;
    }

    /** Hit particles: a spray off the hit point, falling and coming to rest, fading out in steps. */
    static final FxKind PARTICLES = new FxKind("hit_particles", 24) {
        private final float[] p = new float[3], q = new float[3];

        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            float t = fx.time(age);
            FxDraw.origin(out, fx);
            int s = fx.style;
            float base = fx.base;
            for (int i = 0; i < fx.i0; i++) {
                float h4 = FxDraw.hash(fx.seed, i * 4 + 3);
                float endAt = base * (0.55f + 0.45f * h4);
                float a = Ramp.steps(Math.min(1f, (endAt - t) / (base * 0.25f)), 4, fx.snap) * fx.early(now);
                if (a <= 0f) {
                    continue;
                }
                at(fx, i, t, p);
                float x = Ramp.snap(p[0], fx.snap), y = Ramp.snap(p[1], fx.snap), z = Ramp.snap(p[2], fx.snap);
                float px = PIXEL[s] * T * fx.size;
                if (s == 0) {
                    // Pixels: one or two texels, in the ramp's tones.
                    float side = px * (h4 < 0.3f ? 2f : 1f);
                    int tone = h4 < 0.35f ? Ramp.CORE : h4 < 0.8f ? Ramp.MAIN : Ramp.SHADE;
                    FxDraw.square(out, x, y, z, side, Colors.withAlpha(fx.tone(tone, i, now), a));
                    if (tone != Ramp.SHADE) {
                        FxDraw.squareGlow(out, fx, x, y, z, side, a);
                    }
                } else if (s == 5) {
                    // Sparks: a texel and two fading behind it along its path.
                    for (int k = 0; k < 3; k++) {
                        at(fx, i, Math.max(0f, t - k * 0.022f), q);
                        float sx = Ramp.snap(q[0], fx.snap), sy = Ramp.snap(q[1], fx.snap), sz = Ramp.snap(q[2], fx.snap);
                        int tone = k == 0 ? Ramp.CORE : k == 1 ? Ramp.MAIN : Ramp.SHADE;
                        FxDraw.square(out, sx, sy, sz, px, Colors.withAlpha(fx.tone(tone, i, now), a * (k == 2 ? 0.6f : 1f)));
                        if (k == 0) {
                            FxDraw.squareGlow(out, fx, sx, sy, sz, px, a);
                        }
                    }
                } else {
                    FxDraw.sprite(out, fx, SPRITES[s], x, y, z, px * (0.85f + 0.3f * h4), i, a, now);
                }
            }
        }
    };

    /** A bubble: swells over 0.2 s at the hit point, pops (a flash and a broken ring), and falls as six droplets. */
    static final FxKind BUBBLE = new FxKind("hit_bubble", 8) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            float t = fx.time(age);
            FxDraw.origin(out, fx);
            float px = 0.8f * T * fx.size;
            float early = fx.early(now);
            if (t < 0.2f) {
                float k = Ramp.steps(t / 0.2f, 3, fx.snap);
                FxDraw.sprite(out, fx, FxSprites.BUBBLE, 0f, 0f, 0f, px * (0.45f + 0.55f * k), 0, 0.9f * early, now);
                return;
            }
            out.right(RR);
            out.up(UU);
            float r = 3.5f * px;
            if (t < 0.28f) {
                // The pop: a bright plus and the ring breaking outward.
                FxDraw.sprite(out, fx, FxSprites.PLUS, 0f, 0f, 0f, px, 0, early, now);
                float rr = r * (1f + (t - 0.2f) * 6f);
                for (int k = 0; k < 8; k++) {
                    double ang = k * Math.PI / 4.0;
                    float cx = (float) Math.cos(ang) * rr, cy = (float) Math.sin(ang) * rr;
                    float x = Ramp.snap(RR[0] * cx + UU[0] * cy, fx.snap), y = Ramp.snap(RR[1] * cx + UU[1] * cy, fx.snap);
                    float z = Ramp.snap(RR[2] * cx + UU[2] * cy, fx.snap);
                    FxDraw.square(out, x, y, z, T * fx.size, Colors.withAlpha(fx.tone(Ramp.MAIN, k, now), 0.8f * early));
                }
            }
            // Six droplets, out and down.
            float dt = t - 0.2f;
            float a = Ramp.steps(1f - dt / 0.4f, 3, fx.snap) * early;
            if (a <= 0f) {
                return;
            }
            for (int k = 0; k < 6; k++) {
                double ang = k * Math.PI / 3.0 + FxDraw.hash(fx.seed, k) * 0.5;
                float ox = (float) Math.cos(ang), oy = (float) Math.sin(ang);
                float sx = RR[0] * ox + UU[0] * oy, sy = RR[1] * ox + UU[1] * oy, sz = RR[2] * ox + UU[2] * oy;
                float v = 1.1f * fx.size;
                float x = sx * (r + v * dt), z = sz * (r + v * dt);
                float y = Math.max(fx.c, sy * (r + v * dt) + 0.5f * dt - 3f * dt * dt);
                FxDraw.square(out, Ramp.snap(x, fx.snap), Ramp.snap(y, fx.snap), Ramp.snap(z, fx.snap), T * fx.size,
                        Colors.withAlpha(fx.tone(k == 0 ? Ramp.CORE : Ramp.MAIN, k, now), a));
                FxDraw.squareGlow(out, fx, x, y, z, T * fx.size, a * 0.6f);
            }
        }
    };

    /** A word hit: pops with an overshoot, floats up 0.8 blocks and fades out in steps. */
    static final FxKind WORD = new FxKind("hit_word", 10) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            float t = fx.time(age);
            FxDraw.origin(out, fx);
            float pop = Ramp.pop(t, 0.18f, fx.snap);
            float rise = Ramp.snap(0.8f * KillEffects.easeOut(t), fx.snap);
            float a = (t < 0.7f ? 1f : Ramp.steps(1f - (t - 0.7f) / 0.3f, 4, fx.snap)) * fx.early(now);
            if (a <= 0f || fx.text == null) {
                return;
            }
            float px = 0.7f * T * fx.size * pop * (fx.i1 == 2 ? 1.3f : 1f);
            out.right(RR);
            float x = RR[0] * fx.a, z = RR[2] * fx.a;
            int tone = fx.i1 == 1 ? Ramp.CORE : Ramp.MAIN;
            FxText.draw(out, fx.text, x, rise, z, px, Colors.withAlpha(fx.tone(tone, 0, now), a),
                    Colors.withAlpha(fx.tone(Ramp.DEEP, 0, now), a));
        }
    };

    /** A damage number: pops over the head, rises 0.6 blocks and fades; hearts with a heart after it. */
    static final FxKind DAMAGE = new FxKind("hit_damage", 16) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            if (out.glowPass() || fx.text == null) {
                return;
            }
            float t = fx.time(age);
            FxDraw.origin(out, fx);
            float pop = Ramp.pop(t, 0.15f, fx.snap);
            float rise = Ramp.snap(0.6f * KillEffects.easeOut(t), fx.snap);
            float a = (t < 0.65f ? 1f : Ramp.steps(1f - (t - 0.65f) / 0.35f, 4, fx.snap)) * fx.early(now);
            if (a <= 0f) {
                return;
            }
            float px = 0.75f * T * fx.size * pop;
            out.right(RR);
            float x = Ramp.snap(fx.a, fx.snap), z = Ramp.snap(fx.b, fx.snap);
            int main = Colors.withAlpha(fx.tone(Ramp.MAIN, 0, now), a), shadow = Colors.withAlpha(fx.tone(Ramp.DEEP, 0, now), a);
            if (fx.i1 == 1) {
                // Hearts: the number, a gap, then a heart, all centred.
                float w = FxText.width(fx.text), shift = (1f + 7f) / 2f * px;
                FxText.draw(out, fx.text, x - RR[0] * shift, rise, z - RR[2] * shift, px, main, shadow);
                float hx = (w / 2f + 1f + 3.5f) * px - shift;
                FxDraw.sprite(out, fx, FxSprites.HEART, x + RR[0] * hx, rise, z + RR[2] * hx, px, 0, a, now);
            } else {
                FxText.draw(out, fx.text, x, rise, z, px, main, shadow);
            }
        }
    };
}
