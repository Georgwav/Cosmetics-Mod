package io.github.georgwav.cosmetics.sol.glint;

import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.render.RecoloredTextures;
import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Glint's outline round an enchanted item in inventories and the hotbar: the item's own picture (from the packs in
 * use, so a pack's sword gets its own shape) grown by one pixel all round, minus the item, in the glint's colour.
 * One small texture per item, repainted only when the colour changes. Items whose picture isn't a plain
 * {@code textures/item/<name>.png} (a shield, a block) get none.
 */
public final class GlintOutline {
    /** Items whose inventory picture has another name than the item. */
    private static final Map<String, String> PICTURE = Map.of("crossbow", "crossbow_standby", "clock", "clock_00",
            "compass", "compass_16", "recovery_compass", "recovery_compass_16");
    private static final Map<String, Entry> CACHE = new HashMap<>();

    private static final class Entry {
        SolTexture texture;
        boolean[] ring;
        int size;
        int colour;
    }

    private GlintOutline() {
    }

    /** Draws {@code stack}'s outline round its 16 x 16 slot picture at (x, y); false when it has none. */
    public static boolean draw(Canvas c, ItemStack stack, int x, int y, int argb) {
        String key = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        Entry e = CACHE.get(key);
        if (e == null && !CACHE.containsKey(key)) {
            e = make(key);
            CACHE.put(key, e);
        }
        if (e == null) {
            return false;
        }
        if (e.texture == null || e.texture.released()) {
            e.texture = new SolTexture("glint_outline/" + key.replace(':', '/'), e.size, e.size, false);
            e.colour = 0;
        }
        if (e.colour != argb) {
            int[] px = new int[e.size * e.size];
            for (int i = 0; i < px.length; i++) {
                px[i] = e.ring[i] ? argb : 0;
            }
            e.texture.upload(px);
            e.colour = argb;
        }
        c.blit(e.texture.id, x - 1, y - 1, 18, 18, e.size);
        return true;
    }

    /** The outline of an item's picture, or null when it has no plain picture of its own. */
    private static Entry make(String key) {
        int colon = key.indexOf(':');
        String ns = key.substring(0, colon), name = key.substring(colon + 1);
        try {
            int[][] read = RecoloredTextures.read(ns + ":textures/item/" + PICTURE.getOrDefault(name, name) + ".png");
            if (read == null) {
                return null;
            }
            // An animated picture is its frames one under another: the first one.
            int s = read[0][0];
            if (s < 8 || read[0][1] < s) {
                return null;
            }
            boolean[] solid = new boolean[s * s];
            for (int i = 0; i < solid.length; i++) {
                solid[i] = (read[1][i] >>> 24) > 24;
            }
            Entry e = new Entry();
            e.ring = ring(solid, s);
            e.size = s + 2 * Math.max(1, s / 16);
            return e;
        } catch (Exception ex) {
            CosmeticsMod.LOGGER.warn("[Sol] No glint outline for {}: {}", key, ex.toString());
            return null;
        }
    }

    /**
     * The pixels just outside a picture's shape ({@code s} x {@code s}): one inventory pixel (s / 16 of its own) wide,
     * along the sides only, as pixel art draws outlines. The result is (s + 2t) square, the picture in its middle.
     */
    static boolean[] ring(boolean[] solid, int s) {
        int t = Math.max(1, s / 16), size = s + 2 * t;
        boolean[] out = new boolean[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int sx = x - t, sy = y - t;
                if (in(solid, s, sx, sy)) {
                    continue;
                }
                boolean near = false;
                for (int k = 1; k <= t && !near; k++) {
                    near = in(solid, s, sx - k, sy) || in(solid, s, sx + k, sy) || in(solid, s, sx, sy - k) || in(solid, s, sx, sy + k);
                }
                out[y * size + x] = near;
            }
        }
        return out;
    }

    private static boolean in(boolean[] solid, int s, int x, int y) {
        return x >= 0 && y >= 0 && x < s && y < s && solid[y * s + x];
    }

    /** After a resource reload: the pictures may have changed. */
    public static void clear() {
        for (Entry e : CACHE.values()) {
            if (e != null && e.texture != null) {
                e.texture.release();
            }
        }
        CACHE.clear();
    }
}
