package io.github.georgwav.cosmetics.render;

import io.github.georgwav.cosmetics.config.CosmeticsConfig;
import io.github.georgwav.cosmetics.model.CosmeticOption;

/**
 * How a cosmetic's texture itself is recoloured: its hue turned, its saturation scaled (0 is black and white) and
 * its brightness scaled, pixel by pixel, on top of the tint colour. Unlike the tint, which can only darken a
 * texture towards a colour, this turns red wings blue. Plain maths, no Minecraft types, so it is unit-tested;
 * {@link RecoloredTextures} makes the recoloured textures.
 *
 * @param hue        degrees the hue turns, -180..180
 * @param saturation saturation factor, 0..1
 * @param brightness brightness factor, 0..2
 */
public record Recolor(int hue, float saturation, float brightness) {

    /** The recolour a cosmetic's settings ask for, or null to draw its texture as it is. */
    public static Recolor of(CosmeticsConfig.CosmeticOptions options, int allowed) {
        if ((allowed & CosmeticOption.SATURATION.bit()) == 0) {
            return null;
        }
        // Rounded so dragging a slider makes few distinct textures.
        int hue = Math.round(options.hue);
        float saturation = Math.round(options.saturation * 100f) / 100f;
        float brightness = Math.round(options.brightness * 100f) / 100f;
        if (hue % 360 == 0 && saturation >= 1f && brightness == 1f) {
            return null;
        }
        return new Recolor(hue, saturation, brightness);
    }

    /** One ARGB pixel recoloured; its alpha is kept. */
    public int apply(int argb) {
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
        return argb & 0xFF000000 | Tint.hsvToRgb(h, s, v);
    }
}
