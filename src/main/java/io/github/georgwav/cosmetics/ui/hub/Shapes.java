package io.github.georgwav.cosmetics.ui.hub;

import io.github.georgwav.cosmetics.ui.Canvas;

/**
 * Smooth filled shapes (circles, pills, the flowing panels) for the hub. The game's GUI only fills rectangles, so a
 * shape is drawn one screen-pixel row at a time: a solid run where the row is fully inside, and see-through pixels
 * along the edge, as see-through as the edge leaves them uncovered (four samples down each pixel). That gives soft
 * edges on every version with nothing but {@link Canvas#fill}. Coordinates are screen pixels: draw inside a
 * transform scaled to them (see {@link HubScreen}).
 */
final class Shapes {
    private static final int SAMPLES = 4;

    private Shapes() {
    }

    /** A shape filled row by row: each row crosses it at most once (convex, or close to it). */
    interface Rows {
        float top();

        float bottom();

        /** Where the line at height y enters and leaves the shape, into {@code out}; false if it misses it. */
        boolean span(float y, float[] out);
    }

    static Rows circle(float cx, float cy, float r) {
        return new Rows() {
            @Override
            public float top() {
                return cy - r;
            }

            @Override
            public float bottom() {
                return cy + r;
            }

            @Override
            public boolean span(float y, float[] out) {
                float dy = y - cy;
                if (dy * dy >= r * r) {
                    return false;
                }
                float w = (float) Math.sqrt(r * r - dy * dy);
                out[0] = cx - w;
                out[1] = cx + w;
                return true;
            }
        };
    }

    /** A rectangle with round corners of radius {@code r} (half the height makes a pill). */
    static Rows roundRect(float x, float y, float w, float h, float r) {
        float rad = Math.max(0f, Math.min(r, Math.min(w, h) / 2f));
        return new Rows() {
            @Override
            public float top() {
                return y;
            }

            @Override
            public float bottom() {
                return y + h;
            }

            @Override
            public boolean span(float sy, float[] out) {
                if (sy < y || sy >= y + h) {
                    return false;
                }
                float d = sy < y + rad ? y + rad - sy : sy > y + h - rad ? sy - (y + h - rad) : 0f;
                float in = d > 0f ? rad - (float) Math.sqrt(Math.max(0f, rad * rad - d * d)) : 0f;
                out[0] = x + in;
                out[1] = x + w - in;
                return true;
            }
        };
    }

    /** A closed outline through n points (xs[i], ys[i]). */
    static Rows polygon(float[] xs, float[] ys, int n) {
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            minY = Math.min(minY, ys[i]);
            maxY = Math.max(maxY, ys[i]);
        }
        float top = minY;
        float bottom = maxY;
        return new Rows() {
            @Override
            public float top() {
                return top;
            }

            @Override
            public float bottom() {
                return bottom;
            }

            @Override
            public boolean span(float y, float[] out) {
                float left = Float.MAX_VALUE;
                float right = -Float.MAX_VALUE;
                for (int i = 0, j = n - 1; i < n; j = i++) {
                    float y0 = ys[j];
                    float y1 = ys[i];
                    if ((y0 <= y && y < y1) || (y1 <= y && y < y0)) {
                        float x = xs[j] + (y - y0) * (xs[i] - xs[j]) / (y1 - y0);
                        left = Math.min(left, x);
                        right = Math.max(right, x);
                    }
                }
                out[0] = left;
                out[1] = right;
                return right > left;
            }
        };
    }

    /** Fills the shape in an ARGB colour. */
    static void fill(Canvas g, Rows shape, int argb) {
        int alpha = argb >>> 24;
        if (alpha == 0) {
            return;
        }
        int rgb = argb & 0xFFFFFF;
        float[] lefts = new float[SAMPLES];
        float[] rights = new float[SAMPLES];
        float[] span = new float[2];
        int y0 = (int) Math.floor(shape.top());
        int y1 = (int) Math.ceil(shape.bottom());
        for (int y = y0; y < y1; y++) {
            int rows = 0;
            float minLeft = Float.MAX_VALUE;
            float maxLeft = -Float.MAX_VALUE;
            float minRight = Float.MAX_VALUE;
            float maxRight = -Float.MAX_VALUE;
            for (int s = 0; s < SAMPLES; s++) {
                if (shape.span(y + (s + 0.5f) / SAMPLES, span) && span[1] > span[0]) {
                    lefts[rows] = span[0];
                    rights[rows] = span[1];
                    rows++;
                    minLeft = Math.min(minLeft, span[0]);
                    maxLeft = Math.max(maxLeft, span[0]);
                    minRight = Math.min(minRight, span[1]);
                    maxRight = Math.max(maxRight, span[1]);
                }
            }
            if (rows == 0) {
                continue;
            }
            int from = (int) Math.floor(minLeft);
            int to = (int) Math.ceil(maxRight);
            int solidFrom = (int) Math.ceil(maxLeft);
            int solidTo = (int) Math.floor(minRight);
            if (solidTo > solidFrom) {
                // Every sample row covers these pixels whole.
                int a = alpha * rows / SAMPLES;
                g.fill(solidFrom, y, solidTo, y + 1, a << 24 | rgb);
                edge(g, y, from, solidFrom, lefts, rights, rows, alpha, rgb);
                edge(g, y, solidTo, to, lefts, rights, rows, alpha, rgb);
            } else {
                edge(g, y, from, to, lefts, rights, rows, alpha, rgb);
            }
        }
    }

    /** Pixels from x0 to x1 on row y, each as covered as the sample rows say; equal neighbours drawn as one. */
    private static void edge(Canvas g, int y, int x0, int x1, float[] lefts, float[] rights, int rows, int alpha, int rgb) {
        int runStart = x0;
        int runAlpha = -1;
        for (int x = x0; x <= x1; x++) {
            int a = -1;
            if (x < x1) {
                float cover = 0f;
                for (int s = 0; s < rows; s++) {
                    cover += Math.max(0f, Math.min(x + 1f, rights[s]) - Math.max((float) x, lefts[s]));
                }
                a = Math.round(alpha * cover / SAMPLES);
            }
            if (a != runAlpha) {
                if (runAlpha > 0) {
                    g.fill(runStart, y, x, y + 1, runAlpha << 24 | rgb);
                }
                runStart = x;
                runAlpha = a;
            }
        }
    }

    /** An ARGB colour with its alpha multiplied by {@code opacity} (0..1). */
    static int fade(int argb, float opacity) {
        int a = Math.round((argb >>> 24) * Math.max(0f, Math.min(1f, opacity)));
        return a << 24 | (argb & 0xFFFFFF);
    }

    /** Each colour channel multiplied by {@code k} (0..1 darkens). */
    static int shade(int argb, float k) {
        int r = Math.min(255, Math.round(((argb >> 16) & 0xFF) * k));
        int gr = Math.min(255, Math.round(((argb >> 8) & 0xFF) * k));
        int b = Math.min(255, Math.round((argb & 0xFF) * k));
        return argb & 0xFF000000 | r << 16 | gr << 8 | b;
    }

    /** Between two ARGB colours, t from 0 (a) to 1 (b). */
    static int mix(int a, int b, float t) {
        float k = Math.max(0f, Math.min(1f, t));
        int out = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int ca = (a >>> shift) & 0xFF;
            int cb = (b >>> shift) & 0xFF;
            out |= Math.round(ca + (cb - ca) * k) << shift;
        }
        return out;
    }
}
