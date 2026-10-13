package io.github.georgwav.cosmetics.render;

import io.github.georgwav.cosmetics.config.CosmeticsConfig;

/** The colour a cosmetic is tinted with. Plain maths, no Minecraft types, so it is unit-tested. */
public final class Tint {
    private Tint() {
    }

    /** ARGB tint: the chosen colour, or a slow trip around the colour wheel. */
    public static int argb(CosmeticsConfig.CosmeticOptions options, double seconds) {
        if (!options.rainbow) {
            return 0xFF000000 | options.color;
        }
        double hue = seconds * options.rainbowSpeed / 10.0;
        return 0xFF000000 | hsvToRgb((float) (hue - Math.floor(hue)), 0.65f, 1f);
    }

    /** RGB for a hue in [0, 1), saturation and value in [0, 1]. */
    static int hsvToRgb(float hue, float saturation, float value) {
        float h = hue * 6f;
        int sector = (int) h % 6;
        float f = h - (int) h;
        float p = value * (1f - saturation);
        float q = value * (1f - f * saturation);
        float t = value * (1f - (1f - f) * saturation);
        float r, g, b;
        switch (sector) {
            case 0 -> { r = value; g = t; b = p; }
            case 1 -> { r = q; g = value; b = p; }
            case 2 -> { r = p; g = value; b = t; }
            case 3 -> { r = p; g = q; b = value; }
            case 4 -> { r = t; g = p; b = value; }
            default -> { r = value; g = p; b = q; }
        }
        return Math.round(r * 255f) << 16 | Math.round(g * 255f) << 8 | Math.round(b * 255f);
    }
}
