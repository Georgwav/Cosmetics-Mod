package io.github.georgwav.cosmetics.sol.atmosphere;

import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.sol.render.WorldDraw;
import io.github.georgwav.cosmetics.sol.theme.ThemeSpec;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.sol.util.Mulberry32;
import io.github.georgwav.cosmetics.sol.util.NebulaBake;
import io.github.georgwav.cosmetics.sol.util.SolExecutors;

import java.util.List;

/**
 * The nebula: a cloud of glowing gas across a third of the sky, in the theme's cloud colours, with billows, bright
 * filaments, dark dust lanes and a hot core (see {@link NebulaBake}). It is painted once per set of colours on Sol's
 * CPU thread and laid over the sky as a curved sheet, so it keeps its shape anywhere on screen and turns with the
 * night sky. Young stars twinkle inside it, with the long spikes of starlight through a lens. Render thread only.
 */
final class Nebula {
    private static final int TEX_W = 768, TEX_H = 384;
    /** The part of the showpiece frame it covers (fractions across and down; the horizon is at 0.64). */
    private static final float X0 = -0.4f, X1 = 1.4f, Y0 = -0.3f, Y1 = 0.7f;
    /** The sheet's vertices across and down. */
    private static final int GX = 36, GY = 18;
    private static final float ASPECT = 16f / 9f;
    private static final int STARS = 22;

    private static SolTexture cloud;
    private static String shownKey;
    private static String askedKey;
    private static volatile String readyKey;
    private static volatile int[] ready;

    private static final float[] out = new float[3];
    private static final float[] starX = new float[STARS], starY = new float[STARS], starS = new float[STARS];
    private static final int[] starC = new int[STARS];
    private static String starsKey;

    private Nebula() {
    }

    /** Draws the nebula facing {@code az} (degrees), {@code night} 0..1. */
    static void render(List<ThemeSpec.Nebula> clouds, float az, float t, float night) {
        String key = key(clouds);
        SolTexture tex = texture(clouds, key);
        if (tex != null) {
            // Gas doesn't move, but it seems to breathe as the eye adjusts.
            float a = night * (0.86f + 0.14f * (float) Math.sin(t * 0.21f));
            int c = Colors.withAlpha(0xFFFFFFFF, Math.min(1f, a));
            WorldDraw.begin(WorldDraw.SKY_ADD, tex);
            for (int j = 0; j < GY; j++) {
                for (int i = 0; i < GX; i++) {
                    corner(i, j, az, c);
                    corner(i, j + 1, az, c);
                    corner(i + 1, j + 1, az, c);
                    corner(i + 1, j, az, c);
                }
            }
            WorldDraw.end();
        }
        stars(clouds, key, az, t, night);
    }

    private static void corner(int i, int j, float az, int c) {
        float u = i / (float) GX, v = j / (float) GY;
        Extras.directionOf(X0 + u * (X1 - X0), Y0 + v * (Y1 - Y0), az, true, out);
        SkyDraw.sphere(out[0], out[1], out[2], u, v, c);
    }

    /** The young stars: a few bright ones in the gas, twinkling, each with a halo and four long spikes. */
    private static void stars(List<ThemeSpec.Nebula> clouds, String key, float az, float t, float night) {
        if (!key.equals(starsKey)) {
            starsKey = key;
            Mulberry32 r = new Mulberry32(key.hashCode());
            for (int k = 0; k < STARS; k++) {
                ThemeSpec.Nebula n = clouds.get(k % clouds.size());
                double a = r.next() * Math.PI * 2, d = Math.sqrt(r.next()) * n.r() * 0.9;
                starX[k] = n.x() + (float) (Math.cos(a) * d);
                starY[k] = n.y() + (float) (Math.sin(a) * d * ASPECT);
                starS[k] = 0.004f + 0.01f * (float) Math.pow(r.next(), 3);
                starC[k] = Colors.mix(n.color(), 0xFFFFFFFF, 0.65f + 0.3f * (float) r.next());
            }
        }
        WorldDraw.begin(WorldDraw.SKY_ADD, SkyDraw.glow());
        for (int k = 0; k < STARS; k++) {
            float tw = 0.55f + 0.45f * (float) Math.sin(t * (0.8f + k * 0.13f) + k * 2.1f);
            float a = night * tw;
            if (a <= 0.02f) {
                continue;
            }
            Extras.directionOf(starX[k], starY[k], az, true, out);
            SkyDraw.billboard(out[0], out[1], out[2]);
            float s = starS[k];
            SkyDraw.glowAt(0f, 0f, s * 3f, s * 3f, Colors.withAlpha(starC[k], 0.5f * a));
            SkyDraw.glowAt(0f, 0f, s, s, Colors.withAlpha(0xFFFFFFFF, 0.9f * a));
            if (s > 0.008f) {
                SkyDraw.glowAt(0f, 0f, s * 7f, s * 0.22f, Colors.withAlpha(starC[k], 0.55f * a));
                SkyDraw.glowAt(0f, 0f, s * 0.22f, s * 7f, Colors.withAlpha(starC[k], 0.55f * a));
            }
        }
        WorldDraw.end();
    }

    private static String key(List<ThemeSpec.Nebula> clouds) {
        StringBuilder b = new StringBuilder();
        for (ThemeSpec.Nebula n : clouds) {
            b.append(n.x()).append(',').append(n.y()).append(',').append(n.r()).append(',').append(n.color()).append(';');
        }
        return b.toString();
    }

    /** The painted cloud for these colours: asked for on the CPU thread, shown once it's ready (null until then). */
    private static SolTexture texture(List<ThemeSpec.Nebula> clouds, String key) {
        if (key.equals(shownKey) && cloud != null && !cloud.released()) {
            return cloud;
        }
        int[] px = ready;
        if (px != null && key.equals(readyKey)) {
            if (cloud == null || cloud.released()) {
                cloud = SolTexture.of("nebula", TEX_W, TEX_H, true, px);
            } else {
                cloud.upload(px);
            }
            shownKey = key;
            ready = null;
            return cloud;
        }
        if (!key.equals(askedKey)) {
            askedKey = key;
            int n = clouds.size();
            float[] cx = new float[n], cy = new float[n], cr = new float[n];
            int[] colors = new int[n];
            for (int k = 0; k < n; k++) {
                ThemeSpec.Nebula c = clouds.get(k);
                cx[k] = c.x();
                cy[k] = c.y();
                cr[k] = c.r();
                colors[k] = c.color();
            }
            SolExecutors.CPU.execute(() -> {
                try {
                    int[] made = NebulaBake.bake(TEX_W, TEX_H, X0, X1, Y0, Y1, ASPECT, cx, cy, cr, colors, 1729);
                    ready = made;
                    readyKey = key;
                } catch (RuntimeException e) {
                    CosmeticsMod.LOGGER.warn("[Sol] Couldn't paint the nebula", e);
                }
            });
        }
        // The last one stays up while the new colours are painted.
        return cloud != null && !cloud.released() ? cloud : null;
    }
}
