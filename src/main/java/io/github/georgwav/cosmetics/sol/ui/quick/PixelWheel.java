package io.github.georgwav.cosmetics.sol.ui.quick;

import io.github.georgwav.cosmetics.sol.ui.kit.PixelTokens;
import io.github.georgwav.cosmetics.sol.util.Colors;

import java.util.ArrayList;
import java.util.List;

/**
 * The quick menu's wheel drawn pixel by pixel (the pixel UI prototype's drawWheel): a 288 x 288 canvas of art pixels,
 * shown at 2 design pixels each. Rings of segments with a 3 px gap, a 2 px edge and a lit outer rim; the section you
 * point at dithered from the accent tint to the slab colour (Bayer 4 x 4); presets in three dithered tones of their sky;
 * the dial's ticks, the sun (or the moon below the horizon), the eclipse's corona with its turning rays, and the disc
 * your head sits on. Redrawn only when something on it changes; pure Java, so the tests can draw it.
 */
public final class PixelWheel {
    /** The canvas, its middle, your head's disc and the sun's track, in art pixels. */
    public static final int WN = 288;
    public static final float WC = 144f, HR = 28f, RT = 128f;
    /**
     * Ring radii in art pixels (2 design pixels each): the main ring 116-220 design px, your ring 116-242, the presets
     * on 116-180 and 188-236 (a third ring 244-286 past ten of your own), the emote ring 116-216.
     */
    public static final float MAIN_A = 58f, MAIN_B = 110f, ME_B = 121f, P1A = 58f, P1B = 90f, P2A = 94f, P2B = 118f,
            P3A = 122f, P3B = 143f, EM_A = 58f, EM_B = 108f;

    private static final float[] PR = new float[WN * WN], PA = new float[WN * WN];
    private static final float[] BAYER = new float[16];

    static {
        for (int y = 0; y < WN; y++) {
            for (int x = 0; x < WN; x++) {
                float dx = x + 0.5f - WC, dy = y + 0.5f - WC;
                PR[y * WN + x] = (float) Math.hypot(dx, dy);
                PA[y * WN + x] = (float) Math.toDegrees(Math.atan2(dy, dx));
            }
        }
        int[] b = {0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5};
        for (int i = 0; i < 16; i++) {
            BAYER[i] = (b[i] + 0.5f) / 16f;
        }
    }

    /** A segment's look. */
    public static final int SLAB = 0, SKY = 1, SAVE = 2;

    /** One segment: its kind, and for a preset its sky's three tones and the lighter one it shows when pointed at. */
    public static final class Seg {
        public int kind = SLAB;
        public int cIn, cMid, cOut, cHot;
        public boolean current;
    }

    /** A ring of segments from r0 to r1 (art pixels), its first segment's middle at {@code start} degrees. */
    public static final class Ring {
        public final float r0, r1, start;
        public final List<Seg> segs = new ArrayList<>();

        public Ring(float r0, float r1, float start) {
            this.r0 = r0;
            this.r1 = r1;
            this.start = start;
        }

        public float span() {
            return 360f / Math.max(1, segs.size());
        }

        /** The middle of segment i, degrees. */
        public float mid(int i) {
            return start + i * span();
        }

        /** The segment at an angle (degrees). */
        public int at(float deg) {
            int n = Math.max(1, segs.size());
            return Math.floorMod(Math.round((deg - start) / span()), n);
        }
    }

    public final int[] buf = new int[WN * WN];
    /**
     * The rings and your head's disc as nothing points at them, and the segment each pixel is in ((ring << 8 | segment)
     * + 1, 0 for none): drawn again only when the rings, their size or the colours change. A frame copies it and paints
     * the hot segment, the dial, the sun and the corona over it, which is a small part of the canvas.
     */
    private final int[] layer = new int[WN * WN], segOf = new int[WN * WN];
    private long layerKey = Long.MIN_VALUE;
    private final float[] rayWidth = new float[361];
    /** The eclipse disc's falloff (1 - r/22)^1.4 in 64 steps out to 22 art pixels past your head. */
    private static final float[] DISC = new float[64];
    /** The pixels of the dial's ticks and of your head's rim, found once. */
    private static final int[] DIAL, HEAD_RIM;

    static {
        for (int i = 0; i < 64; i++) {
            DISC[i] = (float) Math.pow(Math.max(0f, 1f - (i + 0.5f) / 64f), 1.4f);
        }
        int[] dial = new int[WN * 8], rim = new int[WN * 4];
        int nd = 0, nr = 0;
        for (int i = 0; i < WN * WN; i++) {
            float r = PR[i], a = PA[i];
            if (r >= RT - 1.5f && r < RT + 1.5f && Math.abs(((a % 15f) + 15f) % 15f - 7.5f) > 7.5f - 95f / r) {
                dial[nd++] = i;
            }
            if (r < HR && r >= HR - 2f) {
                rim[nr++] = i;
            }
        }
        DIAL = java.util.Arrays.copyOf(dial, nd);
        HEAD_RIM = java.util.Arrays.copyOf(rim, nr);
    }

    // The palette, packed ARGB.
    private int edge, c1, hi, rec, acc, accHi, tint, tick, tickHi, core, glow, glow2, moon, moonHi, crater, moonGlow;

    /** Takes the pixel UI's palette (the prototype's wcols). */
    public void colours(PixelTokens p) {
        int lit = Colors.mixRgb(p.glow, 0xFFFFFFFF, 0.45f);
        edge = p.edge;
        c1 = Colors.withAlpha(p.c1, 238 / 255f);
        hi = Colors.withAlpha(Colors.mixRgb(p.c1, lit, 0.3f), 245 / 255f);
        rec = Colors.withAlpha(p.rec, 250 / 255f);
        acc = p.acc;
        accHi = p.accHi;
        tint = Colors.withAlpha(p.accTint, 245 / 255f);
        tick = Colors.withAlpha(0xFFFFFFFF, 120 / 255f);
        tickHi = Colors.withAlpha(0xFFFFFFFF, 235 / 255f);
        core = 0xFFFFF6DC;
        glow = p.glow;
        glow2 = Colors.mixRgb(p.glow, 0xFFFFFFFF, 0.5f);
        moon = 0xFFE8EEFC;
        moonHi = 0xFFFFFFFF;
        crater = 0xFFB5C0D8;
        moonGlow = Colors.withAlpha(0xFFBCD0FF, 150 / 255f);
    }

    private static float angDiff(float a, float b) {
        float d = (a - b) % 360f;
        if (d > 180f) {
            d -= 360f;
        } else if (d < -180f) {
            d += 360f;
        }
        return d;
    }

    /**
     * Draws the wheel. {@code k} (0..1, in steps) is how far the rings stand out of your head (they shrink into it and
     * grow back when the rings change); {@code hotRing}/{@code hotSeg} the segment pointed at (-1 none);
     * {@code hovMe} whether you are; {@code dial} whether the sun's track and its ticks show (not inside a wheel);
     * {@code sunA} the sun's angle (degrees, 0 right, clockwise); {@code eclipse} 0..1; {@code clock} seconds (the
     * corona's turn).
     */
    public void draw(List<Ring> rings, float k, int hotRing, int hotSeg, boolean hovMe, boolean dial, float sunA, float eclipse,
                     boolean moonUp, float clock) {
        long key = layerKey(rings, k);
        if (key != layerKey) {
            layerKey = key;
            drawLayer(rings, k);
        }
        System.arraycopy(layer, 0, buf, 0, buf.length);
        if (k > 0f && !hovMe && hotRing >= 0 && hotRing < rings.size() && hotSeg >= 0) {
            Ring R = rings.get(hotRing);
            int id = (hotRing << 8 | hotSeg) + 1;
            for (int i = 0; i < WN * WN; i++) {
                if (segOf[i] == id) {
                    buf[i] = segPixel(R, i, k, true);
                }
            }
        }
        // What lies behind the rings: the dial's ticks, the sun (or the moon's glow) and the eclipse's corona.
        if (dial) {
            boolean lit = eclipse < 0.5f;
            for (int i : DIAL) {
                if (layer[i] == 0) {
                    buf[i] = lit && Math.abs(angDiff(PA[i], sunA)) < 24f ? tickHi : tick;
                }
            }
        }
        double sa = Math.toRadians(sunA);
        int sx = Math.round(WC + RT * (float) Math.cos(sa) * (1f - eclipse)), sy = Math.round(WC + RT * (float) Math.sin(sa) * (1f - eclipse));
        if (eclipse < 0.9f) {
            for (int y = Math.max(0, sy - 16); y < Math.min(WN, sy + 17); y++) {
                for (int x = Math.max(0, sx - 16); x < Math.min(WN, sx + 17); x++) {
                    int i = y * WN + x;
                    if (layer[i] != 0) {
                        continue;
                    }
                    float bq = BAYER[(x & 3) + ((y & 3) << 2)];
                    float ds = (float) Math.sqrt((x + 0.5f - sx) * (x + 0.5f - sx) + (y + 0.5f - sy) * (y + 0.5f - sy));
                    if (moonUp) {
                        if (ds < 12f && (1f - ds / 12f) * 0.55f > bq) {
                            buf[i] = moonGlow;
                        }
                    } else if (ds < 6f) {
                        buf[i] = core;
                    } else if (ds < 9f) {
                        buf[i] = glow2;
                    } else if (ds < 16f && (1f - (ds - 9f) / 7f) * 0.7f > bq) {
                        buf[i] = glow;
                    }
                }
            }
        }
        if (eclipse > 0.05f) {
            // The ray's width by whole degree, worked out once a draw (not per pixel).
            for (int d = 0; d < 361; d++) {
                rayWidth[d] = 3.2f + 2f * (float) Math.sin(clock * 2f + (d - 180));
            }
            float rot = clock * 8f;
            int c0 = (int) (WC - HR - 51f), c1x = (int) (WC + HR + 51f);
            for (int y = c0; y < c1x; y++) {
                for (int x = c0; x < c1x; x++) {
                    int i = y * WN + x;
                    float r = PR[i];
                    if (r < HR || r >= HR + 50f || layer[i] != 0) {
                        continue;
                    }
                    float a = PA[i];
                    float ang = Math.abs((((a + rot) % 45f) + 45f) % 45f - 22.5f);
                    float ray = Math.max(0f, 1f - ang / rayWidth[Math.round(a) + 180]) * (1f - (r - HR) / 50f);
                    float f = (DISC[Math.min(63, (int) ((r - HR) / 22f * 64f))] + ray * 0.9f) * eclipse;
                    if (f > BAYER[(x & 3) + ((y & 3) << 2)]) {
                        buf[i] = f > 0.62f ? core : f > 0.34f ? glow2 : glow;
                    }
                }
            }
        }
        if (hovMe) {
            for (int i : HEAD_RIM) {
                buf[i] = acc;
            }
        }
        if (moonUp && eclipse < 0.9f) {
            // Sol's pixel moon: a pale square with its craters, in place of the sun below the horizon.
            int mx = sx - 5, my = sy - 5;
            box(mx, my, 10, 10, moon);
            box(mx, my, 10, 1, moonHi);
            box(mx + 2, my + 2, 2, 2, crater);
            box(mx + 6, my + 4, 3, 2, crater);
            box(mx + 3, my + 7, 2, 1, crater);
            box(mx + 8, my + 8, 1, 1, crater);
        }
    }

    /** What the rings' look depends on: their size, segments and colours. */
    private long layerKey(List<Ring> rings, float k) {
        long h = Float.floatToIntBits(k);
        h = h * 31 + edge;
        h = h * 31 + c1;
        h = h * 31 + hi;
        h = h * 31 + rec;
        h = h * 31 + acc;
        h = h * 31 + accHi;
        h = h * 31 + tint;
        for (Ring R : rings) {
            h = h * 31 + Float.floatToIntBits(R.r0);
            h = h * 31 + Float.floatToIntBits(R.r1);
            h = h * 31 + Float.floatToIntBits(R.start);
            for (Seg s : R.segs) {
                h = h * 31 + s.kind;
                h = h * 31 + s.cIn;
                h = h * 31 + s.cMid;
                h = h * 31 + s.cOut;
                h = h * 31 + s.cHot;
                h = h * 31 + (s.current ? 1 : 0);
            }
            h = h * 31 + R.segs.size();
        }
        return h;
    }

    /** The rings as nothing points at them, and your head's disc. */
    private void drawLayer(List<Ring> rings, float k) {
        java.util.Arrays.fill(layer, 0);
        java.util.Arrays.fill(segOf, 0);
        float outer = HR;
        for (Ring R : rings) {
            outer = Math.max(outer, HR + (R.r1 - HR) * k);
        }
        int lo = Math.max(0, (int) (WC - outer - 1f)), hiI = Math.min(WN, (int) (WC + outer + 2f));
        int nr = rings.size();
        for (int y = lo; y < hiI; y++) {
            for (int x = lo; x < hiI; x++) {
                int i = y * WN + x;
                float r = PR[i];
                if (r < HR) {
                    layer[i] = r >= HR - 2f ? edge : r >= HR - 4f ? hi : rec;
                    continue;
                }
                if (k <= 0f) {
                    continue;
                }
                for (int ri = 0; ri < nr; ri++) {
                    Ring R = rings.get(ri);
                    float ra = HR + (R.r0 - HR) * k, rb = HR + (R.r1 - HR) * k;
                    if (r < ra || r >= rb || R.segs.isEmpty()) {
                        continue;
                    }
                    int n = R.segs.size();
                    float span = 360f / n;
                    int idx = Math.floorMod(Math.round((PA[i] - R.start) / span), n);
                    float d = Math.abs(angDiff(PA[i], R.start + idx * span));
                    if (d >= span / 2f - (float) Math.toDegrees(3f / r)) {
                        continue;
                    }
                    layer[i] = segPixel(R, i, k, false);
                    segOf[i] = (ri << 8 | idx) + 1;
                }
            }
        }
    }

    /** A pixel of a ring's segment (one already known to lie inside it, not in the gap between two). */
    private int segPixel(Ring R, int i, float k, boolean hot) {
        float r = PR[i], a = PA[i];
        float bq = BAYER[(i % WN & 3) + ((i / WN & 3) << 2)];
        float ra = HR + (R.r0 - HR) * k, rb = HR + (R.r1 - HR) * k;
        int n = R.segs.size();
        float span = 360f / n;
        int idx = Math.floorMod(Math.round((a - R.start) / span), n);
        float d = Math.abs(angDiff(a, R.start + idx * span));
        float half = span / 2f, gap = (float) Math.toDegrees(3f / r), edgeA = (float) Math.toDegrees(2.1f / r);
        Seg s = R.segs.get(idx);
        boolean outline = r < ra + 2f || r >= rb - 2f || d >= half - gap - edgeA;
        if (s.kind == SKY) {
            float t = (r - ra) / (rb - ra);
            if (outline) {
                return hot ? accHi : s.current ? acc : edge;
            } else if (s.current && (r < ra + 4f || r >= rb - 4f || d >= half - gap - edgeA * 2f)) {
                return acc;
            }
            return hot && t < 0.5f ? s.cHot : t * 1.6f > bq + 0.55f ? s.cOut : t * 1.6f > bq ? s.cMid : s.cIn;
        } else if (outline) {
            return hot ? acc : edge;
        } else if (r >= rb - 4f) {
            return hot ? accHi : hi;
        } else if (hot) {
            return (r - ra) / (rb - ra) * 0.95f > bq ? tint : c1;
        }
        return s.kind == SAVE ? rec : c1;
    }

    private void box(int x0, int y0, int w, int h, int c) {
        for (int y = Math.max(0, y0); y < Math.min(WN, y0 + h); y++) {
            for (int x = Math.max(0, x0); x < Math.min(WN, x0 + w); x++) {
                buf[y * WN + x] = c;
            }
        }
    }

    /** The colour of a preset's segment tones (the prototype's skyItem): inner, middle, outer and pointed-at. */
    public static void skyTones(Seg s, int top, int horizon, int glow, boolean own) {
        float k = own ? 0.45f : 0f;
        int inner = Colors.mixRgb(Colors.mixRgb(horizon, glow, 0.25f), 0xFF0C0E14, k);
        int outer = Colors.mixRgb(Colors.mixRgb(top, 0xFF000000, 0.2f), 0xFF0C0E14, k);
        s.kind = SKY;
        s.cIn = inner;
        s.cMid = Colors.mixRgb(inner, outer, 0.5f);
        s.cOut = outer;
        s.cHot = Colors.mixRgb(inner, 0xFFFFFFFF, 0.2f);
    }
}
