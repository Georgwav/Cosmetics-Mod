package io.github.georgwav.cosmetics.model;

import java.util.Arrays;

/**
 * Cloth cloaks (a per-cape option): a flat cape drawn as cloth instead of a stiff board. Its faces are split
 * into thin strips down its length once ({@link #prepare}); every frame its centre line is bent ({@link #shape})
 * and each vertex follows it ({@link #bend}). Works in the cape's own space after the vanilla cape sway: the
 * top edge at y = 0, the cape hanging down +Y, the player's back towards -Z.
 */
public final class Cloth {
    /** Strip height, in blocks: one pixel. */
    static final float STRIP = 1f / 16f;
    /** Points along the centre line per shape. */
    public static final int SAMPLES = 24;
    /** Bend at the bottom edge when moving at full speed, radians. */
    static final float MAX_BLOW = (float) Math.toRadians(55);
    /** Ripple either way, radians: standing still, and added at full speed. */
    static final float RIPPLE_STILL = (float) Math.toRadians(4);
    static final float RIPPLE_MOVING = (float) Math.toRadians(9);

    private Cloth() {
    }

    /** A cape's geometry ready to bend: faces along its length split into strips, and its length (blocks). */
    public record Mesh(BakedMesh mesh, float length) {
    }

    /** The centre line for one frame: height, depth and angle at equal steps down the cape. */
    public record Shape(float step, float[] y, float[] z, float[] angle) {
    }

    /** Splits every face that runs down the cape into strips of about one pixel. */
    public static Mesh prepare(BakedMesh mesh) {
        float[] d = mesh.data();
        float bottom = 0f;
        for (int i = 1; i < d.length; i += BakedMesh.FLOATS_PER_VERTEX) {
            bottom = Math.max(bottom, d[i]);
        }
        float[] out = new float[0];
        int used = 0;
        int quad = BakedMesh.FLOATS_PER_QUAD;
        int v = BakedMesh.FLOATS_PER_VERTEX;
        for (int q = 0; q < mesh.quadCount(); q++) {
            int base = q * quad;
            // The pair of opposite edges (0-3 / 1-2, or 0-1 / 3-2) that runs further down the cape gets split.
            float spanA = Math.max(Math.abs(d[base + 3 * v + 1] - d[base + 1]), Math.abs(d[base + 2 * v + 1] - d[base + v + 1]));
            float spanB = Math.max(Math.abs(d[base + v + 1] - d[base + 1]), Math.abs(d[base + 2 * v + 1] - d[base + 3 * v + 1]));
            boolean alongA = spanA >= spanB;
            float span = Math.max(spanA, spanB);
            int strips = Math.max(1, Math.round(span / STRIP));
            if (used + strips * quad > out.length) {
                out = Arrays.copyOf(out, Math.max(out.length * 2, used + strips * quad));
            }
            for (int s = 0; s < strips; s++) {
                float t0 = (float) s / strips;
                float t1 = (float) (s + 1) / strips;
                if (alongA) {
                    // Corners 0 and 1 move towards 3 and 2.
                    lerp(d, base, base + 3 * v, t0, out, used);
                    lerp(d, base + v, base + 2 * v, t0, out, used + v);
                    lerp(d, base + v, base + 2 * v, t1, out, used + 2 * v);
                    lerp(d, base, base + 3 * v, t1, out, used + 3 * v);
                } else {
                    // Corners 0 and 3 move towards 1 and 2.
                    lerp(d, base, base + v, t0, out, used);
                    lerp(d, base, base + v, t1, out, used + v);
                    lerp(d, base + 3 * v, base + 2 * v, t1, out, used + 2 * v);
                    lerp(d, base + 3 * v, base + 2 * v, t0, out, used + 3 * v);
                }
                used += quad;
            }
        }
        return new Mesh(new BakedMesh(Arrays.copyOf(out, used)), bottom);
    }

    private static void lerp(float[] d, int a, int b, float t, float[] out, int at) {
        for (int k = 0; k < BakedMesh.FLOATS_PER_VERTEX; k++) {
            // Normals (k >= 5) are equal at both ends of an edge on flat faces; lerping keeps them.
            out[at + k] = d[a + k] + (d[b + k] - d[a + k]) * t;
        }
    }

    /**
     * The centre line for one frame.
     *
     * @param length   the cape's length, blocks
     * @param blow     how fast the player moves, 0 (still) to 1 (full speed): the faster, the more the lower
     *                 part is blown back and the stronger the ripple
     * @param seconds  time, for the ripple running down the cape
     * @param phase    an offset per player, so capes don't ripple in step
     * @param strength the cape swing setting: 0 (a flat, still cape) to 2
     */
    public static Shape shape(float length, float blow, double seconds, float phase, float strength) {
        return shape(length, blow, seconds, phase, strength, null);
    }

    /**
     * {@link #shape(float, float, double, float, float)} plus {@code lag}: radians each of the {@link #SAMPLES}
     * points is turned further back (wavy capes), or null for none.
     */
    public static Shape shape(float length, float blow, double seconds, float phase, float strength, float[] lag) {
        float step = Math.max(length, STRIP) / (SAMPLES - 1);
        float[] y = new float[SAMPLES];
        float[] z = new float[SAMPLES];
        float[] angle = new float[SAMPLES];
        float b = Math.max(0f, Math.min(1f, blow));
        float ripple = (RIPPLE_STILL + RIPPLE_MOVING * b) * strength;
        double speed = 2 * Math.PI * (0.6 + 1.4 * b); // ripples per second, in radians
        double wavelength = Math.max(length, STRIP) * 0.9;
        for (int i = 0; i < SAMPLES; i++) {
            float t = (float) i / (SAMPLES - 1);
            // Blown back more towards the bottom, plus a ripple that grows towards the bottom too.
            float a = MAX_BLOW * b * strength * t * t
                    + ripple * t * (float) Math.sin(2 * Math.PI * (i * step) / wavelength - speed * seconds + phase);
            if (lag != null) {
                a += lag[i];
            }
            angle[i] = a;
            if (i > 0) {
                float mid = (angle[i - 1] + a) / 2f;
                y[i] = y[i - 1] + step * (float) Math.cos(mid);
                z[i] = z[i - 1] - step * (float) Math.sin(mid); // back is -Z here
            }
        }
        return new Shape(step, y, z, angle);
    }

    /**
     * Where a vertex of the flat cape goes on the bent one: {@code out} gets x, y, z and the normal's x, y, z.
     * A vertex follows the centre line at its height; its depth (z) stays square to the line.
     */
    public static void bend(Shape shape, float x, float y, float z, float nx, float ny, float nz, float[] out) {
        float f = y / shape.step();
        int last = SAMPLES - 1;
        float py, pz, a;
        if (f <= 0f) {
            py = y;
            pz = 0f;
            a = 0f;
        } else if (f >= last) {
            a = shape.angle()[last];
            float over = y - last * shape.step();
            py = shape.y()[last] + over * (float) Math.cos(a);
            pz = shape.z()[last] - over * (float) Math.sin(a);
        } else {
            int i = (int) f;
            float t = f - i;
            py = shape.y()[i] + (shape.y()[i + 1] - shape.y()[i]) * t;
            pz = shape.z()[i] + (shape.z()[i + 1] - shape.z()[i]) * t;
            a = shape.angle()[i] + (shape.angle()[i + 1] - shape.angle()[i]) * t;
        }
        // Turning by -a around X: (y, z) -> (y cos a + z sin a, -y sin a + z cos a).
        float cos = (float) Math.cos(a);
        float sin = (float) Math.sin(a);
        out[0] = x;
        out[1] = py + z * sin;
        out[2] = pz + z * cos;
        out[3] = nx;
        out[4] = ny * cos + nz * sin;
        out[5] = -ny * sin + nz * cos;
    }
}
