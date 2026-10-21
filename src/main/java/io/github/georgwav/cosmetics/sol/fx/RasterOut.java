package io.github.georgwav.cosmetics.sol.fx;

import java.util.Arrays;

/**
 * Effects drawn on the CPU for a settings page's live preview (C.2): an orthographic camera turned a little toward the
 * viewer, a depth buffer the dummy and the ground fill, and the effects tested against it without writing it, added in
 * the glow pass and alpha blended in the body pass, as in the world. Low resolution on purpose: a texel stays a few
 * pixels, and the page shows the picture scaled up without smoothing. Plain arithmetic.
 */
public final class RasterOut implements FxOut {
    public final int width, height;
    public final int[] pixels;
    private final float[] depth;
    /** The camera: its turn, how many pixels a block is, and the point at the picture's middle. */
    private final float[] right = new float[3], up = new float[3], forward = new float[3];
    private float scale, cx, cy, cz;
    private boolean glow, writeDepth;
    private FxTex tex;
    private float ox, oy, oz;
    private final float[] vx = new float[4], vy = new float[4], vz = new float[4], vu = new float[4], vv = new float[4];
    private final int[] vc = new int[4];
    private int n;

    public RasterOut(int width, int height) {
        this.width = width;
        this.height = height;
        pixels = new int[width * height];
        depth = new float[width * height];
    }

    /** Looks at (x, y, z) from {@code yaw} degrees round and {@code pitch} above, {@code pixelsPerBlock} big. */
    public void camera(float x, float y, float z, float yaw, float pitch, float pixelsPerBlock) {
        double a = Math.toRadians(yaw), p = Math.toRadians(pitch);
        // Forward points from the camera into the scene.
        forward[0] = (float) (-Math.sin(a) * Math.cos(p));
        forward[1] = (float) -Math.sin(p);
        forward[2] = (float) (-Math.cos(a) * Math.cos(p));
        right[0] = (float) Math.cos(a);
        right[1] = 0f;
        right[2] = (float) -Math.sin(a);
        // up = right x forward
        up[0] = right[1] * forward[2] - right[2] * forward[1];
        up[1] = right[2] * forward[0] - right[0] * forward[2];
        up[2] = right[0] * forward[1] - right[1] * forward[0];
        scale = pixelsPerBlock;
        cx = x;
        cy = y;
        cz = z;
    }

    public void clear() {
        Arrays.fill(pixels, 0);
        Arrays.fill(depth, Float.POSITIVE_INFINITY);
    }

    /** Starts a pass: glow (added) or body (blended); {@code solid} draws opaque and fills the depth (dummy, ground). */
    public void begin(boolean glowPass, boolean solid) {
        glow = glowPass;
        writeDepth = solid;
        n = 0;
    }

    @Override
    public void use(FxTex t) {
        tex = t;
    }

    @Override
    public void origin(double x, double y, double z) {
        ox = (float) x;
        oy = (float) y;
        oz = (float) z;
    }

    @Override
    public void vertex(float x, float y, float z, float u, float v, int argb) {
        float wx = ox + x - cx, wy = oy + y - cy, wz = oz + z - cz;
        vx[n] = width / 2f + (wx * right[0] + wy * right[1] + wz * right[2]) * scale;
        vy[n] = height / 2f - (wx * up[0] + wy * up[1] + wz * up[2]) * scale;
        vz[n] = wx * forward[0] + wy * forward[1] + wz * forward[2];
        vu[n] = u;
        vv[n] = v;
        vc[n] = argb;
        if (++n == 4) {
            n = 0;
            triangle(0, 1, 2);
            triangle(0, 2, 3);
        }
    }

    private void triangle(int a, int b, int c) {
        float area = (vx[b] - vx[a]) * (vy[c] - vy[a]) - (vx[c] - vx[a]) * (vy[b] - vy[a]);
        if (Math.abs(area) < 1e-6f || tex == null) {
            return;
        }
        int minX = Math.max(0, (int) Math.floor(Math.min(vx[a], Math.min(vx[b], vx[c]))));
        int maxX = Math.min(width - 1, (int) Math.ceil(Math.max(vx[a], Math.max(vx[b], vx[c]))));
        int minY = Math.max(0, (int) Math.floor(Math.min(vy[a], Math.min(vy[b], vy[c]))));
        int maxY = Math.min(height - 1, (int) Math.ceil(Math.max(vy[a], Math.max(vy[b], vy[c]))));
        if (minX > maxX || minY > maxY) {
            return;
        }
        float inv = 1f / area;
        int col = vc[a];
        float ca = (col >>> 24) / 255f, cr = ((col >> 16) & 255) / 255f, cg = ((col >> 8) & 255) / 255f, cb = (col & 255) / 255f;
        int tw = tex.width, th = tex.height;
        int[] t = tex.argb;
        for (int py = minY; py <= maxY; py++) {
            float sy = py + 0.5f;
            for (int px = minX; px <= maxX; px++) {
                float sx = px + 0.5f;
                float w0 = ((vx[b] - sx) * (vy[c] - sy) - (vx[c] - sx) * (vy[b] - sy)) * inv;
                float w1 = ((vx[c] - sx) * (vy[a] - sy) - (vx[a] - sx) * (vy[c] - sy)) * inv;
                float w2 = 1f - w0 - w1;
                if (w0 < 0f || w1 < 0f || w2 < 0f) {
                    continue;
                }
                int i = py * width + px;
                float z = w0 * vz[a] + w1 * vz[b] + w2 * vz[c];
                if (z > depth[i] + 1e-3f) {
                    continue;
                }
                float u = w0 * vu[a] + w1 * vu[b] + w2 * vu[c], v = w0 * vv[a] + w1 * vv[b] + w2 * vv[c];
                int texel = tex.linear ? bilinear(t, tw, th, u, v)
                        : t[Math.min(th - 1, Math.max(0, (int) (v * th))) * tw + Math.min(tw - 1, Math.max(0, (int) (u * tw)))];
                float al = (texel >>> 24) / 255f * ca;
                if (al <= 0.004f) {
                    continue;
                }
                float r = ((texel >> 16) & 255) * cr, g = ((texel >> 8) & 255) * cg, bl = (texel & 255) * cb;
                int under = pixels[i];
                if (writeDepth) {
                    if (al < 0.1f) {
                        continue;
                    }
                    pixels[i] = 0xFF000000 | (int) r << 16 | (int) g << 8 | (int) bl;
                    depth[i] = z;
                } else if (glow) {
                    int ur = (under >> 16) & 255, ug = (under >> 8) & 255, ub = under & 255;
                    int ua = under >>> 24;
                    pixels[i] = Math.max(ua, (int) (al * 255f)) << 24 | Math.min(255, ur + (int) (r * al)) << 16
                            | Math.min(255, ug + (int) (g * al)) << 8 | Math.min(255, ub + (int) (bl * al));
                } else {
                    pixels[i] = over((int) r, (int) g, (int) bl, al, under);
                }
            }
        }
    }

    /** A smooth picture's colour between its four nearest pixels. */
    private static int bilinear(int[] t, int tw, int th, float u, float v) {
        float fx = u * tw - 0.5f, fy = v * th - 0.5f;
        int x0 = (int) Math.floor(fx), y0 = (int) Math.floor(fy);
        float ax = fx - x0, ay = fy - y0;
        int x1 = Math.min(tw - 1, x0 + 1), y1 = Math.min(th - 1, y0 + 1);
        x0 = Math.max(0, Math.min(tw - 1, x0));
        y0 = Math.max(0, Math.min(th - 1, y0));
        int c00 = t[y0 * tw + x0], c10 = t[y0 * tw + x1], c01 = t[y1 * tw + x0], c11 = t[y1 * tw + x1];
        int out = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            float top = (c00 >>> shift & 255) * (1f - ax) + (c10 >>> shift & 255) * ax;
            float bottom = (c01 >>> shift & 255) * (1f - ax) + (c11 >>> shift & 255) * ax;
            out |= Math.round(top * (1f - ay) + bottom * ay) << shift;
        }
        return out;
    }

    private static int over(int r, int g, int b, float a, int under) {
        float ua = (under >>> 24) / 255f;
        float keep = ua * (1f - a), outA = a + keep;
        if (outA <= 0f) {
            return 0;
        }
        int or = (int) ((r * a + ((under >> 16) & 255) * keep) / outA);
        int og = (int) ((g * a + ((under >> 8) & 255) * keep) / outA);
        int ob = (int) ((b * a + (under & 255) * keep) / outA);
        return Math.round(outA * 255f) << 24 | or << 16 | og << 8 | ob;
    }

    @Override
    public boolean glowPass() {
        return glow;
    }

    @Override
    public void right(float[] out) {
        out[0] = right[0];
        out[1] = right[1];
        out[2] = right[2];
    }

    @Override
    public void up(float[] out) {
        out[0] = up[0];
        out[1] = up[1];
        out[2] = up[2];
    }

    @Override
    public void eye(float[] out) {
        out[0] = (float) camX() - ox;
        out[1] = (float) camY() - oy;
        out[2] = (float) camZ() - oz;
    }

    @Override
    public double camX() {
        return cx - forward[0] * 20f;
    }

    @Override
    public double camY() {
        return cy - forward[1] * 20f;
    }

    @Override
    public double camZ() {
        return cz - forward[2] * 20f;
    }

    @Override
    public float ground(double x, double z, double fromY) {
        return 0f;
    }
}
