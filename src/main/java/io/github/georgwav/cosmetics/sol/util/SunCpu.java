package io.github.georgwav.cosmetics.sol.util;

/**
 * The prototypes' 2D Sol sun ({@code sunState} and {@code drawSunBack}) on a {@link PixelCanvas}, for everywhere the
 * sun is a picture rather than part of the sky: the menus' pixel suns, preset thumbnails, the logo's O. Line for line
 * the prototype's maths, so the pictures match its frames.
 */
public final class SunCpu {
    /** Which of the Sun lab's layers to draw. */
    public record Layers(boolean plasma, boolean corona, boolean rays, boolean dogs, boolean pillar, boolean shimmer) {
    }

    /** The menus' little suns ({@code SUN_L}). */
    public static final Layers MENU = new Layers(true, true, true, false, false, false);
    /** The showpiece frames ({@code HERO_LAYERS}). */
    public static final Layers HERO = new Layers(true, true, true, true, true, true);
    /** The logo's O ({@code LOGO_LAYERS}). */
    public static final Layers LOGO = new Layers(true, true, true, true, true, false);

    private static final int[] DOG_RINGS = {0xFFFF8A5C, 0xFFFFE28A, 0xFF8AB8FF};
    private static final int[] DOG_BARS = {0xFFFF9A6A, 0xFFFFFFFF, 0xFF9CC4FF};
    private static final float[][] CORONA = {{1.4f, 0.22f}, {1.85f, 0.12f}, {2.45f, 0.06f}};

    private SunCpu() {
    }

    /** {@code sunState}'s core colour for a height {@code e} (0 on the horizon .. 1 high), quantised to 1/20. */
    public static int core(float e) {
        float q = Math.round(e * 20f) / 20f;
        return q < 0.35f ? Colors.mix(0xFFFF6A2A, 0xFFFFD36B, q / 0.35f) : Colors.mix(0xFFFFD36B, 0xFFFFF8EA, (q - 0.35f) / 0.65f);
    }

    /** {@code sunState}'s halo: the theme's halo pulled toward a deep orange near the horizon. */
    public static int halo(int themeHalo, float e) {
        float q = Math.round(e * 20f) / 20f;
        return Colors.mix(themeHalo, 0xFFFF5A1F, (1f - q) * 0.45f);
    }

    /**
     * {@code drawSunBack(ctx, W, H, S, t)}: the sun at ({@code x}, {@code y}) with half-size {@code s} on a frame
     * {@code h} pixels tall, at height {@code e}, in {@code core}/{@code halo} colours. {@code pix} (16x16 ARGB) replaces
     * the plasma, as the album sun does; null for the plasma.
     */
    public static void drawBack(PixelCanvas g, float h, float x, float y, float s, float e, int core, int halo, Layers l,
                                float t, int[] pix) {
        float low = 1f - e;
        float px = s * 2f / 16f;
        g.save();
        g.setLighter(true);
        g.setGlobalAlpha(0.45f + 0.4f * low);
        float r = h * (0.3f + 0.18f * low);
        g.drawImage(Sprites.glow(halo), x - r, y - r, r * 2f, r * 2f, true);
        if (l.pillar() && low > 0.3f) {
            float k = (low - 0.3f) / 0.7f;
            g.setGlobalAlpha(k * k * 0.6f);
            g.drawImage(Sprites.glow(halo), x - s * 1.2f, y - h * 0.42f, s * 2.4f, h * 0.84f, true);
        }
        if (l.rays()) {
            float len0 = h * (0.3f + 0.22f * low);
            for (int i = 0; i < 14; i++) {
                float a = i / 14f * 6.283f + t * 0.035f;
                float lng = i % 2 == 1 ? 0.6f : 1f;
                float len = len0 * lng * (1f + 0.14f * (float) Math.sin(t * 0.9f + i * 1.7f));
                float w = s * (i % 2 == 1 ? 1.3f : 2f);
                g.setGlobalAlpha((0.2f + 0.16f * low) * (0.75f + 0.25f * (float) Math.sin(t * 1.3f + i)));
                g.save();
                g.translate(x, y);
                g.rotate(a);
                g.drawImage(Sprites.ray(halo), s * 0.5f, -w / 2f, len, w, true);
                g.restore();
            }
            for (int i = 0; i < 9; i++) {
                float a = i / 9f * 6.283f - t * 0.022f + 0.2f;
                float len = len0 * 0.55f * (1f + 0.2f * (float) Math.sin(t * 0.7f + i * 2.3f));
                float w = s * 0.9f;
                g.setGlobalAlpha(0.16f);
                g.save();
                g.translate(x, y);
                g.rotate(a);
                g.drawImage(Sprites.ray(core), s * 0.4f, -w / 2f, len, w, true);
                g.restore();
            }
        }
        if (l.dogs()) {
            float dA = Math.max(0f, 1f - Math.abs(e - 0.22f) / 0.4f);
            float dd = h * 0.3f;
            if (dA > 0f) {
                float lw = Math.max(1f, px * 0.7f);
                for (int o = -1; o <= 1; o++) {
                    g.setGlobalAlpha(0.13f * dA);
                    g.strokeCircle(x, y, dd + o * Math.max(1.5f, px * 0.8f), lw, DOG_RINGS[o + 1]);
                }
                for (int dir = -1; dir <= 1; dir += 2) {
                    float dx = x + dir * dd;
                    g.setGlobalAlpha(0.7f * dA);
                    float rr = s * 1.5f;
                    g.drawImage(Sprites.glow(core), dx - rr, y - rr, rr * 2f, rr * 2f, true);
                    g.save();
                    g.translate(dx, y);
                    g.scale(dir, 1f);
                    g.setGlobalAlpha(0.45f * dA);
                    g.drawImage(Sprites.glow(halo), 0f, -s * 0.22f, s * 4.5f, s * 0.44f, true);
                    g.restore();
                    g.setGlobalAlpha(0.9f * dA);
                    for (int k = 0; k < 3; k++) {
                        g.fillRect(dx + dir * (k - 1) * px - px / 2f, y - px * 1.5f, px, px * 3f, DOG_BARS[k]);
                    }
                }
            }
        }
        if (l.corona()) {
            for (float[] c : CORONA) {
                float hh = Math.round(s * c[0] / px) * px;
                g.setGlobalAlpha(c[1]);
                g.fillRect(x - hh, y - hh, hh * 2f, hh * 2f, halo);
            }
            float lw = Math.max(1f, px * 0.6f);
            for (float o : new float[]{0f, 0.5f}) {
                float ph = ((t * 0.4f + o) % 1f + 1f) % 1f;
                float hh = Math.round(s * (1.15f + ph * 2.4f) / px) * px;
                g.setGlobalAlpha((1f - ph) * (1f - ph) * 0.24f);
                g.strokeRect(x - hh, y - hh, hh * 2f, hh * 2f, lw, core);
            }
        }
        g.setGlobalAlpha(1f);
        g.setLighter(false);
        int white = 0xFFFFFFFF;
        int rim = Colors.mix(core, white, 0.55f);
        float shim = l.shimmer() ? low * low * low * 1.4f : 0f;
        for (int j = 0; j < 16; j++) {
            float off = shim > 0f ? Math.round((float) Math.sin(j * 0.9f + t * 7f) * shim * 2f) / 2f * px : 0f;
            for (int i = 0; i < 16; i++) {
                float d = Math.max(Math.abs(i - 7.5f), Math.abs(j - 7.5f));
                int c;
                if (pix != null) {
                    c = pix[j * 16 + i];
                } else if (!l.plasma()) {
                    c = d < 4f ? white : d > 7f ? rim : core;
                } else {
                    float v = 0.5f + 0.5f * ((float) (Math.sin(i * 0.9 + t * 1.9) * Math.sin(j * 1.1 - t * 1.4)) * 0.6f
                            + (float) Math.sin((i + j) * 0.55 + t * 2.3) * 0.4f);
                    c = d < 3.5f ? Colors.mix(white, core, v * 0.25f)
                            : d > 7f ? rim : Colors.mix(Colors.mix(core, white, 0.25f), halo, v * 0.35f * (d / 7f));
                    float sx = ((t * 0.18f) % 1f) * 10f + 3f;
                    if (Math.abs(i - sx) < 0.6f && Math.abs(j - 9.5f) < 0.6f) {
                        c = Colors.mix(c, halo, 0.45f);
                    }
                }
                g.fillRect(x - s + i * px + off, y - s + j * px, px + 0.5f, px + 0.5f, c | 0xFF000000);
            }
        }
        g.restore();
    }

    /** The quick menu's night icon: the vanilla moon with a cold glow (the prototype's {@code drawQSun} below the horizon). */
    public static void moon(PixelCanvas g) {
        g.save();
        g.setLighter(true);
        g.setGlobalAlpha(0.7f);
        g.drawImage(Sprites.glow(0xFFBCD0FF), 1f, 1f, 30f, 30f, true);
        g.setGlobalAlpha(1f);
        g.setLighter(false);
        g.fillRect(11, 11, 10, 10, 0xFFE8EEFC);
        g.fillRect(11, 11, 10, 1, 0xFFFFFFFF);
        g.fillRect(13, 13, 2, 2, 0xFFB5C0D8);
        g.fillRect(17, 15, 3, 2, 0xFFB5C0D8);
        g.fillRect(14, 18, 2, 1, 0xFFB5C0D8);
        g.fillRect(19, 19, 1, 1, 0xFFB5C0D8);
        g.restore();
    }
}
