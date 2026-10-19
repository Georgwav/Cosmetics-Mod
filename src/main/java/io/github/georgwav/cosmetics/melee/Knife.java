package io.github.georgwav.cosmetics.melee;

import io.github.georgwav.cosmetics.model.CosmeticDefinition;

/**
 * A knife ready to draw: its cosmetic's {@code melee} block, its model and animations (one {@link KnifeRig}, shared by
 * the knife's finishes) and its texture. Made when cosmetics load (see {@link KnifeRegistry}).
 */
public final class Knife {
    /** The cosmetic's id. */
    public final String id;
    public final CosmeticDefinition.Melee spec;
    /** The texture, {@code namespace:path.png}; its UVs are the model's (divided by the size the geometry declares). */
    public final String texture;
    public final KnifeRig rig;

    /** Render-thread caches: the texture with its alpha cut at a half and the render type drawing it. */
    public Object cutTexture, renderType;
    /** The texture's bright colour (ARGB), for the glow's "item" colours; 0 until worked out. */
    public volatile int bright;
    /** Whether the cut texture is being made, or failed to be (the pack's own is drawn meanwhile), and its pixels. */
    public volatile boolean cutting, cutFailed;
    public volatile int[] cutPixels;
    public volatile int cutWidth, cutHeight;
    /** Per bone, its faces that show something with the cut texture (see {@link #solidQuads}); null until it's cut. */
    public volatile float[][] solid;

    private KnifePose third;

    /** The knife as held in third person: {@code static_idle}'s pose, made the first time it's asked for. */
    public KnifePose thirdPose() {
        if (third == null) {
            third = new KnifePose(rig.geo);
            third.pose(rig.staticIdle, 0f, null, 0f, null, 0f, 1f, null);
        }
        return third;
    }

    public Knife(String id, CosmeticDefinition.Melee spec, String texture, KnifeRig rig) {
        this.id = id;
        this.spec = spec;
        this.texture = texture;
        this.rig = rig;
    }

    /**
     * A texture's pixels (ARGB) made ready for the knife: alpha cut at a half (TACZ's cutout), into {@code out}; and
     * the average of its brightest quarter of solid pixels, the colour the glow takes for "item" (ARGB).
     */
    public static int cut(int[] argb, int[] out) {
        int n = argb.length;
        int[] luma = new int[256];
        for (int i = 0; i < n; i++) {
            int c = argb[i];
            boolean solid = (c >>> 24) >= 128;
            out[i] = solid ? c | 0xFF000000 : 0;
            if (solid) {
                luma[luma(c)]++;
            }
        }
        int solidCount = 0;
        for (int l : luma) {
            solidCount += l;
        }
        if (solidCount == 0) {
            return 0xFFFFFFFF;
        }
        // The luma the brightest quarter starts at.
        int need = Math.max(1, solidCount / 4), from = 255;
        for (int seen = 0; from > 0; from--) {
            seen += luma[from];
            if (seen >= need) {
                break;
            }
        }
        long r = 0, g = 0, b = 0, count = 0;
        for (int i = 0; i < n; i++) {
            int c = out[i];
            if (c != 0 && luma(c) >= from) {
                r += (c >> 16) & 0xFF;
                g += (c >> 8) & 0xFF;
                b += c & 0xFF;
                count++;
            }
        }
        return 0xFF000000 | (int) (r / count) << 16 | (int) (g / count) << 8 | (int) (b / count);
    }

    /**
     * Per bone of {@code geo}, its faces that show something with the cut texture {@code cut} ({@code w} by {@code h},
     * from {@link #cut}): a face of no area, or whose picture is see-through everywhere, draws nothing and is left out.
     * Every texel the face's picture touches counts, so no face that could show a pixel is lost. Bones keep their
     * own array when nothing is left out.
     */
    public static float[][] solidQuads(KnifeGeo geo, int[] cut, int w, int h) {
        float[][] out = new float[geo.bones.length][];
        for (int b = 0; b < geo.bones.length; b++) {
            float[] q = geo.bones[b].quads;
            int keep = 0;
            boolean[] shows = new boolean[q.length / KnifeGeo.QUAD];
            for (int f = 0; f < shows.length; f++) {
                shows[f] = shows(q, f * KnifeGeo.QUAD, cut, w, h);
                if (shows[f]) {
                    keep++;
                }
            }
            if (keep == shows.length) {
                out[b] = q;
                continue;
            }
            float[] kept = new float[keep * KnifeGeo.QUAD];
            for (int f = 0, k = 0; f < shows.length; f++) {
                if (shows[f]) {
                    System.arraycopy(q, f * KnifeGeo.QUAD, kept, k++ * KnifeGeo.QUAD, KnifeGeo.QUAD);
                }
            }
            out[b] = kept;
        }
        return out;
    }

    private static boolean shows(float[] q, int o, int[] cut, int w, int h) {
        // No area: the corners on a line or a point.
        float ax = q[o + 5] - q[o], ay = q[o + 6] - q[o + 1], az = q[o + 7] - q[o + 2];
        float bx = q[o + 15] - q[o], by = q[o + 16] - q[o + 1], bz = q[o + 17] - q[o + 2];
        float cx = ay * bz - az * by, cy = az * bx - ax * bz, cz = ax * by - ay * bx;
        if (cx * cx + cy * cy + cz * cz < 1.0e-10f) {
            return false;
        }
        float u0 = Float.MAX_VALUE, u1 = -Float.MAX_VALUE, v0 = Float.MAX_VALUE, v1 = -Float.MAX_VALUE;
        for (int k = 0; k < 4; k++) {
            float u = q[o + k * 5 + 3], v = q[o + k * 5 + 4];
            u0 = Math.min(u0, u);
            u1 = Math.max(u1, u);
            v0 = Math.min(v0, v);
            v1 = Math.max(v1, v);
        }
        int x0 = Math.max(0, (int) Math.floor(u0 * w + 1.0e-4f)), x1 = Math.min(w, Math.max(x0 + 1, (int) Math.ceil(u1 * w - 1.0e-4f)));
        int y0 = Math.max(0, (int) Math.floor(v0 * h + 1.0e-4f)), y1 = Math.min(h, Math.max(y0 + 1, (int) Math.ceil(v1 * h - 1.0e-4f)));
        if (x0 >= x1 || y0 >= y1) {
            // Its picture is off the texture (the texture's edge repeats or clamps): keep it.
            return true;
        }
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                if (cut[y * w + x] != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int luma(int c) {
        return (((c >> 16) & 0xFF) * 54 + ((c >> 8) & 0xFF) * 183 + (c & 0xFF) * 19) >> 8;
    }
}
