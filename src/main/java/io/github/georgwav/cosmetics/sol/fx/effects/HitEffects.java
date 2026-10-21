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
import io.github.georgwav.cosmetics.sol.fx.Vfx;
import io.github.georgwav.cosmetics.sol.fx.FxBits;
import io.github.georgwav.cosmetics.sol.fx.FxSoft;
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
            f.i0 = Math.max(8, Math.min(28, Math.round(16 * f.amount))) * (crit ? 2 : 1);
            f.i1 = crit ? 1 : 0;
            f.tx = dx;
            f.tz = dz;
            f.c = Math.min(-0.05f, ground(pool, x, z, y) - (float) y);
            FxPool.delay(f, delay);
        }
        if (bubbles.on() && (target == null || bubbles.allows(target))) {
            Fx f = start(pool, bubbles, BUBBLE, x, y, z, 0.9f);
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
    private static final float[] GRAVITY = {9f, 7f, -1.2f, 9f, -0.6f, 11f};
    private static final float[] SPEED = {1f, 0.95f, 0.85f, 1f, 0.8f, 1.7f};

    /** Where particle {@code i} is at {@code t} (into {@code p}), resting where it lands. */
    static void at(Fx fx, int i, float t, float[] p) {
        int s = fx.style;
        float h1 = FxDraw.hash(fx.seed, i * 4), h2 = FxDraw.signed(fx.seed, i * 4 + 1), h3 = FxDraw.hash(fx.seed, i * 4 + 2);
        float k = SPEED[s] * (fx.i1 == 1 ? 1.3f : 1f);
        // Off the body's surface: back toward you, out to the sides and up, fast at first and slowed by the air.
        float along = -(0.5f + 1.5f * h1) * k, side = h2 * 4.4f * k, up = (s == 4 || s == 2 ? 0.3f + 1.6f * h3 : 1.4f + 3.2f * h3) * k;
        float tx = (float) fx.tx, tz = (float) fx.tz;
        float vx = tx * along - tz * side, vz = tz * along + tx * side;
        float drag = s == 5 ? 1.3f : 2.1f;
        p[0] = Vfx.fly(vx, drag, 0f, t);
        p[1] = Math.max(fx.c, Vfx.fly(up, drag, -GRAVITY[s], t));
        p[2] = Vfx.fly(vz, drag, 0f, t);
    }

    /**
     * Hit particles: a flash and a cross of light at the point you hit (bigger and gold-white on a crit, with a ring
     * bursting out), then a spray off it in the picked style: glowing pixels, spinning stars, hearts drifting up,
     * crystals catching the light, orbs of light, or sparks streaking out and falling.
     */
    static final FxKind PARTICLES = new FxKind("hit_particles", 24) {
        private final float[] p = new float[3], q = new float[3];

        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            float t = fx.time(age);
            Vfx.origin(out, fx);
            int s = fx.style;
            float base = fx.base, e = fx.early(now), gk = FxBits.glowK(fx), sz = fx.size;
            boolean crit = fx.i1 == 1;
            int c = fx.tone(Ramp.MAIN, 0, now);
            int flash = crit ? 0xFFFFE7A8 : Vfx.white(c, 0.6f);
            // The flash where it struck, a ring of light bursting out and rays of light shot out round it.
            float fk = t / (crit ? 0.28f : 0.2f);
            if (fk < 1f) {
                float f = (1f - fk) * (1f - fk);
                if (out.glowPass()) {
                    Vfx.hot(out, 0f, 0f, 0f, (crit ? 1f : 0.65f) * sz, Vfx.with(flash, f * e * Math.max(0.6f, gk)));
                    Vfx.glow(out, 0f, 0f, 0f, (crit ? 1.8f : 1.2f) * sz, Vfx.with(c, 0.5f * f * e * gk));
                    Vfx.flare(out, 0f, 0f, 0f, (crit ? 2.3f : 1.5f) * sz, fx.seed * 0.7f, Vfx.with(flash, 0.9f * f * e * gk));
                }
                float rk = Vfx.outCubic(fk);
                int rays = crit ? 12 : 8;
                for (int i = 0; i < rays; i++) {
                    float dx = Vfx.s(fx.seed, i + 500), dy = Vfx.s(fx.seed, i + 510), dz = Vfx.s(fx.seed, i + 520);
                    float dl = (float) Math.sqrt(dx * dx + dy * dy + dz * dz) + 1e-4f;
                    dx /= dl;
                    dy /= dl;
                    dz /= dl;
                    float r = (0.12f + (crit ? 1.1f : 0.75f) * rk) * sz;
                    float len = (crit ? 0.45f : 0.32f) * sz * (1f - fk);
                    if (out.glowPass()) {
                        Vfx.streak(out, dx * r, dy * r, dz * r, dx, dy, dz, len, 0.09f * sz, Vfx.with(flash, f * e * gk));
                    } else {
                        Vfx.streak(out, dx * r, dy * r, dz * r, dx, dy, dz, len, 0.035f * sz, Vfx.with(0xFFFFFFFF, 0.7f * f * e));
                    }
                }
            }
            float ringLife = crit ? 0.32f : 0.24f;
            if (t < ringLife && out.glowPass()) {
                float k = t / ringLife;
                Vfx.sprite(out, FxSoft.RING, 0f, 0f, 0f, (0.15f + (crit ? 0.9f : 0.55f) * Vfx.outCubic(k)) * sz, 0f,
                        Vfx.with(crit ? 0xFFFFD27A : Vfx.white(c, 0.3f), (1f - k) * e * gk));
            }
            for (int i = 0; i < fx.i0; i++) {
                float h4 = FxDraw.hash(fx.seed, i * 4 + 3);
                float endAt = base * (0.55f + 0.45f * h4);
                float a = Math.min(1f, (endAt - t) / (base * 0.3f)) * e;
                if (a <= 0f) {
                    continue;
                }
                at(fx, i, t, p);
                at(fx, i, Math.max(0f, t - 0.03f), q);
                float x = p[0], y = p[1], z = p[2];
                float vx = (p[0] - q[0]) / 0.03f, vy = (p[1] - q[1]) / 0.03f, vz = (p[2] - q[2]) / 0.03f;
                float sp = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
                int col = fx.tone(Ramp.MAIN, i, now);
                int hot = Vfx.white(col, 0.55f);
                float spin = t * (6f + 8f * h4) * (i % 2 == 0 ? 1f : -1f) + h4 * 6f;
                switch (s) {
                    case 0 -> {
                        // Pixels: little squares of light streaking as they fly.
                        float side = (h4 < 0.3f ? 0.13f : 0.09f) * sz;
                        if (out.glowPass()) {
                            Vfx.glow(out, x, y, z, side * 3.2f, Vfx.with(col, 0.55f * a * gk));
                            if (sp > 0.5f) {
                                Vfx.streak(out, x, y, z, vx, vy, vz, Math.min(0.35f, sp * 0.04f) * sz, side * 1.2f, Vfx.with(hot, 0.6f * a * gk));
                            }
                        } else {
                            Vfx.sprite(out, FxSoft.SQUARE, x, y, z, side * 0.5f, spin * 0.3f, Vfx.with(h4 < 0.4f ? hot : col, a));
                        }
                    }
                    case 1 -> {
                        // Stars: turning as they tumble, glinting.
                        float r = (0.1f + 0.06f * h4) * sz;
                        if (out.glowPass()) {
                            Vfx.glow(out, x, y, z, r * 2.8f, Vfx.with(col, 0.45f * a * gk));
                            Vfx.glint(out, x, y, z, r * 2.2f * (0.6f + 0.4f * (float) Math.sin(t * 25f + i)), spin, Vfx.with(0xFFFFFFFF, 0.6f * a * gk));
                        } else {
                            Vfx.sprite(out, FxSoft.STAR, x, y, z, r, spin, Vfx.with(Vfx.white(col, 0.2f), a));
                        }
                    }
                    case 2 -> {
                        // Hearts: drifting up instead of falling, swaying, beating.
                        float r = (0.1f + 0.05f * h4) * sz * (1f + 0.12f * (float) Math.sin(t * 14f + i));
                        float hy = y, hx = x + (float) Math.sin(t * 5f + i) * 0.06f;
                        if (out.glowPass()) {
                            Vfx.glow(out, hx, hy, z, r * 3f, Vfx.with(col, 0.5f * a * gk));
                        } else {
                            Vfx.sprite(out, FxSoft.HEART, hx, hy, z, r, (float) Math.sin(t * 4f + i) * 0.3f, Vfx.with(Vfx.white(col, 0.35f), a));
                        }
                    }
                    case 3 -> {
                        // Crystals: shards turning, flashing as a face catches the light.
                        float r = (0.09f + 0.06f * h4) * sz;
                        if (out.glowPass()) {
                            float shine = (float) Math.pow(Math.max(0f, Math.sin(spin * 1.5f)), 10f);
                            Vfx.glow(out, x, y, z, r * 2.5f, Vfx.with(col, 0.4f * a * gk));
                            if (shine > 0.05f) {
                                Vfx.glint(out, x, y, z, r * 4f, spin, Vfx.with(0xFFFFFFFF, shine * a * gk));
                            }
                        } else {
                            float flip = 0.35f + 0.65f * Math.abs((float) Math.cos(spin));
                            Vfx.billboard(out, i % 2 == 0 ? FxSoft.DIAMOND : FxSoft.SHARD[i % 3], x, y, z, r * flip, r * 1.3f, spin * 0.25f,
                                    Vfx.with(Vfx.white(col, 0.25f), a));
                        }
                    }
                    case 4 -> {
                        // Orbs: soft balls of light floating up.
                        float r = (0.08f + 0.06f * h4) * sz;
                        float oy = y;
                        if (out.glowPass()) {
                            Vfx.hot(out, x, oy, z, r * 4f, Vfx.with(Vfx.white(col, 0.3f), 0.8f * a * gk));
                        } else {
                            Vfx.sprite(out, FxSoft.ORB, x, oy, z, r, 0f, Vfx.with(Vfx.white(col, 0.5f), 0.65f * a));
                        }
                    }
                    default -> {
                        // Sparks: fast, white-hot, cooling to the colour.
                        int sc = Vfx.mix(0xFFFFFFFF, col, Math.min(1f, t / (base * 0.6f)));
                        float len = Math.max(0.06f, Math.min(0.5f, sp * 0.05f)) * sz;
                        if (out.glowPass()) {
                            Vfx.streak(out, x, y, z, vx, vy, vz, len, 0.1f * sz, Vfx.with(sc, a * gk));
                        } else {
                            Vfx.streak(out, x, y, z, vx, vy, vz, len, 0.045f * sz, Vfx.with(col, 0.75f * a));
                        }
                    }
                }
            }
        }
    };

    /** A bubble: swells at the hit point, wobbling, and pops: a flash, a ring of spray and droplets falling. */
    static final FxKind BUBBLE = new FxKind("hit_bubble", 8) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            float t = fx.time(age);
            Vfx.origin(out, fx);
            float e = fx.early(now), gk = FxBits.glowK(fx), sz = fx.size;
            boolean glow = out.glowPass();
            int c = fx.tone(Ramp.MAIN, 0, now);
            // The film's sheen: the colour, and a hue a little round from it.
            int sheen = Vfx.hue(c, 70f);
            float pop = 0.3f;
            float r = 0.36f * sz;
            if (t < pop) {
                // Swelling fast, then wobbling as it hangs there.
                float k = Vfx.outBack(Math.min(1f, t / 0.13f));
                float wob = 1f + 0.09f * (float) Math.sin(t * 55f) * (1f - t / pop);
                float hw = r * k * wob, hh = r * k / wob;
                float spin = t * 1.5f;
                if (glow) {
                    Vfx.glow(out, 0f, 0f, 0f, r * 2.2f * k, Vfx.with(c, 0.3f * e * gk));
                    Vfx.billboard(out, FxSoft.RING, 0f, 0f, 0f, hw * 1.08f, hh * 1.08f, spin, Vfx.with(c, 0.45f * e * gk));
                    Vfx.billboard(out, FxSoft.BUBBLE, 0f, 0f, 0f, hw, hh, spin, Vfx.with(0xFFFFFFFF, 0.6f * e * gk));
                } else {
                    Vfx.billboard(out, FxSoft.GLOW, 0f, 0f, 0f, hw * 1.05f, hh * 1.05f, 0f, Vfx.with(Vfx.mix(c, sheen, 0.5f), 0.22f * e));
                    Vfx.billboard(out, FxSoft.RING, 0f, 0f, 0f, hw * 1.05f, hh * 1.05f, spin, Vfx.with(Vfx.white(sheen, 0.3f), 0.6f * e));
                    Vfx.billboard(out, FxSoft.BUBBLE, 0f, 0f, 0f, hw, hh, spin, Vfx.with(0xFFFFFFFF, 0.95f * e));
                }
                return;
            }
            float dt = t - pop;
            if (dt < 0.22f) {
                // The pop: a flash and the film's ring torn outward.
                float k = dt / 0.22f, f = (1f - k) * (1f - k);
                if (glow) {
                    Vfx.hot(out, 0f, 0f, 0f, r * 1.8f, Vfx.with(Vfx.white(c, 0.7f), f * e * gk));
                    Vfx.glint(out, 0f, 0f, 0f, r * 2.4f, 0.4f, Vfx.with(0xFFFFFFFF, f * e * gk));
                    Vfx.sprite(out, FxSoft.RING, 0f, 0f, 0f, r * (1f + 1.8f * Vfx.outCubic(k)), 0f, Vfx.with(Vfx.white(c, 0.4f), f * e * gk));
                } else {
                    Vfx.sprite(out, FxSoft.RING, 0f, 0f, 0f, r * (1f + 1.8f * Vfx.outCubic(k)), 0f, Vfx.with(Vfx.white(sheen, 0.4f), 0.8f * f * e));
                }
            }
            // Droplets flung out round the ring as it bursts, then falling.
            out.right(RR);
            out.up(UU);
            for (int k = 0; k < 16; k++) {
                double ang = k * Math.PI / 8 + FxDraw.hash(fx.seed, k) * 0.35;
                float ox = (float) Math.cos(ang), oy = (float) Math.sin(ang);
                float sx = RR[0] * ox + UU[0] * oy, sy = RR[1] * ox + UU[1] * oy, sz2 = RR[2] * ox + UU[2] * oy;
                float v = (2.2f + 1.6f * FxDraw.hash(fx.seed, k + 20)) * sz;
                float d = r + Vfx.fly(v, 3.2f, 0f, dt);
                float x = sx * d, z = sz2 * d;
                float y = Math.max(fx.c, sy * d + Vfx.fly(0.6f * sz, 1.2f, -9f, dt) - 0.6f * sz * dt);
                float a = Vfx.life(dt, 0.6f, 0f, 0.3f) * e;
                float size = (0.035f + 0.03f * FxDraw.hash(fx.seed, k + 40)) * sz;
                int col = (k & 1) == 0 ? c : sheen;
                if (glow) {
                    Vfx.hot(out, x, y, z, size * 4.5f, Vfx.with(Vfx.white(col, 0.4f), 0.7f * a * gk));
                } else {
                    Vfx.sprite(out, FxSoft.ORB, x, y, z, size, 0f, Vfx.with(Vfx.white(col, 0.3f), a));
                }
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
            float px = 0.85f * T * fx.size * pop * (fx.i1 == 2 ? 1.35f : 1f);
            out.right(RR);
            float x = RR[0] * fx.a, z = RR[2] * fx.a;
            int col = fx.i1 == 2 ? KILL_RED : fx.i1 == 1 ? 0xFFFFD86B : fx.tone(Ramp.MAIN, 0, now);
            if (out.glowPass()) {
                Vfx.origin(out, fx);
                float w = FxText.width(fx.text) * px;
                float gk = FxBits.glowK(fx);
                for (int k = -1; k <= 1; k++) {
                    Vfx.glow(out, x + RR[0] * k * w * 0.3f, rise, z + RR[2] * k * w * 0.3f, w * 0.45f, Vfx.with(col, 0.32f * a * gk));
                }
                if (fx.i1 != 0 && t < 0.35f) {
                    Vfx.glint(out, x + RR[0] * w * 0.55f, rise + 3f * px, z + RR[2] * w * 0.55f, 0.35f * fx.size, t * 6f,
                            Vfx.with(0xFFFFFFFF, (1f - t / 0.35f) * a * gk));
                }
                return;
            }
            FxText.draw(out, fx.text, x, rise, z, px, Colors.withAlpha(Vfx.white(col, 0.15f), a), Colors.withAlpha(Vfx.mix(col, 0xFF000000, 0.7f), a));
        }
    };

    /** A damage number: pops over the head, rises 0.6 blocks and fades; hearts with a heart after it. */
    static final FxKind DAMAGE = new FxKind("hit_damage", 16) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            if (fx.text == null) {
                return;
            }
            float t = fx.time(age);
            FxDraw.origin(out, fx);
            float pop = Ramp.pop(t, 0.15f, fx.snap);
            if (out.glowPass()) {
                // A soft light behind the number (a crit's twinkles too).
                float rise = 0.6f * KillEffects.easeOut(t);
                float a = (t < 0.65f ? 1f : 1f - (t - 0.65f) / 0.35f) * fx.early(now);
                Vfx.origin(out, fx);
                float w = FxText.width(fx.text) * 0.75f * T * fx.size * pop;
                int col = fx.tone(Ramp.MAIN, 0, now);
                Vfx.glow(out, fx.a, rise, fx.b, Math.max(0.25f, w * 0.7f), Vfx.with(col, 0.35f * a));
                if (col == CRIT_GOLD && t < 0.4f) {
                    Vfx.glint(out, fx.a + w * 0.4f, rise + 0.12f, fx.b, 0.3f * fx.size, t * 5f, Vfx.with(0xFFFFFFFF, (1f - t / 0.4f) * a));
                }
                return;
            }
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
