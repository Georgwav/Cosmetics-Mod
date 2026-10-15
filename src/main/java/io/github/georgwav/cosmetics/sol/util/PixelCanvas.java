package io.github.georgwav.cosmetics.sol.util;

/**
 * A small CPU drawing surface with the parts of the browser canvas the Sol prototypes draw with: rectangles,
 * polygons, ellipses, linear and radial gradients, images (smooth or pixelated) under a 2D transform, a global
 * alpha, and the "source-over" and "lighter" (additive) blends. Pixels are kept premultiplied in floats, so
 * stacked glows add up like the prototypes' canvas. Everything Sol draws at pixel scale (the logo, the suns of the
 * menus, the eclipse corona, sky thumbnails, baked sprites) is drawn here and then uploaded as a texture.
 *
 * <p>Not thread safe: one canvas belongs to one thread at a time.
 */
public final class PixelCanvas {
    public final int width;
    public final int height;
    /** Premultiplied red, green, blue, alpha per pixel, row by row. */
    final float[] px;

    private float globalAlpha = 1f;
    private boolean lighter;
    // The transform from drawing units to pixels: x' = a·x + c·y + e, y' = b·x + d·y + f.
    private float ta = 1, tb = 0, tc = 0, td = 1, te = 0, tf = 0;
    /** Saved states (transform, alpha, blend), reused so save/restore allocate nothing. */
    private final float[][] saved = new float[32][8];
    private int depth;
    private final float[] quadX = new float[4];
    private final float[] quadY = new float[4];
    private final float[] scratch = new float[4];
    private final float[] uv = new float[2];
    /** The one solid paint the int-colour calls reuse, so drawing a frame allocates nothing. */
    private final SolidPaint solidPaint = new SolidPaint();
    private float[] polyX = new float[8];
    private float[] polyY = new float[8];

    private static final class SolidPaint implements Paint {
        float r, g, b, a;

        SolidPaint set(int argb) {
            a = Colors.a(argb) / 255f;
            r = Colors.r(argb) / 255f * a;
            g = Colors.g(argb) / 255f * a;
            b = Colors.b(argb) / 255f * a;
            return this;
        }

        @Override
        public void at(float x, float y, float[] out) {
            out[0] = r; out[1] = g; out[2] = b; out[3] = a;
        }
    }

    public PixelCanvas(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Canvas size " + width + "x" + height);
        }
        this.width = width;
        this.height = height;
        this.px = new float[width * height * 4];
    }

    // ------------------------------------------------------------------ state

    public void setGlobalAlpha(float alpha) {
        this.globalAlpha = Math.max(0f, Math.min(1f, alpha));
    }

    public float globalAlpha() {
        return globalAlpha;
    }

    /** True: "lighter" (additive) blending; false: normal "source-over". */
    public void setLighter(boolean lighter) {
        this.lighter = lighter;
    }

    public void save() {
        if (depth >= saved.length) {
            throw new IllegalStateException("PixelCanvas.save() nested too deep");
        }
        float[] s = saved[depth++];
        s[0] = ta; s[1] = tb; s[2] = tc; s[3] = td; s[4] = te; s[5] = tf;
        s[6] = globalAlpha;
        s[7] = lighter ? 1 : 0;
    }

    public void restore() {
        if (depth == 0) {
            return;
        }
        float[] s = saved[--depth];
        ta = s[0]; tb = s[1]; tc = s[2]; td = s[3]; te = s[4]; tf = s[5];
        globalAlpha = s[6];
        lighter = s[7] != 0;
    }

    public void resetTransform() {
        ta = 1; tb = 0; tc = 0; td = 1; te = 0; tf = 0;
    }

    public void translate(float x, float y) {
        te += ta * x + tc * y;
        tf += tb * x + td * y;
    }

    public void scale(float sx, float sy) {
        ta *= sx; tb *= sx;
        tc *= sy; td *= sy;
    }

    public void rotate(float radians) {
        float cos = (float) Math.cos(radians), sin = (float) Math.sin(radians);
        float na = ta * cos + tc * sin, nb = tb * cos + td * sin;
        float nc = -ta * sin + tc * cos, nd = -tb * sin + td * cos;
        ta = na; tb = nb; tc = nc; td = nd;
    }

    private boolean axisAligned() {
        return tb == 0f && tc == 0f;
    }

    // ------------------------------------------------------------------ pixels

    public void clear() {
        java.util.Arrays.fill(px, 0f);
    }

    /** Fills the whole canvas with one colour (no blending). */
    public void fillAll(int argb) {
        float a = Colors.a(argb) / 255f;
        float r = Colors.r(argb) / 255f * a, g = Colors.g(argb) / 255f * a, b = Colors.b(argb) / 255f * a;
        for (int i = 0; i < px.length; i += 4) {
            px[i] = r; px[i + 1] = g; px[i + 2] = b; px[i + 3] = a;
        }
    }

    /** Blends one premultiplied colour, scaled by {@code coverage}, into pixel (x, y). */
    private void blend(int x, int y, float r, float g, float b, float a, float coverage) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return;
        }
        float k = coverage * globalAlpha;
        if (k <= 0f) {
            return;
        }
        r *= k; g *= k; b *= k; a *= k;
        int i = (y * width + x) * 4;
        if (lighter) {
            px[i] = Math.min(1f, px[i] + r);
            px[i + 1] = Math.min(1f, px[i + 1] + g);
            px[i + 2] = Math.min(1f, px[i + 2] + b);
            px[i + 3] = Math.min(1f, px[i + 3] + a);
        } else {
            float inv = 1f - a;
            px[i] = r + px[i] * inv;
            px[i + 1] = g + px[i + 1] * inv;
            px[i + 2] = b + px[i + 2] * inv;
            px[i + 3] = a + px[i + 3] * inv;
        }
    }

    /** Sets a pixel outright (no blending, no transform), from a straight ARGB colour. */
    public void setPixel(int x, int y, int argb) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return;
        }
        float a = Colors.a(argb) / 255f;
        int i = (y * width + x) * 4;
        px[i] = Colors.r(argb) / 255f * a;
        px[i + 1] = Colors.g(argb) / 255f * a;
        px[i + 2] = Colors.b(argb) / 255f * a;
        px[i + 3] = a;
    }

    /** The pixel as straight ARGB. */
    public int getPixel(int x, int y) {
        int i = (y * width + x) * 4;
        return unpremultiply(px[i], px[i + 1], px[i + 2], px[i + 3]);
    }

    private static int unpremultiply(float r, float g, float b, float a) {
        if (a <= 1e-6f) {
            return 0;
        }
        return Colors.argb(Math.round(a * 255f), Math.round(r / a * 255f), Math.round(g / a * 255f),
                Math.round(b / a * 255f));
    }

    /** Copies the canvas into {@code out} as straight ARGB, row by row. */
    public int[] toArgb(int[] out) {
        if (out == null || out.length < width * height) {
            out = new int[width * height];
        }
        for (int p = 0, i = 0; p < width * height; p++, i += 4) {
            out[p] = unpremultiply(px[i], px[i + 1], px[i + 2], px[i + 3]);
        }
        return out;
    }

    // ------------------------------------------------------------------ paints

    /** How a shape is coloured: one colour, or a gradient in drawing units. */
    public interface Paint {
        /** Writes the premultiplied colour at drawing-unit point (x, y) into {@code out}. */
        void at(float x, float y, float[] out);
    }

    public static Paint solid(int argb) {
        float a = Colors.a(argb) / 255f;
        float r = Colors.r(argb) / 255f * a, g = Colors.g(argb) / 255f * a, b = Colors.b(argb) / 255f * a;
        return (x, y, out) -> {
            out[0] = r; out[1] = g; out[2] = b; out[3] = a;
        };
    }

    /** Gradient stops: offsets 0..1 and straight ARGB colours. */
    public record Stops(float[] offsets, int[] colors) {
        public static Stops of(Object... pairs) {
            float[] o = new float[pairs.length / 2];
            int[] c = new int[pairs.length / 2];
            for (int i = 0; i < o.length; i++) {
                o[i] = ((Number) pairs[i * 2]).floatValue();
                c[i] = (Integer) pairs[i * 2 + 1];
            }
            return new Stops(o, c);
        }

        void sample(float t, float[] out) {
            int n = offsets.length;
            int c;
            if (t <= offsets[0]) {
                c = colors[0];
            } else if (t >= offsets[n - 1]) {
                c = colors[n - 1];
            } else {
                int i = 0;
                while (i < n - 2 && t > offsets[i + 1]) {
                    i++;
                }
                float span = offsets[i + 1] - offsets[i];
                float k = span <= 0 ? 0 : (t - offsets[i]) / span;
                // Premultiplied interpolation, as browsers do.
                float a0 = Colors.a(colors[i]) / 255f, a1 = Colors.a(colors[i + 1]) / 255f;
                out[3] = a0 + (a1 - a0) * k;
                out[0] = (Colors.r(colors[i]) / 255f * a0) * (1 - k) + (Colors.r(colors[i + 1]) / 255f * a1) * k;
                out[1] = (Colors.g(colors[i]) / 255f * a0) * (1 - k) + (Colors.g(colors[i + 1]) / 255f * a1) * k;
                out[2] = (Colors.b(colors[i]) / 255f * a0) * (1 - k) + (Colors.b(colors[i + 1]) / 255f * a1) * k;
                return;
            }
            float a = Colors.a(c) / 255f;
            out[0] = Colors.r(c) / 255f * a;
            out[1] = Colors.g(c) / 255f * a;
            out[2] = Colors.b(c) / 255f * a;
            out[3] = a;
        }
    }

    public static Paint linear(float x0, float y0, float x1, float y1, Stops stops) {
        float dx = x1 - x0, dy = y1 - y0, len2 = dx * dx + dy * dy;
        return (x, y, out) -> stops.sample(len2 <= 0 ? 0 : ((x - x0) * dx + (y - y0) * dy) / len2, out);
    }

    /** A radial gradient from the centre (offset 0) to radius {@code r} (offset 1). */
    public static Paint radial(float cx, float cy, float r, Stops stops) {
        return (x, y, out) -> stops.sample((float) Math.hypot(x - cx, y - cy) / r, out);
    }

    // ------------------------------------------------------------------ shapes

    /** Inverse of the current transform, applied to a pixel centre: drawing-unit x, y into {@code out}. */
    private boolean inverse(float[] out, float x, float y) {
        float det = ta * td - tb * tc;
        if (Math.abs(det) < 1e-9f) {
            return false;
        }
        float dx = x - te, dy = y - tf;
        out[0] = (td * dx - tc * dy) / det;
        out[1] = (-tb * dx + ta * dy) / det;
        return true;
    }

    public void fillRect(float x, float y, float w, float h, int argb) {
        fillRect(x, y, w, h, solidPaint.set(argb));
    }

    public void fillRect(float x, float y, float w, float h, Paint paint) {
        if (w <= 0 || h <= 0) {
            return;
        }
        if (axisAligned()) {
            float x0 = ta * x + te, x1 = ta * (x + w) + te, y0 = td * y + tf, y1 = td * (y + h) + tf;
            if (x0 > x1) { float s = x0; x0 = x1; x1 = s; }
            if (y0 > y1) { float s = y0; y0 = y1; y1 = s; }
            int ix0 = Math.max(0, (int) Math.floor(x0)), ix1 = Math.min(width, (int) Math.ceil(x1));
            int iy0 = Math.max(0, (int) Math.floor(y0)), iy1 = Math.min(height, (int) Math.ceil(y1));
            for (int py = iy0; py < iy1; py++) {
                float cy = Math.min(py + 1, y1) - Math.max(py, y0);
                for (int pxl = ix0; pxl < ix1; pxl++) {
                    float cx = Math.min(pxl + 1, x1) - Math.max(pxl, x0);
                    float coverage = cx * cy;
                    if (coverage <= 0f) {
                        continue;
                    }
                    inverse(uv, pxl + 0.5f, py + 0.5f);
                    paint.at(uv[0], uv[1], scratch);
                    blend(pxl, py, scratch[0], scratch[1], scratch[2], scratch[3], Math.min(1f, coverage));
                }
            }
            return;
        }
        quadX[0] = x; quadX[1] = x + w; quadX[2] = x + w; quadX[3] = x;
        quadY[0] = y; quadY[1] = y; quadY[2] = y + h; quadY[3] = y + h;
        fillPolygon(quadX, quadY, paint);
    }

    /** Outline of a rectangle, {@code lineWidth} drawing units wide, centred on the edge like the canvas. */
    public void strokeRect(float x, float y, float w, float h, float lineWidth, int argb) {
        float hw = lineWidth / 2f;
        fillRect(x - hw, y - hw, w + lineWidth, lineWidth, argb);
        fillRect(x - hw, y + h - hw, w + lineWidth, lineWidth, argb);
        fillRect(x - hw, y + hw, lineWidth, h - lineWidth, argb);
        fillRect(x + w - hw, y + hw, lineWidth, h - lineWidth, argb);
    }

    public void fillPolygon(float[] xs, float[] ys, int argb) {
        fillPolygon(xs, ys, solidPaint.set(argb));
    }

    /** Fills a polygon (even-odd), anti-aliased with 4×4 samples per pixel. */
    public void fillPolygon(float[] xs, float[] ys, Paint paint) {
        int n = xs.length;
        if (polyX.length < n) {
            polyX = new float[n];
            polyY = new float[n];
        }
        float[] dx = polyX, dy = polyY;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            dx[i] = ta * xs[i] + tc * ys[i] + te;
            dy[i] = tb * xs[i] + td * ys[i] + tf;
            minX = Math.min(minX, dx[i]); maxX = Math.max(maxX, dx[i]);
            minY = Math.min(minY, dy[i]); maxY = Math.max(maxY, dy[i]);
        }
        int ix0 = Math.max(0, (int) Math.floor(minX)), ix1 = Math.min(width, (int) Math.ceil(maxX));
        int iy0 = Math.max(0, (int) Math.floor(minY)), iy1 = Math.min(height, (int) Math.ceil(maxY));
        for (int py = iy0; py < iy1; py++) {
            for (int pxl = ix0; pxl < ix1; pxl++) {
                int inside = 0;
                for (int sy = 0; sy < 4; sy++) {
                    float sampleY = py + (sy + 0.5f) / 4f;
                    for (int sx = 0; sx < 4; sx++) {
                        float sampleX = pxl + (sx + 0.5f) / 4f;
                        if (contains(dx, dy, n, sampleX, sampleY)) {
                            inside++;
                        }
                    }
                }
                if (inside == 0) {
                    continue;
                }
                inverse(uv, pxl + 0.5f, py + 0.5f);
                paint.at(uv[0], uv[1], scratch);
                blend(pxl, py, scratch[0], scratch[1], scratch[2], scratch[3], inside / 16f);
            }
        }
    }

    private static boolean contains(float[] xs, float[] ys, int n, float x, float y) {
        boolean in = false;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            if ((ys[i] > y) != (ys[j] > y) && x < (xs[j] - xs[i]) * (y - ys[i]) / (ys[j] - ys[i]) + xs[i]) {
                in = !in;
            }
        }
        return in;
    }

    public void fillEllipse(float cx, float cy, float rx, float ry, int argb) {
        fillEllipse(cx, cy, rx, ry, solidPaint.set(argb));
    }

    /** Fills an ellipse (in drawing units, so a rotated transform turns it), 4×4 samples per pixel. */
    public void fillEllipse(float cx, float cy, float rx, float ry, Paint paint) {
        if (rx <= 0 || ry <= 0) {
            return;
        }
        // Device-space bounding box of the transformed ellipse.
        float ex = (float) Math.sqrt(ta * ta * rx * rx + tc * tc * ry * ry);
        float ey = (float) Math.sqrt(tb * tb * rx * rx + td * td * ry * ry);
        float dcx = ta * cx + tc * cy + te, dcy = tb * cx + td * cy + tf;
        int ix0 = Math.max(0, (int) Math.floor(dcx - ex)), ix1 = Math.min(width, (int) Math.ceil(dcx + ex));
        int iy0 = Math.max(0, (int) Math.floor(dcy - ey)), iy1 = Math.min(height, (int) Math.ceil(dcy + ey));
        for (int py = iy0; py < iy1; py++) {
            for (int pxl = ix0; pxl < ix1; pxl++) {
                int inside = 0;
                for (int sy = 0; sy < 4; sy++) {
                    for (int sx = 0; sx < 4; sx++) {
                        if (!inverse(uv, pxl + (sx + 0.5f) / 4f, py + (sy + 0.5f) / 4f)) {
                            continue;
                        }
                        float nx = (uv[0] - cx) / rx, ny = (uv[1] - cy) / ry;
                        if (nx * nx + ny * ny <= 1f) {
                            inside++;
                        }
                    }
                }
                if (inside == 0) {
                    continue;
                }
                inverse(uv, pxl + 0.5f, py + 0.5f);
                paint.at(uv[0], uv[1], scratch);
                blend(pxl, py, scratch[0], scratch[1], scratch[2], scratch[3], inside / 16f);
            }
        }
    }

    /** A ring of the given line width around a circle (the sun dogs' halo, the dial's track). */
    public void strokeCircle(float cx, float cy, float r, float lineWidth, int argb) {
        Paint paint = solidPaint.set(argb);
        float outer = r + lineWidth / 2f, inner = Math.max(0f, r - lineWidth / 2f);
        float scale = (float) Math.sqrt(Math.abs(ta * td - tb * tc));
        float dcx = ta * cx + tc * cy + te, dcy = tb * cx + td * cy + tf, dr = outer * Math.max(scale, 1e-6f) + 1;
        int ix0 = Math.max(0, (int) Math.floor(dcx - dr)), ix1 = Math.min(width, (int) Math.ceil(dcx + dr));
        int iy0 = Math.max(0, (int) Math.floor(dcy - dr)), iy1 = Math.min(height, (int) Math.ceil(dcy + dr));
        for (int py = iy0; py < iy1; py++) {
            for (int pxl = ix0; pxl < ix1; pxl++) {
                int inside = 0;
                for (int sy = 0; sy < 4; sy++) {
                    for (int sx = 0; sx < 4; sx++) {
                        if (!inverse(uv, pxl + (sx + 0.5f) / 4f, py + (sy + 0.5f) / 4f)) {
                            continue;
                        }
                        float d = (float) Math.hypot(uv[0] - cx, uv[1] - cy);
                        if (d <= outer && d >= inner) {
                            inside++;
                        }
                    }
                }
                if (inside > 0) {
                    paint.at(0, 0, scratch);
                    blend(pxl, py, scratch[0], scratch[1], scratch[2], scratch[3], inside / 16f);
                }
            }
        }
    }

    /** A straight line {@code lineWidth} wide (with square ends). */
    public void line(float x0, float y0, float x1, float y1, float lineWidth, Paint paint) {
        float dx = x1 - x0, dy = y1 - y0, len = (float) Math.hypot(dx, dy);
        if (len <= 0) {
            return;
        }
        float nx = -dy / len * lineWidth / 2f, ny = dx / len * lineWidth / 2f;
        quadX[0] = x0 + nx; quadX[1] = x1 + nx; quadX[2] = x1 - nx; quadX[3] = x0 - nx;
        quadY[0] = y0 + ny; quadY[1] = y1 + ny; quadY[2] = y1 - ny; quadY[3] = y0 - ny;
        fillPolygon(quadX, quadY, paint);
    }

    // ------------------------------------------------------------------ images

    /**
     * Draws {@code src} into the rectangle (dx, dy, dw, dh) in drawing units: smooth (bilinear) or pixelated
     * (nearest), under the current transform, global alpha and blend.
     */
    public void drawImage(PixelCanvas src, float dx, float dy, float dw, float dh, boolean smooth) {
        drawImage(src, 0, 0, src.width, src.height, dx, dy, dw, dh, smooth);
    }

    /** Draws the part (sx, sy, sw, sh) of {@code src}. */
    public void drawImage(PixelCanvas src, float sx, float sy, float sw, float sh, float dx, float dy, float dw, float dh,
                          boolean smooth) {
        if (dw == 0 || dh == 0 || sw <= 0 || sh <= 0) {
            return;
        }
        float[] cx = {dx, dx + dw, dx + dw, dx};
        float[] cy = {dy, dy, dy + dh, dy + dh};
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            float x = ta * cx[i] + tc * cy[i] + te, y = tb * cx[i] + td * cy[i] + tf;
            minX = Math.min(minX, x); maxX = Math.max(maxX, x);
            minY = Math.min(minY, y); maxY = Math.max(maxY, y);
        }
        int ix0 = Math.max(0, (int) Math.floor(minX)), ix1 = Math.min(width, (int) Math.ceil(maxX));
        int iy0 = Math.max(0, (int) Math.floor(minY)), iy1 = Math.min(height, (int) Math.ceil(maxY));
        float rx0 = Math.min(dx, dx + dw), rx1 = Math.max(dx, dx + dw), ry0 = Math.min(dy, dy + dh), ry1 = Math.max(dy, dy + dh);
        for (int py = iy0; py < iy1; py++) {
            for (int pxl = ix0; pxl < ix1; pxl++) {
                if (!inverse(uv, pxl + 0.5f, py + 0.5f)) {
                    continue;
                }
                float ux = uv[0], uy = uv[1];
                if (ux < rx0 || ux >= rx1 || uy < ry0 || uy >= ry1) {
                    continue;
                }
                float fx = sx + (ux - dx) / dw * sw, fy = sy + (uy - dy) / dh * sh;
                if (smooth) {
                    src.sampleBilinear(fx - 0.5f, fy - 0.5f, scratch);
                } else {
                    src.sampleNearest(fx, fy, scratch);
                }
                if (scratch[3] <= 0f) {
                    continue;
                }
                blend(pxl, py, scratch[0], scratch[1], scratch[2], scratch[3], 1f);
            }
        }
    }

    void sampleNearest(float x, float y, float[] out) {
        int ix = Math.max(0, Math.min(width - 1, (int) Math.floor(x)));
        int iy = Math.max(0, Math.min(height - 1, (int) Math.floor(y)));
        int i = (iy * width + ix) * 4;
        out[0] = px[i]; out[1] = px[i + 1]; out[2] = px[i + 2]; out[3] = px[i + 3];
    }

    void sampleBilinear(float x, float y, float[] out) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        float fx = x - x0, fy = y - y0;
        out[0] = out[1] = out[2] = out[3] = 0f;
        for (int j = 0; j < 2; j++) {
            for (int i = 0; i < 2; i++) {
                int xx = Math.max(0, Math.min(width - 1, x0 + i)), yy = Math.max(0, Math.min(height - 1, y0 + j));
                float w = (i == 0 ? 1 - fx : fx) * (j == 0 ? 1 - fy : fy);
                int k = (yy * width + xx) * 4;
                out[0] += px[k] * w; out[1] += px[k + 1] * w; out[2] += px[k + 2] * w; out[3] += px[k + 3] * w;
            }
        }
    }

    /** A canvas holding the given straight ARGB pixels. */
    public static PixelCanvas fromArgb(int width, int height, int[] argb) {
        PixelCanvas c = new PixelCanvas(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                c.setPixel(x, y, argb[y * width + x]);
            }
        }
        return c;
    }
}
