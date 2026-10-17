package io.github.georgwav.cosmetics.sol.atmosphere;

import io.github.georgwav.cosmetics.sol.render.FrameInfo;
import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.sol.render.WorldDraw;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.sol.util.Mulberry32;

/**
 * Sakura's petals and blossoms in 3D. A petal is a small curved surface, cupped across its width and curling back
 * at the tip, that turns and tumbles as it falls; a blossom is five of them round a centre of stamens. The petal's
 * texture is detailed (a notched tip, fine veins, a deeper pink at the base, paler edges) and baked at several
 * sizes, so a petal close to you is as sharp as one far off. Each vertex is lit: the side toward the theme's light
 * takes its colour, a petal with the light behind it glows through, and an edge turned toward you catches a sheen.
 * Render thread only; nothing is allocated per frame.
 */
final class Petals {
    /** The atlas: the petal at {@link #LEVELS} in the top half; the out-of-focus petal and the stamens below. */
    static final int W = 256, H = 256;
    static final int[] LEVELS = {128, 64, 32, 16};
    private static final int[] LEVEL_X = {0, 128, 192, 224};
    /** The out-of-focus petal (a lens petal) and the blossom's centre, in the bottom half. */
    private static final int BLUR_X = 0, BLUR_Y = 128, BLUR_S = 128, STAMEN_X = 128, STAMEN_Y = 128, STAMEN_S = 64;
    /** How the surface bends: the sides rise by this much of the half-width, the tip curls back by this much. */
    private static final float CUP = 0.42f, CURL = 0.22f;
    /** Rows and columns of quads on a petal close up (far ones are a single quad). */
    private static final int ROWS = 3, COLS = 2;

    private static SolTexture texture;

    // The light this frame (see light()).
    private static float lx, ly = 1f, lz, sun, ambient;
    private static float sunR = 1f, sunG = 1f, sunB = 1f, backR = 1f, backG = 1f, backB = 1f;
    private static float pxPerBlock = 700f;

    // Scratch: the petal's frame, a vertex row's positions and colours.
    private static final float[] basis = new float[9];
    private static final float[] rowX = new float[(COLS + 1) * (ROWS + 1)];
    private static final float[] rowY = new float[rowX.length];
    private static final float[] rowZ = new float[rowX.length];
    private static final int[] rowC = new int[rowX.length];
    private static final float[] uv = new float[4];

    private Petals() {
    }

    static SolTexture texture() {
        if (texture == null || texture.released()) {
            texture = SolTexture.of("petals", W, H, true, bake());
        }
        return texture;
    }

    /**
     * The light for this frame's petals: the direction toward the theme's light, how strongly it lights them, its
     * colour on the lit side and through the petal, and the light everywhere else.
     */
    static void light(float strength, int sunTint, int backColor, float everywhere, float screenPxPerBlock) {
        lx = KeyLight.x;
        ly = KeyLight.y;
        lz = KeyLight.z;
        sun = strength;
        ambient = everywhere;
        sunR = Colors.r(sunTint) / 255f;
        sunG = Colors.g(sunTint) / 255f;
        sunB = Colors.b(sunTint) / 255f;
        backR = Colors.r(backColor) / 255f;
        backG = Colors.g(backColor) / 255f;
        backB = Colors.b(backColor) / 255f;
        pxPerBlock = screenPxPerBlock;
    }

    // ------------------------------------------------------------------ drawing

    /**
     * One petal at camera-relative (x, y, z): {@code len} long and {@code wid} wide (blocks), turned by yaw, tumbled
     * by flip and rocked by roll (radians).
     */
    static void petal(float x, float y, float z, float len, float wid, float yaw, float flip, float roll, int color,
                      float alpha) {
        frame(yaw, flip, roll);
        float d = Math.max(0.3f, (float) Math.sqrt(x * x + y * y + z * z));
        surface(x, y, z, basis[0], basis[1], basis[2], basis[3], basis[4], basis[5], basis[6], basis[7], basis[8],
                len, wid, -0.45f, color, alpha, len * pxPerBlock / d);
    }

    /** A blossom at (x, y, z), {@code r} across its petals: five petals cupped round a centre of stamens. */
    static void blossom(float x, float y, float z, float r, float yaw, float flip, float roll, int color, float alpha) {
        frame(yaw, flip, roll);
        float bx = basis[0], by = basis[1], bz = basis[2];
        float tx = basis[3], ty = basis[4], tz = basis[5];
        float nx = basis[6], ny = basis[7], nz = basis[8];
        float d = Math.max(0.3f, (float) Math.sqrt(x * x + y * y + z * z));
        float px = r * pxPerBlock / d;
        // Each petal leans up out of the flower's plane, so the blossom is a shallow cup.
        float tilt = 0.5f, ct = (float) Math.cos(tilt), st = (float) Math.sin(tilt);
        float len = r * 0.58f, wid = r * 0.36f;
        for (int k = 0; k < 5; k++) {
            double a = k * Math.PI * 2 / 5;
            float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
            // Outward in the plane, then up toward the normal.
            float ox = bx * ca + tx * sa, oy = by * ca + ty * sa, oz = bz * ca + tz * sa;
            float pTx = ox * ct + nx * st, pTy = oy * ct + ny * st, pTz = oz * ct + nz * st;
            float pBx = -bx * sa + tx * ca, pBy = -by * sa + ty * ca, pBz = -bz * sa + tz * ca;
            // Normal = petal across × petal along (keeps the cup facing the flower's normal).
            float pNx = pBy * pTz - pBz * pTy, pNy = pBz * pTx - pBx * pTz, pNz = pBx * pTy - pBy * pTx;
            float baseX = x + ox * r * 0.08f, baseY = y + oy * r * 0.08f, baseZ = z + oz * r * 0.08f;
            surface(baseX, baseY, baseZ, pBx, pBy, pBz, pTx, pTy, pTz, pNx, pNy, pNz, len, wid, 0f, color, alpha, px * 0.6f);
        }
        stamens(x + nx * r * 0.05f, y + ny * r * 0.05f, z + nz * r * 0.05f, bx, by, bz, tx, ty, tz, nx, ny, nz, r * 0.24f,
                alpha);
    }

    /** Where the out-of-focus petal is in the atlas: {u0, v0, u1, v1}. */
    static void blurUv(float[] out) {
        out[0] = BLUR_X / (float) W;
        out[1] = BLUR_Y / (float) H;
        out[2] = (BLUR_X + BLUR_S) / (float) W;
        out[3] = (BLUR_Y + BLUR_S) / (float) H;
    }

    /** The petal's frame (across, along, normal) for yaw, then flip, then roll. */
    private static void frame(float yaw, float flip, float roll) {
        float cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw);
        float cf = (float) Math.cos(flip), sf = (float) Math.sin(flip);
        float cr = (float) Math.cos(roll), sr = (float) Math.sin(roll);
        // R = Ry(yaw) · Rx(flip) · Rz(roll), applied to the local axes (1,0,0), (0,1,0), (0,0,1).
        rotate(cr, sr, 0f, cy, sy, cf, sf, 0);
        rotate(-sr, cr, 0f, cy, sy, cf, sf, 3);
        rotate(0f, 0f, 1f, cy, sy, cf, sf, 6);
    }

    private static void rotate(float x, float y, float z, float cy, float sy, float cf, float sf, int at) {
        float y1 = y * cf - z * sf, z1 = y * sf + z * cf;
        basis[at] = x * cy + z1 * sy;
        basis[at + 1] = y1;
        basis[at + 2] = -x * sy + z1 * cy;
    }

    /**
     * A petal surface from {@code (ox, oy, oz)}: across B, along T (base to tip) and normal N; the base sits
     * {@code pivot} of the length behind the origin. {@code px} is its length on screen, which picks the texture
     * size and how finely it's bent.
     */
    private static void surface(float ox, float oy, float oz, float bx, float by, float bz, float tx, float ty, float tz,
                                float nx, float ny, float nz, float len, float wid, float pivot, int color, float alpha,
                                float px) {
        int level = LEVELS.length - 1;
        while (level > 0 && LEVELS[level] < px * 1.2f) {
            level--;
        }
        int s = LEVELS[level];
        float u0 = LEVEL_X[level] / (float) W, u1 = (LEVEL_X[level] + s) / (float) W;
        float v0 = 0f, v1 = s / (float) H;
        boolean fine = px > 6f;
        int cols = fine ? COLS : 1, rows = fine ? ROWS : 1;
        float cr = Colors.r(color) / 255f, cg = Colors.g(color) / 255f, cb = Colors.b(color) / 255f;
        int n = 0;
        for (int j = 0; j <= rows; j++) {
            float ss = j / (float) rows;
            for (int i = 0; i <= cols; i++) {
                float uu = i / (float) cols * 2f - 1f;
                // The surface: across, along, and bent (cupped across, curling back at the tip).
                float a = uu * wid, b = (ss + pivot) * len;
                float c = fine ? CUP * wid * uu * uu - CURL * len * ss * ss : 0f;
                float x = ox + bx * a + tx * b + nx * c;
                float y = oy + by * a + ty * b + ny * c;
                float z = oz + bz * a + tz * b + nz * c;
                // Its normal in the petal's frame: the cross of the surface's two slopes, (-2·CUP·u, 2·CURL·s, 1).
                float lnx = fine ? -2f * CUP * uu : 0f, lny = fine ? 2f * CURL * ss : 0f;
                float wx = bx * lnx + tx * lny + nx, wy = by * lnx + ty * lny + ny, wz = bz * lnx + tz * lny + nz;
                float wl = (float) Math.sqrt(wx * wx + wy * wy + wz * wz);
                rowX[n] = x;
                rowY[n] = y;
                rowZ[n] = z;
                rowC[n] = shade(x, y, z, wx / wl, wy / wl, wz / wl, cr, cg, cb, alpha);
                n++;
            }
        }
        int stride = cols + 1;
        for (int j = 0; j < rows; j++) {
            float va = v1 - (j / (float) rows) * (v1 - v0), vb = v1 - ((j + 1) / (float) rows) * (v1 - v0);
            for (int i = 0; i < cols; i++) {
                float ua = u0 + (i / (float) cols) * (u1 - u0), ub = u0 + ((i + 1) / (float) cols) * (u1 - u0);
                int k00 = j * stride + i, k10 = k00 + 1, k01 = k00 + stride, k11 = k01 + 1;
                vertex(k00, ua, va);
                vertex(k01, ua, vb);
                vertex(k11, ub, vb);
                vertex(k10, ub, va);
            }
        }
    }

    /** The blossom's centre: a flat disc of stamens in the flower's plane, in their own colours. */
    private static void stamens(float x, float y, float z, float bx, float by, float bz, float tx, float ty, float tz,
                                float nx, float ny, float nz, float r, float alpha) {
        int c = shade(x, y, z, nx, ny, nz, 1f, 1f, 1f, alpha);
        float u0 = STAMEN_X / (float) W, v0 = STAMEN_Y / (float) H;
        float u1 = (STAMEN_X + STAMEN_S) / (float) W, v1 = (STAMEN_Y + STAMEN_S) / (float) H;
        put(x - bx * r - tx * r, y - by * r - ty * r, z - bz * r - tz * r, u0, v1, c);
        put(x - bx * r + tx * r, y - by * r + ty * r, z - bz * r + tz * r, u0, v0, c);
        put(x + bx * r + tx * r, y + by * r + ty * r, z + bz * r + tz * r, u1, v0, c);
        put(x + bx * r - tx * r, y + by * r - ty * r, z + bz * r - tz * r, u1, v1, c);
    }

    /**
     * A vertex's colour: the light on the side you see (the light everywhere, plus the theme's light on a side
     * turned to it), the light through the petal when it's behind, and a sheen on edges turned toward you.
     */
    private static int shade(float x, float y, float z, float nx, float ny, float nz, float r, float g, float b,
                             float alpha) {
        float d = Math.max(1e-4f, (float) Math.sqrt(x * x + y * y + z * z));
        float vx = -x / d, vy = -y / d, vz = -z / d;
        float ndv = nx * vx + ny * vy + nz * vz;
        if (ndv < 0f) {
            nx = -nx;
            ny = -ny;
            nz = -nz;
            ndv = -ndv;
        }
        float ndl = nx * lx + ny * ly + nz * lz;
        // Petals are thin and pale: even the side in shade stays light, the light's side brightens toward white.
        float amb = ambient * (0.8f + 0.14f * ny);
        float diff = Math.max(0f, ndl) * sun * 0.32f;
        float through = Math.max(0f, -ndl) * sun * 0.45f;
        float e = 1f - ndv;
        float sheen = e * e * e * 0.22f * ambient;
        float or = r * (amb + diff * sunR) + (r * 0.5f + 0.5f) * through * backR + sheen;
        float og = g * (amb + diff * sunG) + (g * 0.5f + 0.5f) * through * backG + sheen;
        float ob = b * (amb + diff * sunB) + (b * 0.5f + 0.5f) * through * backB + sheen;
        return Colors.argb(Math.round(Math.min(1f, alpha) * 255f), clamp255(or), clamp255(og), clamp255(ob));
    }

    private static int clamp255(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255f)));
    }

    private static void vertex(int k, float u, float v) {
        put(rowX[k], rowY[k], rowZ[k], u, v, rowC[k]);
    }

    /** A camera-relative world point, turned into view space. */
    private static void put(float x, float y, float z, float u, float v, int c) {
        var m = FrameInfo.view;
        WorldDraw.vertex(m.m00() * x + m.m10() * y + m.m20() * z, m.m01() * x + m.m11() * y + m.m21() * z,
                m.m02() * x + m.m12() * y + m.m22() * z, u, v, c);
    }

    // ------------------------------------------------------------------ the texture

    /** Half the petal's width at {@code s} along it (0 base, 1 tip), as a fraction of its widest. */
    static float halfWidth(float s) {
        if (s < 0f || s > 1f) {
            return 0f;
        }
        if (s <= 0.66f) {
            return 0.2f + 0.8f * (float) Math.pow(Math.sin(Math.PI / 2 * s / 0.66f), 0.85);
        }
        float k = (s - 0.66f) / 0.36f;
        return (float) Math.sqrt(Math.max(0f, 1f - k * k));
    }

    /** Whether (x, s) is on the petal: x across (-1..1), s along (0 base, 1 tip), the notch cut from the tip. */
    static boolean inside(float x, float s) {
        float w = halfWidth(s);
        if (Math.abs(x) >= w) {
            return false;
        }
        float notch = 0.13f * (1f - Math.abs(x) / 0.26f);
        return !(notch > 0f && s > 1f - notch);
    }

    /** The petal's colour at (x, s), white to be multiplied by the petal's own: deeper at the base, paler at the edge. */
    private static float[] petalColor(float x, float s, float[] out) {
        float k = smooth(0f, 0.55f, s);
        float r = 1f, g = 0.7f + 0.3f * k, b = 0.82f + 0.18f * k;
        // Fine veins fanning from the base, fading before the tip.
        float w = Math.max(0.05f, halfWidth(s));
        float vein = 0f;
        for (int j = -3; j <= 3; j++) {
            float cx = j / 3.6f * w * (0.35f + 0.65f * s);
            float dv = (x - cx) / 0.035f;
            vein = Math.max(vein, (float) Math.exp(-dv * dv));
        }
        float veinK = 1f - 0.08f * vein * smooth(0.04f, 0.3f, s) * (1f - smooth(0.82f, 1f, s));
        // The edge is thinner, so paler; a faint crease down the middle catches light.
        float edge = smooth(0.72f, 1f, Math.abs(x) / w);
        float crease = 1f + 0.05f * (float) Math.exp(-(x / 0.05f) * (x / 0.05f)) * smooth(0.1f, 0.4f, s);
        out[0] = Math.min(1f, (r + (1f - r) * edge * 0.2f) * veinK * crease);
        out[1] = Math.min(1f, (g + (1f - g) * edge * 0.2f) * veinK * crease);
        out[2] = Math.min(1f, (b + (1f - b) * edge * 0.2f) * veinK * crease);
        return out;
    }

    private static float smooth(float e0, float e1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - e0) / (e1 - e0)));
        return t * t * (3f - 2f * t);
    }

    /** Bakes the atlas: the petal at 512 px with 4×4 samples, filtered down to each size; the blurred one; stamens. */
    private static int[] bake() {
        int big = 512;
        // Straight colour and coverage at full size (each texel from 4×4 samples).
        float[] cr = new float[big * big], cg = new float[big * big], cb = new float[big * big], ca = new float[big * big];
        float[] col = new float[3];
        for (int j = 0; j < big; j++) {
            for (int i = 0; i < big; i++) {
                float sr = 0f, sg = 0f, sb = 0f, cov = 0f;
                for (int sy = 0; sy < 4; sy++) {
                    for (int sx = 0; sx < 4; sx++) {
                        float x = (i + (sx + 0.5f) / 4f) / big * 2f - 1f;
                        // Texture rows go from the tip (top) to the base (bottom).
                        float s = 1f - (j + (sy + 0.5f) / 4f) / big;
                        if (inside(x * 1.04f, s * 1.02f - 0.01f)) {
                            petalColor(x, s, col);
                            sr += col[0];
                            sg += col[1];
                            sb += col[2];
                            cov += 1f;
                        }
                    }
                }
                int k = j * big + i;
                ca[k] = cov / 16f;
                if (cov > 0f) {
                    cr[k] = sr / cov;
                    cg[k] = sg / cov;
                    cb[k] = sb / cov;
                }
            }
        }
        int[] out = new int[W * H];
        for (int l = 0; l < LEVELS.length; l++) {
            downsample(cr, cg, cb, ca, big, LEVELS[l], 1f, out, LEVEL_X[l], 0);
        }
        // The out-of-focus petal: smaller in its cell (room for the blur), then blurred wide.
        int[] blur = new int[BLUR_S * BLUR_S];
        downsample(cr, cg, cb, ca, big, BLUR_S, 0.62f, blur, 0, 0, BLUR_S);
        blurAlpha(blur, BLUR_S, 7f);
        for (int j = 0; j < BLUR_S; j++) {
            System.arraycopy(blur, j * BLUR_S, out, (BLUR_Y + j) * W + BLUR_X, BLUR_S);
        }
        stamensBake(out);
        return out;
    }

    /** Box-filters the full-size petal into an {@code s}×{@code s} cell at (x0, y0), scaled by {@code scale}. */
    private static void downsample(float[] cr, float[] cg, float[] cb, float[] ca, int big, int s, float scale, int[] out,
                                   int x0, int y0) {
        downsample(cr, cg, cb, ca, big, s, scale, out, x0, y0, W);
    }

    private static void downsample(float[] cr, float[] cg, float[] cb, float[] ca, int big, int s, float scale, int[] out,
                                   int x0, int y0, int stride) {
        int f = Math.max(1, Math.round(big * scale / s));
        float inner = s * scale;
        float off = (s - inner) / 2f;
        for (int j = 0; j < s; j++) {
            for (int i = 0; i < s; i++) {
                // This texel's area in the full-size petal.
                float fx = (i - off) / inner * big, fy = (j - off) / inner * big;
                int bx = Math.round(fx), by = Math.round(fy);
                float r = 0f, g = 0f, b = 0f, a = 0f;
                int count = 0;
                for (int v = 0; v < f; v++) {
                    for (int u = 0; u < f; u++) {
                        int px = bx + u, py = by + v;
                        count++;
                        if (px < 0 || py < 0 || px >= big || py >= big) {
                            continue;
                        }
                        int k = py * big + px;
                        float w = ca[k];
                        r += cr[k] * w;
                        g += cg[k] * w;
                        b += cb[k] * w;
                        a += w;
                    }
                }
                int argb = 0x00FFFFFF;
                if (a > 0f) {
                    argb = Colors.argb(Math.round(a / count * 255f), Math.round(r / a * 255f), Math.round(g / a * 255f),
                            Math.round(b / a * 255f));
                }
                out[(y0 + j) * stride + x0 + i] = argb;
            }
        }
    }

    /** A Gaussian blur of the coverage (the colour is spread with it, so the edges keep the petal's pink). */
    private static void blurAlpha(int[] px, int s, float sigma) {
        int r = (int) Math.ceil(sigma * 3);
        float[] kernel = new float[r * 2 + 1];
        float sum = 0f;
        for (int i = -r; i <= r; i++) {
            kernel[i + r] = (float) Math.exp(-(i * i) / (2 * sigma * sigma));
            sum += kernel[i + r];
        }
        for (int i = 0; i < kernel.length; i++) {
            kernel[i] /= sum;
        }
        float[][] ch = new float[4][s * s];
        for (int k = 0; k < s * s; k++) {
            float a = (px[k] >>> 24) / 255f;
            ch[0][k] = a;
            ch[1][k] = ((px[k] >> 16) & 0xFF) / 255f * a;
            ch[2][k] = ((px[k] >> 8) & 0xFF) / 255f * a;
            ch[3][k] = (px[k] & 0xFF) / 255f * a;
        }
        float[] tmp = new float[s * s];
        for (float[] c : ch) {
            for (int pass = 0; pass < 2; pass++) {
                for (int j = 0; j < s; j++) {
                    for (int i = 0; i < s; i++) {
                        float acc = 0f;
                        for (int t = -r; t <= r; t++) {
                            int x = pass == 0 ? i + t : i, y = pass == 0 ? j : j + t;
                            if (x >= 0 && y >= 0 && x < s && y < s) {
                                acc += (pass == 0 ? c : tmp)[y * s + x] * kernel[t + r];
                            }
                        }
                        if (pass == 0) {
                            tmp[j * s + i] = acc;
                        } else {
                            c[j * s + i] = acc;
                        }
                    }
                }
            }
        }
        for (int k = 0; k < s * s; k++) {
            float a = ch[0][k];
            if (a <= 1e-4f) {
                px[k] = 0x00FFFFFF;
                continue;
            }
            px[k] = Colors.argb(Math.round(a * 255f), clamp255(ch[1][k] / a), clamp255(ch[2][k] / a), clamp255(ch[3][k] / a));
        }
    }

    /** The blossom's centre: a deep pink heart with a ring of pale filaments tipped with yellow anthers. */
    private static void stamensBake(int[] out) {
        int s = STAMEN_S, ss = 4;
        Mulberry32 rnd = new Mulberry32(17);
        int n = 14;
        float[] ax = new float[n], ay = new float[n];
        for (int k = 0; k < n; k++) {
            double a = k * Math.PI * 2 / n + (rnd.next() - 0.5) * 0.3;
            float rr = 0.52f + (float) rnd.next() * 0.22f;
            ax[k] = (float) Math.cos(a) * rr;
            ay[k] = (float) Math.sin(a) * rr;
        }
        for (int j = 0; j < s; j++) {
            for (int i = 0; i < s; i++) {
                float sr = 0f, sg = 0f, sb = 0f, sa = 0f;
                for (int sy = 0; sy < ss; sy++) {
                    for (int sx = 0; sx < ss; sx++) {
                        float x = (i + (sx + 0.5f) / ss) / s * 2f - 1f, y = (j + (sy + 0.5f) / ss) / s * 2f - 1f;
                        float d = (float) Math.sqrt(x * x + y * y);
                        float r = 0f, g = 0f, b = 0f, a = 0f;
                        // The heart.
                        float heart = 1f - smooth(0.22f, 0.4f, d);
                        if (heart > 0f) {
                            r = 0.86f;
                            g = 0.36f;
                            b = 0.52f;
                            a = heart;
                        }
                        for (int k = 0; k < n; k++) {
                            // A filament from the heart out to its anther.
                            float t = Math.max(0f, Math.min(1f, (x * ax[k] + y * ay[k]) / (ax[k] * ax[k] + ay[k] * ay[k])));
                            float fx = x - ax[k] * t, fy = y - ay[k] * t;
                            float fd = (float) Math.sqrt(fx * fx + fy * fy);
                            if (fd < 0.035f && t > 0.2f) {
                                r = 1f;
                                g = 0.86f;
                                b = 0.9f;
                                a = Math.max(a, 0.9f);
                            }
                            float dx = x - ax[k], dy = y - ay[k];
                            if (dx * dx + dy * dy < 0.012f) {
                                r = 1f;
                                g = 0.88f;
                                b = 0.42f;
                                a = 1f;
                            }
                        }
                        sr += r * a;
                        sg += g * a;
                        sb += b * a;
                        sa += a;
                    }
                }
                int argb = 0x00FFFFFF;
                if (sa > 0f) {
                    argb = Colors.argb(Math.round(sa / (ss * ss) * 255f), clamp255(sr / sa), clamp255(sg / sa), clamp255(sb / sa));
                }
                out[(STAMEN_Y + j) * W + STAMEN_X + i] = argb;
            }
        }
    }
}
