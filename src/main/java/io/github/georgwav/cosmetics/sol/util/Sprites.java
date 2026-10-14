package io.github.georgwav.cosmetics.sol.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The prototypes' baked images, ported pixel for pixel: the soft glow every halo and particle is drawn with, the
 * aurora strip, the light-ray wedge, the Deep Field galaxy, the Grace rune (with its bloom and crackle) and the
 * mist bands. Each is drawn once on a {@link PixelCanvas} and cached; the world renderer uploads them as textures
 * and the CPU renderers draw them directly.
 */
public final class Sprites {
    private static final int CACHE = 64;
    private static final Map<String, PixelCanvas> CACHED = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, PixelCanvas> eldest) {
            return size() > CACHE;
        }
    };

    private Sprites() {
    }

    private static synchronized PixelCanvas cached(String key, java.util.function.Supplier<PixelCanvas> make) {
        PixelCanvas c = CACHED.get(key);
        if (c == null) {
            c = make.get();
            CACHED.put(key, c);
        }
        return c;
    }

    private static int rgba(int rgb, float alpha) {
        return Colors.withAlpha(rgb, alpha);
    }

    /** 64×64 radial glow: full colour in the middle, .55 at a quarter, clear at the edge ({@code glow()}). */
    public static PixelCanvas glow(int color) {
        return cached("glow" + color, () -> {
            PixelCanvas c = new PixelCanvas(64, 64);
            c.fillRect(0, 0, 64, 64, PixelCanvas.radial(32, 32, 32,
                    PixelCanvas.Stops.of(0f, rgba(color, 1f), 0.25f, rgba(color, 0.55f), 1f, rgba(color, 0f))));
            return c;
        });
    }

    /** 1×64 vertical strip: clear, .9 at 70%, clear ({@code strip()}, the aurora's columns). */
    public static PixelCanvas strip(int color) {
        return cached("strip" + color, () -> {
            PixelCanvas c = new PixelCanvas(1, 64);
            c.fillRect(0, 0, 1, 64, PixelCanvas.linear(0, 0, 0, 64,
                    PixelCanvas.Stops.of(0f, rgba(color, 0f), 0.7f, rgba(color, 0.9f), 1f, rgba(color, 0f))));
            return c;
        });
    }

    /** 256×64 wedge fading along its length ({@code ray()}, one light ray). */
    public static PixelCanvas ray(int color) {
        return cached("ray" + color, () -> {
            PixelCanvas c = new PixelCanvas(256, 64);
            c.fillPolygon(new float[]{0, 256, 256, 0}, new float[]{28, 0, 64, 36}, PixelCanvas.linear(0, 0, 256, 0,
                    PixelCanvas.Stops.of(0f, rgba(color, 0.95f), 0.3f, rgba(color, 0.4f), 1f, rgba(color, 0f))));
            return c;
        });
    }

    /** The Deep Field spiral galaxy: two arms of 1,500 dots, a halo and a core, 512×512 ({@code galaxySprite}). */
    public static PixelCanvas galaxy() {
        return cached("galaxy", () -> {
            int s = 512;
            PixelCanvas g = new PixelCanvas(s, s);
            Mulberry32 r = new Mulberry32(99);
            g.setLighter(true);
            g.fillRect(0, 0, s, s, PixelCanvas.radial(s / 2f, s / 2f, s / 2f, PixelCanvas.Stops.of(
                    0f, Colors.argb(Math.round(0.5f * 255), 255, 255, 255), 0.12f, Colors.argb(Math.round(0.2f * 255), 235, 235, 245),
                    0.5f, Colors.argb(Math.round(0.05f * 255), 200, 200, 215), 1f, 0)));
            for (int arm = 0; arm < 2; arm++) {
                for (int i = 0; i < 1500; i++) {
                    double u = r.next(), a = u * 4.4 * Math.PI + arm * Math.PI, rad = 6 + u * s * 0.46;
                    double spread = (r.next() - 0.5) * (8 + u * 50);
                    double x = s / 2.0 + Math.cos(a) * rad + Math.cos(a + 1.57) * spread * 0.6;
                    double y = s / 2.0 + Math.sin(a) * rad + Math.sin(a + 1.57) * spread * 0.6;
                    double sz = r.next() < 0.04 ? 2.4 : r.next() * 1.3 + 0.4;
                    g.setGlobalAlpha((float) ((1 - u * 0.75) * (0.35 + r.next() * 0.65)));
                    int color = r.next() < 0.15 ? 0xFFFFFFFF : 0xFFC9C9D4;
                    g.fillRect((float) x, (float) y, (float) sz, (float) sz, color);
                }
            }
            g.setGlobalAlpha(1f);
            g.fillRect(0, 0, s, s, PixelCanvas.radial(s / 2f, s / 2f, s * 0.1f,
                    PixelCanvas.Stops.of(0f, 0xFFFFFFFF, 1f, 0x00FFFFFF)));
            return g;
        });
    }

    /** The baked Grace rune: crisp pixels, bloom, three crackle layers, its line pixels and its nodes. */
    public record Sigil(PixelCanvas img, PixelCanvas glow, PixelCanvas[] crackle, int[] pts, float[] nodes, int width,
                        int height, int unit, int cx, int cy) {
        /** How many line pixels (x, y pairs in {@link #pts}). */
        public int count() {
            return pts.length / 2;
        }
    }

    /**
     * The Elden Ring rune of Appendix B, fitted to the key art: units are the upper circle's radius (30 px), y
     * down. Port of {@code sigilSprite}, every random draw in the same order so it matches the design exactly.
     */
    public static Sigil sigil(int color, int halo) {
        String key = "sigil" + color + "/" + halo;
        synchronized (Sprites.class) {
            Object cachedSigil = SIGILS.get(key);
            if (cachedSigil != null) {
                return (Sigil) cachedSigil;
            }
        }
        Sigil made = makeSigil(color, halo);
        synchronized (Sprites.class) {
            SIGILS.put(key, made);
        }
        return made;
    }

    private static final Map<String, Object> SIGILS = new LinkedHashMap<>();

    /** Appendix B geometry, shared with the unit test. */
    public static final double S3 = Math.sqrt(3);
    /** The four circles: centre x, centre y, radius (upper, middle, lower left, lower right). */
    public static final double[][] SIGIL_CIRCLES = {{0, 0, 1}, {0, 1 / S3, 0.5 + 1 / S3}, {-0.5, S3 / 2, 1}, {0.5, S3 / 2, 1}};
    /** The bowl: the bottom arc of this circle, |x| ≤ 1.3. */
    public static final double BOWL_CY = -4.47, BOWL_R = 2.75, BOWL_HALF_WIDTH = 1.3;
    /** The spine's nodes: y and size. */
    public static final double[][] SIGIL_SPINE_NODES = {{-1.72, 0.8}, {-1, 1}, {-0.5, 0.6}, {0, 0.95}, {1, 0.75}, {S3, 1.25}};

    /** The bowl's brightness at x: exp(-(|x|/0.8)^2.2), nothing below 0.07, so the tips taper away. */
    public static double bowlWeight(double x) {
        double w = Math.exp(-Math.pow(Math.abs(x) / 0.8, 2.2));
        return w < 0.07 ? 0 : w;
    }

    /** The spine's brightness at y: full down to 1.75, then fading to 0.15 at 2.32. */
    public static double spineWeight(double y) {
        return y < 1.75 ? 1 : 1 - (y - 1.75) / 0.57 * 0.85;
    }

    private static Sigil makeSigil(int color, int haloColor) {
        final int u = 30, m = 16, cx = 46 + m, cy = 73 + m, nw = cx * 2, nh = cy + 70 + m;
        final double tau = Math.PI * 2;
        float[] wt = new float[nw * nh];
        List<int[]> pts = new ArrayList<>();
        java.util.function.BiConsumer<double[], Double> plot = (xy, w) -> {
            int x = (int) Math.round(xy[0]), y = (int) Math.round(xy[1]);
            if (x < 0 || y < 0 || x >= nw || y >= nh || w <= 0) {
                return;
            }
            int i = y * nw + x;
            if (wt[i] == 0f) {
                pts.add(new int[]{x, y});
            }
            if (w > wt[i]) {
                wt[i] = (float) (double) w;
            }
        };
        for (double y = -1.72; y <= 2.32; y += 0.4 / u) {
            plot.accept(new double[]{cx, cy + y * u}, spineWeight(y));
        }
        for (double[] c : SIGIL_CIRCLES) {
            arc(plot, cx, cy, u, c[0], c[1], c[2], -Math.PI / 2, Math.PI * 1.5, null);
        }
        double bw = Math.asin(BOWL_HALF_WIDTH / BOWL_R);
        arc(plot, cx, cy, u, 0, BOWL_CY, BOWL_R, Math.PI / 2 + bw, Math.PI / 2 - bw, Sprites::bowlWeight);
        List<double[]> nodes = new ArrayList<>();
        for (double[] n : SIGIL_SPINE_NODES) {
            nodes.add(new double[]{0, n[0], n[1]});
        }
        double[][] circles = SIGIL_CIRCLES;
        for (int i = 0; i < circles.length; i++) {
            for (int j = i + 1; j < circles.length; j++) {
                double x1 = circles[i][0], y1 = circles[i][1], r1 = circles[i][2];
                double x2 = circles[j][0], y2 = circles[j][1], r2 = circles[j][2];
                double dx = x2 - x1, dy = y2 - y1, d = Math.hypot(dx, dy), a = (d * d + r1 * r1 - r2 * r2) / (2 * d);
                double h = Math.sqrt(Math.max(0, r1 * r1 - a * a));
                for (int s : new int[]{-1, 1}) {
                    double px = x1 + dx * a / d - s * dy * h / d, py = y1 + dy * a / d + s * dx * h / d;
                    boolean near = false;
                    for (double[] n : nodes) {
                        if (Math.hypot(n[0] - px, n[1] - py) < 0.08) {
                            near = true;
                            break;
                        }
                    }
                    if (Math.abs(px) > 0.05 && !near) {
                        nodes.add(new double[]{px, py, 0.45});
                    }
                }
            }
        }
        PixelCanvas img = new PixelCanvas(nw, nh), gl = new PixelCanvas(nw, nh);
        PixelCanvas[] ck = {new PixelCanvas(nw, nh), new PixelCanvas(nw, nh), new PixelCanvas(nw, nh)};
        Mulberry32 r = new Mulberry32(77);
        int hot = Colors.mixRgb(color, 0xFFFFFFFF, 0.72f), warm = Colors.mixRgb(color, 0xFFFF9A3C, 0.45f);
        int[] ember = {0xFFFFCF6A, 0xFFFFB347, 0xFFFFF0B8, 0xFFFF9A3C};
        // Faint straight threads crossing the rune: an X through the upper circle, a low chevron.
        for (double[] t : new double[][]{{47, -0.08}, {133, -0.08}, {71, 1.05}, {109, 1.05}}) {
            double a = Math.toRadians(t[0]), nx = Math.cos(a), ny = Math.sin(a), rho = t[1];
            for (double s = -2.2; s <= 2.2; s += 0.5 / u) {
                double x = nx * rho - ny * s, y = ny * rho + nx * s, d = Math.hypot(x, y - 0.6);
                if (d < 1.95 && r.next() < 0.8) {
                    img.setGlobalAlpha((float) (0.22 * (1 - d / 1.95) + 0.05));
                    img.fillRect(Math.round(cx + x * u), Math.round(cy + y * u), 1, 1, warm);
                }
            }
        }
        // Soft shoulders either side of every line, so it reads bold like the painted strokes.
        for (int[] p : pts) {
            float w = wt[p[1] * nw + p[0]];
            img.setGlobalAlpha(0.42f * w);
            img.fillRect(p[0] - 1, p[1], 3, 1, warm);
            img.fillRect(p[0], p[1] - 1, 1, 3, warm);
        }
        // Crisp line pixels: a white-gold spine, pale gold above warming to orange-gold toward the clouds.
        for (int[] p : pts) {
            float w = wt[p[1] * nw + p[0]];
            double ry = (p[1] - cy) / (double) u;
            boolean spine = p[0] == cx;
            img.setGlobalAlpha(Math.min(1f, 0.25f + w * 0.85f));
            int c = spine || r.next() < 0.16 ? hot
                    : Colors.mixRgb(color, warm, (float) Math.max(0, Math.min(1, (ry + 0.2) / 2)));
            img.fillRect(p[0], p[1], 1, 1, c);
        }
        // Pixel starbursts on the nodes.
        for (double[] n : nodes) {
            int x = (int) Math.round(cx + n[0] * u), y = (int) Math.round(cy + n[1] * u), len = (int) Math.round(1 + n[2] * 4);
            img.setGlobalAlpha(1f);
            img.fillRect(x - 1, y, 3, 1, 0xFFFFFFFF);
            img.fillRect(x, y - 1, 1, 3, 0xFFFFFFFF);
            for (int k = 2; k <= len; k++) {
                img.setGlobalAlpha(1f - (k - 1f) / (len + 1f));
                img.fillRect(x - k, y, 1, 1, hot);
                img.fillRect(x + k, y, 1, 1, hot);
                img.fillRect(x, y - k, 1, 1, hot);
                img.fillRect(x, y + k, 1, 1, hot);
            }
            if (n[2] > 0.7) {
                img.setGlobalAlpha(0.55f);
                img.fillRect(x - 1, y - 1, 1, 1, hot);
                img.fillRect(x + 1, y - 1, 1, 1, hot);
                img.fillRect(x - 1, y + 1, 1, 1, hot);
                img.fillRect(x + 1, y + 1, 1, 1, hot);
            }
        }
        img.setGlobalAlpha(1f);
        // Three crackle layers: sparks and short filaments clinging to the lines, cross-faded when drawn.
        for (int j = 0; j < 3; j++) {
            PixelCanvas q = ck[j];
            Mulberry32 rr = new Mulberry32(101 + j * 57);
            for (int[] p : pts) {
                float w = wt[p[1] * nw + p[0]];
                if (rr.next() < 0.3 * w) {
                    double a = rr.next() * tau;
                    int d = 1 + (int) Math.floor(rr.next() * 2.2);
                    q.setGlobalAlpha((float) (0.35 + rr.next() * 0.6));
                    int c = ember[(int) Math.floor(rr.next() * 4)];
                    q.fillRect(Math.round(p[0] + Math.cos(a) * d), Math.round(p[1] + Math.sin(a) * d), 1, 1, c);
                }
                if (rr.next() < 0.035 * w) {
                    double a = rr.next() * tau;
                    int count = 2 + (int) Math.floor(rr.next() * 3);
                    int c = ember[(int) Math.floor(rr.next() * 3)];
                    for (int k = 1; k <= count; k++) {
                        q.setGlobalAlpha(0.8f * (1f - k / (count + 1f)));
                        q.fillRect(Math.round(p[0] + Math.cos(a) * k), Math.round(p[1] + Math.sin(a) * k), 1, 1, c);
                    }
                }
            }
            q.setGlobalAlpha(1f);
        }
        // Pre-baked bloom: a tight gold glow and a wide orange one along every line, brighter on the nodes.
        gl.setLighter(true);
        PixelCanvas gColor = glow(color), gHalo = glow(haloColor), gNode = glow(0xFFFFF1C8);
        for (int i = 0; i < pts.size(); i++) {
            int[] p = pts.get(i);
            float w = wt[p[1] * nw + p[0]];
            if (i % 2 == 0 && r.next() < w) {
                gl.setGlobalAlpha(0.12f * w);
                gl.drawImage(gColor, p[0] - 4, p[1] - 4, 8, 8, true);
            }
            if (i % 3 == 0 && r.next() < w) {
                gl.setGlobalAlpha(0.05f * w);
                gl.drawImage(gHalo, p[0] - 13, p[1] - 13, 26, 26, true);
            }
        }
        for (double[] n : nodes) {
            float x = (float) (cx + n[0] * u + 0.5), y = (float) (cy + n[1] * u + 0.5), rad = (float) (3 + n[2] * 4);
            gl.setGlobalAlpha((float) Math.min(1, 0.25 + n[2] * 0.3));
            gl.drawImage(gNode, x - rad, y - rad, rad * 2, rad * 2, true);
        }
        gl.setGlobalAlpha(1f);
        int[] flat = new int[pts.size() * 2];
        for (int i = 0; i < pts.size(); i++) {
            flat[i * 2] = pts.get(i)[0];
            flat[i * 2 + 1] = pts.get(i)[1];
        }
        float[] nodeArray = new float[nodes.size() * 3];
        for (int i = 0; i < nodes.size(); i++) {
            nodeArray[i * 3] = (float) nodes.get(i)[0];
            nodeArray[i * 3 + 1] = (float) nodes.get(i)[1];
            nodeArray[i * 3 + 2] = (float) nodes.get(i)[2];
        }
        return new Sigil(img, gl, ck, flat, nodeArray, nw, nh, u, cx, cy);
    }

    private static void arc(java.util.function.BiConsumer<double[], Double> plot, int cx, int cy, int u, double ox,
                            double oy, double r, double a0, double a1, java.util.function.DoubleUnaryOperator weight) {
        int n = (int) Math.ceil(Math.abs(a1 - a0) * r * u * 2.2);
        for (int i = 0; i <= n; i++) {
            double a = a0 + (a1 - a0) * i / n, x = ox + Math.cos(a) * r, y = oy + Math.sin(a) * r;
            plot.accept(new double[]{cx + x * u, cy + y * u}, weight != null ? weight.applyAsDouble(x) : 1.0);
        }
    }

    /** The Grace mist: three teal bands and the warm cloud bank, 128×32 each ({@code mistTex}). */
    public record Mist(PixelCanvas[] teal, PixelCanvas warm) {
    }

    public static Mist mist(int color, Integer warmColor) {
        String key = "mist" + color + "/" + warmColor;
        synchronized (Sprites.class) {
            Object m = SIGILS.get(key);
            if (m != null) {
                return (Mist) m;
            }
        }
        PixelCanvas warm = null;
        if (warmColor != null) {
            warm = mistBand(51, warmColor, 0.5f);
            int tw = 128, th = 32;
            for (int y = 0; y < th; y++) {
                for (int x = 0; x < tw; x++) {
                    double ex = (x + 0.5) / tw * 2 - 1, ey = ((y + 0.5) / th * 2 - 1) * 1.15;
                    double e = Math.max(0, 1 - ex * ex - ey * ey), k = Math.pow(Math.min(1, Math.hypot(ex * 1.1, ey)), 1.6);
                    int old = warm.getPixel(x, y);
                    int r = (int) Math.round(Colors.r(warmColor) + (Colors.r(color) - Colors.r(warmColor)) * k);
                    int g = (int) Math.round(Colors.g(warmColor) + (Colors.g(color) - Colors.g(warmColor)) * k);
                    int b = (int) Math.round(Colors.b(warmColor) + (Colors.b(color) - Colors.b(warmColor)) * k);
                    int a = (int) Math.round(Math.min(255, (Colors.a(old) + 40 * e) * Math.min(1, e * 2.6)));
                    warm.setPixel(x, y, Colors.argb(a, r, g, b));
                }
            }
        }
        Mist made = new Mist(new PixelCanvas[]{mistBand(11, color, 0.5f),
                mistBand(23, Colors.mixRgb(color, 0xFF000000, 0.35f), 0.6f),
                mistBand(37, Colors.mixRgb(color, 0xFF9FD8C0, 0.12f), 0.45f)}, warm);
        synchronized (Sprites.class) {
            SIGILS.put(key, made);
        }
        return made;
    }

    /** Horizontally tileable fractal noise in one colour, alpha fading at the band's top and bottom. */
    static PixelCanvas mistBand(int seed, int color, float density) {
        int tw = 128, th = 32;
        Mulberry32 rr = new Mulberry32(seed);
        int[][] oct = {{6, 2}, {12, 4}, {24, 8}, {48, 16}};
        float[] amps = {0.5f, 0.27f, 0.15f, 0.08f};
        float[][] grid = new float[oct.length][];
        for (int o = 0; o < oct.length; o++) {
            grid[o] = new float[oct[o][0] * (oct[o][1] + 1)];
            for (int i = 0; i < grid[o].length; i++) {
                grid[o][i] = (float) rr.next();
            }
        }
        PixelCanvas c = new PixelCanvas(tw, th);
        for (int y = 0; y < th; y++) {
            for (int x = 0; x < tw; x++) {
                double v = 0;
                for (int o = 0; o < oct.length; o++) {
                    int gx = oct[o][0], gy = oct[o][1];
                    double fx = (double) x / tw * gx, fy = (double) y / th * gy;
                    int ix = (int) Math.floor(fx), iy = (int) Math.floor(fy);
                    double tx = fx - ix, ty = fy - iy, sx = tx * tx * (3 - 2 * tx), sy = ty * ty * (3 - 2 * ty);
                    float[] g = grid[o];
                    double v00 = g[iy * gx + (ix % gx)], v10 = g[iy * gx + ((ix + 1) % gx)];
                    double v01 = g[(iy + 1) * gx + (ix % gx)], v11 = g[(iy + 1) * gx + ((ix + 1) % gx)];
                    v += amps[o] * ((v00 * (1 - sx) + v10 * sx) * (1 - sy) + (v01 * (1 - sx) + v11 * sx) * sy);
                }
                double e = Math.sin(Math.PI * (y + 0.5) / th);
                double a = Math.max(0, Math.min(1, (v - (1 - density) * 0.6) / 0.4)) * e * e;
                c.setPixel(x, y, Colors.argb((int) Math.round(a * 255), Colors.r(color), Colors.g(color), Colors.b(color)));
            }
        }
        return c;
    }
}
