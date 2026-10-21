package io.github.georgwav.cosmetics.sol.fx.effects;

import io.github.georgwav.cosmetics.client.Setting;
import io.github.georgwav.cosmetics.sol.fx.Fx;
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

    private static float easeOut(float k) {
        k = Math.max(0f, Math.min(1f, k));
        return 1f - (1f - k) * (1f - k);
    }

    /** Fades out in steps over the last 40% of {@code p} (0..1). */
    private static float fade(Fx fx, float p, float now) {
        return Ramp.steps(Math.min(1f, (1f - p) / 0.4f), 4, fx.snap) * fx.early(now);
    }

    /**
     * A shape's outline of texel cells lying on the terrain: {@code sides} straight sides (0 for a circle) round the
     * middle, {@code radius} out, {@code width} texels wide, turned {@code turn}; sampled under each cell.
     */
    static void outline(FxOut out, Fx fx, int sides, float radius, float turn, int width, int tone, float alpha, float now) {
        if (alpha <= 0f || radius <= 0f) {
            return;
        }
        if (sides == 0) {
            FxDraw.ring(out, fx, fx.x, fx.y, fx.z, 0f, fx.c, 0f, radius, width, tone, alpha, true, now);
            return;
        }
        boolean glow = out.glowPass();
        if (glow && fx.glow == 0) {
            return;
        }
        out.use(FxSprites.atlas());
        float[] uv = FxSprites.SQUARE.uv[FxSprites.ALL];
        int lastX = Integer.MIN_VALUE, lastZ = Integer.MIN_VALUE;
        for (int w = 0; w < width; w++) {
            float r = radius - w * T;
            if (r <= 0f) {
                break;
            }
            for (int side = 0; side < sides; side++) {
                double a0 = turn + side * Math.PI * 2 / sides, a1 = turn + (side + 1) * Math.PI * 2 / sides;
                float x0 = (float) Math.cos(a0) * r, z0 = (float) Math.sin(a0) * r, x1 = (float) Math.cos(a1) * r, z1 = (float) Math.sin(a1) * r;
                int steps = Math.max(2, (int) Math.ceil(Math.hypot(x1 - x0, z1 - z0) / T));
                for (int s = 0; s < steps; s++) {
                    float k = s / (float) steps;
                    int gx = Math.round((x0 + (x1 - x0) * k) / T), gz = Math.round((z0 + (z1 - z0) * k) / T);
                    if (gx == lastX && gz == lastZ) {
                        continue;
                    }
                    lastX = gx;
                    lastZ = gz;
                    float sx = gx * T, sz = gz * T;
                    float sy = out.ground(fx.x + sx, fx.z + sz, fx.y + fx.c + 0.6) - (float) fx.y;
                    if (glow) {
                        float g = fx.glow == 2 ? 1.7f : 1f;
                        FxDraw.flat(out, sx, sy + 0.01f, sz, T, T, uv, Colors.withAlpha(fx.glowColour, Math.min(1f, 0.22f * g * alpha)));
                    } else {
                        FxDraw.flat(out, sx, sy + 0.006f, sz, T / 2f, T / 2f, uv, Colors.withAlpha(fx.tone(tone, s + side, now), alpha));
                    }
                }
            }
        }
    }

    /** The ring's particle set: bits thrown up from the ring as it spreads. */
    static void particleSet(FxOut out, Fx fx, float radius, float t, float alpha, float now) {
        if (fx.i0 == 0 || alpha <= 0f) {
            return;
        }
        int n = Math.round(14 * fx.amount);
        for (int i = 0; i < n; i++) {
            double ang = i * Math.PI * 2 / n + FxDraw.hash(fx.seed, i) * 0.4;
            float rise = t * (0.6f + FxDraw.hash(fx.seed, i + 30) * 0.8f) * (fx.i0 == 3 || fx.i0 == 4 ? 1.6f : 1f);
            float r = radius * (0.9f + 0.2f * FxDraw.hash(fx.seed, i + 60));
            float x = Ramp.snap((float) Math.cos(ang) * r, fx.snap), z = Ramp.snap((float) Math.sin(ang) * r, fx.snap);
            float y = Ramp.snap(fx.c + 0.1f + rise, fx.snap);
            float a = alpha * (FxDraw.hash((int) (t * 10f) + i, fx.seed) > 0.25f || fx.i0 != 5 ? 1f : 0.3f);
            switch (fx.i0) {
                case 1 -> {
                    FxDraw.square(out, x, y, z, T * fx.size, Colors.withAlpha(fx.tone((i & 1) == 0 ? Ramp.CORE : Ramp.MAIN, i, now), a));
                    FxDraw.squareGlow(out, fx, x, y, z, T * fx.size, a);
                }
                case 2 -> {
                    int c = SET_COLOURS[0][i % 3];
                    if ((i & 1) == 0) {
                        FxDraw.spriteFlat(out, FxSprites.PLUS, x, y, z, 0.4f * T * fx.size, Colors.withAlpha(c, a));
                    } else {
                        FxDraw.square(out, x, y, z, T * fx.size, Colors.withAlpha(c, a));
                    }
                }
                case 3, 4 -> {
                    int[] set = SET_COLOURS[fx.i0 - 2];
                    FxDraw.spriteFlat(out, FxSprites.FLAME[(i + (int) (t * 8f)) % 3], x, y, z, 0.35f * T * fx.size,
                            Colors.withAlpha(set[1 + (i & 1)], a));
                }
                default -> FxDraw.spriteFlat(out, FxSprites.STAR, x, y, z, 0.4f * T * fx.size, Colors.withAlpha(SET_COLOURS[3][i % 3], a));
            }
        }
    }

    /** A ring style: {@code sides} 4 (Square ring), 0 (Ring), 6 (Hexagon); {@code doubled} adds a second, later and smaller. */
    private static FxKind rings(String id, int sides, boolean doubled) {
        return new FxKind(id, 6) {
            @Override
            public void draw(Fx fx, float age, float now, FxOut out) {
                float t = fx.time(age);
                FxDraw.origin(out, fx);
                float base = fx.base;
                float scale = fx.a * fx.size;
                float turn = sides == 4 ? (float) Math.toRadians(45f - fx.yaw) : 0f;
                for (int k = 0; k < (doubled ? 2 : 1); k++) {
                    float p = (t - k * 0.14f) / (base * 0.85f);
                    if (p <= 0f || p >= 1f) {
                        continue;
                    }
                    float r = (0.25f + 2.1f * easeOut(p)) * scale * (k == 1 ? 0.7f : 1f);
                    outline(out, fx, sides, r, turn, 2, k == 0 ? Ramp.MAIN : Ramp.CORE, fade(fx, p, now), now);
                    if (sides == 4) {
                        // Sol's square sun: the inner square, smaller, a moment behind.
                        float pi = (t - 0.08f) / (base * 0.85f);
                        if (pi > 0f && pi < 1f) {
                            outline(out, fx, 4, (0.15f + 1.3f * easeOut(pi)) * scale, turn, 1, Ramp.CORE, fade(fx, pi, now), now);
                        }
                    }
                    if (k == 0) {
                        particleSet(out, fx, r, t, fade(fx, p, now), now);
                    }
                }
            }
        };
    }

    static final FxKind SQUARE_RING = rings("jump_square_ring", 4, false);
    static final FxKind RING = rings("jump_ring", 0, false);
    static final FxKind DOUBLE_RING = rings("jump_double_ring", 0, true);
    static final FxKind HEXAGON = rings("jump_hexagon", 6, false);

    /**
     * Star burst: eight rays of texels shoot out over the ground from your feet, each a dash brightest at its tip,
     * then shatter into pieces with gaps opening between them as they slide on and fade.
     */
    static final FxKind STAR_BURST = new FxKind("jump_star_burst", 6) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            float t = fx.time(age);
            FxDraw.origin(out, fx);
            float scale = fx.a * fx.size;
            float p = Math.min(1f, t / (fx.base * 0.9f));
            float a = fade(fx, p, now);
            if (a <= 0f) {
                return;
            }
            if (t < 0.12f) {
                FxDraw.sprite(out, fx, FxSprites.STAR, 0f, fx.c + 0.15f, 0f, 1.2f * T * scale, 0, fx.early(now), now);
            }
            int rays = 8;
            float reach = (0.3f + 2.3f * easeOut(p)) * scale;
            float len = Math.max(0.3f, reach * 0.5f);
            boolean broken = p > 0.4f;
            int phase = (int) (p * 12f);
            for (int r = 0; r < rays; r++) {
                double ang = r * Math.PI * 2 / rays + FxDraw.signed(fx.seed, r) * 0.12;
                float cs = (float) Math.cos(ang), sn = (float) Math.sin(ang);
                int cells = Math.max(3, (int) (len / (2f * T)));
                for (int k = 0; k < cells; k++) {
                    if (broken && (k + phase + r) % 3 == 0) {
                        continue;
                    }
                    // From the tip back toward your feet.
                    float d = reach - k * 2f * T;
                    float x = Ramp.snap(cs * d, fx.snap), z = Ramp.snap(sn * d, fx.snap);
                    float y = out.ground(fx.x + x, fx.z + z, fx.y + fx.c + 0.6) - (float) fx.y + 0.01f;
                    float f = k / (float) cells;
                    int tone = k == 0 ? Ramp.CORE : f < 0.6f ? Ramp.MAIN : Ramp.SHADE;
                    // Two texels wide, three at the tip: a ray you can see across the ground.
                    float side = T * fx.size * (k == 0 ? 3f : 2f);
                    FxDraw.cell(out, x, y, z, side, Colors.withAlpha(fx.tone(tone, r, now), a));
                    if (out.glowPass() && k == 0) {
                        FxDraw.squareGlow(out, fx, x, y + 0.05f, z, side * 0.7f, a * 0.6f);
                    }
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

    /** Block wave: copies of the tops of the blocks round you rise and fall as a wave runs out six blocks. */
    static final FxKind BLOCK_WAVE = new FxKind("jump_block_wave", 3) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            if (out.glowPass()) {
                return;
            }
            float t = fx.time(age);
            FxDraw.origin(out, fx);
            float reach = 6f * Math.min(1.4f, fx.a * fx.size) / 1.4f;
            float front = t / fx.base * (reach + 1.5f);
            float a = fx.early(now);
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
                    float lift = Ramp.snap((float) Math.sin(u * Math.PI) * 0.33f * (1f - d / (reach + 1f)) * fx.size, fx.snap);
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
                    // The wave's crest in the effect's colour along the top edge.
                    if (u > 0.35f && u < 0.65f) {
                        FxDraw.box(out, x0, top + lift, z0, x0 + 1f, top + lift + T, z0 + 1f, Colors.withAlpha(fx.ramp[Ramp.MAIN], a * 0.8f),
                                Colors.withAlpha(fx.ramp[Ramp.SHADE], a * 0.8f), Colors.withAlpha(fx.ramp[Ramp.SHADE], a * 0.8f), 0);
                    }
                }
            }
        }
    };

    /** Landing bolts: four to six tiny bolts strike round your feet in a quarter second, each leaving a scorch. */
    static final FxKind LANDING_BOLTS = new FxKind("jump_landing_bolts", 4) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            float t = fx.time(age);
            FxDraw.origin(out, fx);
            out.right(RR);
            int n = Math.max(4, Math.min(6, Math.round(5 * fx.amount)));
            float scale = fx.a * fx.size;
            for (int b = 0; b < n; b++) {
                float at = b * 0.04f, life = 0.16f;
                float k = (t - at) / life;
                double ang = b * Math.PI * 2 / n + FxDraw.hash(fx.seed, b) * 0.6;
                float r = (0.5f + 0.7f * FxDraw.hash(fx.seed, b + 9)) * scale;
                float gx = Ramp.snap((float) Math.cos(ang) * r, fx.snap), gz = Ramp.snap((float) Math.sin(ang) * r, fx.snap);
                float gy = out.ground(fx.x + gx, fx.z + gz, fx.y + fx.c + 0.6) - (float) fx.y;
                if (k >= 0f && k < 1f) {
                    float a = (k < 0.5f ? 1f : 0.5f) * fx.early(now);
                    for (int s = 0; s < 7; s++) {
                        float ny = gy + 1.4f * scale * (1f - (s + 1) / 7f);
                        float nx = s == 6 ? 0f : FxDraw.signed(fx.seed + b, s) * 0.1f;
                        float px = gx + nx * RR[0], pz = gz + nx * RR[2];
                        FxDraw.square(out, Ramp.snap(px, fx.snap), Ramp.snap(ny, fx.snap), Ramp.snap(pz, fx.snap), T * fx.size,
                                Colors.withAlpha(fx.tone(s % 3 == 0 ? Ramp.CORE : Ramp.MAIN, b, now), a));
                        FxDraw.squareGlow(out, fx, px, ny, pz, T * fx.size * 2f, a);
                    }
                }
                if (t >= at + life * 0.6f) {
                    float sa = Ramp.steps(1f - (t - at - life) / 0.25f, 3, fx.snap) * fx.early(now);
                    if (sa > 0f && !out.glowPass()) {
                        out.use(FxSprites.atlas());
                        FxDraw.flat(out, gx, gy + 0.008f, gz, 2f * T * fx.size, 2f * T * fx.size, FxSprites.SCORCH.uv[FxSprites.ALL],
                                Colors.withAlpha(fx.tone(Ramp.DEEP, b, now), sa * 0.8f));
                    }
                }
            }
        }
    };

    /** Hologram: a see-through copy of you in your colour, scanned in rows, stays a second and flickers out. */
    static final FxKind HOLOGRAM = new FxKind("jump_hologram", 3) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            if (fx.copy == null) {
                return;
            }
            float t = fx.time(age);
            FxDraw.origin(out, fx);
            float in = Ramp.steps(Math.min(1f, t / 0.15f), 3, fx.snap);
            float flicker = t > 0.85f && FxDraw.hash((int) (t * 30f), fx.seed) < 0.4f ? 0.4f : 1f;
            float a = 0.45f * in * flicker * fx.early(now);
            float cut = t > 0.9f ? Math.min(1f, (t - 0.9f) / 0.3f) : 0f;
            if (out.glowPass()) {
                if (fx.glow > 0) {
                    fx.copy.drawFaces(out, 0f, 0f, 0f, fx.size, fx.copy.tinted(fx.tone(Ramp.MAIN, 0, now), 1f),
                            Colors.withAlpha(fx.glowColour, a * 0.4f), false);
                }
                return;
            }
            fx.copy.draw(out, 0f, 0f, 0f, fx.size, 0f, fx.copy.tinted(fx.tone(Ramp.MAIN, 0, now), 1f),
                    Colors.withAlpha(0xFFFFFFFF, a), cut > 0f ? PoseSnapshot.NOISE : PoseSnapshot.WHOLE, Ramp.steps(cut, 4, fx.snap));
            // Its bright edge: the faces turned away of a copy a little bigger, in the colour itself.
            if (cut <= 0f) {
                float k = 1.05f;
                fx.copy.drawFaces(out, 0f, -fx.copy.height * fx.size * (k - 1f) / 2f, 0f, fx.size * k, WHITE,
                        Colors.withAlpha(fx.tone(Ramp.MAIN, 0, now), Math.min(1f, a * 2f)), true);
            }
        }
    };

    private static final io.github.georgwav.cosmetics.sol.fx.FxTex WHITE = new io.github.georgwav.cosmetics.sol.fx.FxTex("jump_white", 1, 1,
            new int[]{0xFFFFFFFF});

    static final FxKind[] KINDS = {SQUARE_RING, RING, DOUBLE_RING, HEXAGON, STAR_BURST, BLOCK_WAVE, LANDING_BOLTS, HOLOGRAM};
}
