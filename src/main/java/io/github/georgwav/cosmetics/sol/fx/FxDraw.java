package io.github.georgwav.cosmetics.sol.fx;

import io.github.georgwav.cosmetics.sol.util.Colors;

/**
 * The Sol FX style's drawing pieces (C.1), shared by every effect: texel squares and sprites facing the camera, flat
 * squares lying on the ground, rings of texels that follow the terrain, boxes, and the stepped glow behind a sprite
 * (two rings, 35% alpha at 1.5 times its size, then 15% at 2.2 times). Sizes are in blocks; one texel is 1/16 block.
 * Each piece draws only in the pass it belongs to: glows in the glow pass, bodies in the body pass.
 */
public final class FxDraw {
    public static final float TEXEL = 1f / 16f;
    private static final float[] R = new float[3], U = new float[3];
    private static FxOut cameraOf;

    private FxDraw() {
    }

    /** Reads the camera's right and up for the quads that face it; once per pass. */
    public static void camera(FxOut out) {
        out.right(R);
        out.up(U);
        cameraOf = out;
    }

    /** Sets the origin to the effect's place, on the texel grid when it snaps. */
    public static void origin(FxOut out, Fx fx) {
        out.origin(Ramp.snap(fx.x, fx.snap), Ramp.snap(fx.y, fx.snap), Ramp.snap(fx.z, fx.snap));
        if (cameraOf != out) {
            camera(out);
        }
    }

    /** A camera-facing quad centred at (x, y, z), half sizes {@code hw} and {@code hh}, from an atlas rectangle. */
    public static void billboard(FxOut out, float x, float y, float z, float hw, float hh, float[] uv, int argb) {
        float rx = R[0] * hw, ry = R[1] * hw, rz = R[2] * hw;
        float ux = U[0] * hh, uy = U[1] * hh, uz = U[2] * hh;
        out.vertex(x - rx - ux, y - ry - uy, z - rz - uz, uv[0], uv[3], argb);
        out.vertex(x - rx + ux, y - ry + uy, z - rz + uz, uv[0], uv[1], argb);
        out.vertex(x + rx + ux, y + ry + uy, z + rz + uz, uv[2], uv[1], argb);
        out.vertex(x + rx - ux, y + ry - uy, z + rz - uz, uv[2], uv[3], argb);
    }

    /** A camera-facing square of texels, {@code side} blocks wide (body pass). */
    public static void square(FxOut out, float x, float y, float z, float side, int argb) {
        if (out.glowPass() || Colors.a(argb) == 0) {
            return;
        }
        out.use(FxSprites.atlas());
        float h = side / 2f;
        billboard(out, x, y, z, h, h, FxSprites.SQUARE.uv[FxSprites.ALL], argb);
    }

    /** A camera-facing glowing square: a stepped halo in the glow pass around a square of {@code side}. */
    public static void squareGlow(FxOut out, Fx fx, float x, float y, float z, float side, float alpha) {
        if (!out.glowPass() || fx.glow == 0 || alpha <= 0f) {
            return;
        }
        out.use(FxSprites.atlas());
        float[] uv = FxSprites.SQUARE.uv[FxSprites.ALL];
        float k = fx.glow == 2 ? 1.7f : 1f;
        float h = side / 2f;
        billboard(out, x, y, z, h * 1.5f, h * 1.5f, uv, Colors.withAlpha(fx.glowColour, Math.min(1f, 0.35f * k * alpha)));
        billboard(out, x, y, z, h * 2.2f, h * 2.2f, uv, Colors.withAlpha(fx.glowColour, Math.min(1f, 0.15f * k * alpha)));
    }

    /**
     * A sprite facing the camera, one sprite pixel {@code texel} blocks wide, coloured from the effect's ramp (part
     * {@code i} picks a colour when it has two), at {@code alpha}; its stepped glow in the glow pass.
     */
    public static void sprite(FxOut out, Fx fx, FxSprites.Sprite s, float x, float y, float z, float texel, int i, float alpha,
                              float now) {
        if (alpha <= 0f) {
            return;
        }
        out.use(FxSprites.atlas());
        float hw = s.w * texel / 2f, hh = s.h * texel / 2f;
        if (out.glowPass()) {
            if (fx.glow == 0) {
                return;
            }
            float k = fx.glow == 2 ? 1.7f : 1f;
            float[] uv = s.uv[FxSprites.ALL];
            int g = fx.rainbow ? fx.tone(Ramp.CORE, i, now) : fx.glowColour;
            billboard(out, x, y, z, hw * 1.5f, hh * 1.5f, uv, Colors.withAlpha(g, Math.min(1f, 0.35f * k * alpha)));
            billboard(out, x, y, z, hw * 2.2f, hh * 2.2f, uv, Colors.withAlpha(g, Math.min(1f, 0.15f * k * alpha)));
            return;
        }
        for (int t = 0; t < Ramp.TONES; t++) {
            float[] uv = s.uv[t];
            if (uv != null) {
                billboard(out, x, y, z, hw, hh, uv, Colors.withAlpha(fx.tone(t, i, now), alpha));
            }
        }
    }

    /**
     * {@link #sprite}, stretched {@code sx} across and {@code sy} up (a negative {@code sx} mirrors it): a wing's flap.
     */
    public static void spriteScaled(FxOut out, Fx fx, FxSprites.Sprite s, float x, float y, float z, float texel, float sx, float sy,
                                    int i, float alpha, float now) {
        if (alpha <= 0f || sx == 0f || sy == 0f) {
            return;
        }
        out.use(FxSprites.atlas());
        float hw = s.w * texel * sx / 2f, hh = s.h * texel * sy / 2f;
        if (out.glowPass()) {
            if (fx.glow == 0) {
                return;
            }
            float k = fx.glow == 2 ? 1.7f : 1f;
            int g = fx.rainbow ? fx.tone(Ramp.CORE, i, now) : fx.glowColour;
            billboard(out, x, y, z, hw * 1.5f, hh * 1.5f, s.uv[FxSprites.ALL], Colors.withAlpha(g, Math.min(1f, 0.35f * k * alpha)));
            return;
        }
        for (int t = 0; t < Ramp.TONES; t++) {
            float[] uv = s.uv[t];
            if (uv != null) {
                billboard(out, x, y, z, hw, hh, uv, Colors.withAlpha(fx.tone(t, i, now), alpha));
            }
        }
    }

    /** A sprite in one colour (its every pixel), facing the camera; body pass. */
    public static void spriteFlat(FxOut out, FxSprites.Sprite s, float x, float y, float z, float texel, int argb) {
        if (out.glowPass() || Colors.a(argb) == 0) {
            return;
        }
        out.use(FxSprites.atlas());
        billboard(out, x, y, z, s.w * texel / 2f, s.h * texel / 2f, s.uv[FxSprites.ALL], argb);
    }

    /**
     * An upright strip facing the camera round the vertical (a beam's column): centred on (x, z) across, from y0 up to
     * y1, {@code hw} half wide, an atlas rectangle stretched over it. {@code offset} moves it sideways (blocks).
     */
    public static void column(FxOut out, float x, float y0, float y1, float z, float hw, float offset, float[] uv, int argb) {
        // The camera's right, flattened.
        float rx = R[0], rz = R[2];
        float l = (float) Math.sqrt(rx * rx + rz * rz);
        if (l < 1e-4f) {
            rx = 1f;
            rz = 0f;
            l = 1f;
        }
        rx /= l;
        rz /= l;
        float cx = x + rx * offset, cz = z + rz * offset;
        out.vertex(cx - rx * hw, y0, cz - rz * hw, uv[0], uv[3], argb);
        out.vertex(cx - rx * hw, y1, cz - rz * hw, uv[0], uv[1], argb);
        out.vertex(cx + rx * hw, y1, cz + rz * hw, uv[2], uv[1], argb);
        out.vertex(cx + rx * hw, y0, cz + rz * hw, uv[2], uv[3], argb);
    }

    /** A horizontal quad (lying on the ground) centred at (x, y, z), half sizes along x and z. */
    public static void flat(FxOut out, float x, float y, float z, float hx, float hz, float[] uv, int argb) {
        out.vertex(x - hx, y, z - hz, uv[0], uv[1], argb);
        out.vertex(x - hx, y, z + hz, uv[0], uv[3], argb);
        out.vertex(x + hx, y, z + hz, uv[2], uv[3], argb);
        out.vertex(x + hx, y, z - hz, uv[2], uv[1], argb);
    }

    /** A flat texel cell {@code side} blocks wide, a little over the ground so it never fights it. */
    public static void cell(FxOut out, float x, float y, float z, float side, int argb) {
        out.use(FxSprites.atlas());
        float h = side / 2f;
        flat(out, x, y + 0.004f, z, h, h, FxSprites.SQUARE.uv[FxSprites.ALL], argb);
    }

    /**
     * A ring of texel cells round (ox + cx, oz + cz) lying on the ground (heights sampled under each cell when
     * {@code terrain}, else at {@code cy}), {@code radius} blocks out and {@code width} texels wide. The colour is tone
     * {@code tone} of the effect's ramp; in the glow pass a wider faint band. {@code wx, wy, wz} is the origin's world
     * position, for the ground samples.
     */
    public static void ring(FxOut out, Fx fx, double wx, double wy, double wz, float cx, float cy, float cz, float radius,
                            int width, int tone, float alpha, boolean terrain, float now) {
        if (alpha <= 0f || radius <= 0f) {
            return;
        }
        out.use(FxSprites.atlas());
        boolean glow = out.glowPass();
        if (glow && fx.glow == 0) {
            return;
        }
        float cellSize = TEXEL * (glow ? 2f : 1f);
        int steps = Math.max(8, (int) Math.ceil(2.0 * Math.PI * radius / TEXEL));
        int lastX = Integer.MIN_VALUE, lastZ = Integer.MIN_VALUE;
        float[] uv = FxSprites.SQUARE.uv[FxSprites.ALL];
        for (int w = 0; w < width; w++) {
            float r = radius - w * TEXEL;
            if (r <= 0f) {
                break;
            }
            for (int s = 0; s < steps; s++) {
                double ang = s * 2.0 * Math.PI / steps;
                float px = cx + (float) Math.cos(ang) * r, pz = cz + (float) Math.sin(ang) * r;
                int gx = Math.round(px / TEXEL), gz = Math.round(pz / TEXEL);
                if (gx == lastX && gz == lastZ) {
                    continue;
                }
                lastX = gx;
                lastZ = gz;
                float sx = gx * TEXEL, sz = gz * TEXEL;
                float sy = terrain ? out.ground(wx + sx, wz + sz, wy + cy + 0.6) - (float) wy : cy;
                int c = fx.tone(tone, s, now);
                if (glow) {
                    float k = fx.glow == 2 ? 1.7f : 1f;
                    flat(out, sx, sy + 0.01f, sz, cellSize, cellSize, uv, Colors.withAlpha(fx.glowColour, Math.min(1f, 0.22f * k * alpha)));
                } else {
                    flat(out, sx, sy + 0.006f, sz, cellSize / 2f, cellSize / 2f, uv, Colors.withAlpha(c, alpha));
                }
            }
        }
    }

    /** An axis-aligned box from (x0, y0, z0) to (x1, y1, z1), one colour per face direction: top, sides, bottom. */
    public static void box(FxOut out, float x0, float y0, float z0, float x1, float y1, float z1, int top, int side, int side2,
                           int bottom) {
        out.use(FxSprites.atlas());
        float[] uv = FxSprites.SQUARE.uv[FxSprites.ALL];
        float u = (uv[0] + uv[2]) / 2f, v = (uv[1] + uv[3]) / 2f;
        // Top and bottom.
        out.vertex(x0, y1, z0, u, v, top);
        out.vertex(x0, y1, z1, u, v, top);
        out.vertex(x1, y1, z1, u, v, top);
        out.vertex(x1, y1, z0, u, v, top);
        out.vertex(x0, y0, z0, u, v, bottom);
        out.vertex(x1, y0, z0, u, v, bottom);
        out.vertex(x1, y0, z1, u, v, bottom);
        out.vertex(x0, y0, z1, u, v, bottom);
        // North and south.
        out.vertex(x0, y0, z0, u, v, side);
        out.vertex(x0, y1, z0, u, v, side);
        out.vertex(x1, y1, z0, u, v, side);
        out.vertex(x1, y0, z0, u, v, side);
        out.vertex(x0, y0, z1, u, v, side);
        out.vertex(x1, y0, z1, u, v, side);
        out.vertex(x1, y1, z1, u, v, side);
        out.vertex(x0, y1, z1, u, v, side);
        // West and east.
        out.vertex(x0, y0, z0, u, v, side2);
        out.vertex(x0, y0, z1, u, v, side2);
        out.vertex(x0, y1, z1, u, v, side2);
        out.vertex(x0, y1, z0, u, v, side2);
        out.vertex(x1, y0, z0, u, v, side2);
        out.vertex(x1, y1, z0, u, v, side2);
        out.vertex(x1, y1, z1, u, v, side2);
        out.vertex(x1, y0, z1, u, v, side2);
    }

    /** A cube of {@code side} blocks centred at (x, y, z), shaded from the ramp: core on top, main and shade sides. */
    public static void cube(FxOut out, float x, float y, float z, float side, int[] ramp, float alpha) {
        if (out.glowPass()) {
            return;
        }
        float h = side / 2f;
        box(out, x - h, y - h, z - h, x + h, y + h, z + h, Colors.withAlpha(ramp[Ramp.CORE], alpha),
                Colors.withAlpha(ramp[Ramp.MAIN], alpha), Colors.withAlpha(ramp[Ramp.SHADE], alpha),
                Colors.withAlpha(ramp[Ramp.DEEP], alpha));
    }

    /** A small hash in [0, 1) from an effect's seed and a part number: the effects' only randomness. */
    public static float hash(int seed, int i) {
        int h = seed * 0x27d4eb2d ^ (i + 0x165667b1) * 0x9E3779B1;
        h ^= h >>> 15;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        return (h >>> 8) / (float) (1 << 24);
    }

    /** A hash in [-1, 1). */
    public static float signed(int seed, int i) {
        return hash(seed, i) * 2f - 1f;
    }
}
