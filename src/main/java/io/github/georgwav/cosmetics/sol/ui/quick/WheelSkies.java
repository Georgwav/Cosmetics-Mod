package io.github.georgwav.cosmetics.sol.ui.quick;

import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.sol.theme.ThemeRegistry;
import io.github.georgwav.cosmetics.sol.util.MiniSky;
import io.github.georgwav.cosmetics.sol.util.SolExecutors;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Each theme's sky as the presets wheel shows it in its sector: {@link MiniSky} without its particles (the wheel draws
 * those live), drawn on the CPU worker and uploaded on the render thread. The sector pointed at is drawn again about a
 * dozen times a second, so its sun, aurora, galaxy and mist move; the rest keep their first picture. 168 x 128, about
 * a sector's shape.
 */
final class WheelSkies {
    static final int W = 168, H = 128;
    /** Seconds between pictures of the live sector. */
    private static final float LIVE_STEP = 0.08f;
    private final Map<String, SolTexture> textures = new HashMap<>();
    private final Map<String, int[]> ready = new ConcurrentHashMap<>();
    private final Set<String> busy = ConcurrentHashMap.newKeySet();
    /** Themes whose picture couldn't be drawn (not tried again until the themes reload). */
    private final Set<String> failed = ConcurrentHashMap.newKeySet();
    private final Map<String, Float> drawnAt = new HashMap<>();
    private int generation = -1;

    /**
     * The theme's sky, or null until its first picture is ready. {@code t}: the wheel's clock (seconds); {@code live}:
     * keep drawing it as {@code t} moves on.
     */
    SolTexture get(ThemeRegistry.Entry e, float t, boolean live) {
        if (generation != ThemeRegistry.generation()) {
            release();
            generation = ThemeRegistry.generation();
        }
        String id = e.id();
        SolTexture tex = textures.get(id);
        int[] px = ready.remove(id);
        if (px != null) {
            if (tex == null || tex.released()) {
                tex = SolTexture.of("wheel_sky_" + id.replace(':', '_'), W, H, true, px);
                textures.put(id, tex);
            } else {
                tex.upload(px);
            }
        }
        boolean first = tex == null && !drawnAt.containsKey(id);
        Float at = drawnAt.get(id);
        if ((first || live && at != null && t - at >= LIVE_STEP) && !failed.contains(id) && busy.add(id)) {
            drawnAt.put(id, t);
            SolExecutors.CPU.execute(() -> {
                try {
                    ready.put(id, MiniSky.render(e.base(), id, W, H, t, false).toArgb(null));
                } catch (RuntimeException ex) {
                    failed.add(id);
                    io.github.georgwav.cosmetics.CosmeticsMod.LOGGER.warn("[Sol] Couldn't draw the {} sky for the presets wheel", id, ex);
                } finally {
                    busy.remove(id);
                }
            });
        }
        return tex == null || tex.released() ? null : tex;
    }

    void release() {
        textures.values().forEach(SolTexture::release);
        textures.clear();
        ready.clear();
        drawnAt.clear();
        failed.clear();
    }
}
