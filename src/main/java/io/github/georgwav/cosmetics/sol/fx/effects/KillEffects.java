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
 * ticked ones at random), all in Sol's texels: Soul rise, Lightning (moved here from PvP Effects, restyled),
 * Firework, Shatter, Black hole, Sky laser, Gravestone, Confetti, Burning copy, Burst and Shockwave. Only on your
 * screen; nothing is spawned in the world.
 */
public final class KillEffects extends FxModule {
    public static final List<String> STYLES = List.of("soul_rise", "lightning", "firework", "shatter", "black_hole", "sky_laser",
            "gravestone", "confetti", "burning_copy", "burst", "shockwave");
    /** Each style's length at 100%, seconds. */
    private static final float[] BASE = {1.6f, 0.7f, 2.2f, 1.5f, 1.6f, 1.5f, 5f, 2.5f, 1.8f, 3f, 1.4f};

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
            if (style == 1) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.LIGHTNING_BOLT_THUNDER, 1.1f, 0.3f));
                FxScreen.flash(f.ramp[Ramp.CORE], 0.06f, 0.32f, 0.16f);
            }
        }
        return f.life;
    }

    // ------------------------------------------------------------------ the styles

    private static final float T = FxDraw.TEXEL;
    private static final float[] P = new float[3], RR = new float[3], UU = new float[3];

    static float easeOut(float k) {
        k = Math.max(0f, Math.min(1f, k));
        return 1f - (1f - k) * (1f - k);
    }

    private static float alpha(Fx fx, float a, float now) {
        return Ramp.steps(Math.max(0f, Math.min(1f, a)), 4, fx.snap) * fx.early(now);
    }

    /** Soul rise: the copy, tinted 60% to the colour, rises two blocks, dissolving from the top in four steps. */
    static final FxKind SOUL_RISE = new FxKind("kill_soul_rise", 6) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            float t = fx.time(age);
            FxDraw.origin(out, fx);
            float p = t / 1.6f;
            float rise = Ramp.snap(2f * fx.size * easeOut(p), fx.snap);
            float cut = Math.min(1f, (float) Math.floor(p * 4f) / 4f);
            if (!out.glowPass() && fx.copy != null) {
                fx.copy.draw(out, 0f, rise, 0f, fx.size, 0f, fx.copy.tinted(fx.tone(Ramp.MAIN, 0, now), 0.6f),
                        Colors.withAlpha(0xFFFFFFFF, 0.85f * fx.early(now)), PoseSnapshot.FROM_TOP, cut);
            }
            // Plus sparkles shed where the dissolve is, drifting up.
            int n = Math.round(18 * fx.amount);
            float h = fx.copy != null ? fx.copy.height : 1.8f;
            for (int i = 0; i < n; i++) {
                float ti = i / (float) n * 1.4f, a = (t - ti) / 0.7f;
                if (a < 0f || a > 1f) {
                    continue;
                }
                float pi = ti / 1.6f;
                float level = 2f * fx.size * easeOut(pi) + h * fx.size * (1f - (float) Math.floor(pi * 4f) / 4f);
                double ang = FxDraw.hash(fx.seed, i) * Math.PI * 2;
                float r = 0.32f * fx.size * (0.5f + FxDraw.hash(fx.seed, i + 50));
                float x = Ramp.snap((float) Math.cos(ang) * r, fx.snap), z = Ramp.snap((float) Math.sin(ang) * r, fx.snap);
                float y = Ramp.snap(level + a * 0.8f, fx.snap);
                FxDraw.sprite(out, fx, FxSprites.PLUS, x, y, z, T * fx.size, i, alpha(fx, 1f - a, now), now);
            }
        }
    };

    /** Lightning: a stepped bolt of texels, white at its core and the colour at its edges, branching twice. */
    static final FxKind LIGHTNING = new FxKind("kill_lightning", 4) {
        private final float[] bx = new float[48], by = new float[48], bz = new float[48];

        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            float t = fx.time(age);
            float a = t < 0.22f ? 1f : t < 0.4f ? 0.5f : t < 0.5f ? 0.85f : t < 0.62f ? 0.35f : 0f;
            a *= fx.early(now);
            if (a <= 0f) {
                return;
            }
            FxDraw.origin(out, fx);
            float top = 22f;
            int n = path(fx.seed, top, 0f, 0f, 0f, 14, 0);
            run(out, fx, n, 0, a, now);
            // Two branches from a third and from halfway down, shorter, angling away.
            for (int b = 0; b < 2; b++) {
                int from = b == 0 ? 4 : 7;
                int m = branch(fx.seed + 31 * (b + 1), from, n, 5, 14);
                run(out, fx, m, 14, a * 0.8f, now);
            }
        }

        /** The main bolt's corners into slots [0, steps]: from the top down to the ground at the middle. */
        private int path(int seed, float top, float x0, float y0, float z0, int steps, int at) {
            float sx = FxDraw.signed(seed, 1) * 2f, sz = FxDraw.signed(seed, 2) * 2f;
            for (int k = 0; k <= steps; k++) {
                float f = k / (float) steps;
                float y = top * (1f - f);
                float jx = k == 0 || k == steps ? 0f : FxDraw.signed(seed, 10 + k) * 0.45f;
                float jz = k == 0 || k == steps ? 0f : FxDraw.signed(seed, 40 + k) * 0.45f;
                bx[at + k] = Math.round(((1f - f) * sx + jx) * 16f) / 16f;
                by[at + k] = Math.round(y * 16f) / 16f;
                bz[at + k] = Math.round(((1f - f) * sz + jz) * 16f) / 16f;
            }
            return steps + 1;
        }

        private int branch(int seed, int from, int mainCount, int steps, int at) {
            float dx = FxDraw.signed(seed, 3) * 0.5f, dz = FxDraw.signed(seed, 4) * 0.5f;
            float x = bx[from], y = by[from], z = bz[from];
            for (int k = 0; k <= steps; k++) {
                bx[at + k] = Math.round(x * 16f) / 16f;
                by[at + k] = Math.round(y * 16f) / 16f;
                bz[at + k] = Math.round(z * 16f) / 16f;
                x += dx + FxDraw.signed(seed, 20 + k) * 0.2f;
                z += dz + FxDraw.signed(seed, 60 + k) * 0.2f;
                y -= 0.9f + FxDraw.hash(seed, 80 + k) * 0.5f;
            }
            return steps + 1;
        }

        /** Draws the corners as stepped runs: straight down, then across; the edges first, the white core over them. */
        private void run(FxOut out, Fx fx, int count, int at, float a, float now) {
            float core = 2f * T * fx.size, edge = 4f * T * fx.size, halo = 8f * T * fx.size;
            int white = Colors.withAlpha(0xFFFFFFFF, a);
            int accent = Colors.withAlpha(fx.tone(Ramp.MAIN, 0, now), a);
            int glow = Colors.withAlpha(fx.glowColour, (fx.glow == 2 ? 0.5f : 0.3f) * a);
            if (out.glowPass() && fx.glow == 0) {
                return;
            }
            for (int pass = out.glowPass() ? 0 : 1; pass < (out.glowPass() ? 1 : 3); pass++) {
                float w = pass == 0 ? halo : pass == 1 ? edge : core;
                int c = pass == 0 ? glow : pass == 1 ? accent : white;
                for (int k = 0; k + 1 < count; k++) {
                    float x0 = bx[at + k], y0 = by[at + k], z0 = bz[at + k];
                    float x1 = bx[at + k + 1], y1 = by[at + k + 1], z1 = bz[at + k + 1];
                    // Down at (x0, z0), then across at y1.
                    bar(out, x0, Math.min(y0, y1), z0, x0, Math.max(y0, y1), z0, w, c);
                    bar(out, Math.min(x0, x1), y1, z0, Math.max(x0, x1), y1, z0, w, c);
                    bar(out, x1, y1, Math.min(z0, z1), x1, y1, Math.max(z0, z1), w, c);
                }
            }
        }

        private void bar(FxOut out, float x0, float y0, float z0, float x1, float y1, float z1, float w, int c) {
            float h = w / 2f;
            out.use(FxSprites.atlas());
            FxDraw.box(out, x0 - h, y0 - h, z0 - h, x1 + h, y1 + h, z1 + h, c, c, c, c);
        }
    };

    /** Firework: a rocket climbs eight blocks, then bursts into square sparks in a pixel sphere that fall and blink out. */
    static final FxKind FIREWORK = new FxKind("kill_firework", 5) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            float t = fx.time(age);
            FxDraw.origin(out, fx);
            float h = 8f * fx.size;
            if (t < 0.6f) {
                float y = Ramp.snap(1f + (h - 1f) * easeOut(t / 0.6f), fx.snap);
                FxDraw.square(out, 0f, y, 0f, 2f * T, fx.tone(Ramp.CORE, 0, now));
                FxDraw.squareGlow(out, fx, 0f, y, 0f, 2f * T, 1f);
                for (int i = 1; i <= 6; i++) {
                    float ty = y - i * 2f * T;
                    if (ty > 1f) {
                        FxDraw.square(out, 0f, ty, 0f, T, Colors.withAlpha(fx.tone(i < 3 ? Ramp.MAIN : Ramp.SHADE, 0, now),
                                Ramp.steps(1f - i / 7f, 3, fx.snap)));
                    }
                }
                return;
            }
            float tb = t - 0.6f;
            int n = Math.round(80 * fx.amount);
            float speed = 5f * fx.size;
            float k = 2.2f;
            float spread = (1f - (float) Math.exp(-k * tb)) / k;
            for (int i = 0; i < n; i++) {
                float life = 1.0f + 0.6f * FxDraw.hash(fx.seed, i);
                if (tb > life) {
                    continue;
                }
                if (tb > life - 0.4f && (((int) (tb * 12f) + i) & 1) == 1) {
                    continue;
                }
                // A Fibonacci sphere: even directions.
                float yy = 1f - (i + 0.5f) / n * 2f;
                float rr = (float) Math.sqrt(Math.max(0f, 1f - yy * yy));
                double th = i * 2.39996323;
                float v = speed * (0.85f + 0.15f * FxDraw.hash(fx.seed, i + 200));
                float x = (float) Math.cos(th) * rr * v * spread, z = (float) Math.sin(th) * rr * v * spread;
                float y = h + yy * v * spread - 1.5f * tb * tb;
                x = Ramp.snap(x, fx.snap);
                y = Ramp.snap(y, fx.snap);
                z = Ramp.snap(z, fx.snap);
                int tone = i % 4;
                FxDraw.square(out, x, y, z, 2f * T * fx.size, fx.tone(tone, i, now));
                if (tone <= Ramp.MAIN) {
                    FxDraw.squareGlow(out, fx, x, y, z, 2f * T * fx.size, 0.8f);
                }
            }
        }
    };

    /** Shatter: the body breaks into its own skin's texels, blown sideways on a wind, fading over a second and a half. */
    static final FxKind SHATTER = new FxKind("kill_shatter", 4) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            if (out.glowPass() || fx.copy == null) {
                return;
            }
            float t = fx.time(age);
            FxDraw.origin(out, fx);
            float a = alpha(fx, 1f - t / 1.5f, now);
            double wind = Math.toRadians(fx.yaw + 90f + FxDraw.signed(fx.seed, 1) * 40f);
            float wx = (float) Math.cos(wind), wz = (float) Math.sin(wind);
            int rows = fx.copy.rowCount();
            int stride = Math.max(1, rows / 420);
            for (int r = 0; r < rows; r += stride) {
                int c = fx.copy.row(r, P);
                if ((c >>> 24) < 40) {
                    continue;
                }
                float hh = FxDraw.hash(fx.seed, r);
                float v = 0.8f + 1.4f * hh + P[1] * 0.4f;
                float x = P[0] * fx.size + wx * v * t + FxDraw.signed(fx.seed, r + 7000) * 0.4f * t;
                float y = P[1] * fx.size + (0.6f * FxDraw.hash(fx.seed, r + 3000)) * t - 0.75f * t * t;
                float z = P[2] * fx.size + wz * v * t + FxDraw.signed(fx.seed, r + 9000) * 0.4f * t;
                if (y < fx.c) {
                    y = fx.c;
                }
                FxDraw.square(out, Ramp.snap(x, fx.snap), Ramp.snap(y, fx.snap), Ramp.snap(z, fx.snap), 1.5f * T * fx.size,
                        Colors.withAlpha(c | 0xFF000000, a));
            }
        }
    };

    /** Black hole: a pixel ring with a black core opens; the copy spirals in, shrinking; it closes with a two-step flash. */
    static final FxKind BLACK_HOLE = new FxKind("kill_black_hole", 3) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            float t = fx.time(age);
            FxDraw.origin(out, fx);
            float h = fx.copy != null ? fx.copy.height : 1.8f;
            float cy = Ramp.snap(h * 0.55f * fx.size, fx.snap);
            float rMax = 1.1f * fx.size;
            float open = t < 0.3f ? Ramp.steps(t / 0.3f, 3, true) : t < 1.3f ? 1f : 1f - Ramp.steps((t - 1.3f) / 0.3f, 3, true);
            float r = rMax * open;
            float a = fx.early(now);
            out.right(RR);
            out.up(UU);
            if (r > T) {
                if (!out.glowPass()) {
                    // The black core, row by row of texels.
                    out.use(FxSprites.atlas());
                    float[] uv = FxSprites.SQUARE.uv[FxSprites.ALL];
                    int rows = Math.max(1, Math.round(r / T));
                    for (int k = -rows; k < rows; k++) {
                        float yy = (k + 0.5f) * T;
                        float w = (float) Math.sqrt(Math.max(0f, r * r - yy * yy));
                        w = Math.round(w / T) * T;
                        if (w <= 0f) {
                            continue;
                        }
                        FxDraw.billboard(out, UU[0] * yy, cy + UU[1] * yy, UU[2] * yy, w, T / 2f, uv, Colors.withAlpha(0xFF07040C, a));
                    }
                }
                // The rim: texels round the edge, the colour with its core tone inside.
                int cells = Math.max(12, (int) (2 * Math.PI * r / T));
                for (int i = 0; i < cells; i++) {
                    double ang = i * 2 * Math.PI / cells + t * 0.8;
                    float cx = (float) Math.cos(ang), sy = (float) Math.sin(ang);
                    for (int ring = 0; ring < 2; ring++) {
                        float rr2 = r - ring * T;
                        float px = Math.round(cx * rr2 / T) * T, py = Math.round(sy * rr2 / T) * T;
                        float x = RR[0] * px + UU[0] * py, y = cy + RR[1] * px + UU[1] * py, z = RR[2] * px + UU[2] * py;
                        if (out.glowPass()) {
                            FxDraw.squareGlow(out, fx, x, y, z, T, a);
                        } else {
                            FxDraw.square(out, x, y, z, T, Colors.withAlpha(fx.tone(ring == 0 ? Ramp.MAIN : Ramp.CORE, i, now), a));
                        }
                    }
                }
            }
            // The copy spirals in, turning and shrinking.
            if (!out.glowPass() && fx.copy != null && t > 0.15f && t < 1.35f) {
                float k = Math.min(1f, (t - 0.15f) / 1.1f);
                float s = fx.size * (1f - k) * 0.95f;
                if (s > 0.02f) {
                    double ang = k * Math.PI * 3;
                    float orbit = (1f - k) * 0.9f * fx.size;
                    float px = (float) Math.cos(ang) * orbit, pz = (float) Math.sin(ang) * orbit;
                    fx.copy.draw(out, px, cy - h * s * 0.5f, pz, s, (float) ang, fx.copy.tinted(fx.tone(Ramp.CORE, 0, now), 0.3f),
                            Colors.withAlpha(0xFFFFFFFF, a), PoseSnapshot.WHOLE, 0f);
                }
            }
            // The flash as it closes, two steps of a dithered glow.
            if (out.glowPass() && t > 1.42f && t < 1.58f) {
                float fa = t < 1.5f ? 0.9f : 0.45f;
                out.use(FxSprites.atlas());
                FxDraw.billboard(out, 0f, cy, 0f, rMax * 1.6f, rMax * 1.6f, FxSprites.DITHER.uv[FxSprites.ALL],
                        Colors.withAlpha(fx.tone(Ramp.CORE, 0, now), fa * a));
            }
        }
    };

    /** Sky laser: a beam of texel columns slams down from the sky, holds, and leaves a scorch ring of texels. */
    static final FxKind SKY_LASER = new FxKind("kill_sky_laser", 3) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            float t = fx.time(age);
            FxDraw.origin(out, fx);
            float top = 40f;
            float ground = fx.c;
            float bottom = t < 0.15f ? ground + (top - ground) * (1f - t / 0.15f) : ground;
            float beamA = t < 0.55f ? 1f : t < 0.65f ? 0.6f : t < 0.75f ? 0.3f : 0f;
            float a = fx.early(now);
            out.use(FxSprites.atlas());
            float[] sq = FxSprites.SQUARE.uv[FxSprites.ALL];
            if (beamA > 0f) {
                int n = Math.max(4, Math.round(0.6f * 16f * fx.size));
                if (out.glowPass()) {
                    if (fx.glow > 0) {
                        // The big glow: a Bayer-dithered halo along the beam, and a disc where it lands.
                        float k = fx.glow == 2 ? 1f : 0.65f;
                        FxDraw.column(out, 0f, bottom, top, 0f, 0.9f * fx.size, 0f, FxSprites.DITHER.uv[FxSprites.ALL],
                                Colors.withAlpha(fx.glowColour, beamA * a * k));
                        FxDraw.flat(out, 0f, ground + 0.02f, 0f, 1.3f * fx.size, 1.3f * fx.size, FxSprites.DITHER.uv[FxSprites.ALL],
                                Colors.withAlpha(fx.tone(Ramp.MAIN, 0, now), beamA * a * k));
                    }
                } else {
                    for (int j = 0; j < n; j++) {
                        float off = (j - n / 2f + 0.5f) * T;
                        float edge = Math.abs(j - (n - 1) / 2f) / (n / 2f);
                        int c = edge < 0.45f ? 0xFFFFFFFF : edge < 0.8f ? fx.tone(Ramp.CORE, j, now) : fx.tone(Ramp.MAIN, j, now);
                        FxDraw.column(out, 0f, bottom, top, 0f, T / 2f, off, sq, Colors.withAlpha(c, beamA * a));
                    }
                }
            }
            // The scorch ring, once it has landed, fading in three steps.
            if (t >= 0.15f && !out.glowPass()) {
                float sa = alpha(fx, 1f - Math.max(0f, t - 0.75f) / 0.75f, now);
                FxDraw.ring(out, fx, fx.x, fx.y, fx.z, 0f, ground, 0f, 0.8f * fx.size, 3, Ramp.DEEP, sa, true, now);
                FxDraw.ring(out, fx, fx.x, fx.y, fx.z, 0f, ground, 0f, 0.55f * fx.size, 1, Ramp.SHADE, sa * 0.8f, true, now);
                int n = Math.round(14 * fx.amount);
                for (int i = 0; i < n; i++) {
                    double ang = FxDraw.hash(fx.seed, i) * Math.PI * 2;
                    float rr = (0.2f + 0.5f * FxDraw.hash(fx.seed, i + 40)) * fx.size;
                    float x = Math.round((float) Math.cos(ang) * rr / T) * T, z = Math.round((float) Math.sin(ang) * rr / T) * T;
                    FxDraw.cell(out, x, ground, z, T, Colors.withAlpha(0xFF14100C, sa * 0.85f));
                }
            }
        }
    };

    /** Gravestone: Sol's own stone rises with the name on its face, stays four seconds and sinks. */
    static final FxKind GRAVESTONE = new FxKind("kill_gravestone", 4) {
        private final float[] r = new float[3], u = {0f, 1f, 0f};

        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            if (out.glowPass()) {
                return;
            }
            float t = fx.time(age);
            FxDraw.origin(out, fx);
            float s = fx.size * 1.3f;
            float off = t < 0.5f ? -1.05f * s * (1f - Ramp.steps(t / 0.5f, 6, fx.snap))
                    : t > 4.5f ? -1.05f * s * Ramp.steps((t - 4.5f) / 0.5f, 6, fx.snap) : 0f;
            float y = fx.c + off;
            // Its face toward where the body faced (the one who saw it fall), the nearest of four ways.
            double yr = Math.toRadians(fx.yaw);
            float nx = (float) -Math.sin(yr), nz = (float) Math.cos(yr);
            boolean alongX = Math.abs(nz) >= Math.abs(nx);
            float sign = alongX ? Math.signum(nz) : Math.signum(nx);
            if (sign == 0f) {
                sign = 1f;
            }
            float a = fx.early(now);
            int top = Colors.withAlpha(STONE[0], a), side = Colors.withAlpha(STONE[1], a), side2 = Colors.withAlpha(STONE[2], a),
                    bottom = Colors.withAlpha(STONE[3], a);
            // Base slab, the stone, its stepped top.
            box(out, alongX, -0.4f * s, y, -0.22f * s, 0.4f * s, y + 0.12f * s, 0.22f * s, top, side, side2, bottom);
            box(out, alongX, -0.3f * s, y + 0.12f * s, -0.08f * s, 0.3f * s, y + 0.86f * s, 0.08f * s, top, side, side2, bottom);
            box(out, alongX, -0.22f * s, y + 0.86f * s, -0.08f * s, 0.22f * s, y + 0.94f * s, 0.08f * s, top, side, side2, bottom);
            box(out, alongX, -0.12f * s, y + 0.94f * s, -0.08f * s, 0.12f * s, y + 1.0f * s, 0.08f * s, top, side, side2, bottom);
            // A carved cross above the name, in the stone's deep tone.
            int carve = Colors.withAlpha(STONE[3], a);
            float fz = 0.081f * s * sign, d = 0.004f * sign;
            float cx = T * s, cy = y + 0.66f * s;
            box(out, alongX, -cx, cy - 3.5f * T * s, Math.min(fz, fz + d), cx, cy + 3.5f * T * s, Math.max(fz, fz + d), carve, carve, carve, carve);
            box(out, alongX, -3f * T * s, cy + 0.5f * T * s, Math.min(fz, fz + d), 3f * T * s, cy + 2.5f * T * s, Math.max(fz, fz + d), carve, carve,
                    carve, carve);
            // The name on the face that looks toward you, in the colour, with a deep shadow.
            String name = fx.text == null ? "" : fx.text;
            int w = Math.max(1, FxText.width(name));
            float texel = Math.min(T * s * 0.75f, 0.5f * s / w);
            float tz = fz + d * 2f;
            int ink = Colors.withAlpha(fx.tone(Ramp.MAIN, 0, now), a), shadow = Colors.withAlpha(STONE[3], a);
            if (alongX) {
                r[0] = sign;
                r[1] = 0f;
                r[2] = 0f;
                FxText.drawOn(out, name, 0f, y + 0.4f * s, tz, r, u, texel, ink, shadow);
            } else {
                r[0] = 0f;
                r[1] = 0f;
                r[2] = -sign;
                FxText.drawOn(out, name, tz, y + 0.4f * s, 0f, r, u, texel, ink, shadow);
            }
        }

        private void box(FxOut out, boolean alongX, float x0, float y0, float z0, float x1, float y1, float z1, int top, int side,
                         int side2, int bottom) {
            if (alongX) {
                FxDraw.box(out, x0, y0, z0, x1, y1, z1, top, side, side2, bottom);
            } else {
                FxDraw.box(out, z0, y0, x0, z1, y1, x1, top, side2, side, bottom);
            }
        }
    };
    private static final int[] STONE = Ramp.of(0xFF9C9EA8);

    /** Confetti: papers in five of the preset's colours burst up and flutter down, flipping in two frames. */
    static final FxKind CONFETTI = new FxKind("kill_confetti", 4) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            if (out.glowPass()) {
                return;
            }
            float t = fx.time(age);
            FxDraw.origin(out, fx);
            int n = Math.round(80 * fx.amount);
            float k = 3f, vt = 0.7f;
            float e = (1f - (float) Math.exp(-k * t)) / k;
            float fade = alpha(fx, (2.5f - t) / 0.5f, now);
            float y0 = (fx.copy != null ? fx.copy.height : 1.8f) * 0.7f;
            for (int i = 0; i < n; i++) {
                double th = FxDraw.hash(fx.seed, i) * Math.PI * 2;
                float vh = (0.8f + 2.2f * FxDraw.hash(fx.seed, i + 100)) * fx.size;
                float vy = (4f + 3f * FxDraw.hash(fx.seed, i + 200)) * fx.size;
                float x = (float) Math.cos(th) * vh * e + (float) Math.sin(t * 5f + i) * 0.12f;
                float z = (float) Math.sin(th) * vh * e + (float) Math.cos(t * 4f + i) * 0.12f;
                float y = y0 - vt * t + (vy + vt) * e;
                boolean landed = y <= fx.c + T;
                if (landed) {
                    y = fx.c + T;
                }
                int frame = landed ? 0 : (((int) (t * 8f + i)) & 1);
                int c = fx.palette[i % 5];
                if (frame == 1) {
                    c = Colors.mixRgb(c, 0xFF000000, 0.35f);
                }
                FxDraw.spriteFlat(out, FxSprites.CONFETTI[frame], Ramp.snap(x, fx.snap), Ramp.snap(y, fx.snap), Ramp.snap(z, fx.snap),
                        T * fx.size, Colors.withAlpha(c, fade));
            }
        }
    };

    /** Burning copy: an orange copy stands while three-frame flames climb it, dissolving from the feet up. */
    static final FxKind BURNING_COPY = new FxKind("kill_burning_copy", 4) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            float t = fx.time(age);
            FxDraw.origin(out, fx);
            float h = (fx.copy != null ? fx.copy.height : 1.8f) * fx.size;
            float cut = t < 0.15f ? 0f : Ramp.steps(Math.min(1f, (t - 0.15f) / 1.5f), 6, true);
            float a = fx.early(now);
            if (!out.glowPass() && fx.copy != null && cut < 1f) {
                fx.copy.draw(out, 0f, 0f, 0f, fx.size, 0f, fx.copy.tinted(fx.tone(Ramp.MAIN, 0, now), 0.65f), Colors.withAlpha(0xFFFFFFFF, a),
                        PoseSnapshot.FROM_FEET, cut);
            }
            int n = Math.round(14 * fx.amount);
            for (int i = 0; i < n; i++) {
                double ang = i * 2 * Math.PI / n + FxDraw.hash(fx.seed, i);
                float rr = 0.3f * fx.size;
                float y = cut * h + FxDraw.hash(fx.seed, i + 30) * 0.5f * h * (1f - cut);
                if (y > h + 0.2f || t > 1.75f) {
                    continue;
                }
                float x = Ramp.snap((float) Math.cos(ang) * rr, fx.snap), z = Ramp.snap((float) Math.sin(ang) * rr, fx.snap);
                int frame = (((int) (t * 12f)) + i) % 3;
                FxDraw.sprite(out, fx, FxSprites.FLAME[frame], x, Ramp.snap(y + 0.25f * fx.size, fx.snap), z, T * fx.size, i, a, now);
            }
        }
    };

    /** Burst: squares spray from the chest and land as texel splats that fade over three seconds. */
    static final FxKind BURST = new FxKind("kill_burst", 5) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            float t = fx.time(age);
            FxDraw.origin(out, fx);
            int n = Math.max(40, Math.min(150, Math.round(80 * fx.amount)));
            float y0 = (fx.copy != null ? fx.copy.height : 1.8f) * 0.6f * fx.size;
            float g = 14f;
            float splat = alpha(fx, (3f - t) / 1.2f, now);
            for (int i = 0; i < n; i++) {
                double th = FxDraw.hash(fx.seed, i) * Math.PI * 2;
                double up = Math.toRadians(20 + 60 * FxDraw.hash(fx.seed, i + 300));
                float v = (2.5f + 2.5f * FxDraw.hash(fx.seed, i + 600)) * fx.size;
                float vh = v * (float) Math.cos(up), vy = v * (float) Math.sin(up);
                float drop = y0 - fx.c;
                float land = (vy + (float) Math.sqrt(vy * vy + 2f * g * Math.max(0f, drop))) / g;
                float tt = Math.min(t, land);
                float x = (float) Math.cos(th) * vh * tt, z = (float) Math.sin(th) * vh * tt;
                if (t < land) {
                    float y = y0 + vy * t - 0.5f * g * t * t;
                    int tone = i % 3;
                    FxDraw.square(out, Ramp.snap(x, fx.snap), Ramp.snap(y, fx.snap), Ramp.snap(z, fx.snap), T * fx.size, fx.tone(tone, i, now));
                    if (tone == Ramp.CORE) {
                        FxDraw.squareGlow(out, fx, x, y, z, T * fx.size, 0.7f);
                    }
                } else if (!out.glowPass()) {
                    out.use(FxSprites.atlas());
                    int tone = i % 3 == 0 ? Ramp.MAIN : i % 3 == 1 ? Ramp.SHADE : Ramp.DEEP;
                    FxDraw.cell(out, Math.round(x / T) * T, fx.c, Math.round(z / T) * T, 2f * T * fx.size,
                            Colors.withAlpha(fx.tone(tone, i, now), splat));
                }
            }
        }
    };

    /** Shockwave: two or three pixel rings race out over the terrain, sparkles spiralling up the middle. */
    static final FxKind SHOCKWAVE = new FxKind("kill_shockwave", 4) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            float t = fx.time(age);
            FxDraw.origin(out, fx);
            int rings = fx.amount >= 1f ? 3 : 2;
            for (int k = 0; k < rings; k++) {
                float p = (t - 0.15f * k) / 1.0f;
                if (p <= 0f || p >= 1f) {
                    continue;
                }
                float r = 6f * fx.size * easeOut(p);
                int tone = k == 0 ? Ramp.MAIN : k == 1 ? Ramp.SHADE : Ramp.CORE;
                // Full until it's spread 60% of the way, then out in steps: its own colour shows at its key moment.
                FxDraw.ring(out, fx, fx.x, fx.y, fx.z, 0f, fx.c, 0f, r, 2, tone, alpha(fx, (1f - p) / 0.4f, now), true, now);
            }
            int n = Math.round(12 * fx.amount);
            for (int i = 0; i < n; i++) {
                double ang = i * 2 * Math.PI / n + t * 4;
                float rr = 0.45f * fx.size * Math.max(0f, 1f - t / 1.4f);
                float y = fx.c + (t / 1.4f) * 2.5f * fx.size + i * 0.08f;
                float x = Ramp.snap((float) Math.cos(ang) * rr, fx.snap), z = Ramp.snap((float) Math.sin(ang) * rr, fx.snap);
                FxDraw.sprite(out, fx, FxSprites.PLUS, x, Ramp.snap(y, fx.snap), z, T * fx.size, i, alpha(fx, 1f - t / 1.4f, now), now);
            }
        }
    };

    static final FxKind[] KINDS = {SOUL_RISE, LIGHTNING, FIREWORK, SHATTER, BLACK_HOLE, SKY_LASER, GRAVESTONE, CONFETTI, BURNING_COPY,
            BURST, SHOCKWAVE};

    /** Every kind, for the checks. */
    public static FxKind kind(String style) {
        int i = STYLES.indexOf(style);
        return i < 0 ? null : KINDS[i];
    }
}
