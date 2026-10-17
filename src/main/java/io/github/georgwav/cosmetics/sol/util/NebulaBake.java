package io.github.georgwav.cosmetics.sol.util;

/**
 * Paints a nebula: glowing gas round a theme's cloud centres, each in its colour, shaped by warped noise into
 * billows, with thin bright filaments along them, dark dust lanes cutting through, a hot pale core where the gas is
 * thickest, and faint stars inside. Pure Java (no game classes), so it bakes on Sol's CPU thread and in tests.
 *
 * <p>The picture covers a window of the theme's showpiece frame ({@code x0..x1} across, {@code y0..y1} down, in the
 * frame's fractions); clouds are given in the same fractions, their radii as fractions of the frame's width. Each
 * pixel is ARGB: the colour at full strength, and how bright it is in alpha (for drawing added).
 */
public final class NebulaBake {
    private NebulaBake() {
    }

    /** Bakes a {@code w} x {@code h} nebula; the arrays describe the clouds (centre, radius, colour). */
    public static int[] bake(int w, int h, float x0, float x1, float y0, float y1, float aspect, float[] cx, float[] cy,
                             float[] cr, int[] colors, int seed) {
        int n = colors.length;
        float[] r = new float[n], g = new float[n], b = new float[n];
        for (int k = 0; k < n; k++) {
            r[k] = Colors.r(colors[k]) / 255f;
            g[k] = Colors.g(colors[k]) / 255f;
            b[k] = Colors.b(colors[k]) / 255f;
        }
        int[] out = new int[w * h];
        Mulberry32 rnd = new Mulberry32(seed);
        float sa = (float) rnd.next() * 100f, sb = (float) rnd.next() * 100f, sc = (float) rnd.next() * 100f;
        float sd = (float) rnd.next() * 100f, se = (float) rnd.next() * 100f;
        float[] cloud = new float[n];
        for (int j = 0; j < h; j++) {
            float fy = y0 + (j + 0.5f) / h * (y1 - y0);
            for (int i = 0; i < w; i++) {
                float fx = x0 + (i + 0.5f) / w * (x1 - x0);
                // In the frame's height units, so the clouds are round.
                float px = fx * aspect, py = fy;
                // Warp the space so the gas billows and swirls instead of sitting in round blobs.
                float qx = px + 0.42f * (fbm(px * 1.7f + sa, py * 1.7f + sb, 4) - 0.5f) * 2f;
                float qy = py + 0.42f * (fbm(px * 1.7f + sc, py * 1.7f + sd, 4) - 0.5f) * 2f;
                float billow = fbm(qx * 2.3f + se, qy * 2.3f - se, 5);
                float total = 0f, cr0 = 0f, cg0 = 0f, cb0 = 0f;
                for (int k = 0; k < n; k++) {
                    float dx = qx - cx[k] * aspect, dy = qy - cy[k];
                    float rr = Math.max(0.02f, cr[k] * aspect * 1.25f);
                    float fall = (float) Math.exp(-(dx * dx + dy * dy) / (rr * rr) * 1.7f);
                    float d = fall * smooth(0.18f, 0.9f, billow * 0.7f + fall * 0.5f);
                    cloud[k] = d;
                    total += d;
                    cr0 += r[k] * d;
                    cg0 += g[k] * d;
                    cb0 += b[k] * d;
                }
                // Dark lanes of dust across the gas.
                float dust = smooth(0.54f, 0.72f, fbm(qx * 3.4f - sd, qy * 3.4f + sa, 4));
                float keep = 1f - 0.78f * dust * smooth(0.05f, 0.4f, total);
                // Thin bright filaments along the billows' edges.
                float ridge = 1f - Math.abs(fbm(qx * 4.6f + sb, qy * 4.6f - sc, 4) * 2f - 1f);
                float fil = (float) Math.pow(ridge, 7) * smooth(0.02f, 0.3f, total) * 0.9f;
                // A hot, pale core where the gas is thickest.
                float core = smooth(0.55f, 1.3f, total);
                float cr1 = (cr0 + fil * (0.5f + 0.5f * cr0 / Math.max(1e-3f, total)) + core * 0.45f) * keep;
                float cg1 = (cg0 + fil * (0.5f + 0.5f * cg0 / Math.max(1e-3f, total)) + core * 0.4f) * keep;
                float cb1 = (cb0 + fil * (0.5f + 0.5f * cb0 / Math.max(1e-3f, total)) + core * 0.45f) * keep;
                // Faded out toward the window's edges and the horizon, so it has no border.
                float edge = smooth(x0, x0 + 0.22f, fx) * smooth(x1, x1 - 0.22f, fx) * smooth(y0, y0 + 0.18f, fy)
                        * smooth(y1, y1 - 0.2f, fy);
                cr1 *= edge;
                cg1 *= edge;
                cb1 *= edge;
                float m = Math.max(cr1, Math.max(cg1, cb1));
                if (m <= 1e-4f) {
                    out[j * w + i] = 0x00FFFFFF;
                    continue;
                }
                // Brightness in alpha, softly limited; colour at full strength.
                float bright = 1f - (float) Math.exp(-m * 1.5f);
                out[j * w + i] = Colors.argb(Math.round(bright * 255f), Math.round(cr1 / m * 255f), Math.round(cg1 / m * 255f),
                        Math.round(cb1 / m * 255f));
            }
        }
        stars(out, w, h, rnd);
        return out;
    }

    /** Faint stars sprinkled through it, a little brighter where the gas is (young stars in their cloud). */
    private static void stars(int[] out, int w, int h, Mulberry32 rnd) {
        int count = w * h / 900;
        for (int s = 0; s < count; s++) {
            int x = (int) (rnd.next() * w), y = (int) (rnd.next() * h);
            int k = y * w + x;
            int a = out[k] >>> 24;
            // Like the gas, they thin out toward the picture's edges.
            float edge = smooth(0f, 1f, Math.min(Math.min(x, w - 1 - x) / (0.12f * w), Math.min(y, h - 1 - y) / (0.12f * h)));
            float b = (0.25f + 0.55f * (float) rnd.next()) * (0.4f + 0.6f * a / 255f) * edge;
            if (b < 0.2f) {
                continue;
            }
            int na = Math.min(255, a + Math.round(b * 255f));
            int c = out[k];
            int mix = Colors.mix(c | 0xFF000000, 0xFFFFFFFF, Math.min(1f, b * 1.4f));
            out[k] = (na << 24) | (mix & 0xFFFFFF);
        }
    }

    private static float smooth(float e0, float e1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - e0) / (e1 - e0)));
        return t * t * (3f - 2f * t);
    }

    /** Fractal value noise, 0..1, {@code octaves} layers each turned a little so no grid shows. */
    static float fbm(float x, float y, int octaves) {
        float sum = 0f, amp = 0.5f, norm = 0f;
        for (int o = 0; o < octaves; o++) {
            sum += amp * noise(x, y);
            norm += amp;
            float nx = x * 1.6f - y * 1.2f, ny = x * 1.2f + y * 1.6f;
            x = nx + 17.3f;
            y = ny - 9.1f;
            amp *= 0.5f;
        }
        return sum / norm;
    }

    /** Smooth value noise, 0..1. */
    static float noise(float x, float y) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        float fx = x - ix, fy = y - iy;
        float ux = fx * fx * fx * (fx * (fx * 6f - 15f) + 10f), uy = fy * fy * fy * (fy * (fy * 6f - 15f) + 10f);
        float a = hash(ix, iy), b = hash(ix + 1, iy), c = hash(ix, iy + 1), d = hash(ix + 1, iy + 1);
        return a + (b - a) * ux + (c - a) * uy + (a - b - c + d) * ux * uy;
    }

    private static float hash(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }
}
