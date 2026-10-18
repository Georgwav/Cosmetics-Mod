package io.github.georgwav.cosmetics.sol.sky;

import io.github.georgwav.cosmetics.sol.util.Mulberry32;

import static io.github.georgwav.cosmetics.sol.sky.LabNoise.fbm;
import static io.github.georgwav.cosmetics.sol.sky.LabNoise.sstep;

/**
 * One of the Nebula theme's three huge painted nebulae (the Night Sky lab's {@code nebulaTex}): domain-warped noise
 * in four colours, folded into billows and filaments, a hot pale core where it's thickest, young stars inside, and
 * a second picture of the dark dust lanes that cross it, drawn over the stars behind. Pure Java: bakes on Sol's CPU
 * thread and in tests.
 */
public final class NebulaTex {
    public static final int W = 300, H = 200;

    /** Where each sits (hours from the point opposite the sun, declination), its size in degrees, turn, palette, strength. */
    public record Spec(double dra, double dec, double w, double h, double rot, int seed, int[] pal, double a) {
    }

    public static final Spec[] BIG = {
            new Spec(-1, 36, 96, 64, 20, 1, new int[]{0xC2307E, 0x6A3CFF, 0x27C7C0, 0xFFCB6B}, 1),
            new Spec(3.2, 10, 62, 42, -30, 2, new int[]{0xFF5A3C, 0xFF2F8A, 0x3FE0B0, 0xFFE08A}, .9),
            new Spec(-4.5, 62, 50, 34, 60, 3, new int[]{0x3A6BFF, 0x9B4DFF, 0x5FFFD0, 0xFFFFFF}, .85)};

    /** The glowing gas (colour at full strength, brightness in alpha) and the dust (dark, its cover in alpha). */
    public record Pictures(int[] emission, int[] dust) {
    }

    private NebulaTex() {
    }

    public static Pictures bake(int seed, int[] pal) {
        int[] em = new int[W * H], dust = new int[W * H];
        double so = seed * 13.7;
        double[][] p = new double[4][];
        for (int i = 0; i < 4; i++) {
            p[i] = new double[]{pal[i] >> 16 & 255, pal[i] >> 8 & 255, pal[i] & 255};
        }
        for (int j = 0; j < H; j++) {
            for (int i = 0; i < W; i++) {
                double u = ((double) i / W * 2 - 1) * 1.5, v = (double) j / H * 2 - 1;
                double q0 = fbm(u * 1.4 + so, v * 1.4, 1, 4), q1 = fbm(u * 1.4 + 5.2, v * 1.4 + so, 2, 4);
                double r0 = fbm(u * 1.4 + 3.5 * q0 + 1.7, v * 1.4 + 3.5 * q1 + 9.2, 3, 4);
                double r1 = fbm(u * 1.4 + 3.5 * q0 + 8.3, v * 1.4 + 3.5 * q1 + 2.8, 4, 4);
                double f = fbm(u * 1.6 + 3 * r0, v * 1.6 + 3 * r1, 5 + so, 5);
                double env = Math.max(0, 1 - (u * u / 2.25 + v * v) * .95);
                double d = sstep(.36, .76, f) * Math.pow(env, 1.3) * (.5 + .75 * Math.pow(1 - Math.abs(r0 - .5) * 2, 3));
                double[] col = mix(p[0], p[1], clamp((q0 - .35) * 2.4));
                col = mix(col, p[2], clamp((r1 - .45) * 2.6));
                col = mix(col, p[3], sstep(.66, .9, f) * .8);
                col = mix(col, new double[]{255, 246, 250}, sstep(.8, .95, f * env) * .7);
                double du = sstep(.5, .66, fbm(u * 4 + 2, v * 4, 7 + so, 4)) * sstep(.1, .5, env);
                d *= 1 - .85 * du;
                int k = j * W + i;
                em[k] = argb(clamp(d * 1.35), col[0], col[1], col[2]);
                dust[k] = argb(Math.min(.85, Math.max(0, du * env * 1.4)), 4, 3, 10);
            }
        }
        // Young stars: small crosses with a little glow.
        Mulberry32 r = new Mulberry32(seed * 7 + 1);
        for (int k = 0; k < 14; k++) {
            int x = (int) (W * (.2 + .6 * r.next())), y = (int) (H * (.25 + .5 * r.next()));
            double b = .4 + .6 * r.next();
            for (int dy = -4; dy <= 4; dy++) {
                for (int dx = -4; dx <= 4; dx++) {
                    double g = Math.max(0, 1 - Math.sqrt(dx * dx + dy * dy) / 4) * .4 * b;
                    boolean cross = (dx == 0 && Math.abs(dy) <= 1) || (dy == 0 && Math.abs(dx) <= 1);
                    add(em, x + dx, y + dy, cross ? Math.max(g, .6 * b) : g);
                }
            }
        }
        return new Pictures(em, dust);
    }

    /** Adds white light of strength {@code s} (0..1) to a pixel. */
    private static void add(int[] em, int x, int y, double s) {
        if (x < 0 || y < 0 || x >= W || y >= H || s <= 0) {
            return;
        }
        int c = em[y * W + x];
        double a = (c >>> 24) / 255.0, r = (c >> 16 & 255) * a, g = (c >> 8 & 255) * a, b = (c & 255) * a;
        r = Math.min(255, r + 255 * s);
        g = Math.min(255, g + 255 * s);
        b = Math.min(255, b + 255 * s);
        double m = Math.max(r, Math.max(g, b));
        em[y * W + x] = m <= 0 ? 0 : argb(m / 255, r / m * 255, g / m * 255, b / m * 255);
    }

    private static double[] mix(double[] a, double[] b, double t) {
        return new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    private static double clamp(double v) {
        return v < 0 ? 0 : Math.min(1, v);
    }

    private static int argb(double a, double r, double g, double b) {
        return ((int) Math.round(clamp(a) * 255) << 24) | ((int) Math.round(Math.max(0, Math.min(255, r))) << 16)
                | ((int) Math.round(Math.max(0, Math.min(255, g))) << 8) | (int) Math.round(Math.max(0, Math.min(255, b)));
    }
}
