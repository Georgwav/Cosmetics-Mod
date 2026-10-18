package io.github.georgwav.cosmetics.sol.sky;

import static io.github.georgwav.cosmetics.sol.sky.LabNoise.fbm;
import static io.github.georgwav.cosmetics.sol.sky.LabNoise.h3;
import static io.github.georgwav.cosmetics.sol.sky.LabNoise.sstep;
import static io.github.georgwav.cosmetics.sol.sky.LabNoise.vn1;
import static io.github.georgwav.cosmetics.sol.sky.LabNoise.vn3;

/**
 * The surface maps of the moon, the planets and the wild worlds (the Night Sky lab's {@code GEN}): each a 256 x 128
 * longitude-latitude picture of colour, plus a light channel for what glows by itself (lava, city lights, the
 * Death Star's dish, the Carian glyph). Made from the lab's noise, so they match it. Pure Java.
 */
public final class BodyMaps {
    public static final int W = 256, H = 128;

    private BodyMaps() {
    }

    /** The map of a body: RGBA floats (A is the light channel), row by row from the north. Null for none. */
    public static float[] make(String id) {
        if (!has(id)) {
            return null;
        }
        float[] d = new float[W * H * 4];
        double[] c = new double[4];
        for (int j = 0; j < H; j++) {
            double lat = (0.5 - (j + 0.5) / H) * Math.PI, cl = Math.cos(lat), sl = Math.sin(lat);
            for (int i = 0; i < W; i++) {
                double lon = (i + 0.5) / W * Math.PI * 2 - Math.PI;
                c[3] = 0;
                gen(id, Math.toDegrees(lat), Math.toDegrees(lon), cl * Math.cos(lon), cl * Math.sin(lon), sl, c);
                int o = (j * W + i) * 4;
                d[o] = (float) c[0];
                d[o + 1] = (float) c[1];
                d[o + 2] = (float) c[2];
                d[o + 3] = (float) c[3];
            }
        }
        return d;
    }

    public static boolean has(String id) {
        return switch (id) {
            case "moon", "mercury", "venus", "mars", "jupiter", "saturn", "uranus", "neptune", "tatooine", "hoth",
                    "mustafar", "coruscant", "deathstar", "endor", "kamino", "arrakis", "polyphemus", "shattered",
                    "carian", "majora" -> true;
            default -> false;
        };
    }

    // ------------------------------------------------------------------ helpers

    static double[] unit(String hex) {
        int v = Integer.parseInt(hex.substring(1), 16);
        return new double[]{(v >> 16 & 255) / 255.0, (v >> 8 & 255) / 255.0, (v & 255) / 255.0};
    }

    private static void set(double[] out, double[] c) {
        out[0] = c[0];
        out[1] = c[1];
        out[2] = c[2];
    }

    private static double[] mix(double[] a, double[] b, double t) {
        return new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    private static double[] mul(double[] a, double k) {
        return new double[]{a[0] * k, a[1] * k, a[2] * k};
    }

    private static double adiff(double a, double b) {
        return (((a - b + 540) % 360) + 360) % 360 - 180;
    }

    private static double clamp(double v, double a, double b) {
        return v < a ? a : Math.min(b, v);
    }

    private static double angle(double x, double y, double z, double[] v) {
        return Math.toDegrees(Math.acos(clamp(x * v[0] + y * v[1] + z * v[2], -1, 1)));
    }

    /** A colour from bands by s (the lab's {@code bands}), blended softly across each edge. */
    private static double[] bands(Object[][] b, double s) {
        int i = 0;
        while (i < b.length - 1 && s >= (double) b[i + 1][0]) {
            i++;
        }
        double[] c = (double[]) b[i][1];
        if (i < b.length - 1) {
            double e = (double) b[i + 1][0], w = sstep(e - 0.03, e + 0.03, s);
            if (w > 0) {
                c = mix(c, (double[]) b[i + 1][1], w);
            }
        }
        if (i > 0) {
            double e = (double) b[i][0], w = 1 - sstep(e - 0.03, e + 0.03, s);
            if (w > 0) {
                c = mix(c, (double[]) b[i - 1][1], w);
            }
        }
        return c;
    }

    private static Object[][] band(Object... pairs) {
        Object[][] out = new Object[pairs.length / 2][];
        for (int k = 0; k < out.length; k++) {
            out[k] = new Object[]{((Number) pairs[k * 2]).doubleValue(), unit((String) pairs[k * 2 + 1])};
        }
        return out;
    }

    private static double[] dirLL(double lo, double la) {
        double a = Math.toRadians(lo), b = Math.toRadians(la);
        return new double[]{Math.cos(b) * Math.cos(a), Math.cos(b) * Math.sin(a), Math.sin(b)};
    }

    private static final Object[][] JB = band(-1, "#8e8a84", -.82, "#c7b293", -.62, "#a57b5b", -.48, "#e6d5b5", -.3, "#996748",
            -.12, "#ebdcc4", .1, "#a06a46", .3, "#e0cdae", .46, "#b08564", .6, "#cbb696", .82, "#8e8a84");
    private static final Object[][] SB = band(-1, "#a9a88f", -.75, "#d9c590", -.5, "#e8d6a4", -.25, "#d2b77c", -.1, "#efe0b0",
            .12, "#d4b679", .3, "#e6d3a0", .55, "#d6c08c", .78, "#b5b096", .9, "#8d9a9c");
    private static final Object[][] PB = band(-1, "#d8e8f0", -.7, "#7fb3d0", -.45, "#cfe6ee", -.25, "#4f8fb8", -.05, "#e4f2f4",
            .15, "#5aa0c0", .35, "#bfe0ea", .6, "#6aa6c8", .85, "#e0eef4");
    private static final double[] DISH = dirLL(-25, 32), RIFT = dirLL(40, -10);
    private static final double[][] MARIA_V;
    private static final double[] MARIA_R;
    private static final double[][] CRATER_V;
    private static final double[][] CRATER = {{-11, -43, 1.8, 1, 38}, {-20, 10, 1.5, 1, 20}, {-38, 8, 1, .8, 12},
            {-47, 24, .9, 1.4, 0}, {-9, 51, 1.7, -1, 0}, {-68, -5, 2.6, -.8, 0}, {-3, -34, 1.2, .5, 0}, {61, -44, 1.4, .5, 0}};

    static {
        double[][] maria = {{-57, 18, 26}, {-16, 33, 17}, {17, 28, 11}, {31, 8, 13}, {59, 17, 7.5}, {51, -8, 10}, {34, -15, 6.5},
                {-17, -21, 11}, {-39, -24, 6.5}, {-31, 7, 8}, {-23, -10, 6.5}, {4, 13, 4.5}, {-50, -5, 8}, {-8, 56, 5}, {10, 57, 5},
                {25, 56, 4.5}, {-30, 55, 5}};
        MARIA_V = new double[maria.length][];
        MARIA_R = new double[maria.length];
        for (int i = 0; i < maria.length; i++) {
            MARIA_V[i] = dirLL(maria[i][0], maria[i][1]);
            MARIA_R[i] = maria[i][2];
        }
        CRATER_V = new double[CRATER.length][];
        for (int i = 0; i < CRATER.length; i++) {
            CRATER_V[i] = dirLL(CRATER[i][0], CRATER[i][1]);
        }
    }

    private static final double[] HIGHLAND = unit("#d8d5cd"), MARE = unit("#858b93"), DARK_CRATER = unit("#6b7078");

    // ------------------------------------------------------------------ the maps

    static void gen(String id, double lat, double lon, double x, double y, double z, double[] out) {
        switch (id) {
            case "moon" -> moon(x, y, z, out);
            case "mercury" -> {
                double n = fbm(x * 4, y * 4, z * 4, 5);
                double[] c = mix(unit("#7d746d"), unit("#bab1a7"), n);
                double k = vn3(x * 16, y * 16, z * 16);
                if (k > .78) {
                    c = mix(c, unit("#ddd5c9"), (k - .78) * 3);
                }
                set(out, c);
            }
            case "venus" -> {
                double s = lat / 90 + (fbm(x * 1.6, y * 1.6, z * 3.2, 4) - .5) * .5;
                set(out, mul(mix(unit("#f0e3b6"), unit("#d3bd85"), .5 + .5 * Math.sin(s * 10)), .96 + .07 * fbm(x * 3, y * 3, z * 3, 3)));
            }
            case "mars" -> {
                double n = fbm(x * 2.3 + 3, y * 2.3, z * 2.3, 5);
                double[] c = unit("#bb582f");
                c = mix(c, unit("#6e3622"), sstep(.53, .64, n) * .85);
                c = mix(c, unit("#dc8f5c"), sstep(.42, .3, n) * .6);
                double e = (fbm(x * 6, y * 6, z * 6, 3) - .5) * 8;
                c = mix(c, unit("#f3efe8"), Math.max(sstep(70, 74, lat + e), sstep(-76, -80, lat + e)));
                set(out, c);
            }
            case "jupiter" -> {
                double sl = Math.sin(Math.toRadians(lat));
                double turb = (fbm(x * 3, y * 3, z * 3, 4) - .5) * .06
                        + Math.sin(Math.toRadians(lon) * 7 + sl * 25) * .012 * (Math.abs(sl) < .2 ? 1 : .3);
                double[] c = mul(bands(JB, sl + turb), .86 + .28 * fbm(x * 5, y * 5, z * 28, 3));
                double dl = (lat + 22) / 6.5, dn = adiff(lon, 40) / 12, e = dl * dl + dn * dn;
                if (e < 1.6) {
                    c = mix(c, e < 1 ? unit("#c8643c") : unit("#ecd2b0"), sstep(1.6, .9, e) * (e < 1 ? .85 : .5));
                }
                for (double[] ov : new double[][]{{-40, 120}, {-41, 150}, {-39, -160}}) {
                    double d2 = Math.pow((lat - ov[0]) / 2.4, 2) + Math.pow(adiff(lon, ov[1]) / 4, 2);
                    if (d2 < 1) {
                        c = mix(c, new double[]{1, .98, .94}, (1 - d2) * .8);
                    }
                }
                set(out, c);
            }
            case "saturn" -> {
                double s = Math.sin(Math.toRadians(lat)) + (fbm(x * 3, y * 3, z * 3, 3) - .5) * .025;
                double[] c = mul(bands(SB, s), .94 + .1 * fbm(x * 4, y * 4, z * 30, 3));
                if (lat > 76) {
                    c = mix(c, unit("#8f9c9c"), .6);
                }
                set(out, c);
            }
            case "uranus" -> {
                double sl = Math.abs(Math.sin(Math.toRadians(lat)));
                set(out, mul(mix(unit("#9fd9e0"), unit("#c6eff2"), sstep(.3, .9, sl)), .97 + .05 * fbm(x * 3, y * 3, z * 12, 3)));
            }
            case "neptune" -> {
                double sl = Math.sin(Math.toRadians(lat));
                double[] c = mul(unit("#3e66d3"), .92 + .12 * fbm(x * 3, y * 3, z * 14, 3));
                if (sl > -.5 && sl < -.32) {
                    c = mix(c, unit("#2e50b8"), .6);
                }
                double e = Math.pow((lat + 20) / 6, 2) + Math.pow(adiff(lon, 30) / 11, 2);
                if (e < 1) {
                    c = mix(c, unit("#20348a"), .85 * (1 - e * .5));
                }
                if (Math.abs(lat + 27) < 2.4 && Math.abs(adiff(lon, 34)) < 40) {
                    c = mix(c, new double[]{1, 1, 1}, .6 * (1 - Math.abs(lat + 27) / 2.4));
                }
                if (Math.abs(lat - 28) < 2 && Math.abs(adiff(lon, 150)) < 50) {
                    c = mix(c, unit("#e8f0ff"), .5 * (1 - Math.abs(lat - 28) / 2));
                }
                set(out, c);
            }
            case "tatooine" -> {
                double n = fbm(x * 2.2, y * 2.2, z * 2.2, 5);
                double dune = .5 + .5 * Math.sin(Math.toRadians(lon * .35 + lat * 1.3) * 8 + n * 9);
                double[] c = mul(mix(unit("#dcb57c"), unit("#b8834e"), sstep(.45, .7, n)), .88 + .16 * dune);
                c = mix(c, unit("#8a5a36"), sstep(.62, .72, fbm(x * 5 + 2, y * 5, z * 5, 3)) * .6);
                if (Math.abs(lat) > 72) {
                    c = mix(c, unit("#efe2c4"), .5);
                }
                set(out, c);
            }
            case "hoth" -> {
                double[] c = mix(unit("#f3f8ff"), unit("#c2d6ec"), fbm(x * 3, y * 3, z * 3, 5));
                double k = Math.abs(fbm(x * 6 + 5, y * 6, z * 6, 4) - .5);
                if (k < .025) {
                    c = mix(c, unit("#7a9cc6"), (1 - k / .025) * .75);
                }
                set(out, mix(c, unit("#9db8d8"), sstep(.6, .75, fbm(x * 1.5, y * 1.5, z * 1.5, 3)) * .4));
            }
            case "mustafar" -> {
                double n = fbm(x * 2.6, y * 2.6, z * 2.6, 5), r = Math.abs(fbm(x * 4 + 3, y * 4, z * 4, 4) - .5);
                double lava = Math.min(1, sstep(.06, 0, r) + sstep(.66, .78, n) * .8);
                set(out, mix(mix(unit("#1a0e0c"), unit("#3a1a12"), n), unit("#ff6a1a"), lava * .85));
                out[3] = lava;
            }
            case "coruscant" -> {
                boolean grid = Math.abs(Math.sin(Math.toRadians(lon) * 60)) < .12 || Math.abs(Math.sin(Math.toRadians(lat) * 60)) < .12;
                set(out, mul(mix(unit("#6b6458"), unit("#a09482"), fbm(x * 3, y * 3, z * 3, 4)), grid ? .78 : 1));
                out[3] = sstep(.42, .8, vn3(x * 40, y * 40, z * 40)) * (grid ? 1 : .55) * (Math.abs(lat) < 76 ? 1 : .15);
            }
            case "deathstar" -> {
                boolean panel = Math.abs(Math.sin(Math.toRadians(lon) * 36)) < .07 || Math.abs(Math.sin(Math.toRadians(lat) * 30)) < .07;
                double[] c = mul(unit("#a2a7ad"), (.84 + .2 * h3((int) Math.floor(lon / 6), (int) Math.floor(lat / 5), 3)) * (panel ? .8 : 1));
                double e = 0;
                if (Math.abs(lat) < 1.6) {
                    c = mul(c, .3);
                }
                double dd = angle(x, y, z, DISH);
                if (dd < 13) {
                    c = dd > 12 ? unit("#c8cdd2") : mul(c, .5 + .35 * dd / 13);
                    if (Math.abs(dd % 4 - 2) < .4) {
                        c = mul(c, .82);
                    }
                    if (dd < 2.4) {
                        c = unit("#8dffa6");
                        e = 1;
                    }
                }
                set(out, c);
                out[3] = e;
            }
            case "endor" -> {
                double n = fbm(x * 2.5, y * 2.5, z * 2.5, 5);
                double[] c = n > .48 ? mix(unit("#2c5a2a"), unit("#4f7d3a"), (n - .48) * 3) : mix(unit("#1a3a58"), unit("#2c5a78"), n * 2);
                set(out, mix(c, new double[]{1, 1, 1}, sstep(.55, .72, fbm(x * 3 + 9, y * 3, z * 6, 4)) * .8));
            }
            case "kamino" -> {
                double[] c = mix(unit("#0d4458"), unit("#1f7088"), fbm(x * 2 + Math.sin(Math.toRadians(lat) * 3) * .8, y * 2, z * 2, 5));
                set(out, mix(c, unit("#e2ecf2"), sstep(.55, .7, fbm(x * 3.5, y * 3.5, z * 3.5, 4)) * .85));
            }
            case "arrakis" -> {
                double n = fbm(x * 2, y * 2, z * 2, 5);
                double[] c = mul(mix(unit("#e3a052"), unit("#b8642a"), sstep(.4, .7, n)), .9 + .12 * (.5 + .5 * Math.sin(Math.toRadians(lon + n * 40) * 14)));
                if (lat > 78) {
                    c = mix(c, unit("#f4e6cc"), .7);
                }
                set(out, c);
            }
            case "polyphemus" -> {
                double s = Math.sin(Math.toRadians(lat)) + (fbm(x * 3, y * 3, z * 3, 4) - .5) * .08 + Math.sin(Math.toRadians(lon) * 5 + lat * .3) * .015;
                set(out, mul(bands(PB, s), .88 + .2 * fbm(x * 5, y * 5, z * 26, 3)));
            }
            case "shattered" -> {
                double n = fbm(x * 3, y * 3, z * 3, 5);
                double[] c = mul(unit("#8d8a86"), .7 + .45 * n);
                double e = sstep(.035, 0, Math.abs(fbm(x * 2.2 + 1, y * 2.2, z * 2.2, 4) - .5))
                        + .6 * sstep(.02, 0, Math.abs(fbm(x * 5 + 7, y * 5, z * 5, 3) - .5));
                double rf = angle(x, y, z, RIFT);
                if (rf < 30) {
                    double k = sstep(30, 18, rf + (n - .5) * 16);
                    c = mix(c, unit("#3a1408"), k);
                    e = Math.max(e, k * .9);
                }
                set(out, c);
                out[3] = Math.min(1, e);
            }
            case "carian" -> {
                moon(x, y, z, out);
                double dd = Math.toDegrees(Math.acos(clamp(x, -1, 1)));
                double an = ((Math.toDegrees(Math.atan2(z, y)) % 45) + 45) % 45;
                out[3] = Math.abs(dd - 30) < 1.5 || Math.abs(dd - 12) < 1.1 ? 1 : dd > 12 && dd < 30 && Math.abs(an - 22.5) < 1.7 ? .8 : 0;
            }
            case "majora" -> majora(lat, lon, x, y, z, out);
            default -> set(out, new double[]{0.5, 0.5, 0.5});
        }
    }

    private static void moon(double x, double y, double z, double[] out) {
        double m = 0;
        double wob = (fbm(x * 5, y * 5, z * 5, 3) - .5) * .5;
        for (int i = 0; i < MARIA_V.length; i++) {
            double ad = angle(x, y, z, MARIA_V[i]), rr = MARIA_R[i] * (1 + wob);
            m = Math.max(m, sstep(rr, rr * .72, ad));
        }
        double hl = .8 + .32 * fbm(x * 6, y * 6, z * 6, 5);
        double[] c = mix(mul(HIGHLAND, hl), mul(MARE, .9 + .22 * fbm(x * 9, y * 9, z * 9, 3)), m * .92);
        double[] p = {x, y, z};
        for (int k = 0; k < CRATER.length; k++) {
            double[] v = CRATER_V[k];
            double r = CRATER[k][2], b = CRATER[k][3], ray = CRATER[k][4], seed = CRATER[k][0] * 7 + CRATER[k][1];
            double ad = angle(x, y, z, v);
            if (ad < r * 1.6) {
                double f = sstep(r * 1.6, r * .6, ad);
                c = b > 0 ? mix(c, new double[]{1, 1, .98}, f * .55 * b) : mix(c, DARK_CRATER, f * .7 * -b);
            }
            if (ray != 0 && ad < ray) {
                // The rays' bearing round the crater, from north.
                double[] t = cross(v, p), up = cross(new double[]{0, 0, 1}, v);
                double brg = Math.atan2(t[2], dot(t, up) + 1e-6);
                double rs = sstep(.66, .85, vn1(Math.toDegrees(brg) / 7 + seed));
                c = mix(c, new double[]{1, 1, 1}, rs * .2 * (1 - ad / ray));
            }
        }
        if (vn3(x * 24, y * 24, z * 24) > .8) {
            c = mix(c, new double[]{1, 1, 1}, .14);
        }
        set(out, c);
    }

    private static void majora(double lat, double lon, double x, double y, double z, double[] out) {
        double[] c = mix(unit("#e3b26a"), unit("#c98a44"), fbm(x * 4, y * 4, z * 4, 4));
        c = mix(c, unit("#9a6232"), sstep(.6, .7, vn3(x * 14, y * 14, z * 14)) * .5);
        for (int sx : new int[]{-1, 1}) {
            double ex = adiff(lon, sx * 23) / 11, ey = (lat - 14) / 9, r = ex * ex + ey * ey;
            if (r < 1) {
                c = unit("#f6efe0");
                double ix = adiff(lon, sx * 20) / 4.6, iy = (lat - 13) / 4.6, ri = ix * ix + iy * iy;
                if (ri < 1) {
                    c = ri < .3 ? unit("#1a0c06") : unit("#f0a020");
                }
            } else if (r < 1.45) {
                c = mul(c, .42);
            }
        }
        double al = Math.abs(lon), bl = 25 + (al - 10) * .38;
        if (al > 9 && al < 38 && Math.abs(lat - bl) < 2.6) {
            c = unit("#5a3416");
        }
        double nx = lon / 6, ny = (lat + 1) / 11;
        if (nx * nx + ny * ny < 1) {
            c = mix(c, unit("#f2c47e"), .65);
        }
        double mx = lon / 40, my = (lat + 24 + mx * mx * 9) / 6;
        if (Math.abs(mx) < 1 && Math.abs(my) < 1) {
            c = unit("#2a0f08");
            if (Math.abs(my) < .55 && Math.abs(Math.sin(Math.toRadians(lon) * 22)) > .3) {
                c = unit("#efe6d2");
            }
        }
        set(out, c);
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }
}
