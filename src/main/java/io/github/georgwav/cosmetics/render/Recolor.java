package io.github.georgwav.cosmetics.render;

import io.github.georgwav.cosmetics.config.CosmeticsConfig;
import io.github.georgwav.cosmetics.model.ColorStyle;
import io.github.georgwav.cosmetics.model.CosmeticOption;

import java.util.ArrayList;
import java.util.List;

/**
 * How a cosmetic's texture itself is recoloured, pixel by pixel: its hue turned, its saturation scaled (0 is black
 * and white) and its brightness scaled, then painted with the player's colour as its {@link ColorStyle} says
 * ({@link Paint}). Unlike the game's tint, which only multiplies (so dark parts and opposite colours turn black),
 * this turns red wings blue. The saturation setting pales the tint instead when the cosmetic is tinted
 * ({@link Tint}), so it isn't applied twice. Plain maths, no Minecraft types, so it is unit-tested;
 * {@link RecoloredTextures} makes the recoloured textures.
 *
 * <p>Before all that, {@link Swap swaps} change some of the texture's own colours and leave the rest: the red of a
 * red and gold cape turned purple, its gold kept. Each pixel takes a swap as far as its hue is near the swap's
 * colour (fully within {@link #SWAP_CORE}, fading over {@link #SWAP_SPREAD}), its shades kept: a darker red turns
 * a darker purple. Colours kept as they are are swaps to themselves, so they hold their hues (a pixel between red
 * and gold takes half the red's change, not all of it); white, grey and black stay.
 *
 * @param hue        degrees the hue turns, -180..180
 * @param saturation saturation factor, 0..1
 * @param brightness brightness factor, 0..2
 * @param paint      how the player's colour is painted in
 * @param tint       the colour (RGB) painted into {@link Paint#COLORED} parts; 0 otherwise
 * @param swaps      the texture's own colours changed (empty for none)
 */
public record Recolor(int hue, float saturation, float brightness, Paint paint, int tint, List<Swap> swaps) {
    /** Textures with fewer coloured pixels than this share count as white ones (see {@link #paintFor}). */
    static final float WHITE_COSMETIC = 0.15f;
    /** Biggest texture (pixels) painted anew as the rainbow turns; bigger ones take it as {@link ColorStyle#WHOLE}. */
    static final int MAX_RAINBOW_PIXELS = 512 * 512;
    /** Degrees the rainbow turns between two paintings of a texture. */
    static final int RAINBOW_STEP = 10;
    /** Hue distance (turns) within which a pixel takes a swap fully. */
    static final float SWAP_CORE = 10f / 360f;
    /** Hue distance (turns) over which it fades beyond that. */
    static final float SWAP_SPREAD = 16f / 360f;
    /** Saturation below which a pixel (white, grey, black) takes no swap, and above which it takes it fully. */
    static final float SWAP_GREY = 0.06f;
    static final float SWAP_COLOURED = 0.2f;
    /** Hue bins (10 degrees each) for finding a texture's colours ({@link #palette}). */
    private static final int PALETTE_BINS = 36;
    /** Share of a texture's coloured pixels a colour needs to count as one of its colours. */
    private static final float PALETTE_SHARE = 0.04f;

    /** One of the texture's own colours (RGB) changed to another (RGB); {@code from == to} keeps it. */
    public record Swap(int from, int to) {
        public Swap {
            from &= 0xFFFFFF;
            to &= 0xFFFFFF;
        }
    }

    public Recolor {
        swaps = swaps == null ? List.of() : List.copyOf(swaps);
    }

    public Recolor(int hue, float saturation, float brightness, Paint paint, int tint) {
        this(hue, saturation, brightness, paint, tint, List.of());
    }

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
        this(hue, saturation, brightness, Paint.NONE, 0, List.of());
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
        Integer picked = Tint.color(options, allowed);
        int tint = picked != null ? picked : defaultColor;
        boolean tinted = rainbow || (tint & 0xFFFFFF) != 0xFFFFFF;
        // Rounded so dragging a slider makes few distinct textures.
        int hue = Math.round(options.hue);
        float saturation = tinted ? 1f : Math.round(options.saturation * 100f) / 100f;
        float brightness = Math.round(options.brightness * 100f) / 100f;
        List<Swap> swaps = swapsOf(options.swaps);
        if (hue % 360 == 0 && saturation >= 1f && brightness == 1f && swaps.isEmpty()) {
            return null;
        }
        return new Recolor(hue, saturation, brightness, Paint.NONE, 0, swaps);
    }

    /** The swaps a cosmetic's settings ask for: all of them (the kept ones hold their colours) if any changes one. */
    static List<Swap> swapsOf(List<CosmeticsConfig.ColorSwap> chosen) {
        if (chosen == null || chosen.stream().allMatch(c -> ((c.from ^ c.to) & 0xFFFFFF) == 0)) {
            return List.of();
        }
        List<Swap> swaps = new ArrayList<>(chosen.size());
        for (CosmeticsConfig.ColorSwap c : chosen) {
            swaps.add(new Swap(c.from, c.to));
        }
        return swaps;
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
        return new Recolor(b.hue, b.saturation, b.brightness, paint, paint == Paint.COLORED ? tint & 0xFFFFFF : 0,
                b.swaps);
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
        return apply(argb, paint == Paint.COLORED ? Tint.rgbToHsv(tint) : null, SwapTable.of(swaps));
    }

    /** Every pixel of an ARGB image recoloured into {@code out}. */
    public void applyAll(int[] argb, int[] out) {
        float[] tintHsv = paint == Paint.COLORED ? Tint.rgbToHsv(tint) : null;
        SwapTable table = SwapTable.of(swaps);
        for (int i = 0; i < argb.length; i++) {
            out[i] = apply(argb[i], tintHsv, table);
        }
    }

    private int apply(int argb, float[] tintHsv, SwapTable table) {
        float[] hsv = Tint.rgbToHsv(argb & 0xFFFFFF);
        if (table != null) {
            table.apply(hsv);
        }
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

    /**
     * The swaps worked out once for a texture: each one's hue (turns), how far it turns the hue (turns, the short
     * way), and its saturation and brightness factors.
     */
    private static final class SwapTable {
        final float[] hue;
        final float[] shift;
        final float[] sat;
        final float[] val;
        final float[] weight;

        private SwapTable(List<Swap> swaps) {
            int n = swaps.size();
            hue = new float[n];
            shift = new float[n];
            sat = new float[n];
            val = new float[n];
            weight = new float[n];
            for (int i = 0; i < n; i++) {
                float[] from = Tint.rgbToHsv(swaps.get(i).from());
                float[] to = Tint.rgbToHsv(swaps.get(i).to());
                hue[i] = from[0];
                float turn = to[0] - from[0];
                // To a grey the hue doesn't matter: kept, so the shades don't swing round the wheel.
                shift[i] = to[1] < 0.01f ? 0f : turn - Math.round(turn);
                sat[i] = from[1] > 0.01f ? Math.min(4f, to[1] / from[1]) : 1f;
                val[i] = from[2] > 0.01f ? Math.min(4f, to[2] / from[2]) : 1f;
            }
        }

        static SwapTable of(List<Swap> swaps) {
            return swaps.isEmpty() ? null : new SwapTable(swaps);
        }

        /** Changes {@code hsv} (hue in turns) by the swaps near its hue. */
        void apply(float[] hsv) {
            float s = hsv[1];
            if (s <= SWAP_GREY) {
                return;
            }
            float coloured = Math.min(1f, (s - SWAP_GREY) / (SWAP_COLOURED - SWAP_GREY));
            float nearest = 0f;
            float total = 0f;
            for (int i = 0; i < hue.length; i++) {
                float d = Math.abs(hsv[0] - hue[i]);
                d = Math.min(d, 1f - d);
                float over = (d - SWAP_CORE) / SWAP_SPREAD;
                float near = d <= SWAP_CORE ? 1f : (float) Math.exp(-over * over);
                nearest = Math.max(nearest, near);
                // Shares by the fourth power: each colour holds its own hues (the kept ones too), and between two
                // the nearer takes most; how near the nearest is says how much changes at all.
                weight[i] = near * near * near * near;
                total += weight[i];
            }
            if (total <= 0f) {
                return;
            }
            float scale = coloured * nearest / total;
            float dh = 0f;
            float ds = 0f;
            float dv = 0f;
            for (int i = 0; i < hue.length; i++) {
                float w = weight[i] * scale;
                dh += w * shift[i];
                ds += w * (sat[i] - 1f);
                dv += w * (val[i] - 1f);
            }
            float h = hsv[0] + dh;
            hsv[0] = h - (float) Math.floor(h);
            hsv[1] = Math.max(0f, Math.min(1f, s * (1f + ds)));
            hsv[2] = Math.max(0f, Math.min(1f, hsv[2] * (1f + dv)));
        }
    }

    /**
     * An image's (ARGB) own colours, most used first, at most {@code max}: one per peak of its coloured pixels'
     * hues (white, grey, black and see-through ones aside), each the average of the pixels around that peak, and at
     * least 20 degrees from a more used one (brown and gold apart). Empty when nothing in it is coloured.
     */
    public static int[] palette(int[] argb, int max) {
        int[] count = new int[PALETTE_BINS];
        long[][] sums = new long[PALETTE_BINS][3];
        int coloured = 0;
        for (int c : argb) {
            if (!coloured(c)) {
                continue;
            }
            float[] hsv = Tint.rgbToHsv(c & 0xFFFFFF);
            int bin = Math.min(PALETTE_BINS - 1, (int) (hsv[0] * PALETTE_BINS));
            count[bin]++;
            sums[bin][0] += (c >> 16) & 0xFF;
            sums[bin][1] += (c >> 8) & 0xFF;
            sums[bin][2] += c & 0xFF;
            coloured++;
        }
        if (coloured == 0) {
            return new int[0];
        }
        // Peaks of the hue counts, each with the pixels of its bin and the two beside it.
        List<int[]> peaks = new ArrayList<>(); // {bin, pixels}
        for (int i = 0; i < PALETTE_BINS; i++) {
            int before = count[(i + PALETTE_BINS - 1) % PALETTE_BINS];
            int after = count[(i + 1) % PALETTE_BINS];
            int mass = before + count[i] + after;
            if (count[i] > 0 && count[i] >= before && count[i] > after && mass >= PALETTE_SHARE * coloured) {
                peaks.add(new int[]{i, mass});
            }
        }
        peaks.sort((a, b) -> Integer.compare(b[1], a[1]));
        List<Integer> kept = new ArrayList<>();
        List<Integer> colours = new ArrayList<>();
        for (int[] peak : peaks) {
            if (colours.size() >= max) {
                break;
            }
            boolean near = false;
            for (int other : kept) {
                int d = Math.abs(peak[0] - other);
                near |= Math.min(d, PALETTE_BINS - d) < 2;
            }
            if (near) {
                continue;
            }
            long r = 0;
            long g = 0;
            long b = 0;
            int n = 0;
            for (int j = -1; j <= 1; j++) {
                int bin = (peak[0] + j + PALETTE_BINS) % PALETTE_BINS;
                r += sums[bin][0];
                g += sums[bin][1];
                b += sums[bin][2];
                n += count[bin];
            }
            kept.add(peak[0]);
            colours.add((int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n));
        }
        return colours.stream().mapToInt(Integer::intValue).toArray();
    }
}
