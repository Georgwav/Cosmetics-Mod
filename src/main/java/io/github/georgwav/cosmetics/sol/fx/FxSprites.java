package io.github.georgwav.cosmetics.sol.fx;

import java.util.ArrayList;
import java.util.List;

/**
 * The effects' pixel sprites (C.1), Sol's own, drawn here as text grids and packed into one atlas at load: the square,
 * the logo's plus sparkle, a diamond, a 5 x 5 star, a heart, a ring segment, the three-frame flame, a bubble, the
 * ankh-like Sol sun, an eye, the moon, two frames of confetti, and a few the effects need (the plumbob, a crystal, a
 * footprint, a leaf, a chain link, a scorch mark). In a grid {@code 1} is the core tone, {@code 2} the main, {@code 3}
 * the shade and {@code 4} the deep tone ({@link Ramp}); {@code .} is empty.
 *
 * <p>Each sprite keeps one white mask per tone it uses, so a draw colours each tone from the effect's ramp, and a mask
 * of all its pixels for the stepped glow behind it. A Bayer-dithered disc is the big glows' ({@link #DITHER}). Plain
 * arithmetic: the GPU copy is made where it's drawn.
 */
public final class FxSprites {
    /** The 4 x 4 Bayer matrix, 0..15 (first: the dither disc below is made from it). */
    static final int[] BAYER = {0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5};

    /** A sprite: its size in texels, and per tone (and {@link #ALL}) its rectangle in the atlas, or null. */
    public static final class Sprite {
        public final String name;
        public final int w, h;
        /** Per pixel, row by row from the top: the tone (0..3), or -1. */
        final int[] tones;
        /** For tones 0..3 and then all pixels: u0, v0, u1, v1 in the atlas, or null when unused. */
        public final float[][] uv = new float[5][];

        Sprite(String name, String... rows) {
            this.name = name;
            this.h = rows.length;
            int width = 0;
            for (String r : rows) {
                width = Math.max(width, r.length());
            }
            this.w = width;
            this.tones = new int[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    char c = x < rows[y].length() ? rows[y].charAt(x) : '.';
                    tones[y * w + x] = c >= '1' && c <= '4' ? c - '1' : -1;
                }
            }
        }

        /** The tone at (x, y), or -1. */
        public int tone(int x, int y) {
            return x < 0 || y < 0 || x >= w || y >= h ? -1 : tones[y * w + x];
        }

        public boolean uses(int tone) {
            return uv[tone] != null;
        }
    }

    public static final int ALL = 4;

    public static final Sprite SQUARE = new Sprite("square", "2");
    public static final Sprite PLUS = new Sprite("plus",
            "..3..",
            "..2..",
            "32123",
            "..2..",
            "..3..");
    public static final Sprite DIAMOND = new Sprite("diamond",
            "..2..",
            ".212.",
            "21112",
            ".223.",
            "..3..");
    public static final Sprite STAR = new Sprite("star",
            "..2..",
            ".212.",
            "21112",
            ".222.",
            ".3.3.");
    public static final Sprite HEART = new Sprite("heart",
            ".22.22.",
            "2112223",
            "2122223",
            ".22223.",
            "..223..",
            "...3...");
    public static final Sprite RING_SEGMENT = new Sprite("ring_segment",
            ".2222.",
            "2.33.2");
    public static final Sprite[] FLAME = {
            new Sprite("flame_0",
                    "..3..",
                    "..3..",
                    ".323.",
                    ".222.",
                    "32122",
                    "21112",
                    "21112",
                    ".222."),
            new Sprite("flame_1",
                    ".3...",
                    ".33..",
                    ".323.",
                    "3222.",
                    "22122",
                    "21112",
                    "21112",
                    ".222."),
            new Sprite("flame_2",
                    "...3.",
                    "..33.",
                    ".323.",
                    ".2223",
                    "22122",
                    "21112",
                    "21112",
                    ".222.")};
    public static final Sprite BUBBLE = new Sprite("bubble",
            "..222..",
            ".2...2.",
            "2.1...2",
            "2.....3",
            "2.....3",
            ".2...3.",
            "..333..");
    /** The Sol sun as a glyph: an ankh, its loop the sun. */
    public static final Sprite SUN = new Sprite("sun",
            "..222..",
            ".21112.",
            ".21112.",
            "..222..",
            "2222222",
            "...2...",
            "...2...",
            "...3...",
            "...3...");
    public static final Sprite EYE = new Sprite("eye",
            "..222..",
            ".21112.",
            "2114112",
            ".21112.",
            "..222..");
    public static final Sprite MOON = new Sprite("moon",
            "..122.",
            ".12...",
            "12....",
            "12....",
            "12....",
            ".12...",
            "..123.");
    public static final Sprite[] CONFETTI = {
            new Sprite("confetti_0", "2", "3"),
            new Sprite("confetti_1", "3")};
    public static final Sprite PLUMBOB = new Sprite("plumbob",
            "..1..",
            ".112.",
            ".122.",
            "11222",
            "12223",
            ".223.",
            ".233.",
            "..3..");
    public static final Sprite CRYSTAL = new Sprite("crystal",
            "..1..",
            ".112.",
            "11222",
            "12223",
            ".223.",
            "..3..");
    /** A shoe's print, 4 x 7 (a quarter block wide in the world, as a player's foot): toes, sole, a gap, the heel. */
    public static final Sprite FOOTPRINT = new Sprite("footprint",
            ".11.",
            "2222",
            "2222",
            ".22.",
            "....",
            "3333",
            ".33.");
    public static final Sprite LEAF = new Sprite("leaf",
            "..12",
            ".122",
            "1223",
            "33..");
    public static final Sprite CHAIN = new Sprite("chain",
            ".22.",
            "2..3",
            ".33.");
    /** A totem of undying: its head with the deep tone's eyes, arms out, its robe. */
    public static final Sprite TOTEM = new Sprite("totem",
            "..22222..",
            ".2111112.",
            ".2141412.",
            ".2111112.",
            "..23332..",
            "222222222",
            "3.21112.3",
            "..21112..",
            "..23332..",
            "...232...",
            "...333...");
    /** A wing, its tip up and out to the left (mirrored for the right). */
    public static final Sprite WING = new Sprite("wing",
            "1.....",
            "11....",
            "211...",
            "2211..",
            "32211.",
            ".3222.",
            "..33..");
    /** A small glowing orb. */
    public static final Sprite ORB = new Sprite("orb",
            ".22.",
            "2112",
            "2123",
            ".33.");
    public static final Sprite SCORCH = new Sprite("scorch",
            "..44.4..",
            ".4344.4.",
            "44.33.44",
            ".34..43.",
            "4.3..3.4",
            ".44.344.",
            "..4.44..");

    /** The big glows' disc: 16 x 16, its falloff Bayer-dithered (all one tone). */
    public static final Sprite DITHER = dither();

    private static final Sprite[] ALL_SPRITES;

    static {
        List<Sprite> all = new ArrayList<>(List.of(SQUARE, PLUS, DIAMOND, STAR, HEART, RING_SEGMENT, BUBBLE, SUN, EYE, MOON,
                PLUMBOB, CRYSTAL, FOOTPRINT, LEAF, CHAIN, ORB, TOTEM, WING, SCORCH, DITHER));
        all.addAll(List.of(FLAME));
        all.addAll(List.of(CONFETTI));
        ALL_SPRITES = all.toArray(new Sprite[0]);
    }

    private static FxTex atlas;

    private FxSprites() {
    }

    private static Sprite dither() {
        String[] rows = new String[16];
        for (int y = 0; y < 16; y++) {
            StringBuilder sb = new StringBuilder();
            for (int x = 0; x < 16; x++) {
                float dx = (x + 0.5f - 8f) / 8f, dy = (y + 0.5f - 8f) / 8f;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                float level = Math.max(0f, 1f - d);
                level *= level;
                // On where the level beats the pixel's threshold in the Bayer matrix.
                boolean on = level * 16f > BAYER[(y & 3) * 4 + (x & 3)] + 0.5f;
                sb.append(on ? '2' : '.');
            }
            rows[y] = sb.toString();
        }
        return new Sprite("dither", rows);
    }

    public static Sprite[] all() {
        return ALL_SPRITES;
    }

    /** The atlas: every sprite's tone masks and its all-pixels mask, white on clear, one texel apart. */
    public static synchronized FxTex atlas() {
        if (atlas == null) {
            atlas = pack();
        }
        return atlas;
    }

    static FxTex pack() {
        int size = 128;
        int[] px = new int[size * size];
        int x = 1, y = 1, rowH = 0;
        for (Sprite s : ALL_SPRITES) {
            for (int layer = 0; layer < 5; layer++) {
                boolean any = false;
                for (int t : s.tones) {
                    if (layer == ALL ? t >= 0 : t == layer) {
                        any = true;
                        break;
                    }
                }
                if (!any) {
                    s.uv[layer] = null;
                    continue;
                }
                if (x + s.w + 1 > size) {
                    x = 1;
                    y += rowH + 1;
                    rowH = 0;
                }
                if (y + s.h + 1 > size) {
                    throw new IllegalStateException("FX sprite atlas is full");
                }
                for (int sy = 0; sy < s.h; sy++) {
                    for (int sx = 0; sx < s.w; sx++) {
                        int t = s.tones[sy * s.w + sx];
                        if (layer == ALL ? t >= 0 : t == layer) {
                            px[(y + sy) * size + x + sx] = 0xFFFFFFFF;
                        }
                    }
                }
                s.uv[layer] = new float[]{x / (float) size, y / (float) size, (x + s.w) / (float) size, (y + s.h) / (float) size};
                x += s.w + 1;
                rowH = Math.max(rowH, s.h);
            }
        }
        return new FxTex("fx_sprites", size, size, px);
    }
}
