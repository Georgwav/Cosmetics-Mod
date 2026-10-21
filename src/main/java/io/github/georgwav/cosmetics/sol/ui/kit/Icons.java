package io.github.georgwav.cosmetics.sol.ui.kit;

import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.sol.util.Colors;

import java.util.HashMap;
import java.util.Map;

/**
 * The UI's icons: Sol's 12 x 12 pixel icons ({@link PixelIcons}) in one small atlas, drawn at a whole number of screen
 * pixels per icon pixel (k = round(icon pixel x the design scale), at least 1), on whole pixels and with nearest
 * filtering, so they're never resampled. In the text's colour; a lit icon gets a one-pixel halo in its glow colour.
 * Render thread only.
 */
public final class Icons {
    /** Kept for callers: the line icons' soft fill strengths (pixel icons have no soft fill). */
    public static final float FILL = 0.16f, FILL_ON = 0.30f;

    private static final int CELL = PixelIcons.CELL + 1;
    private static SolTexture atlas;
    private static final Map<String, Integer> index = new HashMap<>();
    private static int cols, w, h;

    private Icons() {
    }

    public static boolean has(String name) {
        return PixelIcons.has(name);
    }

    /** Frees the atlas (resources reloaded); it's made again on use. */
    public static void release() {
        if (atlas != null) {
            atlas.release();
            atlas = null;
        }
    }

    private static SolTexture atlas() {
        if (atlas != null && !atlas.released()) {
            return atlas;
        }
        Map<String, boolean[]> all = PixelIcons.all();
        cols = 16;
        int rows = Math.max(1, (all.size() + cols - 1) / cols);
        w = cols * CELL;
        h = rows * CELL;
        int[] argb = new int[w * h];
        index.clear();
        int i = 0;
        for (Map.Entry<String, boolean[]> e : all.entrySet()) {
            int ox = (i % cols) * CELL, oy = (i / cols) * CELL;
            boolean[] cells = e.getValue();
            for (int y = 0; y < PixelIcons.CELL; y++) {
                for (int x = 0; x < PixelIcons.CELL; x++) {
                    if (cells[y * PixelIcons.CELL + x]) {
                        argb[(oy + y) * w + ox + x] = 0xFFFFFFFF;
                    }
                }
            }
            index.put(e.getKey(), i++);
        }
        atlas = SolTexture.of("pixel_icons", w, h, false, argb);
        return atlas;
    }

    /** Screen pixels per icon pixel for an icon {@code size} design pixels wide. */
    public static int scale(Gfx g, float size) {
        return Math.max(1, Math.round(size / PixelIcons.CELL * g.d.u));
    }

    /**
     * Draws {@code name} centred in a {@code size} x {@code size} design-pixel square at (x, y), in {@code color}.
     * {@code glow} (when it has any alpha) rings it with a one-pixel halo, for lit icons. {@code fill} is ignored.
     */
    public static void draw(Gfx g, String name, float x, float y, float size, int color, float fill, int glow) {
        String n = PixelIcons.resolve(name);
        if (n == null) {
            n = PixelIcons.resolve("box");
            if (n == null) {
                return;
            }
        }
        SolTexture t = atlas();
        Integer slot = index.get(n);
        if (slot == null) {
            return;
        }
        int k = scale(g, size), px = PixelIcons.CELL * k;
        float sx = Math.round(g.d.sx(x + size / 2f) - px / 2f), sy = Math.round(g.d.sy(y + size / 2f) - px / 2f);
        if (Colors.a(glow) > 0) {
            int halo = Colors.fade(glow, 0.55f);
            quad(g, t, slot, sx - k, sy, px, halo);
            quad(g, t, slot, sx + k, sy, px, halo);
            quad(g, t, slot, sx, sy - k, px, halo);
            quad(g, t, slot, sx, sy + k, px, halo);
        }
        quad(g, t, slot, sx, sy, px, color);
    }

    /** Draws an icon at exactly {@code k} screen pixels per icon pixel with its top left at screen (sx, sy). */
    public static void drawAt(Gfx g, String name, float sx, float sy, int k, int color) {
        String n = PixelIcons.resolve(name);
        if (n == null) {
            return;
        }
        SolTexture t = atlas();
        Integer slot = index.get(n);
        if (slot != null) {
            quad(g, t, slot, Math.round(sx), Math.round(sy), PixelIcons.CELL * k, color);
        }
    }

    private static void quad(Gfx g, SolTexture t, int slot, float sx, float sy, int px, int color) {
        int ox = (slot % cols) * CELL, oy = (slot / cols) * CELL;
        g.imageScreen(t, sx, sy, sx + px, sy + px, ox / (float) w, oy / (float) h, (ox + PixelIcons.CELL) / (float) w,
                (oy + PixelIcons.CELL) / (float) h, color, false);
    }
}
