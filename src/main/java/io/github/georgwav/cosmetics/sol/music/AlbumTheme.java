package io.github.georgwav.cosmetics.sol.music;

import io.github.georgwav.cosmetics.sol.util.Colors;

/**
 * An album cover's colours for the UI (pure; unit tested): the cover's pixels (48 x 48) grouped by k-means (6
 * clusters, 8 rounds, seeded evenly through the pixels sorted by brightness, so it's the same every time); the most
 * vivid big cluster is the accent, the next vivid one the glow, the darkest the panel; the accent is nudged until it
 * reads on the panel (3:1).
 */
public final class AlbumTheme {
    public static final int K = 6, ROUNDS = 8;

    /** The palette: {accent, glow, panel, rim}. */
    public record Palette(int accent, int glow, int panel, int rim) {
    }

    private AlbumTheme() {
    }

    public static Palette palette(int[] argb) {
        int n = argb.length;
        if (n == 0) {
            return new Palette(0xFFFFFFFF, 0xFFFFFFFF, 0xFF101014, 0xFFFFFFFF);
        }
        float[][] px = new float[n][3];
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            px[i][0] = Colors.r(argb[i]);
            px[i][1] = Colors.g(argb[i]);
            px[i][2] = Colors.b(argb[i]);
            order[i] = i;
        }
        java.util.Arrays.sort(order, (a, b) -> Float.compare(lum(px[a]), lum(px[b])));
        int k = Math.min(K, n);
        float[][] c = new float[k][];
        for (int j = 0; j < k; j++) {
            c[j] = px[order[(int) ((j + 0.5f) * n / k)]].clone();
        }
        int[] of = new int[n];
        int[] count = new int[k];
        for (int round = 0; round < ROUNDS; round++) {
            float[][] sum = new float[k][3];
            java.util.Arrays.fill(count, 0);
            for (int i = 0; i < n; i++) {
                int best = 0;
                float bd = Float.MAX_VALUE;
                for (int j = 0; j < k; j++) {
                    float d = dist(px[i], c[j]);
                    if (d < bd) {
                        bd = d;
                        best = j;
                    }
                }
                of[i] = best;
                count[best]++;
                for (int q = 0; q < 3; q++) {
                    sum[best][q] += px[i][q];
                }
            }
            for (int j = 0; j < k; j++) {
                if (count[j] > 0) {
                    for (int q = 0; q < 3; q++) {
                        c[j][q] = sum[j][q] / count[j];
                    }
                }
            }
        }
        // Vivid: saturation times brightness, counting only clusters with a share of the cover.
        int accent = -1, glow = -1, dark = 0;
        float bestV = -1, secondV = -1;
        for (int j = 0; j < k; j++) {
            if (lum(c[j]) < lum(c[dark])) {
                dark = j;
            }
            if (count[j] < n / 40) {
                continue;
            }
            float v = vivid(c[j]);
            if (v > bestV) {
                secondV = bestV;
                glow = accent;
                bestV = v;
                accent = j;
            } else if (v > secondV) {
                secondV = v;
                glow = j;
            }
        }
        if (accent < 0) {
            accent = 0;
        }
        if (glow < 0) {
            glow = accent;
        }
        int panel = Colors.mix(rgb(c[dark]), 0xFF000000, 0.55f);
        int a = Colors.nudgeForContrast(rgb(c[accent]), panel, 3.0);
        int g = Colors.nudgeForContrast(rgb(c[glow]), panel, 2.0);
        return new Palette(a, g, panel, Colors.mix(a, 0xFFFFFFFF, 0.35f));
    }

    /**
     * The album's colours as edits to a theme, made the way the player's own colour edits are: exact at the theme's
     * own moment and as much lighter or darker at the others as the theme is. The UI's accent and panel (the glass
     * keeps the theme's darkness); with {@code sky}, the whole world too: the sky and fog, the sun and its halo, an
     * eclipse's corona, the light, the clouds, the particles, the aurora and the nebulae.
     */
    public static io.github.georgwav.cosmetics.sol.theme.Customisation colours(Palette p, boolean sky) {
        io.github.georgwav.cosmetics.sol.theme.Customisation c = io.github.georgwav.cosmetics.sol.theme.Customisation.NONE
                .withColour("accent", p.accent()).withColour("panel", p.panel());
        if (!sky) {
            return c;
        }
        return c.withColour("sky_top", Colors.mix(p.panel(), 0xFF000000, 0.35f))
                .withColour("sky_middle", Colors.mix(p.panel(), p.glow(), 0.45f))
                .withColour("sky_horizon", Colors.mix(p.glow(), p.accent(), 0.35f))
                .withColour("sky_glow", p.accent())
                .withColour("fog", Colors.mix(p.glow(), p.panel(), 0.4f))
                .withColour("sun_core", Colors.mix(p.accent(), 0xFFFFFFFF, 0.7f))
                .withColour("sun_halo", p.accent())
                .withColour("corona", p.accent())
                .withColour("light", Colors.mix(p.accent(), 0xFFFFFFFF, 0.55f))
                .withColour("sunlight", Colors.mix(p.accent(), 0xFFFFFFFF, 0.35f))
                .withColour("clouds", Colors.mix(p.glow(), 0xFFFFFFFF, 0.5f))
                .withColour("particles", p.glow())
                .withColour("aurora_1", p.accent())
                .withColour("aurora_2", p.glow())
                .withColour("nebula", p.accent());
    }

    /** {@code e} in the album's colours, every time of day of it. */
    public static io.github.georgwav.cosmetics.sol.theme.ThemeRegistry.Entry apply(
            io.github.georgwav.cosmetics.sol.theme.ThemeRegistry.Entry e, Palette p, boolean sky) {
        return io.github.georgwav.cosmetics.sol.theme.ThemeState.customised(e, colours(p, sky)).map(s -> rim(s, p));
    }

    /** One version of a theme in the album's colours (its own reference). */
    public static io.github.georgwav.cosmetics.sol.theme.ThemeSpec apply(io.github.georgwav.cosmetics.sol.theme.ThemeSpec spec,
                                                                        Palette p, boolean sky) {
        return rim(colours(p, sky).apply(spec), p);
    }

    /** The glass's rim in the album's light colour, as bright as the theme's own. */
    private static io.github.georgwav.cosmetics.sol.theme.ThemeSpec rim(io.github.georgwav.cosmetics.sol.theme.ThemeSpec spec,
                                                                       Palette p) {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        com.google.gson.JsonObject ui = new com.google.gson.JsonObject();
        ui.addProperty("rim", hex(p.rim()));
        ui.addProperty("rimAlpha", Colors.a(spec.ui().rim()) / 255f);
        json.add("ui", ui);
        return io.github.georgwav.cosmetics.sol.theme.ThemeParser.parse(spec.id(), json, spec, w -> {
        });
    }

    private static String hex(int argb) {
        return String.format(java.util.Locale.ROOT, "#%06x", argb & 0xFFFFFF);
    }

    private static float lum(float[] p) {
        return 0.2126f * p[0] + 0.7152f * p[1] + 0.0722f * p[2];
    }

    private static float vivid(float[] p) {
        float max = Math.max(p[0], Math.max(p[1], p[2])), min = Math.min(p[0], Math.min(p[1], p[2]));
        float sat = max <= 0 ? 0 : (max - min) / max;
        return sat * (max / 255f);
    }

    private static float dist(float[] a, float[] b) {
        float dr = a[0] - b[0], dg = a[1] - b[1], db = a[2] - b[2];
        return dr * dr + dg * dg + db * db;
    }

    private static int rgb(float[] p) {
        return Colors.argb(255, Math.round(p[0]), Math.round(p[1]), Math.round(p[2]));
    }
}
