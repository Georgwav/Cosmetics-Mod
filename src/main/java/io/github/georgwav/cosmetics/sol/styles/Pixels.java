package io.github.georgwav.cosmetics.sol.styles;

import io.github.georgwav.cosmetics.render.Recolor;

/**
 * The picture work Styles does on a texture, on plain ARGB arrays: recolours (through {@link Recolor}, the cosmetics'
 * own), see-through and off, low fire, and keeping blocks opaque. Plain Java, so it is unit-tested; the mixer runs it
 * off the render thread.
 */
public final class Pixels {
    /** The built-in recolours (B.2.2): their names and colours. */
    public static final String[] PRESETS = {"aqua", "blueberry", "lavender", "red", "ice", "black", "white"};
    public static final int[] PRESET_RGB = {0x3FD8E0, 0x4F5BD5, 0xB497F0, 0xE53935, 0xBFE9FF, 0x26262B, 0xF4F4F4};
    /** How much of a see-through part shows. */
    public static final float SEE_THROUGH = 0.35f;

    private Pixels() {
    }

    /** A picture: its size and its pixels (ARGB, row by row). */
    public record Image(int w, int h, int[] argb) {
        public Image {
            if (argb.length != w * h) {
                throw new IllegalArgumentException("size " + w + "x" + h + " but " + argb.length + " pixels");
            }
        }

        public Image copy() {
            return new Image(w, h, argb.clone());
        }

        public boolean opaque() {
            for (int p : argb) {
                if (p >>> 24 != 255) {
                    return false;
                }
            }
            return true;
        }
    }

    /** The preset's colour, or -1 for a name that isn't one. */
    public static int preset(String name) {
        for (int i = 0; i < PRESETS.length; i++) {
            if (PRESETS[i].equals(name)) {
                return PRESET_RGB[i];
            }
        }
        return -1;
    }

    /**
     * A choice's colours on a picture: hue, saturation and brightness, and a tint painted into the coloured parts
     * (white and grey parts keep their shading), as the cosmetics' recolour does. {@code tint} is RGB or -1.
     */
    public static Image recolour(Image in, int hue, float saturation, float brightness, int tint) {
        float sat = Math.min(1f, saturation);
        // A grey or white texture (an iron sword, the crystal's glass) has no coloured parts to paint: the whole of it
        // takes the tint, by brightness, as the cosmetics' "whole cosmetic" colouring does.
        boolean whole = tint >= 0 && colouredShare(in) < WHOLE_BELOW;
        Recolor r = new Recolor(hue, sat, brightness, tint >= 0 && !whole ? Recolor.Paint.COLORED : Recolor.Paint.NONE, Math.max(0, tint));
        int[] out = new int[in.argb().length];
        r.applyAll(in.argb(), out);
        if (saturation > 1f) {
            vivid(out, saturation - 1f);
        }
        if (whole) {
            int tr = tint >> 16 & 255, tg = tint >> 8 & 255, tb = tint & 255;
            for (int i = 0; i < out.length; i++) {
                int p = out[i];
                float v = Math.max(p >> 16 & 255, Math.max(p >> 8 & 255, p & 255)) / 255f;
                out[i] = p & 0xFF000000 | Math.round(tr * v) << 16 | Math.round(tg * v) << 8 | Math.round(tb * v);
            }
        }
        return new Image(in.w(), in.h(), out);
    }

    /** Below this share of coloured pixels a texture is tinted whole. */
    static final float WHOLE_BELOW = 0.15f;

    /** The share of a picture's visible pixels that are coloured (not white, grey or black). */
    static float colouredShare(Image in) {
        int seen = 0, coloured = 0;
        for (int p : in.argb()) {
            if (p >>> 24 < 16) {
                continue;
            }
            seen++;
            int r = p >> 16 & 255, g = p >> 8 & 255, b = p & 255;
            int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
            if (max > 24 && (max - min) / (float) max > 0.25f) {
                coloured++;
            }
        }
        return seen == 0 ? 0f : coloured / (float) seen;
    }

    /**
     * As {@link #recolour}, only inside a rectangle given as fractions of the picture (x0, y0, x1, y1), so it works
     * at any resolution: the crystal's frame and core are two halves of one texture.
     */
    public static Image recolour(Image in, int hue, float saturation, float brightness, int tint, float[] rect) {
        int w = in.w(), h = in.h();
        int x0 = Math.max(0, Math.round(rect[0] * w)), y0 = Math.max(0, Math.round(rect[1] * h));
        int x1 = Math.min(w, Math.round(rect[2] * w)), y1 = Math.min(h, Math.round(rect[3] * h));
        if (x1 <= x0 || y1 <= y0) {
            return in;
        }
        // The region on its own (so whether it's tinted whole is its own question), then put back.
        int rw = x1 - x0, rh = y1 - y0;
        int[] region = new int[rw * rh];
        for (int y = 0; y < rh; y++) {
            System.arraycopy(in.argb(), (y0 + y) * w + x0, region, y * rw, rw);
        }
        Image done = recolour(new Image(rw, rh, region), hue, saturation, brightness, tint);
        int[] out = in.argb().clone();
        for (int y = 0; y < rh; y++) {
            System.arraycopy(done.argb(), y * rw, out, (y0 + y) * w + x0, rw);
        }
        return new Image(w, h, out);
    }

    /**
     * The glowing pixels (bright and coloured, as the respawn anchor's charge) turned to {@code tint}'s hue and
     * saturation, keeping their brightness; the rest as it is.
     */
    public static Image glow(Image in, int tint) {
        float[] t = java.awt.Color.RGBtoHSB(tint >> 16 & 255, tint >> 8 & 255, tint & 255, null);
        int[] out = in.argb().clone();
        float[] hsv = new float[3];
        for (int i = 0; i < out.length; i++) {
            int p = out[i];
            if (p >>> 24 < 8) {
                continue;
            }
            java.awt.Color.RGBtoHSB(p >> 16 & 255, p >> 8 & 255, p & 255, hsv);
            if (hsv[2] > 0.55f && hsv[1] > 0.25f) {
                out[i] = p & 0xFF000000 | java.awt.Color.HSBtoRGB(t[0], Math.min(1f, t[1] * 0.4f + hsv[1] * 0.6f), hsv[2]) & 0xFFFFFF;
            }
        }
        return new Image(in.w(), in.h(), out);
    }

    /** More saturated by {@code k} (0..1): each channel pushed away from the pixel's grey. */
    static void vivid(int[] argb, float k) {
        for (int i = 0; i < argb.length; i++) {
            int p = argb[i];
            int r = p >> 16 & 255, g = p >> 8 & 255, b = p & 255;
            float grey = 0.299f * r + 0.587f * g + 0.114f * b;
            r = clamp(Math.round(grey + (r - grey) * (1f + k)));
            g = clamp(Math.round(grey + (g - grey) * (1f + k)));
            b = clamp(Math.round(grey + (b - grey) * (1f + k)));
            argb[i] = p & 0xFF000000 | r << 16 | g << 8 | b;
        }
    }

    /** Every pixel at {@code share} of its alpha (a see-through crystal or shield). */
    public static Image seeThrough(Image in, float share) {
        int[] out = in.argb().clone();
        for (int i = 0; i < out.length; i++) {
            int a = Math.round((out[i] >>> 24) * share);
            out[i] = a << 24 | out[i] & 0xFFFFFF;
        }
        return new Image(in.w(), in.h(), out);
    }

    /** Nothing: the same size, every pixel clear (an invisible crystal, no fire). */
    public static Image off(Image in) {
        return new Image(in.w(), in.h(), new int[in.argb().length]);
    }

    /**
     * Low fire: each square frame's flames squashed into its bottom half (the frames of an animated texture stacked as
     * the game stacks them), the top half clear. Works at any size (16x, 32x).
     */
    public static Image lowFire(Image in) {
        int w = in.w(), frames = Math.max(1, in.h() / w);
        int[] out = new int[in.argb().length];
        for (int f = 0; f < frames; f++) {
            int top = f * w;
            for (int y = w / 2; y < w; y++) {
                int sy = top + Math.min(w - 1, (y - w / 2) * 2);
                for (int x = 0; x < w; x++) {
                    out[(top + y) * w + x] = in.argb()[sy * w + x];
                }
            }
        }
        return new Image(w, in.h(), out);
    }

    /**
     * Keeps a block opaque (rule B.0.4: blocks are never thinned): when vanilla's texture is fully opaque, every pixel of
     * the new one is made fully opaque too, its colour kept (a clear pixel takes the nearest opaque colour in its row,
     * else black).
     */
    public static Image keepOpaque(Image in) {
        int[] out = in.argb().clone();
        int w = in.w();
        for (int y = 0; y < in.h(); y++) {
            int last = 0xFF000000;
            for (int x = 0; x < w; x++) {
                int p = out[y * w + x];
                if (p >>> 24 >= 128) {
                    last = 0xFF000000 | p;
                    out[y * w + x] = last;
                } else {
                    out[y * w + x] = last;
                }
            }
        }
        return new Image(w, in.h(), out);
    }

    /** The first frame of an animated texture (frames stacked as squares), or the picture itself. */
    public static Image firstFrame(Image in) {
        if (in.h() <= in.w() || in.h() % in.w() != 0) {
            return in;
        }
        int[] out = new int[in.w() * in.w()];
        System.arraycopy(in.argb(), 0, out, 0, out.length);
        return new Image(in.w(), in.w(), out);
    }

    /** Nearest-neighbour scaled to fit {@code size} x {@code size}, centred, the rest clear (a tile's picture). */
    public static Image fit(Image in, int size) {
        int[] out = new int[size * size];
        float k = Math.min(size / (float) in.w(), size / (float) in.h());
        int w = Math.max(1, Math.round(in.w() * k)), h = Math.max(1, Math.round(in.h() * k));
        int ox = (size - w) / 2, oy = (size - h) / 2;
        for (int y = 0; y < h; y++) {
            int sy = Math.min(in.h() - 1, (int) (y / k));
            for (int x = 0; x < w; x++) {
                out[(oy + y) * size + ox + x] = in.argb()[sy * in.w() + Math.min(in.w() - 1, (int) (x / k))];
            }
        }
        return new Image(size, size, out);
    }

    /**
     * A block's texture as a little cube seen from above at an angle (the menu's block preview): the top lightest,
     * the left a little darker, the right darker still, each face the texture's pixels (16 wide becomes 33 x 33).
     */
    public static Image isoCube(Image tex) {
        Image t = firstFrame(tex);
        int n = t.w(), w = 2 * n + 1, h = 2 * n + 1;
        int[] out = new int[w * h];
        // Top: a rhombus, texel (u, v) at x = n + u - v, y = (u + v) / 2.
        for (int v = 0; v < n; v++) {
            for (int u = 0; u < n; u++) {
                int c = t.argb()[v * n + u];
                int x = n + u - v, y = (u + v) / 2;
                plot(out, w, h, x, y, c, 1f);
                plot(out, w, h, x - 1, y, c, 1f);
            }
        }
        // Left and right faces: texel (u, v) down the side, sheared.
        for (int v = 0; v < n; v++) {
            for (int u = 0; u < n; u++) {
                int c = t.argb()[v * n + u];
                plot(out, w, h, u, n / 2 + u / 2 + v, c, 0.78f);
                plot(out, w, h, n + 1 + u, n - u / 2 + v, c, 0.6f);
            }
        }
        return new Image(w, h, out);
    }

    private static void plot(int[] out, int w, int h, int x, int y, int c, float shade) {
        if (x < 0 || y < 0 || x >= w || y >= h || c >>> 24 < 8) {
            return;
        }
        int r = Math.round((c >> 16 & 255) * shade), g = Math.round((c >> 8 & 255) * shade), b = Math.round((c & 255) * shade);
        out[y * w + x] = 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** The picture's average hue (0..1), for sorting tiles by colour; grey pictures sort last (2). */
    public static float hue(Image in) {
        double sx = 0, sy = 0, wsum = 0;
        for (int p : in.argb()) {
            if (p >>> 24 < 128) {
                continue;
            }
            float[] hsv = java.awt.Color.RGBtoHSB(p >> 16 & 255, p >> 8 & 255, p & 255, null);
            double w = hsv[1] * hsv[2];
            sx += Math.cos(hsv[0] * Math.PI * 2) * w;
            sy += Math.sin(hsv[0] * Math.PI * 2) * w;
            wsum += w;
        }
        if (wsum < in.argb().length * 0.02) {
            return 2f;
        }
        double a = Math.atan2(sy, sx) / (Math.PI * 2);
        return (float) (a < 0 ? a + 1 : a);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
