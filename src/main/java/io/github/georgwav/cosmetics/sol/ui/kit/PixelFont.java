package io.github.georgwav.cosmetics.sol.ui.kit;

import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.sol.util.TtfPixels;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Silkscreen (font/silkscreen-*.ttf, SIL Open Font License), Sol's pixel type: every glyph read from the font file as
 * the bitmap its designer drew (8 pixels to the em, {@link TtfPixels}), both weights packed into one small atlas, and
 * drawn at a whole number of screen pixels per font pixel, so no font pixel is ever resampled. Pixel type is
 * uppercase: strings are upper-cased once and kept. Render thread only (the atlas); widths are plain arithmetic.
 */
public final class PixelFont {
    /** Font pixels to the em, and the capitals' height in font pixels. */
    public static final int EM = 8, CAP = 5;

    private static final String[] FILES = {"silkscreen-regular.ttf", "silkscreen-bold.ttf"};
    private static Face[] faces;
    private static SolTexture atlas;
    private static int aw, ah;
    private static final Map<String, String> UPPER = new HashMap<>();

    /** One weight's glyphs, with each one's place in the atlas. */
    static final class Face {
        final Map<Integer, TtfPixels.Glyph> glyphs;
        final Map<Integer, int[]> place = new HashMap<>();

        Face(TtfPixels font) {
            this.glyphs = font == null ? Map.of() : font.all();
        }
    }

    private PixelFont() {
    }

    static synchronized Face face(boolean bold) {
        if (faces == null) {
            faces = new Face[2];
            for (int i = 0; i < 2; i++) {
                try (InputStream in = PixelFont.class.getResourceAsStream("/assets/cosmeticsmod/font/" + FILES[i])) {
                    faces[i] = new Face(TtfPixels.parse(in.readAllBytes(), EM));
                } catch (Exception e) {
                    CosmeticsMod.LOGGER.warn("[Sol] Couldn't read {}", FILES[i], e);
                    faces[i] = new Face(null);
                }
            }
        }
        return faces[bold ? 1 : 0];
    }

    /** The text as pixel type shows it: upper case (kept, so a frame allocates nothing). */
    public static String upper(String text) {
        if (text == null) {
            return "";
        }
        String u = UPPER.get(text);
        if (u == null) {
            if (UPPER.size() > 2000) {
                UPPER.clear();
            }
            u = text.toUpperCase(Locale.ROOT);
            UPPER.put(text, u);
        }
        return u;
    }

    /** A glyph of either weight (the effects draw their own text from these), or null. */
    public static TtfPixels.Glyph glyph(boolean bold, int codepoint) {
        return face(bold).glyphs.get(codepoint);
    }

    /** Whether every character has a glyph (else the text falls back to Geist). */
    public static boolean covers(boolean bold, String text) {
        Face f = face(bold);
        String u = upper(text);
        for (int i = 0; i < u.length(); i++) {
            char c = u.charAt(i);
            if (c != ' ' && !f.glyphs.containsKey((int) c)) {
                return false;
            }
        }
        return true;
    }

    /** Width in font pixels (multiply by the scale for screen pixels). */
    public static int width(boolean bold, String text) {
        Face f = face(bold);
        String u = upper(text);
        int w = 0;
        for (int i = 0; i < u.length(); i++) {
            TtfPixels.Glyph g = f.glyphs.get((int) u.charAt(i));
            w += g != null ? g.advance() : 4;
        }
        return w;
    }

    private static SolTexture atlas() {
        if (atlas != null && !atlas.released()) {
            return atlas;
        }
        // Shelf-pack both faces' glyphs, one pixel apart.
        aw = 256;
        int x = 1, y = 1, rowH = 0;
        for (int fi = 0; fi < 2; fi++) {
            Face f = face(fi == 1);
            f.place.clear();
            for (TtfPixels.Glyph g : f.glyphs.values()) {
                if (g.w() == 0) {
                    continue;
                }
                if (x + g.w() + 1 > aw) {
                    x = 1;
                    y += rowH + 1;
                    rowH = 0;
                }
                f.place.put(g.codepoint(), new int[]{x, y});
                x += g.w() + 1;
                rowH = Math.max(rowH, g.h());
            }
        }
        ah = Integer.highestOneBit(Math.max(1, y + rowH + 1) - 1) << 1;
        int[] argb = new int[aw * ah];
        for (int fi = 0; fi < 2; fi++) {
            Face f = face(fi == 1);
            for (Map.Entry<Integer, int[]> e : f.place.entrySet()) {
                TtfPixels.Glyph g = f.glyphs.get(e.getKey());
                int ox = e.getValue()[0], oy = e.getValue()[1];
                for (int gy = 0; gy < g.h(); gy++) {
                    for (int gx = 0; gx < g.w(); gx++) {
                        if (g.at(gx, gy)) {
                            argb[(oy + gy) * aw + ox + gx] = 0xFFFFFFFF;
                        }
                    }
                }
            }
        }
        atlas = SolTexture.of("pixel_font", aw, ah, false, argb);
        return atlas;
    }

    /**
     * Draws {@code text} with its pen starting at screen x {@code sx} and its baseline at screen y {@code baseline},
     * {@code k} screen pixels per font pixel. Positions are whole screen pixels.
     */
    static void draw(Gfx g, boolean bold, String text, float sx, float baseline, int k, int argb) {
        SolTexture t = atlas();
        Face f = face(bold);
        String u = upper(text);
        int pen = Math.round(sx), base = Math.round(baseline);
        for (int i = 0; i < u.length(); i++) {
            int c = u.charAt(i);
            TtfPixels.Glyph gl = f.glyphs.get(c);
            if (gl == null) {
                pen += 4 * k;
                continue;
            }
            int[] at = f.place.get(c);
            if (at != null) {
                int x0 = pen + gl.left() * k, y0 = base - gl.top() * k;
                g.imageScreen(t, x0, y0, x0 + gl.w() * k, y0 + gl.h() * k, at[0] / (float) aw, at[1] / (float) ah,
                        (at[0] + gl.w()) / (float) aw, (at[1] + gl.h()) / (float) ah, argb, false);
            }
            pen += gl.advance() * k;
        }
    }

    /** Frees the atlas (resources reloaded). */
    public static void release() {
        if (atlas != null) {
            atlas.release();
            atlas = null;
        }
    }
}
