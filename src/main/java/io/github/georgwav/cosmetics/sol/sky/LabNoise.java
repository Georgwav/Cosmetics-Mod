package io.github.georgwav.cosmetics.sol.sky;

/**
 * The Night Sky lab's noise, bit for bit ({@code h3}, {@code vn3}, {@code fbm}, {@code vn1}), so the planets' and the
 * moon's surfaces come out as they do in the lab. Pure Java.
 */
public final class LabNoise {
    private LabNoise() {
    }

    /** A hash of three whole numbers, 0..1. */
    public static double h3(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 1440662683;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFFFFL) / 4294967296.0;
    }

    /** Smooth 3D value noise, 0..1. */
    public static double vn3(double x, double y, double z) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y), zi = (int) Math.floor(z);
        double xf = x - xi, yf = y - yi, zf = z - zi;
        double u = xf * xf * (3 - 2 * xf), v = yf * yf * (3 - 2 * yf), w = zf * zf * (3 - 2 * zf);
        double a = h3(xi, yi, zi), b = h3(xi + 1, yi, zi), c = h3(xi, yi + 1, zi), d = h3(xi + 1, yi + 1, zi);
        double e = h3(xi, yi, zi + 1), f = h3(xi + 1, yi, zi + 1), g = h3(xi, yi + 1, zi + 1), k = h3(xi + 1, yi + 1, zi + 1);
        return lerp(lerp(lerp(a, b, u), lerp(c, d, u), v), lerp(lerp(e, f, u), lerp(g, k, u), v), w);
    }

    /** {@code o} octaves of {@link #vn3}, each shifted, 0..1. */
    public static double fbm(double x, double y, double z, int o) {
        double s = 0, a = 0.5, f = 1, n = 0;
        for (int i = 0; i < o; i++) {
            s += a * vn3(x * f + i * 17.3, y * f - i * 9.1, z * f);
            n += a;
            a *= 0.5;
            f *= 2.03;
        }
        return s / n;
    }

    /** Smooth 1D value noise, 0..1. */
    public static double vn1(double x) {
        int i = (int) Math.floor(x);
        double f = x - i, u = f * f * (3 - 2 * f);
        return lerp(h3(i, 7, 3), h3(i + 1, 7, 3), u);
    }

    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    public static double sstep(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }
}
