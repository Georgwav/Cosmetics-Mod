package io.github.georgwav.cosmetics.sol.atmosphere;

import io.github.georgwav.cosmetics.sol.fx.FxColour;
import io.github.georgwav.cosmetics.sol.fx.FxDraw;
import io.github.georgwav.cosmetics.sol.fx.FxSprites;
import io.github.georgwav.cosmetics.sol.fx.FxWorld;
import io.github.georgwav.cosmetics.sol.fx.Ramp;
import io.github.georgwav.cosmetics.sol.fx.WorldOut;
import io.github.georgwav.cosmetics.sol.render.FrameInfo;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.sol.util.Mulberry32;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Glowing cubes (Atmosphere › More ambience): up to 40 spinning pixel cubes of 3 to 5 texels drift in the air around
 * you, fall slowly and bounce off blocks, in the preset's colours (the core tone on top, main and shade on the sides)
 * with a soft stepped glow. A layer of the world's effects: moved twenty times a second, drawn between ticks.
 */
public final class GlowCubes implements FxWorld.Layer {
    public static final GlowCubes INSTANCE = new GlowCubes();
    public static final int MAX = 40;
    private static final float T = FxDraw.TEXEL;

    private final double[] x = new double[MAX], y = new double[MAX], z = new double[MAX];
    private final double[] ox = new double[MAX], oy = new double[MAX], oz = new double[MAX];
    private final float[] vx = new float[MAX], vy = new float[MAX], vz = new float[MAX];
    private final float[] spin = new float[MAX], spinWas = new float[MAX], spinSpeed = new float[MAX], tilt = new float[MAX];
    private final float[] size = new float[MAX];
    private final int[] age = new int[MAX], life = new int[MAX];
    private final boolean[] live = new boolean[MAX];
    private int count, ticks;
    private final Mulberry32 random = new Mulberry32(1307);
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    private GlowCubes() {
    }

    /** How many are about now. */
    public int count() {
        return count;
    }

    public void clear() {
        java.util.Arrays.fill(live, false);
        count = 0;
    }

    /** Every client tick while Atmosphere is on: {@code on} whether more ambience and its cubes are. */
    public void tick(Minecraft mc, boolean on) {
        ClientLevel level = mc.level;
        Player you = mc.player;
        if (!on || level == null || you == null) {
            if (count > 0) {
                clear();
            }
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        ticks++;
        // A new one every other tick until there are forty.
        if (count < MAX && (ticks & 1) == 0) {
            spawn(level, you);
        }
        for (int i = 0; i < MAX; i++) {
            if (!live[i]) {
                continue;
            }
            ox[i] = x[i];
            oy[i] = y[i];
            oz[i] = z[i];
            spinWas[i] = spin[i];
            spin[i] += spinSpeed[i];
            // A slow fall, a wandering drift, the air holding them back.
            vy[i] = Math.max(-0.035f, vy[i] - 0.0025f);
            vx[i] = (vx[i] + (random.nextFloat() - 0.5f) * 0.006f) * 0.985f;
            vz[i] = (vz[i] + (random.nextFloat() - 0.5f) * 0.006f) * 0.985f;
            // Each way on its own, bouncing back off what it would go into.
            float r = size[i] / 2f;
            if (solid(level, x[i] + vx[i] + Math.signum(vx[i]) * r, y[i], z[i])) {
                vx[i] = -vx[i] * 0.6f;
            } else {
                x[i] += vx[i];
            }
            if (solid(level, x[i], y[i] + vy[i] + Math.signum(vy[i]) * r, z[i])) {
                // Off the ground they hop back up a little, slower each time.
                vy[i] = vy[i] < 0f ? Math.min(0.08f, -vy[i] * 0.6f + 0.03f) : -vy[i] * 0.5f;
            } else {
                y[i] += vy[i];
            }
            if (solid(level, x[i], y[i], z[i] + vz[i] + Math.signum(vz[i]) * r)) {
                vz[i] = -vz[i] * 0.6f;
            } else {
                z[i] += vz[i];
            }
            double dx = x[i] - you.getX(), dz = z[i] - you.getZ();
            if (++age[i] > life[i] || dx * dx + dz * dz > 24 * 24 || solid(level, x[i], y[i], z[i])) {
                live[i] = false;
                count--;
            }
        }
    }

    private void spawn(ClientLevel level, Player you) {
        for (int tries = 0; tries < 4; tries++) {
            double sx = you.getX() + (random.nextFloat() - 0.5f) * 20f, sz = you.getZ() + (random.nextFloat() - 0.5f) * 20f;
            double sy = you.getY() + 1.0 + random.nextFloat() * 5f;
            if (solid(level, sx, sy, sz)) {
                continue;
            }
            for (int i = 0; i < MAX; i++) {
                if (!live[i]) {
                    live[i] = true;
                    count++;
                    x[i] = ox[i] = sx;
                    y[i] = oy[i] = sy;
                    z[i] = oz[i] = sz;
                    vx[i] = (random.nextFloat() - 0.5f) * 0.04f;
                    vy[i] = random.nextFloat() * 0.02f;
                    vz[i] = (random.nextFloat() - 0.5f) * 0.04f;
                    spin[i] = spinWas[i] = random.nextFloat() * 6.283f;
                    spinSpeed[i] = (random.nextFloat() < 0.5f ? -1f : 1f) * (0.06f + random.nextFloat() * 0.08f);
                    tilt[i] = 0.3f + random.nextFloat() * 0.5f;
                    size[i] = T * (3 + random.nextInt(3));
                    age[i] = 0;
                    life[i] = 400 + random.nextInt(400);
                    return;
                }
            }
            return;
        }
    }

    private boolean solid(ClientLevel level, double px, double py, double pz) {
        pos.set(Math.floor(px), Math.floor(py), Math.floor(pz));
        BlockState state = level.getBlockState(pos);
        return !state.isAir() && !state.getCollisionShape(level, pos).isEmpty();
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public boolean active() {
        return count > 0;
    }

    private static final int[] RAMP = new int[Ramp.TONES];
    private static final float[] CX = new float[8], CY = new float[8], CZ = new float[8], EYE = new float[3];
    /** The cube's faces (corner numbers, outward winding) and each one's outward axis (0..5: -x +x -y +y -z +z). */
    private static final int[][] FACES = {{0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}};

    @Override
    public void draw(WorldOut out, float now) {
        Ramp.of(FxColour.accent(), RAMP);
        int glow = FxColour.glow();
        float pt = FrameInfo.partialTick;
        boolean glowPass = out.glowPass();
        out.use(FxSprites.atlas());
        float[] uv = FxSprites.SQUARE.uv[FxSprites.ALL];
        for (int i = 0; i < MAX; i++) {
            if (!live[i]) {
                continue;
            }
            // In over half a second and out over a second, in steps.
            float in = Math.min(1f, (age[i] + pt) / 10f), left = Math.min(1f, (life[i] - age[i]) / 20f);
            float a = Ramp.steps(Math.min(in, left), 4, true);
            if (a <= 0f) {
                continue;
            }
            out.origin(Ramp.snap(ox[i] + (x[i] - ox[i]) * pt, true), Ramp.snap(oy[i] + (y[i] - oy[i]) * pt, true),
                    Ramp.snap(oz[i] + (z[i] - oz[i]) * pt, true));
            float h = size[i] / 2f;
            if (glowPass) {
                FxDraw.billboard(out, 0f, 0f, 0f, h * 1.6f, h * 1.6f, uv, Colors.withAlpha(glow, 0.3f * a));
                FxDraw.billboard(out, 0f, 0f, 0f, h * 2.4f, h * 2.4f, uv, Colors.withAlpha(glow, 0.12f * a));
                continue;
            }
            cube(out, h, spinWas[i] + (spin[i] - spinWas[i]) * pt, tilt[i], uv, a);
        }
    }

    /** A cube of half size {@code h}, turned {@code yaw} round the vertical and tipped {@code tilt}, its faces toward the camera. */
    private static void cube(WorldOut out, float h, float yaw, float tilt, float[] uv, float a) {
        float cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw), ct = (float) Math.cos(tilt), st = (float) Math.sin(tilt);
        for (int k = 0; k < 8; k++) {
            float px = (k & 4) == 0 ? -h : h, py = (k & 2) == 0 ? -h : h, pz = (k & 1) == 0 ? -h : h;
            // Tipped round x, then turned round y.
            float ty = py * ct - pz * st, tz = py * st + pz * ct;
            CX[k] = px * cy + tz * sy;
            CY[k] = ty;
            CZ[k] = -px * sy + tz * cy;
        }
        out.eye(EYE);
        float u = (uv[0] + uv[2]) / 2f, v = (uv[1] + uv[3]) / 2f;
        for (int[] f : FACES) {
            float mx = (CX[f[0]] + CX[f[2]]) / 2f, my = (CY[f[0]] + CY[f[2]]) / 2f, mz = (CZ[f[0]] + CZ[f[2]]) / 2f;
            if (mx * (EYE[0] - mx) + my * (EYE[1] - my) + mz * (EYE[2] - mz) <= 0f) {
                continue;
            }
            // Lit from above: the faces turned up the core tone, down the shade, the sides main or shade.
            float ny = my / h;
            int tone = ny > 0.5f ? Ramp.CORE : ny < -0.5f ? Ramp.SHADE : mx * 0.6f + mz * 0.8f > 0f ? Ramp.MAIN : Ramp.SHADE;
            int argb = Colors.withAlpha(RAMP[tone], a);
            for (int k = 0; k < 4; k++) {
                out.vertex(CX[f[k]], CY[f[k]], CZ[f[k]], u, v, argb);
            }
        }
    }
}
