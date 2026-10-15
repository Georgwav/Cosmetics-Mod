package io.github.georgwav.cosmetics.sol.ui.kit;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.sol.util.Colors;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * The Sol line icons in the UI: every icon of the set (see {@link IconRaster}) rasterised once per pixel size into a
 * small atlas, its soft fill and its lines in cells of their own, and drawn on whole pixels so it stays sharp.
 * Grey when off; the theme accent with a glow when on; the soft fill at 16%, 30% when active. Render thread only.
 */
public final class Icons {
    /** The soft fill's strength off and on (the prototype's 16% and 30%). */
    public static final float FILL = 0.16f, FILL_ON = 0.30f;

    private static Map<String, IconRaster.Shape> shapes;
    private static final Map<Integer, Atlas> atlases = new HashMap<>();

    private record Atlas(SolTexture texture, Map<String, Integer> index, int cell, int cols, int w, int h) {
    }

    private Icons() {
    }

    private static Map<String, IconRaster.Shape> shapes() {
        if (shapes == null) {
            shapes = new HashMap<>();
            try (InputStream in = Icons.class.getResourceAsStream("/assets/cosmeticsmod/sol/icons.json")) {
                if (in != null) {
                    JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                    for (String name : o.keySet()) {
                        shapes.put(name, IconRaster.parse(o.getAsJsonArray(name)));
                    }
                }
            } catch (Exception e) {
                CosmeticsMod.LOGGER.warn("[Sol] Couldn't read the icon set", e);
            }
        }
        return shapes;
    }

    public static boolean has(String name) {
        return shapes().containsKey(name);
    }

    /** Frees the atlases (the window was resized a lot, or resources reloaded); they're made again on use. */
    public static void release() {
        atlases.values().forEach(a -> a.texture().release());
        atlases.clear();
    }

    private static Atlas atlas(int px) {
        Atlas a = atlases.get(px);
        if (a != null && !a.texture().released()) {
            return a;
        }
        Map<String, IconRaster.Shape> all = shapes();
        int cell = px + 2;
        int count = all.size() * 2;
        int cols = Math.max(1, Math.min(count, 2048 / cell));
        int rows = (count + cols - 1) / cols;
        int w = cols * cell, h = Math.max(1, rows * cell);
        int[] argb = new int[w * h];
        float[] fill = new float[px * px], stroke = new float[px * px];
        Map<String, Integer> index = new HashMap<>();
        int i = 0;
        for (Map.Entry<String, IconRaster.Shape> e : all.entrySet()) {
            IconRaster.raster(e.getValue(), px, fill, stroke);
            index.put(e.getKey(), i);
            blit(argb, w, cell, cols, i, px, fill);
            blit(argb, w, cell, cols, i + 1, px, stroke);
            i += 2;
        }
        a = new Atlas(SolTexture.of("icons_" + px, w, h, false, argb), index, cell, cols, w, h);
        atlases.put(px, a);
        return a;
    }

    private static void blit(int[] argb, int w, int cell, int cols, int slot, int px, float[] alpha) {
        int ox = (slot % cols) * cell + 1, oy = (slot / cols) * cell + 1;
        for (int y = 0; y < px; y++) {
            for (int x = 0; x < px; x++) {
                int a = Math.round(alpha[y * px + x] * 255f);
                argb[(oy + y) * w + ox + x] = (a << 24) | 0xFFFFFF;
            }
        }
    }

    /**
     * Draws {@code name} in a {@code size} x {@code size} design-pixel square at (x, y): lines in {@code color}, the
     * soft fill in the same colour at {@code fill} (see {@link #FILL}); {@code glow} > 0 adds the theme glow behind
     * it (the prototype's drop-shadow on lit icons).
     */
    public static void draw(Gfx g, String name, float x, float y, float size, int color, float fill, int glow) {
        Map<String, IconRaster.Shape> all = shapes();
        if (!all.containsKey(name)) {
            name = all.containsKey("cube") ? "cube" : null;
            if (name == null) {
                return;
            }
        }
        int px = Math.max(8, Math.round(size * g.d.u));
        Atlas a = atlas(px);
        Integer slot = a.index().get(name);
        if (slot == null) {
            return;
        }
        // On whole pixels, centred where the design square is.
        float sx = Math.round(g.d.sx(x + size / 2f) - px / 2f), sy = Math.round(g.d.sy(y + size / 2f) - px / 2f);
        if (Colors.a(glow) > 0) {
            g.glowSprite(x + size / 2f, y + size / 2f, size * 0.75f, size * 0.75f, glow);
        }
        if (fill > 0f) {
            quad(g, a, slot, sx, sy, px, Colors.fade(color, fill));
        }
        quad(g, a, slot + 1, sx, sy, px, color);
    }

    private static void quad(Gfx g, Atlas a, int slot, float sx, float sy, int px, int color) {
        int ox = (slot % a.cols()) * a.cell() + 1, oy = (slot / a.cols()) * a.cell() + 1;
        g.imageScreen(a.texture(), sx, sy, sx + px, sy + px, ox / (float) a.w(), oy / (float) a.h(), (ox + px) / (float) a.w(),
                (oy + px) / (float) a.h(), color, false);
    }
}
