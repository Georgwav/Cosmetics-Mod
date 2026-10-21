package io.github.georgwav.cosmetics.sol.ui.quick;

import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.sol.theme.ThemeRegistry;
import io.github.georgwav.cosmetics.sol.util.MiniSky;
import io.github.georgwav.cosmetics.sol.util.SolExecutors;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Each theme's showpiece as a small card picture (the prototypes' preset thumbnails): drawn by {@link MiniSky} on
 * the CPU worker, uploaded on the render thread when ready, kept until the screen closes. The pixel UI shows them at
 * 2 design pixels per picture pixel, nearest filtered (53 x 22 for the quick menu's strip, 106 x 44 shown).
 */
public final class PresetThumbs {
    public static final int W = 224, H = 92;
    public final int w, h;
    private final boolean linear;
    private final Map<String, int[]> ready = new ConcurrentHashMap<>();
    private final Map<String, SolTexture> textures = new HashMap<>();
    private final Map<String, Boolean> asked = new HashMap<>();
    private int generation = -1;

    /** The default size (224 x 92, smooth). */
    public PresetThumbs() {
        this(W, H, true);
    }

    /** Pictures {@code w} x {@code h}; {@code linear} false for pixel pictures. */
    public PresetThumbs(int w, int h, boolean linear) {
        this.w = w;
        this.h = h;
        this.linear = linear;
    }

    /** The theme's picture, or null while it is being drawn. */
    public SolTexture get(ThemeRegistry.Entry e) {
        if (generation != ThemeRegistry.generation()) {
            release();
            generation = ThemeRegistry.generation();
        }
        SolTexture t = textures.get(e.id());
        if (t != null && !t.released()) {
            return t;
        }
        int[] px = ready.remove(e.id());
        if (px != null) {
            t = SolTexture.of("thumb_" + w + "_" + e.id().replace(':', '_'), w, h, linear, px);
            textures.put(e.id(), t);
            return t;
        }
        if (asked.putIfAbsent(e.id(), Boolean.TRUE) == null) {
            SolExecutors.CPU.execute(() -> {
                try {
                    ready.put(e.id(), MiniSky.render(e.base(), e.id(), w, h, 2f).toArgb(null));
                } catch (RuntimeException ex) {
                    io.github.georgwav.cosmetics.CosmeticsMod.LOGGER.warn("[Sol] Couldn't draw the {} preset picture", e.id(), ex);
                }
            });
        }
        return null;
    }

    public void release() {
        textures.values().forEach(SolTexture::release);
        textures.clear();
        asked.clear();
        ready.clear();
    }
}
