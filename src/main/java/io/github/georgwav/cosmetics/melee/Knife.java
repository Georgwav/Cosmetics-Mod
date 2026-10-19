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

    private static int luma(int c) {
        return (((c >> 16) & 0xFF) * 54 + ((c >> 8) & 0xFF) * 183 + (c & 0xFF) * 19) >> 8;
    }
}
