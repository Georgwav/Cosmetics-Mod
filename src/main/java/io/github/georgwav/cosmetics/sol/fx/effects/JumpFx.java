package io.github.georgwav.cosmetics.sol.fx.effects;

import io.github.georgwav.cosmetics.client.Setting;
import io.github.georgwav.cosmetics.sol.fx.Bolts;
import io.github.georgwav.cosmetics.sol.fx.Fx;
import io.github.georgwav.cosmetics.sol.fx.FxBits;
import io.github.georgwav.cosmetics.sol.fx.FxSoft;
import io.github.georgwav.cosmetics.sol.fx.FxDraw;
import io.github.georgwav.cosmetics.sol.fx.FxEvents;
import io.github.georgwav.cosmetics.sol.fx.FxKind;
import io.github.georgwav.cosmetics.sol.fx.FxModule;
import io.github.georgwav.cosmetics.sol.fx.FxOut;
import io.github.georgwav.cosmetics.sol.fx.FxPool;
import io.github.georgwav.cosmetics.sol.fx.FxPreview;
import io.github.georgwav.cosmetics.sol.fx.FxSettings;
import io.github.georgwav.cosmetics.sol.fx.FxSprites;
import io.github.georgwav.cosmetics.sol.fx.FxWorld;
import io.github.georgwav.cosmetics.sol.fx.PoseSnapshot;
import io.github.georgwav.cosmetics.sol.fx.Ramp;
import io.github.georgwav.cosmetics.sol.fx.SolPicks;
import io.github.georgwav.cosmetics.sol.fx.Vfx;
import io.github.georgwav.cosmetics.sol.fx.WorldOut;
import io.github.georgwav.cosmetics.sol.util.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Jump FX (C.4): when you land (or jump, or both), only on your screen: a Square ring (Sol's square sun as a ripple), a
 * Ring, a Double ring, a Hexagon, a Star burst shattering outward, a Block wave (copies of the nearby blocks' tops
 * rippling out six blocks while the real ones stay put), Landing bolts, or a Hologram (a see-through copy of you in your
 * colour that stays a second). The rings lie on the terrain and grow with the fall; any ring can bring a particle set:
 * the preset's, Ice, Fire, Soul or Galaxy.
 */
public final class JumpFx extends FxModule {
    public static final List<String> STYLES = List.of("square_ring", "ring", "double_ring", "hexagon", "star_burst", "block_wave",
            "landing_bolts", "hologram");
    public static final List<String> SETS = List.of("off", "theme", "ice", "fire", "soul", "galaxy");
    /** Each style's length at 100%, seconds. */
    private static final float[] BASE = {0.8f, 0.7f, 0.85f, 0.8f, 0.8f, 1.1f, 0.45f, 1.2f};

    public final FxSettings fx = fx("jump", STYLES, new FxSettings.Spec().random());
    public final Setting.Choice when = add(new Setting.Choice("jump_when", "land", List.of("land", "jump", "both")).group("jump"));
    public final Setting.Choice particles = add(new Setting.Choice("jump_particles", "theme", SETS).group("jump"));
    public final Setting.Toggle hologramFirst = add(new Setting.Toggle("jump_hologram_first_person", false).group("jump"));

    public JumpFx() {
        super("jump_fx", Category.VIEW, "jump");
        hologramFirst.shownWhen(() -> fx.style.get().equals("hologram") || fx.random.get());
        FxEvents.JUMP.add(new FxEvents.Jump() {
            @Override
            public boolean listening() {
                return enabled();
            }

            @Override
            public void jumped(Player p) {
                if (!when.get().equals("land")) {
                    play(FxWorld.POOL, p, p.getX(), p.getY(), p.getZ(), 1.2f, p.getYRot());
                }
            }

            @Override
            public void landed(Player p, float fallHeight, float fallSpeed, BlockState on) {
                if (!when.get().equals("jump")) {
                    play(FxWorld.POOL, p, p.getX(), p.getY(), p.getZ(), fallHeight, p.getYRot());
                }
            }
        });
        SolPicks.module(this);
        SolPicks.set(fx.style, "square_ring");
        SolPicks.set(fx.random, false);
        SolPicks.set(when, "land");
    }

    /** The size a fall of {@code fall} blocks makes: from 70% for a hop up to twice for a long drop. */
    static float fallScale(float fall) {
        return Math.max(0.7f, Math.min(2f, 0.6f + fall * 0.15f));
    }

    /** One effect at (x, y, z), feet on the ground, after a fall of {@code fall} blocks. */
    private float play(FxPool pool, Player who, double x, double y, double z, float fall, float yaw) {
        int style = fx.pick((int) (System.nanoTime() >>> 10));
        Minecraft mc = Minecraft.getInstance();
        if (style == 7 && who != null && who == mc.player && mc.options.getCameraType().isFirstPerson() && !hologramFirst.get()) {
            // The hologram stands where you are: from inside it you'd see nothing but its faces.
            return 0f;
        }
        Fx f = start(pool, fx, KINDS[style], x, y, z, BASE[style]);
        fx.apply(f, style);
        f.a = fallScale(fall);
        f.c = ground(pool, x, z, y + 0.5) - (float) y;
        f.yaw = yaw;
        f.i0 = Math.max(0, SETS.indexOf(particles.get()));
        if (style == 7) {
            f.copy = who != null ? PoseSnapshot.of(who, 1f) : null;
        }
        return f.life;
    }

    /** The hologram is a copy of you where you landed: the dummy steps away while it shows. */
    @Override
    public boolean hidesDummy() {
        return fx.style.get().equals("hologram") && !fx.random.get();
    }

    @Override
    public float tryIt(FxPool pool, FxPreview.Dummy dummy) {
        int style = fx.pick((int) (System.nanoTime() >>> 10));
        Fx f = start(pool, fx, KINDS[style], dummy.x, dummy.y, dummy.z, BASE[style]);
        fx.apply(f, style);
        f.a = fallScale(3f);
        f.c = ground(pool, dummy.x, dummy.z, dummy.y + 0.5) - (float) dummy.y;
        f.yaw = dummy.yaw;
        f.i0 = Math.max(0, SETS.indexOf(particles.get()));
        f.copy = dummy.body;
        return f.life;
    }

    // ------------------------------------------------------------------ the kinds

    private static final float T = FxDraw.TEXEL;
    private static final float[] RR = new float[3];
    /** The particle sets' colours: ice, fire, soul, galaxy (theme uses the effect's own). */
    private static final int[][] SET_COLOURS = {
            {0xFFFFFFFF, 0xFFBFF6FF, 0xFF7FE0FF},
            {0xFFFFF0A0, 0xFFFF9A2E, 0xFFE0401A},
            {0xFFDFFBFF, 0xFF5AD8E8, 0xFF2A7FA0},
            {0xFFFFFFFF, 0xFFB78CFF, 0xFFFF7AD8}};

    /**
     * The landing itself, under every style: a flash at your feet, the light it throws on the ground, and dust kicked
     * out round you.
     */
    static void landing(FxOut out, Fx fx, float t, float scale, int c) {
        float gy = fx.c, gk = FxBits.glowK(fx), e = fx.early(0f);
        if (out.glowPass() && t < 0.35f) {
            float k = t / 0.35f, f = (1f - k) * (1f - k);
            Vfx.hot(out, 0f, gy + 0.15f, 0f, 0.9f * scale, Vfx.with(Vfx.white(c, 0.6f), 0.8f * f * gk));
            Vfx.pool(out, 0f, gy, 0f, 2.2f * scale, Vfx.with(c, 0.7f * f * gk));
        }
        FxBits.smoke(out, fx, t, 0f, gy + 0.12f, 0f, 8, 0.5f * scale, 0.35f, 0.08f, 0.7f, 0.22f * scale, 0xFFB8AEA2, 0.32f * e, 3);
    }

    /** The ring's particle set: bits thrown up from the ring as it spreads (theme, ice, fire, soul or galaxy). */
    static void particleSet(FxOut out, Fx fx, float radius, float t, float alpha, float now) {
        if (fx.i0 == 0 || alpha <= 0f) {
            return;
        }
        boolean glow = out.glowPass();
        float gk = FxBits.glowK(fx), s = fx.size;
        int n = Math.round(22 * fx.amount);
        for (int i = 0; i < n; i++) {
            double ang = i * Math.PI * 2 / n + Vfx.h(fx.seed, i) * 0.4;
            float sp = 0.6f + Vfx.h(fx.seed, i + 30) * 0.9f;
            float rise = Vfx.fly(sp * (fx.i0 == 3 || fx.i0 == 4 ? 1.8f : 1.3f), 1.2f, fx.i0 == 2 ? -2f : 0f, t);
            float r = radius * (0.85f + 0.25f * Vfx.h(fx.seed, i + 60));
            float x = (float) Math.cos(ang) * r, z = (float) Math.sin(ang) * r;
            float y = fx.c + 0.1f + rise;
            float a = alpha * Vfx.life(t, fx.base * 1.1f, 0.05f, fx.base * 0.5f);
            switch (fx.i0) {
                case 1 -> {
                    int col = fx.tone((i & 1) == 0 ? Ramp.CORE : Ramp.MAIN, i, now);
                    if (glow) {
                        Vfx.hot(out, x, y, z, 0.22f * s, Vfx.with(Vfx.white(col, 0.4f), a * gk));
                    } else {
                        Vfx.sprite(out, FxSoft.DOT, x, y, z, 0.04f * s, 0f, Vfx.with(Vfx.white(col, 0.4f), a));
                    }
                }
                case 2 -> {
                    int col = SET_COLOURS[0][i % 3];
                    float ang2 = t * 3f + i;
                    if (glow) {
                        Vfx.glint(out, x, y, z, 0.16f * s * (0.6f + 0.4f * (float) Math.sin(t * 18f + i)), ang2, Vfx.with(col, a * gk));
                    } else {
                        Vfx.sprite(out, (i & 1) == 0 ? FxSoft.STAR6 : FxSoft.DIAMOND, x, y, z, 0.07f * s, ang2, Vfx.with(col, 0.9f * a));
                    }
                }
                case 3, 4 -> {
                    int[] set = SET_COLOURS[fx.i0 - 2];
                    int col = set[1 + (i & 1)];
                    float fh = 0.16f * s * (1f - 0.5f * Math.min(1f, t / fx.base));
                    if (glow) {
                        Vfx.glow(out, x, y + fh * 0.5f, z, fh * 2.2f, Vfx.with(col, 0.55f * a * gk));
                    } else {
                        Vfx.billboard(out, FxSoft.FLAME[(i + (int) (t * 12f)) & 3], x, y + fh * 0.8f, z, fh * 0.55f, fh * 1.1f, 0f,
                                Vfx.with(Vfx.white(col, 0.2f), a));
                    }
                }
                default -> {
                    int col = SET_COLOURS[3][i % 3];
                    float tw = 0.5f + 0.5f * (float) Math.sin(t * 14f + i * 1.7f);
                    if (glow) {
                        Vfx.glint(out, x, y, z, 0.2f * s * tw, i, Vfx.with(col, a * gk));
                        Vfx.glow(out, x, y, z, 0.2f * s, Vfx.with(col, 0.3f * a * gk));
                    } else {
                        Vfx.sprite(out, FxSoft.DOT_SOFT, x, y, z, 0.05f * s, 0f, Vfx.with(col, 0.7f * a));
                    }
                }
            }
        }
    }

    /**
     * A ring style: {@code sides} 4 (Square ring), 0 (Ring), 6 (Hexagon); {@code doubled} adds a second, later and
     * smaller. A shock front of light races out over the ground, a thin bright edge on it, a soft wall of light standing
     * on a round one, dust kicked up at the landing.
     */
    private static FxKind rings(String id, int sides, boolean doubled) {
        return new FxKind(id, 6) {
            @Override
            public void draw(Fx fx, float age, float now, FxOut out) {
                float t = fx.time(age);
                Vfx.origin(out, fx);
                float base = fx.base, gy = fx.c, gk = FxBits.glowK(fx), e = fx.early(now);
                float scale = fx.a * fx.size;
                boolean glow = out.glowPass();
                float turn = sides == 4 ? (float) Math.toRadians(45f - fx.yaw) : sides == 6 ? (float) Math.toRadians(-fx.yaw) : 0f;
                int c = fx.tone(Ramp.MAIN, 0, now);
                landing(out, fx, t, scale, c);
                for (int k = 0; k < (doubled ? 2 : 1); k++) {
                    float p = (t - k * 0.14f) / (base * 0.85f);
                    if (p <= 0f || p >= 1f) {
                        continue;
                    }
                    float r = (0.25f + 2.3f * Vfx.outExpo(p)) * scale * (k == 1 ? 0.68f : 1f);
                    float a = (float) Math.pow(1f - p, 1.3f) * e;
                    int col = k == 0 ? c : fx.tone(Ramp.CORE, 1, now);
                    shape(out, sides, gy + 0.03f, r, (0.55f - 0.3f * p) * scale, turn, col, a, gk, glow);
                    if (sides == 4) {
                        // Sol's square sun: the inner square, smaller, a moment behind.
                        float pi = (t - 0.08f) / (base * 0.85f);
                        if (pi > 0f && pi < 1f) {
                            shape(out, 4, gy + 0.03f, (0.15f + 1.35f * Vfx.outExpo(pi)) * scale, 0.3f * scale, turn, fx.tone(Ramp.CORE, 1, now),
                                    (float) Math.pow(1f - pi, 1.3f) * e, gk, glow);
                        }
                    }
                    if (k == 0) {
                        if (glow && sides == 0) {
                            Vfx.wall(out, 0f, gy, 0f, r, 0.5f * scale * (1f - p), Vfx.with(col, 0.4f * a * gk), true);
                        }
                        particleSet(out, fx, r, t, a, now);
                    }
                }
            }
        };
    }

    /** One outline of a ring style: a shock front of light (glow pass) and a thin bright edge over the world. */
    private static void shape(FxOut out, int sides, float y, float r, float w, float turn, int col, float a, float gk, boolean glow) {
        if (glow) {
            int c = Vfx.with(col, a * gk);
            if (sides == 0) {
                Vfx.ring(out, FxSoft.FRONT, 0f, y, 0f, r, w, c, true);
            } else {
                Vfx.poly(out, FxSoft.FRONT, 0f, y, 0f, sides, r, w, turn, c, true);
            }
        } else {
            int c = Vfx.with(Vfx.white(col, 0.2f), 0.8f * a);
            float tw = Math.max(0.05f, w * 0.14f);
            if (sides == 0) {
                Vfx.ring(out, FxSoft.TUBE, 0f, y, 0f, r + w * 0.4f, tw, c, true);
            } else {
                Vfx.poly(out, FxSoft.TUBE, 0f, y, 0f, sides, r + w * 0.4f, tw, turn, c, true);
            }
        }
    }

    static final FxKind SQUARE_RING = rings("jump_square_ring", 4, false);
    static final FxKind RING = rings("jump_ring", 0, false);
    static final FxKind DOUBLE_RING = rings("jump_double_ring", 0, true);
    static final FxKind HEXAGON = rings("jump_hexagon", 6, false);

    /**
     * Star burst: a star of light at your feet, then rays of light shoot out over the ground, a glint racing at each
     * tip, breaking up into dashes as they slide on and fade.
     */
    static final FxKind STAR_BURST = new FxKind("jump_star_burst", 6) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            float t = fx.time(age);
            Vfx.origin(out, fx);
            float scale = fx.a * fx.size, gy = fx.c, gk = FxBits.glowK(fx), e = fx.early(now);
            boolean glow = out.glowPass();
            float p = Math.min(1f, t / (fx.base * 0.9f));
            float a = (float) Math.pow(1f - p, 1.2f) * e;
            if (a <= 0f) {
                return;
            }
            int c = fx.tone(Ramp.MAIN, 0, now), core = fx.tone(Ramp.CORE, 0, now);
            landing(out, fx, t, scale, c);
            if (glow && t < 0.25f) {
                float k = t / 0.25f;
                Vfx.glint(out, 0f, gy + 0.3f, 0f, 1.4f * scale * (1f - 0.4f * k), 0.2f, Vfx.with(Vfx.white(c, 0.6f), (1f - k) * e * gk));
            }
            int rays = 10;
            float reach = (0.3f + 2.6f * Vfx.outExpo(p)) * scale;
            float len = Math.max(0.3f, reach * 0.55f);
            for (int r = 0; r < rays; r++) {
                double ang = r * Math.PI * 2 / rays + Vfx.s(fx.seed, r) * 0.12;
                float cs = (float) Math.cos(ang), sn = (float) Math.sin(ang);
                float long1 = (r & 1) == 0 ? 1f : 0.7f;
                float tip = reach * long1, tail = Math.max(0f, tip - len * long1);
                // Unbroken at first, then in dashes opening up.
                int dashes = p < 0.35f ? 1 : 3;
                for (int d = 0; d < dashes; d++) {
                    float d0 = tail + (tip - tail) * d / dashes, d1 = tail + (tip - tail) * (d + (dashes == 1 ? 1f : 0.6f)) / dashes;
                    if (glow) {
                        Vfx.groundStreak(out, cs * d0, gy, sn * d0, cs * d1, sn * d1, 0.3f * scale, Vfx.with(c, a * gk));
                    } else {
                        Vfx.groundStreak(out, cs * d0, gy, sn * d0, cs * d1, sn * d1, 0.08f * scale, Vfx.with(c, 0.85f * a));
                    }
                }
                if (glow) {
                    Vfx.glint(out, cs * tip, Vfx.groundAt(cs * tip, gy, sn * tip) + 0.12f, sn * tip, 0.35f * scale * long1, t * 3f + r,
                            Vfx.with(Vfx.white(c, 0.4f), 0.8f * a * gk));
                }
            }
            particleSet(out, fx, reach * 0.8f, t, a, now);
        }
    };

    /** The colour of a block column's top (the real level's map colour, or the preview's grass). */
    private static int topColour(FxOut out, double x, double y, double z) {
        Minecraft mc = Minecraft.getInstance();
        if (out instanceof WorldOut && mc.level != null) {
            BlockPos pos = BlockPos.containing(x, y - 0.01, z);
            BlockState state = mc.level.getBlockState(pos);
            int c = state.getMapColor(mc.level, pos).col;
            if (c != 0) {
                return 0xFF000000 | c;
            }
        }
        return ((int) Math.floor(x) + (int) Math.floor(z) & 1) == 0 ? 0xFF5E9B4A : 0xFF588F45;
    }

    /**
     * Block wave: copies of the tops of the blocks round you rise and fall as a wave runs out six blocks, a front of
     * light running with it and dust thrown up behind.
     */
    static final FxKind BLOCK_WAVE = new FxKind("jump_block_wave", 3) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            float t = fx.time(age);
            Vfx.origin(out, fx);
            float reach = 6f * Math.min(1.4f, fx.a * fx.size) / 1.4f;
            float front = t / fx.base * (reach + 1.5f);
            float a = fx.early(now), gk = FxBits.glowK(fx);
            int col = fx.tone(Ramp.MAIN, 0, now);
            if (out.glowPass()) {
                float fa = Math.max(0f, 1f - front / (reach + 1.5f)) * a;
                Vfx.ring(out, FxSoft.FRONT, 0f, fx.c + 0.4f, 0f, Math.max(0.3f, front), 0.9f, Vfx.with(Vfx.white(col, 0.3f), 0.7f * fa * gk), true);
                if (t < 0.3f) {
                    Vfx.pool(out, 0f, fx.c, 0f, 2.5f, Vfx.with(col, 0.6f * (1f - t / 0.3f) * a * gk));
                }
                return;
            }
            int bx0 = (int) Math.floor(fx.x), bz0 = (int) Math.floor(fx.z);
            int span = (int) Math.ceil(reach);
            for (int dx = -span; dx <= span; dx++) {
                for (int dz = -span; dz <= span; dz++) {
                    double cx = bx0 + dx + 0.5, cz = bz0 + dz + 0.5;
                    float d = (float) Math.hypot(cx - fx.x, cz - fx.z);
                    if (d > reach || d < 0.6f) {
                        continue;
                    }
                    // A bump that passes as the front goes by: up to a third of a block, less further out.
                    float u = (front - d) / 1.2f;
                    if (u <= 0f || u >= 1f) {
                        continue;
                    }
                    float lift = (float) Math.sin(u * Math.PI) * 0.38f * (1f - d / (reach + 1f)) * fx.size;
                    if (lift < 0.02f) {
                        continue;
                    }
                    float top = out.ground(cx, cz, fx.y + fx.c + 0.6) - (float) fx.y;
                    int c = topColour(out, cx, fx.y + top, cz);
                    int[] ramp = fx.ramp2;
                    Ramp.of(c, ramp);
                    float x0 = (float) (bx0 + dx - fx.x), z0 = (float) (bz0 + dz - fx.z);
                    FxDraw.box(out, x0, top - 0.05f, z0, x0 + 1f, top + lift, z0 + 1f, Colors.withAlpha(ramp[Ramp.MAIN], a),
                            Colors.withAlpha(ramp[Ramp.SHADE], a), Colors.withAlpha(ramp[Ramp.DEEP], a), Colors.withAlpha(ramp[Ramp.DEEP], a));
                    // The wave's crest lit in the effect's colour.
                    if (u > 0.3f && u < 0.7f) {
                        Vfx.decal(out, FxSoft.SQUARE, x0 + 0.5f, top + lift + 0.005f, z0 + 0.5f, 0.5f, 0f,
                                Vfx.with(Vfx.white(col, 0.3f), 0.45f * a * (float) Math.sin((u - 0.3f) / 0.4f * Math.PI)));
                    }
                }
            }
            FxBits.smoke(out, fx, t, 0f, fx.c + 0.15f, 0f, 10, reach * 0.55f, 0.4f, fx.base * 0.6f, 0.9f, 0.35f, 0xFFB8AEA2, 0.28f * a, 5);
        }
    };

    /** Landing bolts: five or six bolts of lightning strike round your feet in a quarter second, each leaving a scorch. */
    static final FxKind LANDING_BOLTS = new FxKind("jump_landing_bolts", 4) {
        private final float[] end = new float[3];

        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            float t = fx.time(age);
            Vfx.origin(out, fx);
            int n = Math.max(4, Math.min(7, Math.round(6 * fx.amount)));
            float scale = fx.a * fx.size, gk = FxBits.glowK(fx), e = fx.early(now);
            int c = fx.tone(Ramp.MAIN, 0, now);
            boolean glow = out.glowPass();
            for (int b = 0; b < n; b++) {
                float at = b * 0.035f, life = 0.2f;
                float k = (t - at) / life;
                double ang = b * Math.PI * 2 / n + Vfx.h(fx.seed, b) * 0.6;
                float r = (0.6f + 0.8f * Vfx.h(fx.seed, b + 9)) * scale;
                float gx = (float) Math.cos(ang) * r, gz = (float) Math.sin(ang) * r;
                float gy = Vfx.groundAt(gx, fx.c, gz);
                if (k >= 0f && k < 1f) {
                    float f = (1f - k) * e;
                    Bolts.build(fx.seed * 13 + b, gx * 0.4f + Vfx.s(fx.seed, b + 40) * 0.6f, gy + 2.6f * scale, gz * 0.4f, gx, gy, gz, 0.2f, 1);
                    Bolts.draw(out, 0.03f * scale, 0.28f * scale, Vfx.with(0xFFFFFFFF, f * Math.max(0.6f, gk)), Vfx.with(c, 0.7f * f * gk),
                            Math.min(1f, k / 0.25f));
                    if (glow && k > 0.2f) {
                        Vfx.hot(out, gx, gy + 0.1f, gz, 0.5f * scale, Vfx.with(Vfx.white(c, 0.6f), f * gk));
                        Vfx.pool(out, gx, gy, gz, 0.9f * scale, Vfx.with(c, 0.7f * f * gk));
                    }
                }
                FxBits.sparks(out, fx, t - at - life * 0.25f, gx, gy + 0.05f, gz, 6, 4f * scale, 0.6f, -12f, 1.5f, 0.45f, c, scale, gy, 10 + b);
                if (!glow && t >= at + life * 0.25f) {
                    float sa = Vfx.life(t - at, fx.base, 0f, 0.25f) * e;
                    Vfx.decal(out, FxSoft.SCORCH, gx, gy + 0.008f, gz, 0.35f * scale, b * 1.3f, Vfx.with(0xFF1C1612, 0.75f * sa));
                }
            }
        }
    };

    /**
     * Hologram: a see-through copy of you in your colour standing on a turning projector disc, a scan line running up
     * it; it stays a second and flickers out.
     */
    static final FxKind HOLOGRAM = new FxKind("jump_hologram", 3) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            float t = fx.time(age);
            Vfx.origin(out, fx);
            float in = Math.min(1f, t / 0.15f);
            float flicker = t > 0.85f && FxDraw.hash((int) (t * 30f), fx.seed) < 0.4f ? 0.4f : 1f;
            float a = 0.5f * in * flicker * fx.early(now);
            float cut = t > 0.9f ? Math.min(1f, (t - 0.9f) / 0.3f) : 0f;
            float gk = FxBits.glowK(fx), s = fx.size, gy = fx.c;
            int col = fx.tone(Ramp.MAIN, 0, now);
            float h = fx.copy != null ? fx.copy.height * s : 1.8f * s;
            float disc = Vfx.life(t, fx.base, 0.12f, 0.35f) * fx.early(now);
            // The projector disc and its beam.
            if (out.glowPass()) {
                Vfx.decalGround(out, FxSoft.HEX, 0f, gy, 0f, 0.75f * s, t * 0.6f, Vfx.with(col, 0.45f * disc * gk));
                Vfx.ring(out, FxSoft.TUBE, 0f, gy + 0.03f, 0f, 0.7f * s, 0.09f * s, Vfx.with(Vfx.white(col, 0.3f), 0.9f * disc * gk), true);
                Vfx.pool(out, 0f, gy, 0f, 1.2f * s, Vfx.with(col, 0.5f * disc * gk));
                Vfx.wall(out, 0f, gy, 0f, 0.62f * s, h * 1.05f, Vfx.with(col, 0.12f * disc * gk), true);
                float sy = gy + ((t * 1.3f) % 1f) * h;
                Vfx.ring(out, FxSoft.LINE, 0f, sy, 0f, 0.45f * s, 0.12f * s, Vfx.with(Vfx.white(col, 0.4f), 0.6f * a * gk), false);
            } else {
                Vfx.ring(out, FxSoft.TUBE, 0f, gy + 0.02f, 0f, 0.7f * s, 0.04f * s, Vfx.with(Vfx.white(col, 0.4f), 0.8f * disc), true);
            }
            if (fx.copy == null) {
                return;
            }
            if (out.glowPass()) {
                if (fx.glow > 0) {
                    fx.copy.drawFaces(out, 0f, 0f, 0f, s, fx.copy.tinted(col, 1f), Colors.withAlpha(fx.glowColour, a * 0.45f), false);
                }
                return;
            }
            fx.copy.draw(out, 0f, 0f, 0f, s, 0f, fx.copy.tinted(col, 1f), Colors.withAlpha(0xFFFFFFFF, a),
                    cut > 0f ? PoseSnapshot.NOISE : PoseSnapshot.WHOLE, cut);
            // Its bright edge: the faces turned away of a copy a little bigger, in the colour itself.
            if (cut <= 0f) {
                float k = 1.05f;
                fx.copy.drawFaces(out, 0f, -fx.copy.height * s * (k - 1f) / 2f, 0f, s * k, WHITE,
                        Colors.withAlpha(col, Math.min(1f, a * 2f)), true);
            }
        }
    };

    private static final io.github.georgwav.cosmetics.sol.fx.FxTex WHITE = new io.github.georgwav.cosmetics.sol.fx.FxTex("jump_white", 1, 1,
            new int[]{0xFFFFFFFF});

    static final FxKind[] KINDS = {SQUARE_RING, RING, DOUBLE_RING, HEXAGON, STAR_BURST, BLOCK_WAVE, LANDING_BOLTS, HOLOGRAM};
}
