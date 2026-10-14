package io.github.georgwav.cosmetics.render;

/**
 * A cape's design laid out as an elytra texture. Plain maths on ARGB pixels, no Minecraft types, so it is
 * unit-tested; {@link CapeElytra} reads the textures and draws the elytra with the result.
 *
 * <p>The game draws a player's elytra with their cape's texture: its wings take the part of the 64 x 32 cape layout
 * from 22 to 46 across and 0 to 22 down (a wing is a 10 x 20 x 2 box; both wings use the same part, the right one
 * mirrored), which the game's capes fill with an elytra version of their design. Capes made for other clients (22
 * blocks wide, as Badlion's) have no such part, and a 64 x 32 one may leave it empty, so here it is made from the
 * cape's own design: the cape's outside on each wing's outside, stretched from the cape's 10 x 16 to the wing's
 * 10 x 20 so it covers all of it; the cape's inside on the wing's inside; and the edges of the two on the wing's
 * edges. A wing's outside then shows the cape as the cape shows it, seen from behind. Cut to the game's elytra
 * texture's shape ({@link #shape}), it is an elytra in the cape's design rather than a plate.
 */
public final class ElytraArt {
    /** The elytra layout, in blocks: the game's 64 x 32 cape texture. */
    public static final int WIDTH = 64;
    public static final int HEIGHT = 32;
    /** Fewest of a wing's outside pixels a 64 x 32 cape texture must fill to count as having an elytra of its own. */
    static final float OWN_ELYTRA_SHARE = 0.25f;

    /**
     * A rectangle of a texture, in pixels: (u1, v1) is the corner the face's top left shows, (u2, v2) the bottom
     * right, so a face drawn mirrored has u1 > u2.
     */
    public record Rect(float u1, float v1, float u2, float v2) {
        float u(float f) {
            return u1 + (u2 - u1) * f;
        }

        float v(float f) {
            return v1 + (v2 - v1) * f;
        }

        float width() {
            return Math.abs(u2 - u1);
        }
    }

    /** Pixels of one area of a texture at a fraction (0..1 across, 0..1 down) of a {@link Rect}. */
    private interface Sampler {
        int at(float fu0, float fu1, float fv0, float fv1);
    }

    private ElytraArt() {
    }

    /**
     * Where a cape's design is in a cape texture without a model to say so: the vanilla cape's place in the 64 x 32
     * layout (a 2:1 texture), else in the 22 blocks wide layout of other clients' capes (square blocks). Returns
     * {outside, inside}.
     */
    public static Rect[] capeLayout(int width, int height) {
        float block = width == 2 * height ? width / (float) WIDTH : width / 22f;
        return new Rect[]{new Rect(block, block, 11 * block, 17 * block), new Rect(12 * block, block, 22 * block, 17 * block)};
    }

    /**
     * Whether a cape texture (one frame) has an elytra of its own: it is the 64 x 32 layout and the elytra's part of
     * it is drawn on.
     */
    public static boolean hasOwnElytra(int[] argb, int width, int height) {
        if (width != 2 * height || width < WIDTH) {
            return false;
        }
        float block = width / (float) WIDTH;
        int x0 = Math.round(36 * block);
        int x1 = Math.round(46 * block);
        int y0 = Math.round(2 * block);
        int y1 = Math.round(22 * block);
        int seen = 0;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                if ((argb[y * width + x] >>> 24) >= 0x80) {
                    seen++;
                }
            }
        }
        return seen >= OWN_ELYTRA_SHARE * (x1 - x0) * (y1 - y0);
    }

    /** Pixels a block for an elytra made from a cape whose outside is {@code outside} (10 blocks wide), 1 to {@code max}. */
    public static int scaleFor(Rect outside, int max) {
        return Math.max(1, Math.min(max, Math.round(outside.width() / 10f)));
    }

    /**
     * The elytra texture for a cape's design, {@code scale} pixels a block ({@link #WIDTH} x {@link #HEIGHT} blocks);
     * see the class. Everything outside the wings' part is see-through.
     *
     * @param argb    the cape texture's pixels (one frame), {@code width} x {@code height}
     * @param outside where the cape's outside (the side seen from behind) is in it
     * @param inside  where its inside (against the back) is
     */
    public static int[] fromCape(int[] argb, int width, int height, Rect outside, Rect inside, int scale) {
        int[] out = new int[WIDTH * scale * HEIGHT * scale];
        Sampler outer = (a, b, c, d) -> sample(argb, width, height, outside, a, b, c, d);
        Sampler inner = (a, b, c, d) -> sample(argb, width, height, inside, a, b, c, d);
        // One pixel of the wing's face, as a fraction of it, for the edges to take the face's last row or column.
        float across = 1f / (10 * scale);
        float down = 1f / (20 * scale);
        // The wing's outside and inside.
        fill(out, scale, 36, 2, 10, 20, outer);
        fill(out, scale, 24, 2, 10, 20, inner);
        // Its edges, 2 blocks deep, the outside's half next to the outside: the outer edge (the outside's right
        // column, the inside's left), the inner edge (the inside's right, the outside's left), the top (its top
        // rows, the outside's read right to left) and the bottom (its bottom rows).
        fill(out, scale, 22, 2, 2, 20, (a, b, c, d) -> a + b < 1f
                ? outer.at(1f - across, 1f, c, d) : inner.at(0f, across, c, d));
        fill(out, scale, 34, 2, 2, 20, (a, b, c, d) -> a + b < 1f
                ? inner.at(1f - across, 1f, c, d) : outer.at(0f, across, c, d));
        fill(out, scale, 24, 0, 10, 2, (a, b, c, d) -> c + d < 1f
                ? outer.at(1f - b, 1f - a, 0f, down) : inner.at(a, b, 0f, down));
        fill(out, scale, 34, 0, 10, 2, (a, b, c, d) -> c + d < 1f
                ? outer.at(1f - b, 1f - a, 1f - down, 1f) : inner.at(a, b, 1f - down, 1f));
        return out;
    }

    /** Fills the area {@code bw} x {@code bh} blocks at block ({@code bx}, {@code by}) of the layout from {@code sampler}. */
    private static void fill(int[] out, int scale, int bx, int by, int bw, int bh, Sampler sampler) {
        int stride = WIDTH * scale;
        int pw = bw * scale;
        int ph = bh * scale;
        for (int y = 0; y < ph; y++) {
            for (int x = 0; x < pw; x++) {
                out[(by * scale + y) * stride + bx * scale + x] = sampler.at(x / (float) pw, (x + 1) / (float) pw,
                        y / (float) ph, (y + 1) / (float) ph);
            }
        }
    }

    /**
     * The colour under the part of {@code rect} from fraction {@code fu0} to {@code fu1} across and {@code fv0} to
     * {@code fv1} down: the average of its pixels, weighted by how seen each is, where it covers more than a pixel
     * (drawn smaller), else the pixel at its middle (drawn bigger, pixel art stays sharp).
     */
    static int sample(int[] argb, int width, int height, Rect rect, float fu0, float fu1, float fv0, float fv1) {
        int[] xs = span(rect.u(fu0), rect.u(fu1), width);
        int[] ys = span(rect.v(fv0), rect.v(fv1), height);
        int x0 = xs[0];
        int x1 = xs[1];
        int y0 = ys[0];
        int y1 = ys[1];
        long a = 0;
        long r = 0;
        long g = 0;
        long b = 0;
        int n = 0;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int c = argb[y * width + x];
                int alpha = c >>> 24;
                a += alpha;
                r += (long) (c >> 16 & 0xFF) * alpha;
                g += (long) (c >> 8 & 0xFF) * alpha;
                b += (long) (c & 0xFF) * alpha;
                n++;
            }
        }
        if (a == 0) {
            return 0;
        }
        return (int) ((a + n / 2) / n) << 24 | (int) (r / a) << 16 | (int) (g / a) << 8 | (int) (b / a);
    }

    /** The pixels (first, last + 1) from {@code a} to {@code b} along a side {@code size} pixels long. */
    private static int[] span(float a, float b, int size) {
        float lo = Math.min(a, b);
        float hi = Math.max(a, b);
        if (hi - lo <= 1f) {
            int middle = clamp((int) Math.floor((lo + hi) / 2f), size);
            return new int[]{middle, middle + 1};
        }
        int first = clamp((int) Math.floor(lo + 1e-4f), size);
        return new int[]{first, Math.max(first + 1, clamp((int) Math.ceil(hi - 1e-4f), size + 1))};
    }

    private static int clamp(int value, int limit) {
        return Math.max(0, Math.min(limit - 1, value));
    }

    /**
     * Cuts the elytra layout {@code argb} ({@code width} x {@code height}) to the shape of the game's elytra texture
     * ({@code mask}, the same layout at its own size): each pixel shows only as much as the elytra texture's pixel
     * there is seen, so the cape's design takes the elytra's wing shape and thin edges instead of a full plate.
     */
    public static void shape(int[] argb, int width, int height, int[] mask, int maskWidth, int maskHeight) {
        for (int y = 0; y < height; y++) {
            int my = Math.min(maskHeight - 1, y * maskHeight / height);
            for (int x = 0; x < width; x++) {
                int mx = Math.min(maskWidth - 1, x * maskWidth / width);
                int seen = mask[my * maskWidth + mx] >>> 24;
                int i = y * width + x;
                int c = argb[i];
                argb[i] = ((c >>> 24) * seen / 255) << 24 | (c & 0xFFFFFF);
            }
        }
    }

    /**
     * Draws {@code top} over {@code base} (both ARGB, the same size), as a glowing part shows over the cape; the
     * elytra can't glow, so the part is drawn in.
     */
    public static void over(int[] base, int[] top) {
        for (int i = 0; i < base.length; i++) {
            int t = top[i];
            int ta = t >>> 24;
            if (ta == 0) {
                continue;
            }
            int b = base[i];
            int ba = b >>> 24;
            int outA = ta + ba * (255 - ta) / 255;
            if (outA == 0) {
                base[i] = 0;
                continue;
            }
            int rr = ((t >> 16 & 0xFF) * ta + (b >> 16 & 0xFF) * ba * (255 - ta) / 255) / outA;
            int gg = ((t >> 8 & 0xFF) * ta + (b >> 8 & 0xFF) * ba * (255 - ta) / 255) / outA;
            int bb = ((t & 0xFF) * ta + (b & 0xFF) * ba * (255 - ta) / 255) / outA;
            base[i] = outA << 24 | rr << 16 | gg << 8 | bb;
        }
    }
}
