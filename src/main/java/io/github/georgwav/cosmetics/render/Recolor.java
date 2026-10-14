package io.github.georgwav.cosmetics.render;

import io.github.georgwav.cosmetics.config.CosmeticsConfig;
import io.github.georgwav.cosmetics.model.ColorStyle;
import io.github.georgwav.cosmetics.model.CosmeticOption;

/**
 * How a cosmetic's texture itself is recoloured, pixel by pixel: its hue turned, its saturation scaled (0 is black
 * and white) and its brightness scaled, then painted with the player's colour as its {@link ColorStyle} says
 * ({@link Paint}). Unlike the game's tint, which only multiplies (so dark parts and opposite colours turn black),
 * this turns red wings blue. The saturation setting pales the tint instead when the cosmetic is tinted
 * ({@link Tint}), so it isn't applied twice. Plain maths, no Minecraft types, so it is unit-tested;
 * {@link RecoloredTextures} makes the recoloured textures.
 *
 * @param hue        degrees the hue turns, -180..180
 * @param saturation saturation factor, 0..1
 * @param brightness brightness factor, 0..2
 * @param paint      how the player's colour is painted in
 * @param tint       the colour (RGB) painted into {@link Paint#COLORED} parts; 0 otherwise
 */
public record Recolor(int hue, float saturation, float brightness, Paint paint, int tint) {
    /** Textures with fewer coloured pixels than this share count as white ones (see {@link #paintFor}). */
    static final float WHITE_COSMETIC = 0.15f;
    /** Biggest texture (pixels) painted anew as the rainbow turns; bigger ones take it as {@link ColorStyle#WHOLE}. */
    static final int MAX_RAINBOW_PIXELS = 512 * 512;
    /** Degrees the rainbow turns between two paintings of a texture. */
    static final int RAINBOW_STEP = 10;

    /** How the player's colour gets into a texture. */
    public enum Paint {
        /** Not painted: drawn tinted with the colour (the game's multiply), or untinted. */
        NONE,
        /** Painted grey by brightness and drawn tinted, so every part takes the colour ({@link ColorStyle#WHOLE}). */
        GREY,
        /** The coloured parts painted in the colour, drawn untinted ({@link ColorStyle#COLORED_PARTS}). */
        COLORED
    }

    public Recolor(int hue, float saturation, float brightness) {
        this(hue, saturation, brightness, Paint.NONE, 0);
    }

    private static final Recolor AS_MADE = new Recolor(0, 1f, 1f);

    /**
     * The texture changes (hue, saturation, brightness) a cosmetic's settings ask for, or null to draw its texture
     * as it is. {@code defaultColor} is the cosmetic's own tint colour (RGB, white for none).
     */
    public static Recolor of(CosmeticsConfig.CosmeticOptions options, int allowed, int defaultColor) {
        if ((allowed & CosmeticOption.SATURATION.bit()) == 0) {
            return null;
        }
        boolean rainbow = options.rainbow && (allowed & CosmeticOption.RAINBOW.bit()) != 0;
        int tint = options.color != null && (allowed & CosmeticOption.TINT.bit()) != 0 ? options.color : defaultColor;
        boolean tinted = rainbow || (tint & 0xFFFFFF) != 0xFFFFFF;
        // Rounded so dragging a slider makes few distinct textures.
        int hue = Math.round(options.hue);
        float saturation = tinted ? 1f : Math.round(options.saturation * 100f) / 100f;
        float brightness = Math.round(options.brightness * 100f) / 100f;
        if (hue % 360 == 0 && saturation >= 1f && brightness == 1f) {
            return null;
        }
        return new Recolor(hue, saturation, brightness);
    }

    /**
     * How a part that takes the player's colour is painted. {@code picked}: the player picked a colour or the
     * rainbow (else the cosmetic shows as its pack made it, tinted with its own colour as the game tints). Left
     * unset ({@code style} null), a white cosmetic (fewer than {@link #WHITE_COSMETIC} of its texture's pixels
     * coloured) takes the colour all over and a coloured one in its coloured parts. A texture too big to paint
     * anew as the rainbow turns takes the rainbow all over.
     *
     * @param coloured share of the texture's pixels that are coloured (see {@link #coloured})
     * @param pixels   the texture's size in pixels
     */
    public static Paint paintFor(ColorStyle style, boolean picked, boolean rainbow, float coloured, int pixels) {
        if (!picked) {
            return Paint.NONE;
        }
        ColorStyle chosen = style != null ? style : coloured < WHITE_COSMETIC ? ColorStyle.WHOLE : ColorStyle.COLORED_PARTS;
        return switch (chosen) {
            case CLASSIC -> Paint.NONE;
            case WHOLE -> Paint.GREY;
            case COLORED_PARTS -> rainbow && pixels > MAX_RAINBOW_PIXELS ? Paint.GREY : Paint.COLORED;
        };
    }

    /**
     * {@code base} (null: the texture as made) painted {@code paint}'s way; {@code tint} is the player's colour (RGB)
     * for {@link Paint#COLORED}. Null when nothing changes the texture.
     */
    public static Recolor painted(Recolor base, Paint paint, int tint) {
        if (paint == Paint.NONE) {
            return base;
        }
        Recolor b = base != null ? base : AS_MADE;
        return new Recolor(b.hue, b.saturation, b.brightness, paint, paint == Paint.COLORED ? tint & 0xFFFFFF : 0);
    }

    /** A rainbow colour with its hue rounded to {@link #RAINBOW_STEP} degrees, so the painting changes in steps. */
    public static int rainbowStep(int rgb) {
        float[] hsv = Tint.rgbToHsv(rgb & 0xFFFFFF);
        float steps = 360f / RAINBOW_STEP;
        float hue = Math.round(hsv[0] * steps) / steps;
        return Tint.hsvToRgb(hue - (float) Math.floor(hue), hsv[1], hsv[2]);
    }

    /** Whether an ARGB pixel counts as coloured, not white, grey or black (and is seen: not see-through). */
    static boolean coloured(int argb) {
        if ((argb >>> 24) < 0x20) {
            return false;
        }
        float[] hsv = Tint.rgbToHsv(argb & 0xFFFFFF);
        return hsv[1] > 0.2f && hsv[2] > 0.15f;
    }

    /** One ARGB pixel recoloured; its alpha is kept. */
    public int apply(int argb) {
        return apply(argb, paint == Paint.COLORED ? Tint.rgbToHsv(tint) : null);
    }

    /** Every pixel of an ARGB image recoloured into {@code out}. */
    public void applyAll(int[] argb, int[] out) {
        float[] tintHsv = paint == Paint.COLORED ? Tint.rgbToHsv(tint) : null;
        for (int i = 0; i < argb.length; i++) {
            out[i] = apply(argb[i], tintHsv);
        }
    }

    private int apply(int argb, float[] tintHsv) {
        float[] hsv = Tint.rgbToHsv(argb & 0xFFFFFF);
        float h = hsv[0] + hue / 360f;
        h -= (float) Math.floor(h);
        float s = Math.min(1f, hsv[1] * saturation);
        float v = hsv[2] * brightness;
        if (v > 1f) {
            // Brighter than white: the colour washes out towards it, as an over-lit picture does.
            s *= 1f / v;
            v = 1f;
        }
        int alpha = argb & 0xFF000000;
        return switch (paint) {
            case NONE -> alpha | Tint.hsvToRgb(h, s, v);
            case GREY -> {
                // Grey by brightness; drawn tinted, white becomes the colour and black stays black.
                int g = Math.round(v * 255f);
                yield alpha | g << 16 | g << 8 | g;
            }
            // The colour's hue; the colour's saturation and brightness in as far as the pixel is coloured, so
            // white, grey and black stay and every shade of a colour keeps its place.
            case COLORED -> alpha | Tint.hsvToRgb(tintHsv[0], s * tintHsv[1], v * (1f - s * (1f - tintHsv[2])));
        };
    }
}
