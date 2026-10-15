package io.github.georgwav.cosmetics.sol.glint;

/**
 * The glint's recolour, pure (unit tested): each pixel of the pack's glint keeps its brightness (luminance, scaled so
 * the brightest pixel is full) and alpha, and takes a new colour: one colour, two blended across the texture and
 * drifting with time, or a rainbow sweeping across it.
 */
public final class GlintRecolor {
    public enum Mode { SOLID, TWO, RAINBOW }

    private GlintRecolor() {
    }

    /**
     * Recolours {@code src} ({@code width} wide, ARGB) into {@code out}: {@code a} (and {@code b} for two colours),
     * at {@code strength} (1 = as bright as the original), the animation at {@code phase} (0..1).
     */
    public static void apply(int[] src, int[] out, int width, Mode mode, int a, int b, float strength, float phase) {
        float max = 1e-6f;
        for (int c : src) {
            max = Math.max(max, luminance(c));
        }
        for (int i = 0; i < src.length; i++) {
            int c = src[i];
            float lum = Math.min(1f, luminance(c) / max * strength);
            float u = (i % width) / (float) width;
            int col = switch (mode) {
                case SOLID -> a;
                case TWO -> mix(a, b, 0.5f - 0.5f * (float) Math.cos((u + phase) * Math.PI * 2.0));
                case RAINBOW -> hsv((u + phase) % 1f, 0.75f, 1f);
            };
            int r = Math.round(((col >> 16) & 255) * lum), g = Math.round(((col >> 8) & 255) * lum), bl = Math.round((col & 255) * lum);
            out[i] = (c & 0xFF000000) | (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, bl);
        }
    }

    static float luminance(int c) {
        return (0.2126f * ((c >> 16) & 255) + 0.7152f * ((c >> 8) & 255) + 0.0722f * (c & 255)) / 255f;
    }

    static int mix(int a, int b, float k) {
        int r = Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * k);
        int g = Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * k);
        int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * k);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    /** A colour from hue (0..1), saturation and value. */
    static int hsv(float h, float s, float v) {
        float hh = (h - (float) Math.floor(h)) * 6f;
        int i = (int) hh;
        float f = hh - i, p = v * (1 - s), q = v * (1 - s * f), t = v * (1 - s * (1 - f));
        float r, g, b;
        switch (i) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return 0xFF000000 | (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
    }
}
