package io.github.georgwav.cosmetics.sol.fx;

import io.github.georgwav.cosmetics.sol.ui.kit.PixelFont;
import io.github.georgwav.cosmetics.sol.util.TtfPixels;

/**
 * Pixel text in the world (C.1): word hits, damage numbers, a gravestone's name. Silkscreen's bold glyphs, packed
 * once into an atlas, each font pixel a whole texel facing the camera, with a one-texel shadow in the deep tone.
 */
public final class FxText {
    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!?+-.:%/'#&*(),;\"<>=@_[]$~|^";
    private static FxTex atlas;
    /** Per character (by index in CHARS): atlas x, y, glyph w, h, left, top, advance. */
    private static final int[][] PLACE = new int[CHARS.length()][];
    private static final float[] R = new float[3], U = new float[3];

    private FxText() {
    }

    static synchronized FxTex atlas() {
        if (atlas == null) {
            int size = 128;
            int[] px = new int[size * size];
            int x = 1, y = 1, rowH = 0;
            for (int i = 0; i < CHARS.length(); i++) {
                TtfPixels.Glyph g = PixelFont.glyph(true, CHARS.charAt(i));
                if (g == null) {
                    continue;
                }
                if (x + g.w() + 1 > size) {
                    x = 1;
                    y += rowH + 1;
                    rowH = 0;
                }
                for (int gy = 0; gy < g.h(); gy++) {
                    for (int gx = 0; gx < g.w(); gx++) {
                        if (g.at(gx, gy)) {
                            px[(y + gy) * size + x + gx] = 0xFFFFFFFF;
                        }
                    }
                }
                PLACE[i] = new int[]{x, y, g.w(), g.h(), g.left(), g.top(), g.advance()};
                x += g.w() + 1;
                rowH = Math.max(rowH, g.h());
            }
            atlas = new FxTex("fx_text", size, size, px);
        }
        return atlas;
    }

    /** The text's width in font pixels. */
    public static int width(String text) {
        return width(text, 0, text.length());
    }

    /** The width of {@code text} from {@code start} up to {@code end} (a line of several), in font pixels. */
    public static int width(String text, int start, int end) {
        atlas();
        int w = 0;
        for (int i = start; i < end; i++) {
            int k = CHARS.indexOf(Character.toUpperCase(text.charAt(i)));
            w += k >= 0 && PLACE[k] != null ? PLACE[k][6] : 4;
        }
        return Math.max(0, w - 1);
    }

    /**
     * Draws {@code text} centred on (x, y, z) facing the camera, one font pixel {@code texel} blocks, in {@code argb}
     * with a shadow in {@code shadow} (0: none). Body pass only.
     */
    public static void draw(FxOut out, String text, float x, float y, float z, float texel, int argb, int shadow) {
        if (out.glowPass() || text == null || text.isEmpty()) {
            return;
        }
        out.right(R);
        out.up(U);
        drawOn(out, text, x, y, z, R, U, texel, argb, shadow);
    }

    /**
     * Draws {@code text} centred on (x, y, z) on a plane: {@code r} its rightward and {@code u} its upward direction
     * (unit vectors). A gravestone's name on its face. Body pass only.
     */
    public static void drawOn(FxOut out, String text, float x, float y, float z, float[] r, float[] u, float texel, int argb,
                              int shadow) {
        if (text != null) {
            drawOn(out, text, 0, text.length(), x, y, z, r, u, texel, argb, shadow);
        }
    }

    /** {@link #drawOn(FxOut, String, float, float, float, float[], float[], float, int, int)} for a part of the text. */
    public static void drawOn(FxOut out, String text, int start, int end, float x, float y, float z, float[] r, float[] u,
                              float texel, int argb, int shadow) {
        if (out.glowPass() || text == null || end <= start) {
            return;
        }
        FxTex tex = atlas();
        out.use(tex);
        int width = width(text, start, end);
        float pen = -width / 2f;
        // The capitals are five font pixels tall; their middle sits on y.
        float baseline = -PixelFont.CAP / 2f;
        for (int pass = shadow != 0 ? 0 : 1; pass < 2; pass++) {
            float px = pen, off = pass == 0 ? 1f : 0f;
            int c = pass == 0 ? shadow : argb;
            for (int i = start; i < end; i++) {
                int k = CHARS.indexOf(Character.toUpperCase(text.charAt(i)));
                int[] p = k >= 0 ? PLACE[k] : null;
                if (p == null) {
                    px += 4;
                    continue;
                }
                float gx0 = px + p[4] + off, gx1 = gx0 + p[2];
                float gy1 = baseline + p[5] - off, gy0 = gy1 - p[3];
                float u0 = p[0] / (float) tex.width, v0 = p[1] / (float) tex.height;
                float u1 = (p[0] + p[2]) / (float) tex.width, v1 = (p[1] + p[3]) / (float) tex.height;
                corner(out, r, u, x, y, z, gx0 * texel, gy0 * texel, u0, v1, c);
                corner(out, r, u, x, y, z, gx0 * texel, gy1 * texel, u0, v0, c);
                corner(out, r, u, x, y, z, gx1 * texel, gy1 * texel, u1, v0, c);
                corner(out, r, u, x, y, z, gx1 * texel, gy0 * texel, u1, v1, c);
                px += p[6];
            }
        }
    }

    private static void corner(FxOut out, float[] r, float[] u, float x, float y, float z, float dx, float dy, float tu, float tv,
                               int c) {
        out.vertex(x + r[0] * dx + u[0] * dy, y + r[1] * dx + u[1] * dy, z + r[2] * dx + u[2] * dy, tu, tv, c);
    }
}
