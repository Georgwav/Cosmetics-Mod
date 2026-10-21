package io.github.georgwav.cosmetics.sol.ui.quick;

import java.util.List;

/**
 * Where each label on the quick menu's wheel goes, and at what size (the prototype's fitLabels): every label lies
 * wholly inside its own segment with at least 4 px to spare, centred on the segment's middle line half way across the
 * band, allowed ±6 px across the band and ±4 px along it. Fitted by sampling the outline of the label's box against
 * the ring sector. If any label doesn't fit, all of them try 11 px, then 10 px, then the small icon, then icon and text
 * in a row. Measured in the real font through {@link Measure}; pure Java, so the tests fit every ring.
 */
public final class WheelLabels {
    /** How much a label must stay inside its segment, and the gap kept from its segment's sides (design px). */
    public static final float MARGIN = 4f, SIDE_GAP = 10f;

    /** Whether a label with this much room fits (a hair under the margin is rounding, not a pixel). */
    public static boolean fits(float room) {
        return room >= MARGIN - 0.05f;
    }
    /**
     * The tries, in order: text size, small icon (24 px, else 36), icon and text in a row. Pixel type only comes in
     * whole steps (11 and 10 are the same 8 px on a 1366 x 768 screen), so the smaller icon is tried at 12 before the
     * text gets smaller.
     */
    static final float[][] TRIES = {{12, 0, 0}, {12, 1, 0}, {11, 0, 0}, {10, 0, 0}, {10, 1, 0}, {12, 1, 1}, {10, 1, 1}};
    /** The gap between the icon and the text, stacked and in a row. */
    static final float STACK_GAP = 6f, ROW_GAP = 7f;

    /** The pixel font's size on screen, in design pixels. */
    public interface Measure {
        float width(String text, float size);

        /** The capitals' height (with the 3 px drop under them). */
        float height(float size);

        /** An icon's real size for one asked at {@code size} (icons snap to whole screen pixels per icon pixel). */
        default float icon(float size) {
            return size;
        }
    }

    /** One label: its segment (design px round the wheel's middle), what it shows, and where it went. */
    public static final class Label {
        /** The ring's inner and outer radius, the segment's middle and half its span (degrees). */
        public float r0, r1, mid, half;
        /** Its text (null for an icon alone), and whether it has an icon. */
        public String text;
        public boolean icon = true;
        /** A picture instead of icon and text (an emote), this size. */
        public float picW, picH;
        /**
         * For an icon on its own: its 12 x 12 pixels, so it is fitted by its shape rather than its square (a plus fits
         * a narrow band at any angle; its empty corners may cross the line).
         */
        public boolean[] bits;
        /** The icon's pixels as boxes round the label's middle (x0, y0, x1, y1 each), for the try being fitted. */
        float[] shape;
        // Where it went.
        public float x, y, w, h, size = 12f, room;
        public boolean small, row;

        public Label(float r0, float r1, float mid, float half, String text) {
            this.r0 = r0;
            this.r1 = r1;
            this.mid = mid;
            this.half = half;
            this.text = text;
        }
    }

    private WheelLabels() {
    }

    /** The label's box for a try. */
    static void measure(Label l, Measure m, float size, boolean small, boolean row) {
        l.size = size;
        l.small = small;
        l.row = row;
        if (l.picW > 0f) {
            l.w = l.picW;
            l.h = l.picH;
            return;
        }
        float iw = l.icon ? m.icon(small ? 24f : 36f) : 0f;
        l.shape = l.text == null && l.icon && l.bits != null ? shape(l.bits, iw) : null;
        float tw = l.text == null ? 0f : m.width(l.text, size), th = l.text == null ? 0f : m.height(size);
        if (row && l.icon && l.text != null) {
            l.w = iw + ROW_GAP + tw;
            l.h = Math.max(iw, th);
        } else {
            l.w = Math.max(iw, tw);
            l.h = iw + (l.icon && l.text != null ? STACK_GAP : 0f) + th;
        }
    }

    /**
     * Places one label (its box already measured) round the centre (cx, cy): the best spot within the allowed shift,
     * and how much room it has to spare there (negative: it doesn't fit). {@code s} is the wheel's size against the
     * prototype's 2 design px per art pixel: the paddings and the margin shrink and grow with it, so a wheel snapped to
     * whole screen pixels keeps the prototype's proportions. The room is given at the prototype's size.
     */
    static void place(Label l, float cx, float cy, float s) {
        double th = Math.toRadians(l.mid);
        float c = (float) Math.cos(th), sn = (float) Math.sin(th), tx = -sn, ty = c;
        float rIn = l.r0 + 6f * s, rOut = l.r1 - 10f * s, mid = (rIn + rOut) / 2f, half = (float) Math.toRadians(l.half);
        float gap = SIDE_GAP * s;
        float bestM = -Float.MAX_VALUE, bestX = 0f, bestY = 0f, fitM = -Float.MAX_VALUE, fitX = 0f, fitY = 0f;
        float fitT = Float.MAX_VALUE;
        for (float rr = mid - 6f; rr <= mid + 6f; rr += 1f) {
            for (float t = -4f; t <= 4f; t += 1f) {
                float at = Math.abs(t);
                if (fitT < Float.MAX_VALUE && at > fitT) {
                    continue;
                }
                float x = cx + c * rr + tx * t, y = cy + sn * rr + ty * t;
                float m = (l.shape != null ? room(x, y, l.shape, cx, cy, th, rIn, rOut, half, gap)
                        : room(x, y, l.w, l.h, cx, cy, th, rIn, rOut, half, gap)) / s;
                if (fits(m) && (at < fitT || at == fitT && m > fitM)) {
                    fitM = m;
                    fitX = x;
                    fitY = y;
                    fitT = at;
                }
                if (m > bestM) {
                    bestM = m;
                    bestX = x;
                    bestY = y;
                }
            }
        }
        if (fitT < Float.MAX_VALUE) {
            l.x = fitX;
            l.y = fitY;
            l.room = fitM;
        } else {
            l.x = bestX;
            l.y = bestY;
            l.room = bestM;
        }
    }

    /** The least room between a box (centred at x, y) and its sector's edges, sampled along its outline. */
    static float room(float x, float y, float w, float h, float cx, float cy, double th, float rIn, float rOut, float half,
                      float gap) {
        float m = Float.MAX_VALUE;
        for (int q = 0; q <= 8; q++) {
            float f = q / 8f;
            for (int side = 0; side < 4; side++) {
                float px = side < 2 ? x - w / 2f + w * f : side == 2 ? x - w / 2f : x + w / 2f;
                float py = side < 2 ? (side == 0 ? y - h / 2f : y + h / 2f) : y - h / 2f + h * f;
                m = Math.min(m, pointRoom(px, py, cx, cy, th, rIn, rOut, half, gap));
            }
        }
        return m;
    }

    /** The least room for an icon's boxes (centred at x, y). */
    static float room(float x, float y, float[] shape, float cx, float cy, double th, float rIn, float rOut, float half,
                      float gap) {
        float m = Float.MAX_VALUE;
        for (int b = 0; b < shape.length; b += 4) {
            float w = shape[b + 2] - shape[b], h = shape[b + 3] - shape[b + 1];
            m = Math.min(m, room(x + shape[b] + w / 2f, y + shape[b + 1] + h / 2f, w, h, cx, cy, th, rIn, rOut, half, gap));
        }
        return m;
    }

    /**
     * A 12 x 12 icon drawn {@code size} px wide as boxes: each row's span from its first pixel to its last, rows with
     * the same span joined.
     */
    static float[] shape(boolean[] bits, float size) {
        int n = (int) Math.round(Math.sqrt(bits.length));
        float px = size / n;
        float[] out = new float[n * 4];
        int boxes = 0, from = -1, a = -1, b = -1;
        for (int y = 0; y <= n; y++) {
            int x0 = -1, x1 = -1;
            for (int x = 0; y < n && x < n; x++) {
                if (bits[y * n + x]) {
                    x0 = x0 < 0 ? x : x0;
                    x1 = x;
                }
            }
            if (from >= 0 && (x0 != a || x1 != b)) {
                out[boxes * 4] = a * px - size / 2f;
                out[boxes * 4 + 1] = from * px - size / 2f;
                out[boxes * 4 + 2] = (b + 1) * px - size / 2f;
                out[boxes * 4 + 3] = y * px - size / 2f;
                boxes++;
                from = -1;
            }
            if (x0 >= 0 && from < 0) {
                from = y;
                a = x0;
                b = x1;
            }
        }
        return java.util.Arrays.copyOf(out, boxes * 4);
    }

    private static float pointRoom(float px, float py, float cx, float cy, double th, float rIn, float rOut, float half,
                                   float gap) {
        float dx = px - cx, dy = py - cy, r = (float) Math.hypot(dx, dy);
        double d = Math.abs(((Math.atan2(dy, dx) - th) % (Math.PI * 2) + Math.PI * 3) % (Math.PI * 2) - Math.PI);
        return Math.min(r - rIn, Math.min(rOut - r, (float) ((half - d) * r) - gap));
    }

    /**
     * Fits every label round (cx, cy) on a wheel {@code s} times the prototype's size: the first try (size, icon,
     * layout) in which all of them fit, or the last try. Returns whether they all fit.
     */
    public static boolean fit(List<Label> labels, Measure m, float cx, float cy, float s) {
        for (int i = 0; i < TRIES.length; i++) {
            float[] t = TRIES[i];
            boolean all = true;
            for (Label l : labels) {
                measure(l, m, t[0], t[1] > 0f, t[2] > 0f);
                place(l, cx, cy, s);
                all &= fits(l.room);
            }
            if (all) {
                return true;
            }
        }
        return false;
    }

    /**
     * Half the chord across a disc of radius {@code r} at {@code dy} from its middle, less 6 px: how wide a line of
     * text under your head may be on each side of the middle.
     */
    public static float chordHalf(float r, float dy) {
        return (float) Math.sqrt(Math.max(0f, r * r - dy * dy)) - 6f;
    }
}
