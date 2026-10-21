package io.github.georgwav.cosmetics.render;

import io.github.georgwav.cosmetics.config.CosmeticsConfig;
import io.github.georgwav.cosmetics.model.BakedCosmetic;
import io.github.georgwav.cosmetics.model.CosmeticDefinition;
import io.github.georgwav.cosmetics.model.CosmeticOption;

/** The colour a cosmetic is tinted with. Plain maths, no Minecraft types, so it is unit-tested. */
public final class Tint {
    /** Saturation of the rainbow at full strength. */
    static final float RAINBOW_SATURATION = 0.65f;

    /**
     * The theme's colours (RGB) for cosmetics set to follow the theme: 0 the accent, 1 the glow. Set by Sol
     * Client; null (no theme) in the tests.
     */
    public static volatile java.util.function.IntUnaryOperator theme;
    /**
     * What every cosmetic follows ("accent", "glow" or null: their own colours), {@link CosmeticsConfig#themeColorAll};
     * set by Sol Client, null in the tests.
     */
    public static volatile java.util.function.Supplier<String> every;

    private Tint() {
    }

    /**
     * The colour the player gave the cosmetic (RGB), or null for its own: the theme's while it follows the theme,
     * else the picked one, and nothing when the cosmetic doesn't take a tint.
     */
    public static Integer color(CosmeticsConfig.CosmeticOptions options, int allowed) {
        if ((allowed & CosmeticOption.TINT.bit()) == 0) {
            return null;
        }
        java.util.function.IntUnaryOperator t = theme;
        String follow = follows(options);
        if (follow != null && t != null) {
            return t.applyAsInt("glow".equals(follow) ? 1 : 0) & 0xFFFFFF;
        }
        return options.color;
    }

    /**
     * Which of the theme's colours the cosmetic follows ("accent" or "glow"), or null: its own setting, else (with no
     * colour picked and no rainbow) what every cosmetic follows.
     */
    public static String follows(CosmeticsConfig.CosmeticOptions options) {
        String own = options.themeColor;
        if (own != null) {
            return own.equals("own") ? null : own;
        }
        java.util.function.Supplier<String> all = every;
        return options.color == null && !options.rainbow && all != null ? all.get() : null;
    }

    /** ARGB tint for a cosmetic that allows every option and has no default colour of its own. */
    public static int argb(CosmeticsConfig.CosmeticOptions options, double seconds) {
        return argb(options, CosmeticOption.ALL, CosmeticDefinition.WHITE, seconds);
    }

    /** ARGB tint for a cosmetic, honouring which colour options it allows. */
    public static int argb(CosmeticsConfig.CosmeticOptions options, BakedCosmetic cosmetic, double seconds) {
        return argb(options, CosmeticsConfig.get().allowedOptions(cosmetic), cosmetic.defaultColor(), seconds);
    }

    /**
     * The player's colour (or the cosmetic's default), or a slow trip around the colour wheel, then made
     * paler by the saturation setting. Options the cosmetic doesn't allow are ignored.
     */
    static int argb(CosmeticsConfig.CosmeticOptions options, int allowed, int defaultColor, double seconds) {
        float saturation = (allowed & CosmeticOption.SATURATION.bit()) != 0 ? options.saturation : 1f;
        if (options.rainbow && (allowed & CosmeticOption.RAINBOW.bit()) != 0) {
            double hue = seconds * options.rainbowSpeed / 10.0;
            return 0xFF000000 | hsvToRgb((float) (hue - Math.floor(hue)), RAINBOW_SATURATION * saturation, 1f);
        }
        Integer picked = color(options, allowed);
        int rgb = picked != null ? picked : defaultColor;
        if (saturation < 1f) {
            rgb = desaturate(rgb, saturation);
        }
        return 0xFF000000 | rgb;
    }

    /**
     * The colour (RGB) a cosmetic's particles take when the player gave the cosmetic a colour or rainbow, or null
     * to keep the particles' own colours (the default, so they look as their creator made them).
     */
    public static Integer particleColor(CosmeticsConfig.CosmeticOptions options, BakedCosmetic cosmetic, double seconds) {
        return particleColor(options, CosmeticsConfig.get().allowedOptions(cosmetic), cosmetic.defaultColor(), seconds);
    }

    static Integer particleColor(CosmeticsConfig.CosmeticOptions options, int allowed, int defaultColor, double seconds) {
        return picked(options, allowed) ? argb(options, allowed, defaultColor, seconds) & 0xFFFFFF : null;
    }

    /** Whether the player picked a colour or the rainbow for the cosmetic (and it takes them). */
    public static boolean picked(CosmeticsConfig.CosmeticOptions options, int allowed) {
        return rainbow(options, allowed) || color(options, allowed) != null;
    }

    /** Whether the cosmetic turns through the rainbow. */
    public static boolean rainbow(CosmeticsConfig.CosmeticOptions options, int allowed) {
        return options.rainbow && (allowed & CosmeticOption.RAINBOW.bit()) != 0;
    }

    /** Scales an RGB colour's saturation (HSV) by {@code factor}, keeping its hue and brightness. */
    static int desaturate(int rgb, float factor) {
        float r = (rgb >> 16 & 0xFF) / 255f;
        float g = (rgb >> 8 & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        if (max <= 0f || max == min) {
            return rgb & 0xFFFFFF;
        }
        float delta = max - min;
        float hue;
        if (max == r) {
            hue = ((g - b) / delta) / 6f;
        } else if (max == g) {
            hue = ((b - r) / delta + 2f) / 6f;
        } else {
            hue = ((r - g) / delta + 4f) / 6f;
        }
        hue -= (float) Math.floor(hue);
        return hsvToRgb(hue, delta / max * factor, max);
    }

    /** {hue, saturation, value} of an RGB colour, each in [0, 1] (hue in [0, 1)). */
    public static float[] rgbToHsv(int rgb) {
        float r = (rgb >> 16 & 0xFF) / 255f;
        float g = (rgb >> 8 & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        float hue = 0f;
        if (delta > 0f) {
            if (max == r) {
                hue = ((g - b) / delta) / 6f;
            } else if (max == g) {
                hue = ((b - r) / delta + 2f) / 6f;
            } else {
                hue = ((r - g) / delta + 4f) / 6f;
            }
            hue -= (float) Math.floor(hue);
        }
        return new float[]{hue, max <= 0f ? 0f : delta / max, max};
    }

    /** RGB for a hue in [0, 1), saturation and value in [0, 1]. */
    public static int hsvToRgb(float hue, float saturation, float value) {
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
