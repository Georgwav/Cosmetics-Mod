package io.github.georgwav.cosmetics.sol.sky;

import static io.github.georgwav.cosmetics.sol.sky.LabNoise.lerp;
import static io.github.georgwav.cosmetics.sol.sky.LabNoise.sstep;
import static io.github.georgwav.cosmetics.sol.sky.LabNoise.vn1;
import static io.github.georgwav.cosmetics.sol.sky.LabNoise.vn3;

/**
 * Ray-traces a body into a little square of pixels (the Night Sky lab's {@code renderBody}, {@code bhPixels},
 * {@code haloPixels}): each pixel is a ray onto the sphere, which finds its latitude and longitude on the turning
 * map, lights it from the sun's side (rock, cloud and gas each scatter the light their own way), and adds the
 * rings' shadow on the ball, the rings themselves lit and shadowed by the ball, small moons, the thin air at the rim,
 * what glows by itself, earthshine and the copper of an eclipse. Gargantua and Halo have their own. Pure Java; the
 * pixels are ARGB (colour at full strength, coverage in alpha), drawn added over a dark disc.
 */
public final class BodyRender {
    /** How a body is lit and turned this time. */
    public static final class Light {
        /** Toward the sun, in the picture's axes (x right, y up, z toward you). */
        public double lx, ly, lz = 1;
        /** The axis's turn on the screen (radians) and its tilt toward you (radians). */
        public double pa, tilt;
        /** How far it has turned (radians), the colour it's tinted, earthshine, how strong its own light is. */
        public double spin, tr = 1, tg = 1, tb = 1, earthshine, glow = 1;
        /** A shadow over it (a lunar eclipse): centre and radius in body radii; r 0 for none. */
        public double ex, ey, er;
        /** Seconds, for what moves on it (moons, the disk). */
        public double t;
    }

    private BodyRender() {
    }

    /** The picture's side in pixels for a body {@code n} pixels across. */
    public static int size(Bodies.Body b, int n) {
        return Math.max(3, (int) Math.round(n * b.box()));
    }

    /** Renders body {@code b}, {@code n} pixels across, into {@code out} (at least {@link #size} squared). */
    public static void render(Bodies.Body b, float[] map, int n, Light o, int[] out) {
        int s = size(b, n);
        double R = n / 2.0, C = s / 2.0;
        double sB = Math.sin(o.tilt), cB = Math.cos(o.tilt);
        double px = -Math.sin(o.pa) * cB, py = Math.cos(o.pa) * cB, pz = sB;
        // e1 = z × p, e2 = p × e1: the axes the longitude is measured in.
        double e1x = -py, e1y = px, e1z = 0, l1 = Math.sqrt(e1x * e1x + e1y * e1y);
        if (l1 < 1e-3) {
            e1x = 1;
            e1y = 0;
            l1 = 1;
        }
        e1x /= l1;
        e1y /= l1;
        double e2x = py * e1z - pz * e1y, e2y = pz * e1x - px * e1z, e2z = px * e1y - py * e1x;
        if (b.kind() == Bodies.Kind.BLACK_HOLE) {
            blackHole(s, R, C, o, out);
            return;
        }
        if (b.kind() == Bodies.Kind.RING_WORLD) {
            ringWorld(s, R, C, o, px, py, pz, e1x, e1y, e1z, e2x, e2y, e2z, out);
            return;
        }
        double lx = o.lx, ly = o.ly, lz = o.lz, pL = px * lx + py * ly + pz * lz;
        Bodies.Rings ring = b.rings();
        int ns = b.sats().size();
        double[] sx = new double[ns], sy = new double[ns], sr = new double[ns];
        boolean[] front = new boolean[ns];
        for (int k = 0; k < ns; k++) {
            Bodies.Satellite st = b.sats().get(k);
            double a = st.a() + st.w() * o.t, sn = Math.sin(a);
            sx[k] = Math.cos(a) * st.d();
            sy[k] = sn * st.d() * st.inc();
            sr[k] = st.r();
            front[k] = st.fixed() || sn < 0;
        }
        for (int j = 0; j < s; j++) {
            double y = (C - (j + 0.5)) / R;
            for (int i = 0; i < s; i++) {
                double x = ((i + 0.5) - C) / R, r2 = x * x + y * y;
                double cr = 0, cg = 0, cb = 0, ca = 0, zs = -9;
                if (r2 < 1) {
                    double z = Math.sqrt(1 - r2);
                    zs = z;
                    double nP = x * px + y * py + z * pz;
                    double lat = Math.asin(Math.max(-1, Math.min(1, nP)));
                    double lon = Math.atan2(-(x * e1x + y * e1y + z * e1z), x * e2x + y * e2y + z * e2z) + o.spin;
                    double u = (((lon / (Math.PI * 2) + 0.5) % 1) + 1) % 1, v = 0.5 - lat / Math.PI;
                    int mo = (Math.min(BodyMaps.H - 1, Math.max(0, (int) (v * BodyMaps.H))) * BodyMaps.W
                            + Math.min(BodyMaps.W - 1, (int) (u * BodyMaps.W))) * 4;
                    double mu0 = x * lx + y * ly + z * lz;
                    double I = switch (b.kind()) {
                        case ROCK -> sstep(-.03, .24, mu0) * (.7 + .32 * Math.min(1, 2 * mu0 / (mu0 + z + .05)));
                        case CLOUD -> Math.pow(Math.max(0, mu0), .75) * 1.05;
                        default -> Math.pow(Math.max(0, mu0), .9) * (.62 + .38 * Math.sqrt(z)) * 1.1;
                    };
                    I *= sstep(-.06, .1, mu0 + .02);
                    if (ring != null && Math.abs(pL) > 1e-3) {
                        // The rings' shadow on the ball.
                        double t = -nP / pL;
                        if (t > 0) {
                            Bodies.Band bnd = ring.at(Math.sqrt(sq(x + t * lx) + sq(y + t * ly) + sq(z + t * lz)));
                            if (bnd != null) {
                                I *= 1 - .8 * bnd.op();
                            }
                        }
                    }
                    double m0 = map[mo], m1 = map[mo + 1], m2 = map[mo + 2], m3 = map[mo + 3];
                    cr = m0 * I * o.tr;
                    cg = m1 * I * o.tg;
                    cb = m2 * I * o.tb;
                    if (b.emits() && m3 > 0) {
                        double k = m3 * (b.emNight() ? 1 - Math.min(1, I * 1.5) : 1) * o.glow;
                        cr += b.emC()[0] * k;
                        cg += b.emC()[1] * k;
                        cb += b.emC()[2] * k;
                    }
                    if (o.earthshine > 0) {
                        double k = o.earthshine * (1 - sstep(-.12, .14, mu0));
                        cr += m0 * k * .55;
                        cg += m1 * k * .62;
                        cb += m2 * k * .76;
                    }
                    if (o.er > 0) {
                        double f = sstep(.05, .62, Math.hypot(x - o.ex, y - o.ey) / o.er);
                        cr *= lerp(.36, .9, f);
                        cg *= lerp(.07, .36, f);
                        cb *= lerp(.04, .2, f);
                    }
                    if (b.atm() > 0) {
                        double rim = Math.pow(1 - z, 2.5) * sstep(-.25, .35, mu0) * b.atm();
                        cr += rim * b.atmC()[0];
                        cg += rim * b.atmC()[1];
                        cb += rim * b.atmC()[2];
                    }
                    ca = 1;
                }
                if (ring != null && Math.abs(pz) > 1e-4) {
                    // The rings in front of the ball (or beside it), lit and in the ball's shadow.
                    double zr = -(px * x + py * y) / pz;
                    if (zr > zs) {
                        double rr = Math.sqrt(x * x + y * y + zr * zr);
                        Bodies.Band bnd = ring.at(rr);
                        if (bnd != null) {
                            double lit = .3 + .7 * Math.sqrt(Math.abs(pL));
                            double bq = x * lx + y * ly + zr * lz, disc = bq * bq - (rr * rr - 1);
                            if (disc > 0 && -bq - Math.sqrt(disc) > 0) {
                                lit *= .06;
                            }
                            double a = bnd.op() * ring.op();
                            cr = cr * (1 - a) + bnd.c()[0] * lit * o.tr * a;
                            cg = cg * (1 - a) + bnd.c()[1] * lit * o.tg * a;
                            cb = cb * (1 - a) + bnd.c()[2] * lit * o.tb * a;
                            ca = ca + a * (1 - ca);
                        }
                    }
                }
                for (int k = 0; k < ns; k++) {
                    double dx = x - sx[k], dy = y - sy[k], dd = dx * dx + dy * dy;
                    if (dd < sr[k] * sr[k] && (front[k] || r2 >= 1)) {
                        double nz = Math.sqrt(sr[k] * sr[k] - dd) / sr[k];
                        double i2 = .1 + .9 * Math.max(0, (dx * lx + dy * ly) / sr[k] + nz * lz);
                        float[] c = b.sats().get(k).c();
                        cr = c[0] * i2 * o.tr;
                        cg = c[1] * i2 * o.tg;
                        cb = c[2] * i2 * o.tb;
                        ca = 1;
                    }
                }
                if (ca > 0 && ca < 1) {
                    cr /= ca;
                    cg /= ca;
                    cb /= ca;
                }
                out[j * s + i] = argb(ca, cr, cg, cb);
            }
        }
    }

    private static double sq(double v) {
        return v * v;
    }

    static int argb(double a, double r, double g, double b) {
        return (c8(a) << 24) | (c8(r) << 16) | (c8(g) << 8) | c8(b);
    }

    private static int c8(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v * 255)));
    }

    private static double[] hot(double k) {
        return new double[]{lerp(.78, 1, k), lerp(.28, .93, k), lerp(.08, .72, k)};
    }

    /** Gargantua: a black shadow, a thin photon ring, the far side of the disk bent over and under, the near side across. */
    private static void blackHole(int s, double R, double C, Light o, int[] out) {
        double rS = .27, tilt = .15;
        for (int j = 0; j < s; j++) {
            double y = (C - (j + 0.5)) / R;
            for (int i = 0; i < s; i++) {
                double x = ((i + 0.5) - C) / R, r = Math.hypot(x, y), ang = Math.atan2(y, x);
                double hal = sstep(.27, .32, r) * (1 - sstep(.4, .74, r)) * (.2 + .8 * Math.pow(Math.abs(Math.sin(ang)), .7));
                double ring = Math.exp(-sq((r - .3) / .02)) * 1.5;
                double[] hc = hot(Math.max(0, Math.min(1, 1.15 - (r - .27) * 2.2)));
                double em = hal * 1.15 + ring;
                double cr = hc[0] * em, cg = hc[1] * em, cb = hc[2] * em, ca = 0;
                if (r < rS) {
                    cr = cg = cb = 0;
                    ca = 1;
                }
                double yd = y / tilt, rd = Math.hypot(x, yd);
                if (rd > .36 && rd < 1 && (y <= 0 || r >= rS)) {
                    double k = 1 - sstep(.36, 1, rd), dop = 1 + .7 * (-x / rd);
                    double sw = .75 + .25 * vn1(rd * 14 - o.t * 1.4 + Math.atan2(yd, x) * 2);
                    double I = Math.pow(k, 1.3) * 1.5 * dop * sw;
                    double[] c2 = hot(Math.max(0, Math.min(1, k * 1.25)));
                    cr += c2[0] * I;
                    cg += c2[1] * I;
                    cb += c2[2] * I;
                }
                double m = Math.max(cr, Math.max(cg, cb));
                if (ca < 1) {
                    ca = Math.min(1, m);
                }
                if (ca > 0 && ca < 1) {
                    cr /= ca;
                    cg /= ca;
                    cb /= ca;
                }
                out[j * s + i] = argb(ca, cr, cg, cb);
            }
        }
    }

    private static final double[] HMET = BodyMaps.unit("#b4bdc9"), HL1 = BodyMaps.unit("#4f9a48"), HL2 = BodyMaps.unit("#b8a46a"),
            HS1 = BodyMaps.unit("#2a64a0"), HS2 = BodyMaps.unit("#4a9ac0");

    /** Halo: a ribbon round an axis, solved as a short cylinder; land and cloud inside, metal outside. */
    private static void ringWorld(int s, double R, double C, Light o, double px, double py, double pz, double e1x, double e1y,
                                  double e1z, double e2x, double e2y, double e2z, int[] out) {
        double h = .075, A = 1 - pz * pz;
        for (int j = 0; j < s; j++) {
            double y = (C - (j + 0.5)) / R;
            for (int i = 0; i < s; i++) {
                double x = ((i + 0.5) - C) / R, qq = px * x + py * y;
                double hz = Double.NaN, hs = 0;
                if (A > 1e-6) {
                    double bq = -2 * pz * qq, cq = x * x + y * y - qq * qq - 1, disc = bq * bq - 4 * A * cq;
                    if (disc >= 0) {
                        double sqd = Math.sqrt(disc);
                        for (double z : new double[]{(-bq + sqd) / (2 * A), (-bq - sqd) / (2 * A)}) {
                            double s2 = qq + pz * z;
                            if (Math.abs(s2) < h) {
                                hz = z;
                                hs = s2;
                                break;
                            }
                        }
                    }
                }
                if (Double.isNaN(hz)) {
                    out[j * s + i] = 0;
                    continue;
                }
                double ax = x - px * hs, ay = y - py * hs, az = hz - pz * hs;
                boolean outer = az > 0;
                double nx = outer ? ax : -ax, ny = outer ? ay : -ay, nz = outer ? az : -az;
                double th = Math.atan2(ax * e2x + ay * e2y + az * e2z, ax * e1x + ay * e1y + az * e1z), sb = hs / h;
                double[] c;
                if (outer || Math.abs(sb) > .8) {
                    double k = Math.abs(Math.sin(th * 48)) < .12 ? .7 : 1;
                    c = new double[]{HMET[0] * k, HMET[1] * k, HMET[2] * k};
                } else {
                    double n1 = vn3(Math.cos(th) * 22 + 50, Math.sin(th) * 22, sb * 2.4);
                    c = n1 > .52 ? mix(HL1, HL2, (n1 - .52) * 2.5) : mix(HS1, HS2, n1 * 1.8);
                    double cl = vn3(Math.cos(th) * 40 + 3, Math.sin(th) * 40, sb * 4);
                    if (cl > .6) {
                        c = mix(c, new double[]{1, 1, 1}, Math.min(1, (cl - .6) * 2.6));
                    }
                }
                double I = .5 + .6 * Math.max(0, nx * o.lx + ny * o.ly + nz * o.lz);
                out[j * s + i] = argb(1, c[0] * I * o.tr, c[1] * I * o.tg, c[2] * I * o.tb);
            }
        }
    }

    private static double[] mix(double[] a, double[] b, double t) {
        return new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }
}
