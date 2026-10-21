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
import io.github.georgwav.cosmetics.sol.fx.FxScreen;
import io.github.georgwav.cosmetics.sol.fx.FxSettings;
import io.github.georgwav.cosmetics.sol.fx.FxSprites;
import io.github.georgwav.cosmetics.sol.fx.FxText;
import io.github.georgwav.cosmetics.sol.fx.FxWorld;
import io.github.georgwav.cosmetics.sol.fx.PoseSnapshot;
import io.github.georgwav.cosmetics.sol.fx.Ramp;
import io.github.georgwav.cosmetics.sol.fx.Vfx;
import io.github.georgwav.cosmetics.sol.fx.FxBits;
import io.github.georgwav.cosmetics.sol.fx.FxSoft;
import io.github.georgwav.cosmetics.sol.fx.Bolts;
import io.github.georgwav.cosmetics.sol.fx.SolPicks;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.util.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * Kill Effects (C.4): when something you hit in the last three seconds dies, one effect where it fell (or one of the
 * ticked ones at random): Soul rise, Lightning (moved here from PvP Effects), Firework, Shatter, Black hole, Sky
 * laser, Gravestone, Confetti, Burning copy, Burst and Shockwave. Each is built of light ({@link Vfx}, {@link FxBits}):
 * a flash, light thrown on the ground, sparks, embers and smoke round its own main act. Only on your screen; nothing
 * is spawned in the world.
 */
public final class KillEffects extends FxModule {
    public static final List<String> STYLES = List.of("soul_rise", "lightning", "firework", "shatter", "black_hole", "sky_laser",
            "gravestone", "confetti", "burning_copy", "burst", "shockwave");
    /** Each style's length at 100%, seconds. */
    private static final float[] BASE = {2.4f, 1.6f, 2.9f, 2.0f, 2.25f, 2.0f, 5f, 3.2f, 2.6f, 3f, 1.8f};

    public final FxSettings fx = fx("kill", STYLES, new FxSettings.Spec().random().sound("chime").who(true));
    /** Burst's colours: the effect's own, red, or soul blue. */
    public final Setting.Choice burstColours = add(new Setting.Choice("kill_burst_colours", "theme", List.of("theme", "red", "soul"))
            .group("kill"));

    public KillEffects() {
        super("kill_effects", Category.VIEW, "skull");
        burstColours.shownWhen(() -> fx.style.get().equals("burst") || fx.random.get());
        FxEvents.KILL.add(new FxEvents.Kill() {
            @Override
            public boolean listening() {
                return enabled();
            }

            @Override
            public void killed(LivingEntity dead, PoseSnapshot copy) {
                if (fx.allows(dead)) {
                    play(FxWorld.POOL, dead.getX(), dead.getY(), dead.getZ(), dead.getYRot(), copy, dead.getName().getString(),
                            dead.getId());
                }
            }
        });
        SolPicks.module(this);
        SolPicks.set(fx.style, "soul_rise");
        SolPicks.set(fx.random, false);
    }

    @Override
    public boolean hidesDummy() {
        return true;
    }

    @Override
    public float tryIt(FxPool pool, FxPreview.Dummy dummy) {
        String name = Minecraft.getInstance().getUser().getName();
        return play(pool, dummy.x, dummy.y, dummy.z, dummy.yaw, dummy.body, name, (int) (System.nanoTime() >>> 8));
    }

    /** Plays the kill effect (picked, or random from the ticked ones) where {@code copy} fell. */
    float play(FxPool pool, double x, double y, double z, float yaw, PoseSnapshot copy, String name, int seed) {
        int style = fx.pick(seed);
        FxKind kind = KINDS[style];
        Fx f = start(pool, fx, kind, x, y, z, BASE[style]);
        fx.apply(f, style);
        f.copy = copy;
        f.text = name;
        f.yaw = yaw;
        f.c = ground(pool, x, z, y + 0.5) - (float) y;
        Tokens tk = ThemeState.tokens();
        if (style == 9) {
            // Burst's own colours.
            switch (burstColours.get()) {
                case "red" -> FxColour.fixed(f, 0xFFD8302C);
                case "soul" -> FxColour.fixed(f, 0xFF46D6E6);
                default -> {
                }
            }
        } else if (style == 8 && fx.colourMode.get().equals("theme")) {
            // The burning copy is orange, unless you pick its colour.
            FxColour.fixed(f, 0xFFFF7A1E);
        } else if (style == 7) {
            // Confetti: five of the preset's colours.
            f.palette[0] = 0xFF000000 | tk.accent;
            f.palette[1] = 0xFF000000 | tk.glow;
            f.palette[2] = 0xFF000000 | tk.skyGlow;
            f.palette[3] = Colors.shiftHue(0xFF000000 | tk.accent, 150f, 1f);
            f.palette[4] = 0xFFFFFFFF;
            if (!fx.colourMode.get().equals("theme")) {
                f.palette[0] = f.ramp[Ramp.MAIN];
                f.palette[1] = f.ramp[Ramp.CORE];
                f.palette[2] = f.ramp2[Ramp.MAIN];
                f.palette[3] = f.ramp2[Ramp.CORE];
            }
        }
        if (audible(pool)) {
            fx.play("kill", 1f);
            if (style == 5) {
                FxScreen.flash(f.ramp[Ramp.CORE], 0.05f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.3f, 0.15f, 0.06f);
            }
            if (style == 1) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.LIGHTNING_BOLT_THUNDER, 1.1f, 0.3f));
                FxScreen.flash(0xFFFFFFFF, 0.04f, 0.0f, 0.38f, 0.12f, 0.3f, 0.08f, 0.04f);
            }
        }
        return f.life;
    }

    // ------------------------------------------------------------------ the styles

    private static final float[] P = new float[3];

    static float easeOut(float k) {
        k = Math.max(0f, Math.min(1f, k));
        return 1f - (1f - k) * (1f - k);
    }

    /** The body's height (the copy's, else a player's). */
    private static float height(Fx fx) {
        return fx.copy != null ? fx.copy.height : 1.8f;
    }

    /** The effect's second colour: the picked second one, else its own a little round the wheel. */
    private static int second(Fx fx, float now) {
        return fx.two ? fx.tone(Ramp.MAIN, 1, now) : Vfx.hue(fx.tone(Ramp.MAIN, 0, now), 28f);
    }

    /**
     * Soul rise: a rune circle opens under the body, a pillar of light comes down, and the body lifts out of itself as a
     * glowing spirit, two strands of light spiralling up round it and embers drifting after.
     */
    static final FxKind SOUL_RISE = new FxKind("kill_soul_rise", 6) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            float t = fx.time(age);
            Vfx.origin(out, fx);
            float s = fx.size, gy = fx.c, e = fx.early(now), gk = FxBits.glowK(fx);
            float h = height(fx) * s;
            int c = fx.tone(Ramp.MAIN, 0, now), c2 = second(fx, now);
            FxBits.impact(out, fx, t, 0f, gy + h * 0.45f, 0f, gy, 0.65f * s, c);
            // The rune circle on the ground, turning as it opens and fades.
            float rk = Vfx.life(t, 2.3f, 0.3f, 1f) * e;
            float rr = 1.7f * s * (0.7f + 0.3f * Vfx.outBack(t / 0.45f));
            if (out.glowPass()) {
                Vfx.decalGround(out, FxSoft.MAGIC, 0f, gy, 0f, rr, t * 0.8f, Vfx.with(c, 0.95f * rk * gk));
                Vfx.decalGround(out, FxSoft.GLOW, 0f, gy, 0f, rr * 1.3f, 0f, Vfx.with(c, 0.35f * rk * gk));
            } else {
                Vfx.decalGround(out, FxSoft.MAGIC, 0f, gy, 0f, rr, t * 0.8f, Vfx.with(c, 0.5f * rk));
            }
            // The pillar of light, from the sky down to the circle.
            float pk = Vfx.life(t, 1.7f, 0.12f, 1.1f) * e;
            if (out.glowPass()) {
                Vfx.line(out, FxSoft.LINE, 0f, gy, 0f, 0f, gy + 9f * s, 0f, 2.4f * s, Vfx.with(c, 0.5f * pk * gk), Vfx.with(c, 0f));
                Vfx.line(out, FxSoft.TUBE, 0f, gy, 0f, 0f, gy + 7f * s, 0f, 0.7f * s, Vfx.with(Vfx.white(c, 0.55f), 0.7f * pk * gk),
                        Vfx.with(c, 0f));
            } else {
                Vfx.line(out, FxSoft.LINE, 0f, gy, 0f, 0f, gy + 6f * s, 0f, 1.2f * s, Vfx.with(c, 0.25f * pk), Vfx.with(c, 0f));
            }
            // The spirit lifts out and climbs, growing a little as it goes.
            float rise = 2.8f * s * Vfx.outCubic(t / 2.1f);
            float sa = Vfx.life(t, 2.1f, 0.06f, 1.3f) * e;
            FxBits.spirit(out, fx, 0f, gy + rise, 0f, s * (1f + 0.12f * t), c, sa);
            // Two strands of light spiralling up round it.
            int per = Math.max(10, Math.round(26 * fx.amount));
            float ha = Vfx.life(t, 2.25f, 0.2f, 1f) * e;
            for (int strand = 0; strand < 2; strand++) {
                for (int j = 0; j < per; j++) {
                    float ph = j / (float) per;
                    float y = gy + rise * 0.85f + (ph * 1.25f - 0.05f) * h;
                    float ang = strand * (float) Math.PI + ph * (float) Math.PI * 3.2f + t * 5.5f;
                    float rad = (0.62f + 0.12f * (float) Math.sin(t * 3f + ph * 6f)) * s * (1f - 0.25f * t / 2.4f);
                    float x = (float) Math.cos(ang) * rad, z = (float) Math.sin(ang) * rad;
                    float tx = -(float) Math.sin(ang) * rad * 5.5f, tz = (float) Math.cos(ang) * rad * 5.5f;
                    float a = ha * (0.35f + 0.65f * ph);
                    int col = Vfx.mix(c, c2, ph);
                    if (out.glowPass()) {
                        Vfx.streak(out, x, y, z, tx, 0.8f, tz, 0.3f * s, 0.11f * s, Vfx.with(Vfx.white(col, 0.35f), a * gk));
                    } else {
                        Vfx.streak(out, x, y, z, tx, 0.8f, tz, 0.25f * s, 0.06f * s, Vfx.with(col, 0.55f * a));
                    }
                }
            }
            FxBits.embers(out, fx, t, 0f, gy + 0.15f, 0f, Math.round(55 * fx.amount), 0.9f * s, 1.4f, 1.3f, 1.6f, c, s, 1);
        }
    };

    /**
     * Lightning: a leader races down from the sky, the strike lands with a white flash and strikes again two or three
     * times, changing shape; arcs crawl over the ground, sparks fly, and a molten scorch smokes where it hit.
     */
    static final FxKind LIGHTNING = new FxKind("kill_lightning", 4) {
        /** The strikes: when each starts and ends, and how bright it is. */
        private final float[] from = {0.05f, 0.2f, 0.36f, 0.55f}, to = {0.16f, 0.28f, 0.47f, 0.62f}, power = {1f, 0.55f, 0.9f, 0.4f};

        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            float t = fx.time(age);
            Vfx.origin(out, fx);
            float s = fx.size, gy = fx.c, e = fx.early(now), gk = FxBits.glowK(fx);
            int c = fx.tone(Ramp.MAIN, 0, now);
            float top = gy + 24f * s;
            float sx = Vfx.s(fx.seed, 1) * 4f * s, sz = Vfx.s(fx.seed, 2) * 4f * s;
            // The leader, racing down.
            if (t < 0.05f) {
                Bolts.build(fx.seed, sx, top, sz, 0f, gy, 0f, 0.13f, 2);
                Bolts.draw(out, 0.05f * s, 0.5f * s, Vfx.with(0xFFFFFFFF, 0.6f * e * gk), Vfx.with(c, 0.4f * e * gk), t / 0.05f);
            }
            for (int k = 0; k < from.length; k++) {
                if (t < from[k] || t > to[k]) {
                    continue;
                }
                float i = power[k] * (float) Math.sqrt(1f - (t - from[k]) / (to[k] - from[k])) * e;
                // Restrikes follow the first channel, then wander off it.
                Bolts.build(fx.seed + (k == 2 ? 0 : k * 13), sx, top, sz, 0f, gy, 0f, 0.13f, k == 0 ? 4 : 3);
                Bolts.draw(out, 0.14f * s, 1.5f * s, Vfx.with(0xFFFFFFFF, i * Math.max(0.7f, gk)), Vfx.with(c, 0.8f * i * gk), 1f);
                if (out.glowPass()) {
                    // The sky lit up above, and the ground below.
                    Vfx.glow(out, sx * 0.6f, top - 4f * s, sz * 0.6f, 14f * s, Vfx.with(c, 0.22f * i * gk));
                    Vfx.hot(out, 0f, gy + 0.3f, 0f, 2.6f * s, Vfx.with(0xFFFFFFFF, 0.8f * i * gk));
                }
                // The body, lit white by the strike.
                if (k < 2) {
                    FxBits.spirit(out, fx, 0f, gy, 0f, s, Vfx.white(c, 0.7f), i);
                }
            }
            FxBits.impact(out, fx, t - 0.05f, 0f, gy + 0.4f * s, 0f, gy, 1.25f * s, c);
            // Arcs crawling over the ground from the strike.
            if (t > 0.05f && t < 0.6f) {
                int arcs = 5;
                float flick = (int) (t * 24f);
                for (int k = 0; k < arcs; k++) {
                    if (Vfx.h(fx.seed, (int) flick * 7 + k) < 0.35f) {
                        continue;
                    }
                    double ang = (k + Vfx.h(fx.seed, 40 + k) * 0.6f) * Math.PI * 2 / arcs;
                    float len = (1.2f + 1.6f * Vfx.h(fx.seed, 50 + k)) * s * Math.min(1f, (t - 0.05f) / 0.12f);
                    float ex = (float) Math.cos(ang) * len, ez = (float) Math.sin(ang) * len;
                    Bolts.build(fx.seed + 100 + k * 31 + (int) flick * 5, 0f, gy + 0.08f, 0f, ex, Vfx.groundAt(ex, gy, ez) + 0.06f, ez, 0.22f, 1);
                    float a = (1f - (t - 0.05f) / 0.55f) * e;
                    Bolts.draw(out, 0.045f * s, 0.55f * s, Vfx.with(0xFFFFFFFF, 0.9f * a * gk), Vfx.with(c, 0.7f * a * gk), 1f);
                }
            }
            FxBits.sparks(out, fx, t - 0.05f, 0f, gy + 0.3f, 0f, Math.round(70 * fx.amount), 8.5f * s, 0.65f, -16f, 2.2f, 1.0f, c, s, gy, 3);
            // The scorch, its cracks glowing and cooling, smoke rising off it.
            float sc = Vfx.life(t, 1.6f, 0.06f, 0.55f) * e;
            if (!out.glowPass()) {
                Vfx.decalGround(out, FxSoft.SCORCH, 0f, gy, 0f, 1.5f * s, fx.seed * 0.1f, Vfx.with(0xFF120D0A, 0.85f * sc));
            } else {
                float cool = Vfx.life(t, 1.3f, 0.06f, 0.9f) * e;
                Vfx.decalGround(out, FxSoft.CRACKS, 0f, gy, 0f, 1.35f * s, fx.seed * 0.1f, Vfx.with(Vfx.white(c, 0.25f), cool * gk));
            }
            FxBits.smoke(out, fx, t - 0.12f, 0f, gy + 0.2f, 0f, 9, 0.45f * s, 1.1f, 0.45f, 1.3f, 0.5f * s, 0xFF2B2A30, 0.55f * e, 5);
            FxBits.embers(out, fx, t - 0.1f, 0f, gy + 0.1f, 0f, Math.round(30 * fx.amount), 0.8f * s, 1.2f, 0.6f, 1.0f, c, s, 7);
        }
    };

    /**
     * Firework: a rocket climbs on a spitting trail and bursts into a sphere of stars in two colours, a ring of gold
     * inside it; the stars streak out, slow, fall and crackle as they die, smoke hanging where it burst.
     */
    static final FxKind FIREWORK = new FxKind("kill_firework", 5) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            float t = fx.time(age);
            Vfx.origin(out, fx);
            float s = fx.size, gy = fx.c, e = fx.early(now), gk = FxBits.glowK(fx);
            int c = fx.tone(Ramp.MAIN, 0, now), c2 = second(fx, now);
            float launch = 0.6f;
            float apex = gy + 10f * s;
            if (t < launch) {
                float k = t / launch;
                float y = gy + 1f + (apex - gy - 1f) * Vfx.outCubic(k);
                float vy = (apex - gy - 1f) * 3f * (1f - k) * (1f - k) / launch;
                if (out.glowPass()) {
                    Vfx.hot(out, 0f, y, 0f, 0.55f * s, Vfx.with(Vfx.white(c, 0.7f), e * Math.max(0.6f, gk)));
                    Vfx.streak(out, 0f, y, 0f, 0f, vy, 0f, Math.min(3f, vy * 0.12f) * s, 0.22f * s, Vfx.with(c, 0.9f * e * gk));
                } else {
                    Vfx.streak(out, 0f, y, 0f, 0f, vy, 0f, Math.min(2f, vy * 0.08f) * s, 0.08f * s, Vfx.with(0xFFFFFFFF, 0.85f * e));
                }
                // Sparks spat from its tail.
                for (int i = 0; i < 26; i++) {
                    float born = i / 26f * launch;
                    float a2 = t - born;
                    if (a2 < 0f || a2 > 0.5f) {
                        continue;
                    }
                    float by = gy + 1f + (apex - gy - 1f) * Vfx.outCubic(born / launch);
                    float dx = Vfx.s(fx.seed, i + 900) * 1.2f, dz = Vfx.s(fx.seed, i + 950) * 1.2f;
                    float px = dx * a2, pz = dz * a2, py = by + Vfx.fly(-1f, 1f, -9f, a2);
                    float a = (1f - a2 / 0.5f) * e;
                    if (out.glowPass()) {
                        Vfx.hot(out, px, py, pz, 0.18f * s, Vfx.with(Vfx.white(c, 0.5f), a * gk));
                    }
                }
                FxBits.smoke(out, fx, t, 0f, gy + 1f, 0f, 6, 0.15f * s, 1.2f, launch, 1.2f, 0.3f * s, 0xFF8A8A96, 0.35f * e, 11);
                return;
            }
            float tb = t - launch;
            // The burst's flash lights the sky and, faintly, the ground.
            if (out.glowPass() && tb < 0.5f) {
                float k = tb / 0.5f, f = (1f - k) * (1f - k);
                Vfx.hot(out, 0f, apex, 0f, 2.8f * s, Vfx.with(0xFFFFFFFF, f * e * Math.max(0.6f, gk)));
                Vfx.glow(out, 0f, apex, 0f, 11f * s, Vfx.with(c, 0.45f * f * e * gk));
                Vfx.flare(out, 0f, apex, 0f, 6f * s, 0.3f, Vfx.with(Vfx.white(c, 0.6f), 0.8f * f * e * gk));
                Vfx.pool(out, 0f, gy, 0f, 7f * s, Vfx.with(c, 0.3f * f * e * gk));
            }
            // The shell: stars over a sphere.
            int n = Math.round(190 * fx.amount);
            float drag = 1.35f, g = -3.2f, speed = 11.5f * s;
            for (int i = 0; i < n; i++) {
                float life = 1.5f + 0.7f * Vfx.h(fx.seed, i + 300);
                if (tb > life) {
                    continue;
                }
                float yy = 1f - (i + 0.5f) / n * 2f;
                float rr = (float) Math.sqrt(Math.max(0f, 1f - yy * yy));
                double th = i * 2.39996323 + Vfx.h(fx.seed, 5) * 6.28;
                float dx = (float) Math.cos(th) * rr, dz = (float) Math.sin(th) * rr, dy = yy;
                float v = speed * (0.9f + 0.1f * Vfx.h(fx.seed, i + 200));
                float d = Vfx.fly(v, drag, 0f, tb), vs = Vfx.speed(v, drag, 0f, tb);
                float px = dx * d, pz = dz * d, py = apex + dy * d + Vfx.fly(0f, drag, g, tb);
                float vx = dx * vs, vz = dz * vs, vy = dy * vs + Vfx.speed(0f, drag, g, tb);
                float k = tb / life;
                float a = (float) Math.pow(1f - k, 0.8f) * e;
                // Crackle: in its last third a star blinks.
                if (k > 0.66f && Vfx.h(fx.seed, i * 31 + (int) (tb * 22f)) < 0.45f) {
                    a *= 0.15f;
                }
                int col = (i & 1) == 0 ? c : c2;
                float sp = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
                float len = Math.max(0.12f, Math.min(1.6f, sp * 0.09f)) * s;
                if (out.glowPass()) {
                    Vfx.streak(out, px, py, pz, vx, vy, vz, len * 1.4f, 0.2f * s, Vfx.with(Vfx.white(col, 0.45f * (1f - k)), a * gk));
                    if ((i & 7) == 0) {
                        Vfx.glow(out, px, py, pz, 0.6f * s, Vfx.with(col, 0.3f * a * gk));
                    }
                    if (k > 0.66f && (i % 3) == 0) {
                        Vfx.glint(out, px, py, pz, 0.35f * s, tb * 3f + i, Vfx.with(0xFFFFFFFF, a * gk));
                    }
                } else {
                    Vfx.streak(out, px, py, pz, vx, vy, vz, len * 0.8f, 0.07f * s, Vfx.with(col, 0.7f * a));
                }
            }
            // A ring of gold inside the shell.
            int ring = Math.round(40 * fx.amount);
            for (int i = 0; i < ring; i++) {
                if (tb > 1.2f) {
                    break;
                }
                double th = i * 2 * Math.PI / ring;
                float v = 5f * s;
                float d = Vfx.fly(v, 2f, 0f, tb), vs = Vfx.speed(v, 2f, 0f, tb);
                float px = (float) Math.cos(th) * d, pz = (float) Math.sin(th) * d * 0.35f, py = apex + (float) Math.sin(th) * d * 0.9f
                        + Vfx.fly(0f, 2f, -3f, tb);
                float a = (1f - tb / 1.2f) * e;
                if (out.glowPass()) {
                    Vfx.streak(out, px, py, pz, (float) Math.cos(th) * vs, (float) Math.sin(th) * vs * 0.9f, (float) Math.sin(th) * vs * 0.35f,
                            Math.max(0.1f, vs * 0.08f) * s, 0.13f * s, Vfx.with(0xFFFFD27A, a * gk));
                } else {
                    Vfx.sprite(out, FxSoft.DOT, px, py, pz, 0.05f * s, 0f, Vfx.with(0xFFFFD27A, 0.8f * a));
                }
            }
            FxBits.smoke(out, fx, tb, 0f, apex - 0.3f, 0f, 10, 2.2f * s, 0.25f, 0.3f, 2.1f, 0.9f * s, 0xFF9A98A8, 0.12f * e, 13);
        }
    };

    /**
     * Shatter: cracks of light run over the body, then it bursts into shards of its own skin that tumble out,
     * catching the light as they turn, and skitter to rest on the ground.
     */
    static final FxKind SHATTER = new FxKind("kill_shatter", 4) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            float t = fx.time(age);
            Vfx.origin(out, fx);
            float s = fx.size, gy = fx.c, e = fx.early(now), gk = FxBits.glowK(fx);
            float h = height(fx) * s;
            int c = fx.tone(Ramp.MAIN, 0, now);
            float crack = 0.18f;
            if (t < crack) {
                // Whole, cracking: the body, and light running through it.
                if (fx.copy != null && !out.glowPass()) {
                    fx.copy.drawFaces(out, 0f, gy, 0f, s, null, Vfx.with(0xFFFFFFFF, e), false);
                }
                if (out.glowPass()) {
                    float k = t / crack;
                    Vfx.sprite(out, FxSoft.CRACKS, 0f, gy + h * 0.55f, 0f, h * 0.55f * (0.4f + 0.8f * k), Vfx.s(fx.seed, 1),
                            Vfx.with(Vfx.white(c, 0.6f), k * e * Math.max(0.6f, gk)));
                }
                return;
            }
            float tp = t - crack;
            FxBits.impact(out, fx, tp, 0f, gy + h * 0.55f, 0f, gy, 0.75f * s, c);
            if (fx.copy == null) {
                return;
            }
            int rows = fx.copy.rowCount();
            int stride = Math.max(1, rows / Math.max(80, Math.round(320 * fx.amount)));
            double wind = Math.toRadians(fx.yaw + 90f + Vfx.s(fx.seed, 1) * 40f);
            float wx = (float) Math.cos(wind), wz = (float) Math.sin(wind);
            float cy = h * 0.5f;
            for (int r = 0, k = 0; r < rows; r += stride, k++) {
                int col = fx.copy.row(r, P);
                if ((col >>> 24) < 40) {
                    continue;
                }
                float bx = P[0] * s, by = gy + P[1] * s, bz = P[2] * s;
                // Out from the body's middle, and a little on the wind.
                float ox = bx, oy = by - gy - cy, oz = bz;
                float ol = (float) Math.sqrt(ox * ox + oy * oy + oz * oz) + 0.15f;
                float v = (2.5f + 3.5f * Vfx.h(fx.seed, k)) * s;
                float vx = ox / ol * v + wx * 1.2f, vy = oy / ol * v + 2.2f * Vfx.h(fx.seed, k + 7), vz = oz / ol * v + wz * 1.2f;
                float x = bx + Vfx.fly(vx, 1.1f, 0f, tp), z = bz + Vfx.fly(vz, 1.1f, 0f, tp);
                float y = by + Vfx.fly(vy, 1.1f, -14f, tp);
                boolean down = y <= gy + 0.03f;
                if (down) {
                    y = gy + 0.03f;
                }
                float life = 1.3f + 0.4f * Vfx.h(fx.seed, k + 13);
                float a = Vfx.life(tp, life, 0f, 0.6f) * e;
                if (a <= 0f) {
                    continue;
                }
                float spin = (down ? 0f : tp) * (4f + 8f * Vfx.h(fx.seed, k + 21)) + Vfx.h(fx.seed, k + 3) * 6f;
                float size = (0.035f + 0.055f * Vfx.h(fx.seed, k + 5)) * s * (down ? Math.max(0f, 1f - (tp - 0.6f) / 0.9f) : 1f);
                if (out.glowPass()) {
                    // A shard catches the light as it turns past the camera.
                    float shine = (float) Math.pow(Math.max(0f, Math.sin(spin * 1.7f)), 12f);
                    if (shine > 0.05f && !down) {
                        Vfx.glint(out, x, y, z, size * 4f, spin, Vfx.with(0xFFFFFFFF, shine * a * gk));
                    }
                } else {
                    float flip = (float) Math.abs(Math.cos(spin));
                    Vfx.billboard(out, FxSoft.SHARD[k % 3], x, y, z, size * (0.3f + 0.7f * flip), size * 1.2f, spin * 0.5f,
                            Vfx.with(col | 0xFF000000, a));
                }
            }
            FxBits.sparks(out, fx, tp, 0f, gy + cy, 0f, Math.round(30 * fx.amount), 6f * s, 0.5f, -10f, 2f, 0.7f, 0xFFFFFFFF, s, gy, 4);
            FxBits.smoke(out, fx, tp, 0f, gy + cy * 0.6f, 0f, 7, 0.5f * s, 0.6f, 0.2f, 1.2f, 0.45f * s, 0xFFB8B6C2, 0.35f * e, 6);
        }
    };

    /**
     * Black hole: a point of darkness opens into a black sphere ringed with light, a disc of glowing matter swirling
     * round it at a slant; streams of light and the body itself spiral in, stretched; it collapses and goes off in a
     * flash and a shock ring.
     */
    static final FxKind BLACK_HOLE = new FxKind("kill_black_hole", 3) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            float t = fx.time(age);
            Vfx.origin(out, fx);
            float s = fx.size, gy = fx.c, e = fx.early(now), gk = FxBits.glowK(fx);
            float h = height(fx) * s;
            int c = fx.tone(Ramp.MAIN, 0, now), c2 = second(fx, now);
            float cy = gy + h * 0.6f;
            float collapse = 1.75f, boom = 1.95f;
            float open = t < 0.4f ? Vfx.outBack(t / 0.4f) : t < collapse ? 1f + 0.04f * (float) Math.sin(t * 9f)
                    : t < boom ? 1f - (float) Math.pow((t - collapse) / (boom - collapse), 2f) : 0f;
            float R = 0.75f * s * open;
            if (R > 0.01f) {
                // Its disc, swirling at a slant toward you.
                float tilt = 0.32f;
                float nx = 0f, ny = 1f, nz = 0f;
                // Lean the disc's face toward the camera a little: read the camera from the origin.
                float ex = (float) (out.camX() - fx.x), ez = (float) (out.camZ() - fx.z);
                float el = (float) Math.sqrt(ex * ex + ez * ez) + 1e-4f;
                nx = ex / el * tilt;
                nz = ez / el * tilt;
                if (out.glowPass()) {
                    Vfx.plane(out, FxSoft.SPIRAL, 0f, cy, 0f, nx, ny, nz, R * 3.6f, -t * 4.5f, Vfx.with(c, 0.95f * e * Math.max(0.5f, gk)));
                    Vfx.plane(out, FxSoft.SPIRAL, 0f, cy, 0f, nx, ny, nz, R * 2.6f, -t * 6.5f + 1f, Vfx.with(Vfx.white(c2, 0.4f), 0.7f * e * gk));
                    Vfx.sprite(out, FxSoft.RING, 0f, cy, 0f, R * 1.45f, t, Vfx.with(Vfx.white(c, 0.6f), e * Math.max(0.6f, gk)));
                    Vfx.sprite(out, FxSoft.RING, 0f, cy, 0f, R * 2.6f, -t, Vfx.with(c, 0.25f * e * gk));
                    Vfx.glow(out, 0f, cy, 0f, R * 4.5f, Vfx.with(c, 0.3f * e * gk));
                } else {
                    Vfx.plane(out, FxSoft.SPIRAL, 0f, cy, 0f, nx, ny, nz, R * 3.6f, -t * 4.5f, Vfx.with(c, 0.35f * e));
                    // The black sphere itself, over everything behind it.
                    Vfx.sprite(out, FxSoft.DOT, 0f, cy, 0f, R * 1.25f, 0f, Vfx.with(0xFF030207, e));
                }
            }
            // Streams of light spiralling in.
            if (t < collapse) {
                int n = Math.round(70 * fx.amount);
                for (int i = 0; i < n; i++) {
                    float ph = (t * 0.7f + Vfx.h(fx.seed, i)) % 1f;
                    float rr = (3.6f * s) * (1f - ph) * (1f - ph) + R * 1.1f;
                    float ang = Vfx.h(fx.seed, i + 50) * 6.28f + 3.5f / Math.max(0.25f, rr) * (1f + t);
                    float yy = cy + Vfx.s(fx.seed, i + 90) * (1f - ph) * 1.2f * s;
                    float x = (float) Math.cos(ang) * rr, z = (float) Math.sin(ang) * rr;
                    float tx = -(float) Math.sin(ang) * 3f - (float) Math.cos(ang) * 1.5f, tz = (float) Math.cos(ang) * 3f - (float) Math.sin(ang) * 1.5f;
                    float a = (float) Math.sin(Math.PI * ph) * e * Math.min(1f, t / 0.3f);
                    int col = Vfx.mix(c2, Vfx.white(c, 0.6f), ph);
                    if (out.glowPass()) {
                        Vfx.streak(out, x, yy, z, tx, 0f, tz, 0.4f * s * (1f - ph * 0.5f), 0.1f * s, Vfx.with(col, a * gk));
                    } else {
                        Vfx.streak(out, x, yy, z, tx, 0f, tz, 0.3f * s, 0.05f * s, Vfx.with(col, 0.5f * a));
                    }
                }
            }
            // The body, dragged in, spinning and stretched thin.
            if (fx.copy != null && t > 0.15f && t < collapse) {
                float k = Math.min(1f, (t - 0.15f) / 1.45f);
                float sc = s * (1f - k * k) * 0.95f;
                if (sc > 0.03f && !out.glowPass()) {
                    double ang = k * k * Math.PI * 5;
                    float orbit = (1f - k) * 1.1f * s;
                    float px = (float) Math.cos(ang) * orbit, pz = (float) Math.sin(ang) * orbit;
                    float py = gy + (cy - gy) * k - height(fx) * sc * 0.5f * k;
                    fx.copy.draw(out, px, py, pz, sc, (float) ang, fx.copy.tinted(c, 0.35f), Vfx.with(0xFFFFFFFF, e * (1f - k * 0.5f)),
                            PoseSnapshot.WHOLE, 0f);
                }
            }
            if (t >= boom) {
                FxBits.impact(out, fx, t - boom, 0f, cy, 0f, gy, 1.3f * s, c);
                FxBits.sparks(out, fx, t - boom, 0f, cy, 0f, Math.round(80 * fx.amount), 11f * s, 0.3f, -6f, 2.4f, 0.8f, c, s, gy, 8);
            }
        }
    };

    /**
     * Sky laser: a targeting circle opens on the ground and light gathers down from the sky; then a beam slams down,
     * thick and roaring with light pouring through it, shock rings pulsing out where it hits, sparks splashing; it
     * thins to a thread and leaves the circle burning out on a scorch.
     */
    static final FxKind SKY_LASER = new FxKind("kill_sky_laser", 3) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            float t = fx.time(age);
            Vfx.origin(out, fx);
            float s = fx.size, gy = fx.c, e = fx.early(now), gk = FxBits.glowK(fx);
            int c = fx.tone(Ramp.MAIN, 0, now), c2 = second(fx, now);
            int hot = Vfx.white(c, 0.65f);
            float charge = 0.4f, stop = 1.3f, gone = 1.6f;
            float top = gy + 46f * s;
            // The targeting circle.
            float ck = Vfx.life(t, 2.0f, 0.25f, 0.7f) * e;
            float cr = 2.1f * s * (0.6f + 0.4f * Vfx.outBack(t / 0.4f));
            if (out.glowPass()) {
                Vfx.decalGround(out, FxSoft.MAGIC, 0f, gy, 0f, cr, t * (t < stop ? 1.6f : 0.4f), Vfx.with(c, ck * gk));
                Vfx.decalGround(out, FxSoft.RUNES, 0f, gy, 0f, cr * 1.35f, -t * 0.9f, Vfx.with(c2, 0.6f * ck * gk));
            } else {
                Vfx.decalGround(out, FxSoft.MAGIC, 0f, gy, 0f, cr, t * (t < stop ? 1.6f : 0.4f), Vfx.with(c, 0.55f * ck));
            }
            if (t < charge) {
                // Light gathering down the beam's path, and a pilot thread.
                float k = t / charge;
                for (int i = 0; i < 30; i++) {
                    float ph = (k * 1.6f + Vfx.h(fx.seed, i)) % 1f;
                    float y = top - (top - gy) * ph * ph;
                    float off = (1f - ph) * 1.2f * s;
                    double ang = Vfx.h(fx.seed, i + 40) * 6.28;
                    float x = (float) Math.cos(ang) * off, z = (float) Math.sin(ang) * off;
                    if (out.glowPass()) {
                        Vfx.streak(out, x, y, z, 0f, -1f, 0f, 1.6f * s, 0.12f * s, Vfx.with(hot, (float) Math.sin(Math.PI * ph) * e * gk));
                    }
                }
                if (out.glowPass()) {
                    Vfx.line(out, FxSoft.TUBE, 0f, gy, 0f, 0f, top, 0f, 0.08f * s, Vfx.with(hot, 0.5f * k * e * gk), Vfx.with(hot, 0f));
                }
            } else if (t < gone) {
                // The beam: wide light, a hot body, a white core, and light pouring down through it.
                float on = t < stop ? 1f : 1f - (float) Math.pow((t - stop) / (gone - stop), 0.6f);
                float pulse = 1f + 0.12f * (float) Math.sin(t * 45f) + 0.08f * (float) Math.sin(t * 17f);
                float w = s * pulse * (t < charge + 0.06f ? (t - charge) / 0.06f : 1f) * on;
                float a = e * Math.min(1f, on * 1.5f);
                if (out.glowPass()) {
                    Vfx.line(out, FxSoft.LINE, 0f, gy, 0f, 0f, top, 0f, 5.2f * w, Vfx.with(c, 0.55f * a * gk), Vfx.with(c, 0.15f * a * gk));
                    Vfx.line(out, FxSoft.TUBE, 0f, gy, 0f, 0f, top, 0f, 2.0f * w, Vfx.with(hot, 0.9f * a * gk), Vfx.with(hot, 0.4f * a * gk));
                    Vfx.flow(out, 0f, gy, 0f, 0f, top, 0f, 1.6f * w, t * 2.6f, Vfx.with(Vfx.white(c2, 0.5f), 0.55f * a * gk));
                    Vfx.line(out, FxSoft.TUBE, 0f, gy, 0f, 0f, top, 0f, 0.6f * w, Vfx.with(0xFFFFFFFF, a), Vfx.with(0xFFFFFFFF, 0.6f * a));
                    // Where it hits.
                    Vfx.hot(out, 0f, gy + 0.5f, 0f, 3.2f * w + 0.5f, Vfx.with(hot, 0.9f * a * gk));
                    Vfx.glow(out, 0f, gy + 1f, 0f, 7f * s, Vfx.with(c, 0.35f * a * gk));
                    Vfx.pool(out, 0f, gy, 0f, 5.5f * s, Vfx.with(c, 0.85f * a * gk));
                } else {
                    Vfx.line(out, FxSoft.LINE, 0f, gy, 0f, 0f, top, 0f, 2.2f * w, Vfx.with(c, 0.4f * a), Vfx.with(c, 0.15f * a));
                    Vfx.line(out, FxSoft.TUBE, 0f, gy, 0f, 0f, top, 0f, 0.8f * w, Vfx.with(0xFFFFFFFF, 0.95f * a), Vfx.with(0xFFFFFFFF, 0.7f * a));
                }
                // Shock rings pulsing out from the foot of the beam.
                if (t < stop) {
                    for (int r = 0; r < 3; r++) {
                        float k = ((t - charge) / 0.3f + r / 3f) % 1f;
                        float rad = 0.6f * s + 4.5f * s * Vfx.outCubic(k);
                        float ra = (1f - k) * a;
                        if (out.glowPass()) {
                            Vfx.ring(out, FxSoft.FRONT, 0f, gy + 0.04f, 0f, rad, 0.9f * s, Vfx.with(hot, 0.8f * ra * gk), true);
                        }
                    }
                }
            }
            if (t >= charge) {
                FxBits.impact(out, fx, t - charge, 0f, gy + 0.6f, 0f, gy, 1.4f * s, c);
                for (int b = 0; b < 5; b++) {
                    FxBits.sparks(out, fx, t - charge - b * 0.17f, 0f, gy + 0.2f, 0f, Math.round(22 * fx.amount), 7.5f * s, 0.8f, -14f, 1.8f,
                            0.8f, c, s, gy, 20 + b);
                }
                FxBits.smoke(out, fx, t - charge, 0f, gy + 0.2f, 0f, 14, 1.3f * s, 1.1f, 0.9f, 1.4f, 0.6f * s, 0xFF39363E, 0.55f * e, 9);
                FxBits.embers(out, fx, t - charge, 0f, gy + 0.1f, 0f, Math.round(40 * fx.amount), 1.5f * s, 1.4f, 1.0f, 1.2f, c, s, 12);
            }
            // The scorch left on the ground.
            if (t > charge && !out.glowPass()) {
                Vfx.decalGround(out, FxSoft.SCORCH, 0f, gy, 0f, 2.2f * s, fx.seed * 0.1f, Vfx.with(0xFF100C0A, 0.8f * Vfx.life(t - charge, 1.6f, 0.1f, 0.5f) * e));
            }
        }
    };

    /**
     * Gravestone: the ground cracks and an arched headstone heaves up through it, shaking off earth and dust: weathered
     * granite on a two-step plinth, a carved border and emblem, the name lit in its face, two candles flickering at its
     * foot and wisps of light circling it while a faint spirit drifts up. It faces whoever saw the body fall; after four
     * seconds it sinks back.
     */
    static final FxKind GRAVESTONE = new FxKind("kill_gravestone", 4) {
        private final float[] eye = new float[3], tr = new float[3], tu = {0f, 1f, 0f};
        /** The stone's right and face directions on the ground, its foot (from the origin) and its scale. */
        private float rx, rz, nx, nz, ox, oy, oz, sc;
        /** The headstone's outline, its foot at 0: up the right side, over the arch, down the left. */
        private final float[] olx = new float[20], oly = new float[20];
        private int ol;

        {
            float w = FxSoft.GRAVE_W / 2f, top = FxSoft.GRAVE_H - FxSoft.GRAVE_R;
            olx[ol] = -w;
            oly[ol++] = 0f;
            olx[ol] = w;
            oly[ol++] = 0f;
            for (int k = 0; k <= 14; k++) {
                double a = Math.PI * k / 14;
                olx[ol] = (float) Math.cos(a) * FxSoft.GRAVE_R;
                oly[ol++] = top + (float) Math.sin(a) * FxSoft.GRAVE_R;
            }
        }

        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            float t = fx.time(age);
            Vfx.origin(out, fx);
            out.eye(eye);
            float s = fx.size * 1.4f, gy = fx.c, e = fx.early(now), gk = FxBits.glowK(fx);
            int c = fx.tone(Ramp.MAIN, 0, now);
            float rise = t < 0.7f ? Vfx.outBack(t / 0.7f) : t > 4.4f ? 1f - Vfx.inOut((t - 4.4f) / 0.6f) : 1f;
            float shake = t < 0.7f ? 0.035f * s * (1f - t / 0.7f) : t > 4.4f ? 0.02f * s : 0f;
            double yr = Math.toRadians(fx.yaw);
            nx = (float) -Math.sin(yr);
            nz = (float) Math.cos(yr);
            rx = nz;
            rz = -nx;
            int jig = (int) (t * 32f);
            ox = Vfx.s(fx.seed, jig) * shake;
            oz = Vfx.s(fx.seed, jig + 7) * shake;
            oy = gy - 1.2f * s * (1f - rise);
            sc = s;
            float stay = Vfx.life(t, 5f, 0.1f, 0.6f) * e;
            // Fresh earth in front of it, the ground cracked round it.
            if (!out.glowPass()) {
                float mx = nx * 0.45f * s, mz = nz * 0.45f * s;
                Vfx.decalGround(out, FxSoft.SCORCH, mx, gy, mz, 0.6f * s * Math.min(1f, t / 0.4f), fx.seed * 0.3f, Vfx.with(0xFF3A2A1C, 0.9f * stay));
                Vfx.decalGround(out, FxSoft.CRACKS, 0f, gy, 0f, 1.25f * s, fx.seed * 0.2f, Vfx.with(0xFF1E1812, 0.8f * stay));
            }
            FxBits.smoke(out, fx, t, 0f, gy + 0.1f, 0f, 11, 0.5f * s, 0.7f, 0.5f, 1.4f, 0.35f * s, 0xFF7A6652, 0.6f * e, 3);
            FxBits.smoke(out, fx, t - 4.4f, 0f, gy + 0.1f, 0f, 8, 0.5f * s, 0.45f, 0.4f, 1.0f, 0.3f * s, 0xFF7A6652, 0.5f * e, 4);
            if (!out.glowPass()) {
                for (int i = 0; i < 30; i++) {
                    float tt = t - 0.08f * Vfx.h(fx.seed, i);
                    if (tt < 0f || tt > 0.9f) {
                        continue;
                    }
                    double th = Vfx.h(fx.seed, i + 10) * 6.28;
                    float v = (1.2f + 2.2f * Vfx.h(fx.seed, i + 20)) * s;
                    float x = (float) Math.cos(th) * Vfx.fly(v, 1f, 0f, tt), z = (float) Math.sin(th) * Vfx.fly(v, 1f, 0f, tt);
                    float yy = Math.max(gy + 0.02f, gy + Vfx.fly(4.5f * s, 1f, -16f, tt));
                    float side = (0.03f + 0.04f * Vfx.h(fx.seed, i + 30)) * s;
                    FxDraw.cube(out, x, yy + side / 2f, z, side, DIRT, (1f - tt / 0.9f) * e);
                }
            }
            if (out.glowPass()) {
                Vfx.pool(out, 0f, gy, 0f, 2.3f * s, Vfx.with(c, 0.5f * stay * gk));
            }
            // The stone and its candles, near ones over far ones.
            float base = 0.17f;
            float candleX = 0.29f, candleZ = 0.13f;
            boolean front = dot(nx, 0f, nz, eye[0] - ox, eye[1] - oy - 0.5f * s, eye[2] - oz) > 0f;
            if (!out.glowPass()) {
                box(out, -0.46f, 0f, -0.27f, 0.46f, 0.1f, 0.27f, e);
                box(out, -0.38f, 0.1f, -0.2f, 0.38f, base, 0.2f, e);
                if (!front) {
                    candles(out, fx, t, base, candleX, candleZ, c, e, gk, now);
                }
                headstone(out, base, e);
                if (front) {
                    candles(out, fx, t, base, candleX, candleZ, c, e, gk, now);
                    // The name carved in its face, lit from within.
                    String name = fx.text == null ? "" : fx.text;
                    int w = Math.max(1, FxText.width(name));
                    float texel = Math.min(FxDraw.TEXEL * s * 0.8f, 0.46f * s / w);
                    float half = 0.075f + 0.003f;
                    float px = ox + nx * half * s, pz = oz + nz * half * s;
                    tr[0] = rx;
                    tr[1] = 0f;
                    tr[2] = rz;
                    // R.I.P. carved large, the name under it lit.
                    float rip = Math.min(0.024f * s, 0.3f * s / Math.max(1, FxText.width("R.I.P.")));
                    FxText.drawOn(out, "R.I.P.", px, oy + (base + 0.5f) * s, pz, tr, tu, rip, Vfx.with(0xFF3E3F48, e),
                            Vfx.with(0xFFC9CAD4, 0.55f * e));
                    FxText.drawOn(out, name, px, oy + (base + 0.33f) * s, pz, tr, tu, texel, Vfx.with(Vfx.white(c, 0.45f), e),
                            Vfx.with(0xFF1A1A20, e));
                }
            } else {
                candles(out, fx, t, base, candleX, candleZ, c, e, gk, now);
                if (front) {
                    float half = 0.09f;
                    float glowA = 0.5f * rise * e * gk * (0.9f + 0.1f * (float) Math.sin(t * 5f));
                    for (int k = -1; k <= 1; k++) {
                        Vfx.glow(out, ox + nx * half * s + rx * k * 0.14f * s, oy + (base + 0.33f) * s, oz + nz * half * s + rz * k * 0.14f * s,
                                0.32f * s, Vfx.with(c, glowA));
                    }
                    Vfx.glow(out, ox + nx * half * s, oy + (base + 0.72f) * s, oz + nz * half * s, 0.3f * s, Vfx.with(c, 0.6f * glowA));
                }
            }
            // Wisps circling it.
            float wa = Vfx.life(t, 4.6f, 0.8f, 0.6f) * e;
            for (int i = 0; i < 3; i++) {
                float ang = t * (1.3f + 0.25f * i) + i * 2.1f;
                float rr = (0.8f + 0.15f * (float) Math.sin(t * 2f + i)) * s;
                float wy = oy + (0.6f + 0.3f * (float) Math.sin(t * 1.7f + i * 1.3f)) * s;
                float x = (float) Math.cos(ang) * rr, z = (float) Math.sin(ang) * rr;
                float tx = -(float) Math.sin(ang), tz = (float) Math.cos(ang);
                if (out.glowPass()) {
                    Vfx.hot(out, x, wy, z, 0.35f * s, Vfx.with(Vfx.white(c, 0.5f), wa * gk));
                    Vfx.streak(out, x, wy, z, tx, 0.1f, tz, 0.9f * s, 0.16f * s, Vfx.with(c, 0.7f * wa * gk));
                } else {
                    Vfx.sprite(out, FxSoft.DOT, x, wy, z, 0.05f * s, 0f, Vfx.with(Vfx.white(c, 0.4f), 0.8f * wa));
                }
            }
            // A faint spirit drifting up out of it.
            float sp = Vfx.life(t - 0.7f, 2f, 0.4f, 1.2f) * e;
            FxBits.spirit(out, fx, 0f, gy + 0.35f + 1.7f * Vfx.outCubic((t - 0.7f) / 2f), 0f, fx.size * 0.9f, c, 0.45f * sp);
        }

        /** Two candles on the plinth's front corners: wax, a wick's flame and its light. */
        private void candles(FxOut out, Fx fx, float t, float base, float cx, float cz, int c, float e, float gk, float now) {
            float lit = Vfx.life(t, 4.6f, 0.9f, 0.4f) * e;
            for (int side = -1; side <= 1; side += 2) {
                float tall = side < 0 ? 0.15f : 0.11f;
                float lx = cx * side, lz = cz;
                float wx = ox + (lx * rx + lz * nx) * sc, wz = oz + (lx * rz + lz * nz) * sc;
                float y0 = oy + base * sc, y1 = oy + (base + tall) * sc;
                float half = 0.024f * sc;
                if (!out.glowPass()) {
                    FxDraw.box(out, wx - half, y0, wz - half, wx + half, y1, wz + half, Vfx.with(0xFFF2EBDC, e), Vfx.with(0xFFE0D6C2, e),
                            Vfx.with(0xFFCFC3AC, e), Vfx.with(0xFFB8AD98, e));
                }
                if (lit <= 0f) {
                    continue;
                }
                float flick = 0.85f + 0.15f * (float) Math.sin(t * 19f + side * 2f) * (float) Math.sin(t * 7.1f + side);
                float fy = y1 + 0.05f * sc;
                int frame = (int) (t * 12f + side + 2) & 3;
                if (out.glowPass()) {
                    Vfx.billboard(out, FxSoft.FLAME[frame], wx, fy, wz, 0.035f * sc, 0.07f * sc, 0f, Vfx.with(0xFFFFB347, lit * flick));
                    Vfx.hot(out, wx, fy, wz, 0.22f * sc, Vfx.with(0xFFFFC46B, 0.55f * lit * flick * Math.max(0.6f, gk)));
                    Vfx.glow(out, wx, fy, wz, 0.6f * sc, Vfx.with(Vfx.mix(0xFFFFA040, c, 0.3f), 0.25f * lit * flick * gk));
                } else {
                    Vfx.billboard(out, FxSoft.FLAME[frame], wx, fy, wz, 0.03f * sc, 0.06f * sc, 0f, Vfx.with(0xFFFFD27A, 0.9f * lit));
                }
            }
        }

        /** The headstone: its granite face and back, and its sides round the outline. */
        private void headstone(FxOut out, float base, float a) {
            float half = 0.075f;
            FxSoft.Region f = FxSoft.GRAVE, g = FxSoft.GRANITE;
            float cxl = 0f, cyl = 0.45f;
            // Face and back as fans from inside the outline.
            for (int side = 1; side >= -1; side -= 2) {
                float lz = half * side;
                if (!visible(0f, 0f, side, 0f, base + cyl, lz)) {
                    continue;
                }
                int col = shade(0f, 0f, side, a);
                FxSoft.Region r = side > 0 ? f : g;
                for (int i = 0; i < ol; i++) {
                    int j = (i + 1) % ol;
                    vertex(out, r, cxl, base + cyl, lz, cxl, cyl, col, side);
                    vertex(out, r, olx[i], base + oly[i], lz, olx[i], oly[i], col, side);
                    vertex(out, r, olx[j], base + oly[j], lz, olx[j], oly[j], col, side);
                    vertex(out, r, olx[j], base + oly[j], lz, olx[j], oly[j], col, side);
                }
            }
            // The sides, edge by edge (the bottom edge sits on the plinth).
            float along = 0f;
            for (int i = 1; i < ol; i++) {
                int j = (i + 1) % ol;
                float ex = olx[j] - olx[i], ey = oly[j] - oly[i];
                float len = (float) Math.sqrt(ex * ex + ey * ey);
                // Outward: the outline runs anticlockwise seen from the face.
                float nlx = ey / len, nly = -ex / len;
                float mx = (olx[i] + olx[j]) / 2f, my = base + (oly[i] + oly[j]) / 2f;
                float u0 = along, u1 = along + len;
                along = u1;
                if (!visible(nlx, nly, 0f, mx, my, 0f)) {
                    continue;
                }
                int col = shade(nlx, nly, 0f, a);
                float gu0 = g.u0 + (g.u1 - g.u0) * (u0 % 1f), gu1 = g.u0 + (g.u1 - g.u0) * Math.min(1f, (u0 % 1f) + len);
                put(out, olx[i], base + oly[i], half, gu0, g.v0, col);
                put(out, olx[j], base + oly[j], half, gu1, g.v0, col);
                put(out, olx[j], base + oly[j], -half, gu1, g.v1, col);
                put(out, olx[i], base + oly[i], -half, gu0, g.v1, col);
            }
        }

        private void vertex(FxOut out, FxSoft.Region r, float lx, float ly, float lz, float sx, float sy, int col, int side) {
            float u = (sx * side) / FxSoft.GRAVE_W + 0.5f, v = 1f - sy / FxSoft.GRAVE_H;
            put(out, lx, ly, lz, r.u0 + (r.u1 - r.u0) * u, r.v0 + (r.v1 - r.v0) * v, col);
        }

        /** A box of granite in the stone's own space. */
        private void box(FxOut out, float x0, float y0, float z0, float x1, float y1, float z1, float a) {
            FxSoft.Region g = FxSoft.GRANITE;
            // Top, front, back, right, left (never the bottom).
            face(out, g, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0f, 1f, 0f, a);
            face(out, g, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0f, 0f, 1f, a);
            face(out, g, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0f, 0f, -1f, a);
            face(out, g, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1f, 0f, 0f, a);
            face(out, g, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1f, 0f, 0f, a);
        }

        private void face(FxOut out, FxSoft.Region g, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
                          float dx, float dy, float dz, float nlx, float nly, float nlz, float a) {
            if (!visible(nlx, nly, nlz, (ax + cx) / 2f, (ay + cy) / 2f, (az + cz) / 2f)) {
                return;
            }
            int col = shade(nlx, nly, nlz, a);
            put(out, ax, ay, az, g.u0, g.v1, col);
            put(out, bx, by, bz, g.u1, g.v1, col);
            put(out, cx, cy, cz, g.u1, g.v0, col);
            put(out, dx, dy, dz, g.u0, g.v0, col);
        }

        /** A point in the stone's own space (x across, y up, z out of its face), placed in the world. */
        private void put(FxOut out, float lx, float ly, float lz, float u, float v, int col) {
            out.use(FxSoft.atlas());
            out.vertex(ox + (lx * rx + lz * nx) * sc, oy + ly * sc, oz + (lx * rz + lz * nz) * sc, u, v, col);
        }

        /** Whether a face with this normal (the stone's own space) at this point is turned toward the camera. */
        private boolean visible(float nlx, float nly, float nlz, float lx, float ly, float lz) {
            float wx = nlx * rx + nlz * nx, wz = nlx * rz + nlz * nz;
            float px = ox + (lx * rx + lz * nx) * sc, py = oy + ly * sc, pz = oz + (lx * rz + lz * nz) * sc;
            return dot(wx, nly, wz, eye[0] - px, eye[1] - py, eye[2] - pz) > 0f;
        }

        /** The stone lit from above and a little to the side: brighter facing up, darker turned away. */
        private int shade(float nlx, float nly, float nlz, float a) {
            float wx = nlx * rx + nlz * nx, wz = nlx * rz + nlz * nz;
            float k = 0.58f + 0.42f * Math.max(0f, dot(wx, nly, wz, 0.38f, 0.82f, 0.43f));
            int l = Math.round(255f * k);
            return Math.round(255f * a) << 24 | l << 16 | l << 8 | l;
        }

        private float dot(float ax, float ay, float az, float bx, float by, float bz) {
            return ax * bx + ay * by + az * bz;
        }
    };

    /**
     * Confetti: a party popper goes off over the body: papers, curls and stars in the preset's colours burst up in a
     * cone, glitter twinkling among them, then flutter down swaying and flipping, and lie on the ground.
     */
    static final FxKind CONFETTI = new FxKind("kill_confetti", 4) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            float t = fx.time(age);
            Vfx.origin(out, fx);
            float s = fx.size, gy = fx.c, e = fx.early(now), gk = FxBits.glowK(fx);
            float y0 = gy + height(fx) * s * 0.75f;
            if (out.glowPass() && t < 0.3f) {
                float k = t / 0.3f;
                Vfx.hot(out, 0f, y0, 0f, 1.3f * s, Vfx.with(0xFFFFFFFF, (1f - k) * (1f - k) * e * Math.max(0.6f, gk)));
                Vfx.flare(out, 0f, y0, 0f, 2.4f * s, 0.4f, Vfx.with(Vfx.white(fx.palette[0], 0.5f), (1f - k) * e * gk));
            }
            int n = Math.round(170 * fx.amount);
            float fade = Vfx.life(t, 3.2f, 0f, 0.6f) * e;
            for (int i = 0; i < n; i++) {
                double th = Vfx.h(fx.seed, i) * Math.PI * 2;
                float cone = 0.15f + 0.75f * Vfx.h(fx.seed, i + 100);
                float v = (5f + 6f * Vfx.h(fx.seed, i + 200)) * s;
                float vx = (float) Math.cos(th) * cone * v, vz = (float) Math.sin(th) * cone * v, vy = (1f - cone * 0.5f) * v;
                float drag = 2.6f + 1.5f * Vfx.h(fx.seed, i + 300);
                float x = Vfx.fly(vx, drag, 0f, t), z = Vfx.fly(vz, drag, 0f, t);
                float y = y0 + Vfx.fly(vy, drag, -4.5f, t);
                // Swaying as it flutters down.
                float sway = Math.min(1f, t / 0.8f) * 0.3f * s;
                x += (float) Math.sin(t * (3f + 2f * Vfx.h(fx.seed, i + 400)) + i) * sway;
                z += (float) Math.cos(t * (2.5f + 2f * Vfx.h(fx.seed, i + 500)) + i * 1.7f) * sway;
                boolean down = y <= gy + 0.02f;
                int kind = i % 7 == 0 ? 2 : i % 4 == 0 ? 1 : 0;
                int col = fx.palette[i % 5];
                float size = (kind == 0 ? 0.07f : 0.09f) * s;
                if (out.glowPass()) {
                    if (kind == 2 && !down) {
                        float tw = 0.5f + 0.5f * (float) Math.sin(t * 20f + i * 3f);
                        Vfx.glint(out, x, y, z, 0.35f * s, t * 2f + i, Vfx.with(Vfx.white(col, 0.6f), tw * fade * gk));
                    }
                    continue;
                }
                if (down) {
                    Vfx.decal(out, FxSoft.CONFETTI[kind], x, gy + 0.005f * (i % 5), z, size, Vfx.h(fx.seed, i + 600) * 6f, Vfx.with(col, fade));
                } else {
                    float spin = t * (5f + 9f * Vfx.h(fx.seed, i + 700)) + i;
                    float flip = (float) Math.cos(spin);
                    int shade = flip < 0f ? Vfx.mix(col, 0xFF000000, 0.3f) : col;
                    Vfx.billboard(out, FxSoft.CONFETTI[kind], x, y, z, size * (0.15f + 0.85f * Math.abs(flip)), size * (kind == 0 ? 1.6f : 1f),
                            spin * 0.3f, Vfx.with(shade, fade));
                }
            }
            // Glitter in the air.
            if (out.glowPass()) {
                for (int i = 0; i < 40; i++) {
                    float tt = t - 0.05f;
                    double th = Vfx.h(fx.seed, i + 800) * 6.28;
                    float v = (2f + 4f * Vfx.h(fx.seed, i + 820)) * s;
                    float x = (float) Math.cos(th) * Vfx.fly(v * 0.6f, 2.2f, 0f, tt), z = (float) Math.sin(th) * Vfx.fly(v * 0.6f, 2.2f, 0f, tt);
                    float y = y0 + Vfx.fly(v, 2.2f, -2.5f, tt);
                    float tw = (float) Math.max(0f, Math.sin(t * 14f + i * 2.3f));
                    Vfx.hot(out, x, y, z, 0.18f * s, Vfx.with(Vfx.white(fx.palette[i % 5], 0.6f), tw * fade * gk));
                }
            }
        }
    };

    /**
     * Burning copy: the body stands wreathed in flames that lick up its whole shape, glowing and blackening, smoke
     * and embers pouring off it, light flickering on the ground; then it crumbles into ash.
     */
    static final FxKind BURNING_COPY = new FxKind("kill_burning_copy", 4) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            float t = fx.time(age);
            Vfx.origin(out, fx);
            float s = fx.size, gy = fx.c, e = fx.early(now), gk = FxBits.glowK(fx);
            float h = height(fx) * s;
            int c = fx.tone(Ramp.MAIN, 0, now);
            int yellow = Vfx.white(Vfx.hue(c, 22f), 0.35f);
            float crumble = 1.7f, end = 2.6f;
            float burn = Math.min(1f, t / crumble);
            float flick = 0.8f + 0.2f * (float) Math.sin(t * 23f) * (float) Math.sin(t * 7.3f + 1f);
            if (fx.copy != null && t < end) {
                if (!out.glowPass()) {
                    // Glowing as it catches, charring as it burns, then gone to ash from the top.
                    float cut = t < crumble ? 0f : Math.min(1f, (t - crumble) / (end - crumble - 0.15f));
                    fx.copy.draw(out, 0f, gy, 0f, s, 0f, fx.copy.tinted(c, 0.7f), Vfx.with(0xFFFFFFFF, (1f - burn) * e), PoseSnapshot.NOISE, cut);
                    fx.copy.draw(out, 0f, gy, 0f, s, 0f, fx.copy.tinted(0xFF15100C, 0.92f), Vfx.with(0xFFFFFFFF, burn * e), PoseSnapshot.NOISE,
                            cut);
                } else if (t < crumble + 0.3f) {
                    FxBits.spirit(out, fx, 0f, gy, 0f, s, c, 0.4f * flick * e * Math.min(1f, t / 0.3f) * (1f - Math.max(0f, t - crumble) / 0.3f));
                }
            }
            // Flames over the body's shape.
            if (fx.copy != null && t < crumble + 0.35f) {
                int rows = fx.copy.rowCount();
                int n = Math.round(46 * fx.amount);
                float fa = Math.min(1f, t / 0.25f) * (t > crumble ? 1f - (t - crumble) / 0.35f : 1f) * e;
                for (int i = 0; i < n; i++) {
                    int r = (int) (Vfx.h(fx.seed, i) * rows);
                    fx.copy.row(r, P);
                    float x = P[0] * s * 1.05f, y = gy + P[1] * s, z = P[2] * s * 1.05f;
                    // Licks: each flame flares and dies on its own beat.
                    float beat = (t * (2.2f + 1.5f * Vfx.h(fx.seed, i + 30)) + Vfx.h(fx.seed, i + 60)) % 1f;
                    float size = (0.22f + 0.22f * Vfx.h(fx.seed, i + 90)) * s * (float) Math.sin(Math.PI * beat);
                    if (size < 0.02f) {
                        continue;
                    }
                    int frame = (int) (t * 14f + i) & 3;
                    float lift = beat * 0.35f * s;
                    if (out.glowPass()) {
                        Vfx.billboard(out, FxSoft.FLAME[frame], x, y + size + lift, z, size * 0.75f, size * 1.4f, 0f, Vfx.with(c, 0.85f * fa * gk));
                        Vfx.billboard(out, FxSoft.FLAME[(frame + 2) & 3], x, y + size * 0.8f + lift, z, size * 0.4f, size * 0.8f, 0f,
                                Vfx.with(yellow, 0.9f * fa * gk));
                    } else {
                        Vfx.billboard(out, FxSoft.FLAME[frame], x, y + size + lift, z, size * 0.7f, size * 1.3f, 0f, Vfx.with(c, 0.55f * fa));
                    }
                }
                if (out.glowPass()) {
                    Vfx.glow(out, 0f, gy + h * 0.5f, 0f, 2.6f * s, Vfx.with(c, 0.45f * flick * fa * gk));
                    Vfx.pool(out, 0f, gy, 0f, 2.8f * s, Vfx.with(c, 0.7f * flick * fa * gk));
                }
            }
            FxBits.smoke(out, fx, t, 0f, gy + h * 0.8f, 0f, 16, 0.3f * s, 1.3f, crumble, 1.6f, 0.4f * s, 0xFF221E1C, 0.55f * e, 2);
            FxBits.embers(out, fx, t, 0f, gy + 0.2f, 0f, Math.round(60 * fx.amount), 0.45f * s, 1.8f, crumble, 1.3f, c, s, 3);
            // Ash falling as it crumbles.
            if (t > crumble && !out.glowPass()) {
                int n = Math.round(90 * fx.amount);
                for (int i = 0; i < n; i++) {
                    float tt = t - crumble - 0.4f * Vfx.h(fx.seed, i + 400);
                    if (tt < 0f) {
                        continue;
                    }
                    float x = Vfx.s(fx.seed, i + 410) * 0.35f * s + Vfx.fly(Vfx.s(fx.seed, i + 420) * 0.8f, 2f, 0f, tt);
                    float z = Vfx.s(fx.seed, i + 430) * 0.35f * s + Vfx.fly(Vfx.s(fx.seed, i + 440) * 0.8f, 2f, 0f, tt);
                    float y = Math.max(gy + 0.02f, gy + h * (0.2f + 0.8f * Vfx.h(fx.seed, i + 450)) + Vfx.fly(0.3f, 2.5f, -3f, tt));
                    Vfx.sprite(out, FxSoft.DOT_SOFT, x, y, z, 0.05f * s, 0f, Vfx.with(0xFF3A3532, 0.85f * Vfx.life(tt, 1.2f, 0f, 0.5f) * e));
                }
            }
        }
    };

    /**
     * Burst: the body swells for an instant and bursts: a flash, a ball of mist, a hundred and more droplets of every size
     * flying out, stretched by their speed, splashing where they land and leaving splats round a big pool of it.
     */
    static final FxKind BURST = new FxKind("kill_burst", 5) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            float t = fx.time(age);
            Vfx.origin(out, fx);
            float s = fx.size, gy = fx.c, e = fx.early(now), gk = FxBits.glowK(fx);
            float h = height(fx) * s;
            int c = fx.tone(Ramp.MAIN, 0, now), deep = Vfx.mix(c, 0xFF000000, 0.5f), light = Vfx.white(c, 0.3f);
            float y0 = gy + h * 0.55f;
            float pop = 0.1f;
            // The body swells, then it's gone.
            if (fx.copy != null && t < pop && !out.glowPass()) {
                float sw = 1f + 0.12f * t / pop;
                fx.copy.drawFaces(out, 0f, gy, 0f, s * sw, fx.copy.tinted(c, 0.25f + 0.5f * t / pop), Vfx.with(0xFFFFFFFF, e), false);
            }
            float tb = t - pop;
            if (tb < 0f) {
                return;
            }
            if (out.glowPass() && tb < 0.4f) {
                float k = tb / 0.4f, f = (1f - k) * (1f - k);
                Vfx.hot(out, 0f, y0, 0f, 1.8f * s, Vfx.with(light, f * e * gk));
                Vfx.glow(out, 0f, y0, 0f, 3.4f * s, Vfx.with(c, 0.45f * f * e * gk));
                Vfx.pool(out, 0f, gy, 0f, 2.6f * s, Vfx.with(c, 0.5f * f * e * gk));
            }
            // The ball of mist it leaves, swelling and thinning.
            if (!out.glowPass() && tb < 1.4f) {
                float k = tb / 1.4f;
                for (int i = 0; i < 6; i++) {
                    float ox = Vfx.s(fx.seed, i + 1000) * 0.35f * s, oy = Vfx.s(fx.seed, i + 1010) * 0.35f * s,
                            oz = Vfx.s(fx.seed, i + 1020) * 0.35f * s;
                    float size = (0.55f + 1.1f * Vfx.outCubic(k)) * s;
                    Vfx.sprite(out, FxSoft.SMOKE[i & 3], ox * (1f + k), y0 + oy - 0.3f * k, oz * (1f + k), size, Vfx.h(fx.seed, i + 1030) * 6f + k,
                            Vfx.with(Vfx.mix(c, deep, 0.4f), 0.5f * (1f - k) * (1f - k) * e));
                }
            }
            int n = Math.round(170 * fx.amount);
            float g = -15f;
            float splat = Vfx.life(t, 3f, 0f, 1.1f) * e;
            for (int i = 0; i < n; i++) {
                double th = Vfx.h(fx.seed, i) * Math.PI * 2;
                double upA = Math.toRadians(-15 + 85 * Vfx.h(fx.seed, i + 300));
                float v = (2f + 6.5f * (float) Math.pow(Vfx.h(fx.seed, i + 600), 1.4f)) * s;
                float vh = v * (float) Math.cos(upA), vy = v * (float) Math.sin(upA);
                float dx = (float) Math.cos(th), dz = (float) Math.sin(th);
                float sy = y0 + Vfx.s(fx.seed, i + 900) * 0.45f * s;
                float drop = sy - gy;
                float land = (vy + (float) Math.sqrt(vy * vy - 2f * g * Math.max(0f, drop))) / -g;
                int col = Vfx.mix(light, deep, Vfx.h(fx.seed, i + 700));
                float size = (0.03f + 0.11f * (float) Math.pow(Vfx.h(fx.seed, i + 800), 2.2f)) * s;
                if (tb < land) {
                    float x = dx * vh * tb, z = dz * vh * tb, y = sy + vy * tb + 0.5f * g * tb * tb;
                    float vyy = vy + g * tb;
                    float sp = (float) Math.sqrt(vh * vh + vyy * vyy);
                    if (out.glowPass()) {
                        if (gk > 0f && (i & 3) == 0) {
                            Vfx.glow(out, x, y, z, size * 5f, Vfx.with(c, 0.3f * e * gk));
                        }
                    } else {
                        Vfx.streak(out, x, y, z, dx * vh, vyy, dz * vh, Math.min(0.6f, sp * 0.06f) * s + size, size * 2.2f,
                                Vfx.with(col, 0.95f * e));
                        // A bright fleck on the bigger drops.
                        if (size > 0.06f * s) {
                            Vfx.sprite(out, FxSoft.DOT, x, y, z, size * 0.45f, 0f, Vfx.with(Vfx.white(col, 0.6f), 0.8f * e));
                        }
                    }
                } else if (!out.glowPass()) {
                    float x = dx * vh * land, z = dz * vh * land;
                    float grow = Math.min(1f, (tb - land) / 0.1f);
                    // A splash where it lands, then the splat it leaves.
                    if (tb - land < 0.2f && size > 0.05f * s) {
                        float sk = (tb - land) / 0.2f;
                        Vfx.ring(out, FxSoft.TUBE, x, Vfx.groundAt(x, gy, z) + 0.03f, z, size * (1.5f + 6f * sk), size * 0.9f,
                                Vfx.with(col, 0.6f * (1f - sk) * e), false);
                    }
                    Vfx.decal(out, FxSoft.SCORCH, x, Vfx.groundAt(x, gy, z), z, size * 3.6f * grow, Vfx.h(fx.seed, i + 950) * 6f,
                            Vfx.with(col, 0.9f * splat));
                }
            }
            if (!out.glowPass()) {
                Vfx.decalGround(out, FxSoft.SCORCH, 0f, gy, 0f, 1.6f * s * Vfx.outCubic(tb / 0.6f), fx.seed * 0.3f, Vfx.with(deep, 0.85f * splat));
            }
        }
    };

    /**
     * Shockwave: a flash, then three shock fronts race out over the ground behind a dome of light, kicking up dust and
     * chunks of earth; strands of light spiral up the middle.
     */
    static final FxKind SHOCKWAVE = new FxKind("kill_shockwave", 4) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            float t = fx.time(age);
            Vfx.origin(out, fx);
            float s = fx.size, gy = fx.c, e = fx.early(now), gk = FxBits.glowK(fx);
            float h = height(fx) * s;
            int c = fx.tone(Ramp.MAIN, 0, now), c2 = second(fx, now);
            int hot = Vfx.white(c, 0.6f);
            FxBits.impact(out, fx, t, 0f, gy + h * 0.4f, 0f, gy, 1.1f * s, c);
            int rings = fx.amount >= 1f ? 3 : 2;
            for (int k = 0; k < rings; k++) {
                float p = (t - 0.12f * k) / 1.15f;
                if (p <= 0f || p >= 1f) {
                    continue;
                }
                float r = 0.4f * s + 7.5f * s * Vfx.outExpo(p);
                float a = (float) Math.pow(1f - p, 1.3f) * e;
                int col = k == 1 ? c2 : c;
                if (out.glowPass()) {
                    Vfx.ring(out, FxSoft.FRONT, 0f, gy + 0.04f, 0f, r, 1.8f * s * (1f - 0.4f * p), Vfx.with(Vfx.white(col, 0.3f), a * gk), true);
                    if (k == 0) {
                        Vfx.wall(out, 0f, gy, 0f, r, 1.4f * s * (1f - p), Vfx.with(col, 0.55f * a * gk), true);
                    }
                } else {
                    Vfx.ring(out, FxSoft.FRONT, 0f, gy + 0.03f, 0f, r, 0.5f * s, Vfx.with(Vfx.white(col, 0.5f), 0.45f * a), true);
                }
                if (k == 0 && out.glowPass()) {
                    Vfx.decalGround(out, FxSoft.SHOCK, 0f, gy, 0f, r * 1.05f, 0f, Vfx.with(c, 0.35f * a * gk));
                }
            }
            // Dust kicked up where the first front passes.
            if (!out.glowPass()) {
                int puffs = 22;
                for (int i = 0; i < puffs; i++) {
                    double ang = (i + Vfx.h(fx.seed, i)) * 2 * Math.PI / puffs;
                    float dist = (1.5f + 4.5f * Vfx.h(fx.seed, i + 30)) * s;
                    // When the front reaches it.
                    float k = (dist - 0.4f * s) / (7.5f * s);
                    float born = k <= 0f ? 0f : (float) (-Math.log(Math.max(1e-3, 1 - k)) / Math.log(2) / 10.0) * 1.15f;
                    float tt = t - born;
                    if (tt < 0f || tt > 1.1f) {
                        continue;
                    }
                    float x = (float) Math.cos(ang) * dist, z = (float) Math.sin(ang) * dist;
                    float y = Vfx.groundAt(x, gy, z) + 0.2f + tt * 0.6f;
                    float size = (0.35f + 0.5f * tt) * s;
                    Vfx.sprite(out, FxSoft.SMOKE[i & 3], x, y, z, size, Vfx.h(fx.seed, i + 60) * 6f, Vfx.with(0xFFA89A88,
                            0.28f * Math.min(1f, tt / 0.1f) * (1f - tt / 1.1f) * e));
                }
                // Chunks of earth thrown up.
                for (int i = 0; i < 24; i++) {
                    double ang = Vfx.h(fx.seed, i + 200) * 6.28;
                    float v = (2.5f + 3f * Vfx.h(fx.seed, i + 210)) * s;
                    float tt = t - 0.05f;
                    if (tt < 0f || tt > 1.3f) {
                        continue;
                    }
                    float d = Vfx.fly(v, 0.6f, 0f, tt);
                    float x = (float) Math.cos(ang) * (0.6f * s + d), z = (float) Math.sin(ang) * (0.6f * s + d);
                    float y = Math.max(gy + 0.04f, gy + Vfx.fly((4f + 3f * Vfx.h(fx.seed, i + 220)) * s, 0.6f, -18f, tt));
                    float side = (0.06f + 0.05f * Vfx.h(fx.seed, i + 230)) * s;
                    FxDraw.cube(out, x, y + side / 2f, z, side, DIRT, Math.min(1f, (1.3f - tt) / 0.3f) * e);
                }
            }
            // Strands of light up the middle.
            float sa = Vfx.life(t, 1.6f, 0.1f, 0.8f) * e;
            for (int strand = 0; strand < 2; strand++) {
                for (int j = 0; j < 16; j++) {
                    float ph = j / 16f;
                    float y = gy + ph * 3f * s * Math.min(1f, t / 0.5f);
                    float ang = strand * (float) Math.PI + ph * 9f + t * 7f;
                    float rad = 0.5f * s * (1f - ph * 0.6f);
                    float x = (float) Math.cos(ang) * rad, z = (float) Math.sin(ang) * rad;
                    if (out.glowPass()) {
                        Vfx.streak(out, x, y, z, -(float) Math.sin(ang), 0.5f, (float) Math.cos(ang), 0.3f * s, 0.1f * s,
                                Vfx.with(hot, sa * (1f - ph * 0.6f) * gk));
                    }
                }
            }
            FxBits.sparks(out, fx, t, 0f, gy + 0.3f, 0f, Math.round(40 * fx.amount), 7f * s, 0.3f, -12f, 1.6f, 0.9f, c, s, gy, 6);
        }
    };
    private static final int[] DIRT = Ramp.of(0xFF6B5442);

    static final FxKind[] KINDS = {SOUL_RISE, LIGHTNING, FIREWORK, SHATTER, BLACK_HOLE, SKY_LASER, GRAVESTONE, CONFETTI, BURNING_COPY,
            BURST, SHOCKWAVE};

    /** Every kind, for the checks. */
    public static FxKind kind(String style) {
        int i = STYLES.indexOf(style);
        return i < 0 ? null : KINDS[i];
    }
}
