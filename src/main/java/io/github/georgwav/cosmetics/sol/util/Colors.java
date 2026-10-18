package io.github.georgwav.cosmetics.sol.util;

import java.util.Locale;

/**
 * Colour maths for Sol: ARGB ints, hex parsing, mixing, HSL, luminance and contrast. Plain Java, no Minecraft
 * types, so the theme engine and the UI kit share it and it is unit-tested. The HSL conversions and the "colour
 * on top of the accent" rule are the prototypes' own ({@code toHsl}, {@code fromHsl}, {@code onColour}).
 */
public final class Colors {
    public static final int WHITE = 0xFFFFFFFF;
    public static final int BLACK = 0xFF000000;
    public static final int TRANSPARENT = 0;
    /** Text on a light accent: the prototype's near-black. */
    public static final int ON_LIGHT = 0xFF0C0C10;

    private Colors() {
    }

    public static int a(int c) {
        return c >>> 24;
    }

    public static int r(int c) {
        return (c >> 16) & 0xFF;
    }

    public static int g(int c) {
        return (c >> 8) & 0xFF;
    }

    public static int b(int c) {
        return c & 0xFF;
    }

    public static int argb(int a, int r, int g, int b) {
        return (clamp8(a) << 24) | (clamp8(r) << 16) | (clamp8(g) << 8) | clamp8(b);
    }

    public static int rgb(int r, int g, int b) {
        return argb(255, r, g, b);
    }

    /** From 0..1 floats. */
    public static int argbF(float a, float r, float g, float b) {
        return argb(Math.round(a * 255f), Math.round(r * 255f), Math.round(g * 255f), Math.round(b * 255f));
    }

    private static int clamp8(int v) {
        return v < 0 ? 0 : Math.min(255, v);
    }

    /** The colour with its alpha replaced (0..1). */
    public static int withAlpha(int c, float alpha) {
        return (clamp8(Math.round(alpha * 255f)) << 24) | (c & 0xFFFFFF);
    }

    /** The colour with its alpha multiplied (0..1). */
    public static int fade(int c, float alpha) {
        return (clamp8(Math.round(a(c) * alpha)) << 24) | (c & 0xFFFFFF);
    }

    /**
     * Parses "#rgb", "#rrggbb", "#aarrggbb", "rrggbb" or "rgba(r,g,b,a)" (the prototypes' panel colours). Anything
     * else gives {@code fallback}.
     */
    public static int parse(String text, int fallback) {
        Integer parsed = tryParse(text);
        return parsed != null ? parsed : fallback;
    }

    /** As {@link #parse}, or null when the text isn't a colour. */
    public static Integer tryParse(String text) {
        if (text == null) {
            return null;
        }
        String s = text.trim().toLowerCase(Locale.ROOT);
        try {
            if (s.startsWith("rgba(") && s.endsWith(")") || s.startsWith("rgb(") && s.endsWith(")")) {
                String[] parts = s.substring(s.indexOf('(') + 1, s.length() - 1).split(",");
                if (parts.length < 3 || parts.length > 4) {
                    return null;
                }
                int r = Integer.parseInt(parts[0].trim());
                int g = Integer.parseInt(parts[1].trim());
                int b = Integer.parseInt(parts[2].trim());
                float a = parts.length == 4 ? Float.parseFloat(parts[3].trim()) : 1f;
                if (r < 0 || r > 255 || g < 0 || g > 255 || b < 0 || b > 255 || !(a >= 0f && a <= 1f)) {
                    return null;
                }
                return argb(Math.round(a * 255f), r, g, b);
            }
            if (s.startsWith("#")) {
                s = s.substring(1);
            }
            if (!s.matches("[0-9a-f]+")) {
                return null;
            }
            return switch (s.length()) {
                case 3 -> {
                    int v = Integer.parseInt(s, 16);
                    int r = (v >> 8) & 0xF, g = (v >> 4) & 0xF, b = v & 0xF;
                    yield rgb(r * 17, g * 17, b * 17);
                }
                case 6 -> 0xFF000000 | Integer.parseInt(s, 16);
                case 8 -> (int) Long.parseLong(s, 16);
                default -> null;
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** "#rrggbb" (alpha dropped). */
    public static String hex(int c) {
        return String.format(Locale.ROOT, "#%06x", c & 0xFFFFFF);
    }

    /** Linear mix of every channel, alpha included; t 0 gives {@code from}, 1 gives {@code to}. */
    public static int mix(int from, int to, float t) {
        if (t <= 0f) {
            return from;
        }
        if (t >= 1f) {
            return to;
        }
        return argb(Math.round(a(from) + (a(to) - a(from)) * t), Math.round(r(from) + (r(to) - r(from)) * t),
                Math.round(g(from) + (g(to) - g(from)) * t), Math.round(b(from) + (b(to) - b(from)) * t));
    }

    /** The prototypes' {@code mixHex}: the colour channels mixed, fully opaque. */
    public static int mixRgb(int from, int to, float t) {
        return 0xFF000000 | (mix(from, to, t) & 0xFFFFFF);
    }

    /** Multiplies the colour channels (keeps alpha). */
    public static int scale(int c, float k) {
        return argb(a(c), Math.round(r(c) * k), Math.round(g(c) * k), Math.round(b(c) * k));
    }

    /** Channel-wise multiply of two colours (alpha from the first). */
    public static int multiply(int c, int tint) {
        return argb(a(c), r(c) * r(tint) / 255, g(c) * g(tint) / 255, b(c) * b(tint) / 255);
    }

    /** HSL of the colour, each 0..1 (the prototypes' {@code toHsl}). */
    public static float[] toHsl(int c) {
        float r = r(c) / 255f, g = g(c) / 255f, b = b(c) / 255f;
        float mx = Math.max(r, Math.max(g, b)), mn = Math.min(r, Math.min(g, b)), l = (mx + mn) / 2f;
        float h = 0f, s = 0f;
        if (mx != mn) {
            float d = mx - mn;
            s = l > 0.5f ? d / (2f - mx - mn) : d / (mx + mn);
            if (mx == r) {
                h = (g - b) / d + (g < b ? 6f : 0f);
            } else if (mx == g) {
                h = (b - r) / d + 2f;
            } else {
                h = (r - g) / d + 4f;
            }
            h /= 6f;
        }
        return new float[]{h, s, l};
    }

    /** An opaque colour from HSL, each 0..1 (the prototypes' {@code fromHsl}). */
    public static int fromHsl(float h, float s, float l) {
        float a = s * Math.min(l, 1f - l);
        return rgb(Math.round(hslChannel(0, h, l, a) * 255f), Math.round(hslChannel(8, h, l, a) * 255f),
                Math.round(hslChannel(4, h, l, a) * 255f));
    }

    private static float hslChannel(int n, float h, float l, float a) {
        float k = (n + h * 12f) % 12f;
        if (k < 0) {
            k += 12f;
        }
        return l - a * Math.max(-1f, Math.min(Math.min(k - 3f, 9f - k), 1f));
    }

    /**
     * Turns the colour round the colour wheel by {@code degrees} and multiplies its saturation (the prototype's
     * {@code shiftHex}); alpha is kept.
     */
    public static int shiftHue(int c, float degrees, float saturation) {
        if (degrees == 0f && saturation == 1f) {
            return c;
        }
        float[] hsl = toHsl(c);
        float h = (hsl[0] + degrees / 360f) % 1f;
        if (h < 0f) {
            h += 1f;
        }
        return (c & 0xFF000000) | (fromHsl(h, Math.min(1f, hsl[1] * saturation), hsl[2]) & 0xFFFFFF);
    }

    /**
     * {@code x} recoloured the way {@code from} became {@code to}: {@code to}'s hue, and x's saturation and lightness
     * moved as from's became to's (toward black or grey by the same share when they fall, toward white or full colour
     * when they rise). {@code recolour(from, from, to)} is {@code to}; x's alpha is kept.
     */
    public static int recolour(int x, int from, int to) {
        return recolour(x, from, to, false);
    }

    /** As {@link #recolour(int, int, int)}; with {@code spread}, x keeps its hue's distance from from's (a palette stays varied). */
    public static int recolour(int x, int from, int to, boolean spread) {
        if ((x & 0xFFFFFF) == (from & 0xFFFFFF)) {
            return (x & 0xFF000000) | (to & 0xFFFFFF);
        }
        float[] hx = toHsl(x), hf = toHsl(from), ht = toHsl(to);
        // A grey has no hue of its own to keep a distance from: it takes the new one.
        float h = !spread || hf[1] < 0.04f || hx[1] < 0.04f ? ht[0] : ht[0] + (hx[0] - hf[0]);
        h = (h % 1f + 1f) % 1f;
        return (x & 0xFF000000) | (fromHsl(h, move(hx[1], hf[1], ht[1]), move(hx[2], hf[2], ht[2])) & 0xFFFFFF);
    }

    /** {@code v} moved the way {@code from} moved to {@code to}, within 0..1. */
    private static float move(float v, float from, float to) {
        float out;
        if (to <= from) {
            out = from <= 1e-4f ? to : v * to / from;
        } else {
            out = from >= 1f - 1e-4f ? to : 1f - (1f - v) * (1f - to) / (1f - from);
        }
        return Math.max(0f, Math.min(1f, out));
    }

    /** HSV (0..1 each) to an opaque colour. */
    public static int fromHsv(float h, float s, float v) {
        h = (h % 1f + 1f) % 1f;
        float c = v * s, x = c * (1f - Math.abs((h * 6f) % 2f - 1f)), m = v - c;
        float r, g, b;
        switch ((int) (h * 6f) % 6) {
            case 0 -> { r = c; g = x; b = 0; }
            case 1 -> { r = x; g = c; b = 0; }
            case 2 -> { r = 0; g = c; b = x; }
            case 3 -> { r = 0; g = x; b = c; }
            case 4 -> { r = x; g = 0; b = c; }
            default -> { r = c; g = 0; b = x; }
        }
        return argbF(1f, r + m, g + m, b + m);
    }

    /** The prototypes' quick brightness: Rec. 709 weights on the gamma values, 0..1. */
    public static float luma(int c) {
        return (r(c) * 0.2126f + g(c) * 0.7152f + b(c) * 0.0722f) / 255f;
    }

    /** Text and knobs on top of a filled accent: dark on light accents, white otherwise (the 0.62 rule). */
    public static int onColour(int accent) {
        return luma(accent) > 0.62f ? ON_LIGHT : WHITE;
    }

    /** WCAG relative luminance of the colour (alpha ignored). */
    public static double luminance(int c) {
        return 0.2126 * linear(r(c)) + 0.7152 * linear(g(c)) + 0.0722 * linear(b(c));
    }

    private static double linear(int channel) {
        double v = channel / 255.0;
        return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    /** WCAG contrast ratio of two colours, 1..21. */
    public static double contrast(int x, int y) {
        double a = luminance(x), b = luminance(y);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    /**
     * The colour moved lighter or darker (keeping its hue and saturation) until it reaches {@code minRatio}
     * contrast with {@code background}, or as far as it can go. Used so every label stays readable on album and
     * customised themes.
     */
    public static int nudgeForContrast(int colour, int background, double minRatio) {
        if (contrast(colour, background) >= minRatio) {
            return colour;
        }
        float[] hsl = toHsl(colour);
        boolean lighter = luminance(background) < 0.18;
        int best = colour;
        for (int step = 1; step <= 40; step++) {
            float l = lighter ? Math.min(1f, hsl[2] + step * 0.025f) : Math.max(0f, hsl[2] - step * 0.025f);
            int candidate = (colour & 0xFF000000) | (fromHsl(hsl[0], hsl[1], l) & 0xFFFFFF);
            best = candidate;
            if (contrast(candidate, background) >= minRatio) {
                return candidate;
            }
        }
        // The other direction can still be better (a mid-grey background).
        int flipped = lighter ? (colour & 0xFF000000) | 0x101014 : (colour & 0xFF000000) | 0xFFFFFF;
        return contrast(flipped, background) > contrast(best, background) ? flipped : best;
    }

    /** Composites {@code top} over an opaque {@code bottom}. */
    public static int over(int top, int bottom) {
        float t = a(top) / 255f;
        return rgb(Math.round(r(top) * t + r(bottom) * (1 - t)), Math.round(g(top) * t + g(bottom) * (1 - t)),
                Math.round(b(top) * t + b(bottom) * (1 - t)));
    }
}
