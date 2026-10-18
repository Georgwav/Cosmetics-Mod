package io.github.georgwav.cosmetics.sol.sky;

import io.github.georgwav.cosmetics.sol.util.Mulberry32;

import java.util.List;

/**
 * The moon, the planets and the wild worlds of the Night Sky lab ({@code BODIES}, {@code RINGS}): how each looks
 * (its kind of surface, size in the sky, phase, axis tilt, spin, glow, rings, moons, air) and where it is. The real
 * planets sit on the ecliptic at a fixed angle from the sun ({@code rel}), so every night has all of them at the same
 * times; the wild worlds sit at a fixed place from the point opposite the sun ({@code dra} hours, {@code dec}).
 * Pure Java; immutable.
 */
public final class Bodies {
    public enum Kind { ROCK, CLOUD, GAS, ICE, BLACK_HOLE, RING_WORLD }

    /** A small moon drawn on or round a body: angle, distance and speed round it, tilt, radius, colour. */
    public record Satellite(double a, double d, double w, double inc, double r, float[] c, boolean fixed) {
    }

    /** One ring band: from and to (body radii), opacity, colour. */
    public record Band(double r0, double r1, double op, float[] c) {
    }

    public record Rings(double op, List<Band> bands) {
        Band at(double r) {
            for (Band b : bands) {
                if (r >= b.r0() && r < b.r1()) {
                    return b;
                }
            }
            return null;
        }
    }

    /**
     * A body. {@code size} is its diameter in the sky in degrees; {@code phase} how far round its lit side is
     * (degrees); {@code vis} the sun's altitude at which it starts to show; {@code tilt} (B) and {@code turn} (pa)
     * its axis; {@code box} how much room round it its rings or moons need.
     */
    public record Body(String id, String name, String wild, Kind kind, double size, double phase, double rel, double bet,
                       double dra, double dec, double vis, double spin, double tilt, double turn, Rings rings, double box,
                       double glow, double atm, float[] atmC, float[] emC, boolean emPulse, boolean emNight, boolean moons,
                       List<Satellite> sats) {
        public boolean real() {
            return !Double.isNaN(rel);
        }

        public boolean placed() {
            return real() || !Double.isNaN(dra);
        }

        public boolean emits() {
            return emC != null;
        }
    }

    private static final double NO = Double.NaN;

    public static final Rings SATURN_RINGS = new Rings(0.92, List.of(
            band(1.24, 1.53, .28, 150, 138, 118), band(1.53, 1.72, .82, 226, 212, 176), band(1.72, 1.95, .98, 236, 222, 186),
            band(1.95, 2.03, .05, 120, 110, 95), band(2.03, 2.2, .72, 205, 190, 152), band(2.2, 2.23, .12, 150, 140, 120),
            band(2.23, 2.27, .6, 200, 186, 150)));
    public static final Rings URANUS_RINGS = new Rings(0.5, List.of(
            band(1.62, 1.67, .25, 120, 140, 160), band(1.76, 1.8, .25, 120, 140, 160), band(1.94, 2.02, .55, 170, 190, 205)));

    private static Band band(double r0, double r1, double op, int r, int g, int b) {
        return new Band(r0, r1, op, new float[]{r / 255f, g / 255f, b / 255f});
    }

    private static float[] c(double r, double g, double b) {
        return new float[]{(float) r, (float) g, (float) b};
    }

    private static Body body(String id, String name, String wild, Kind kind, double size, double phase, double rel, double bet,
                             double dra, double dec, double vis, double spin, double tilt, double turn, Rings rings, double box,
                             double glow, double atm, float[] atmC, float[] emC) {
        return new Body(id, name, wild, kind, size, phase, rel, bet, dra, dec, vis, spin, tilt, turn, rings, box, glow, atm, atmC,
                emC, false, false, false, List.of());
    }

    public static final Body MOON = body("moon", "Moon", null, Kind.ROCK, 5, 0, NO, 0, NO, 0, 0, 0, 0, 0, null, 1, 0, 0, null, null);
    public static final Body CARIAN = body("carian", "Carian moon", null, Kind.ROCK, 5, 0, NO, 0, NO, 0, 0, 0, 0, 0, null, 1, 0, 0,
            null, c(.55, .82, 1.1));
    public static final Body MAJORA = body("majora", "Falling moon", null, Kind.ROCK, 5, 0, NO, 0, NO, 0, 0, 0, 0, 0, null, 1, 0, 0,
            null, null);

    /** Every planet and wild world, in the lab's order. */
    public static final List<Body> PLANETS;

    static {
        Body mercury = body("mercury", "Mercury", null, Kind.ROCK, .8, 95, -19, 1.5, NO, 0, -4, .05, 0, 0, null, 1, .5, 0, null, null);
        Body venus = body("venus", "Venus", null, Kind.CLOUD, 1.3, 112, 38, 2, NO, 0, -1, .02, 0, 0, null, 1, 1.2, .5, c(1, .95, .8), null);
        Body mars = body("mars", "Mars", null, Kind.ROCK, 1.1, 34, 165, 1, NO, 0, -6, .15, 22, 0, null, 1, .45, .25, c(1, .6, .45), null);
        Body jupiter = new Body("jupiter", "Jupiter", null, Kind.GAS, 2, 32, 128, -1, NO, 0, -4, .26, 3, 0, null, 1, .6, 0, null,
                null, false, false, true, List.of());
        Body saturn = body("saturn", "Saturn", null, Kind.GAS, 1.6, 36, -150, -1.5, NO, 0, -6, .2, 22, -6, SATURN_RINGS, 2.4, .5, 0,
                null, null);
        Body uranus = body("uranus", "Uranus", null, Kind.ICE, .9, 26, 100, -.5, NO, 0, -11, .1, 58, 80, URANUS_RINGS, 2.15, .4, .3,
                c(.8, 1, 1), null);
        Body neptune = body("neptune", "Neptune", null, Kind.ICE, .85, 26, -110, -1, NO, 0, -13, .12, 24, 0, null, 1, .4, .3,
                c(.6, .75, 1), null);
        Body gargantua = body("gargantua", "Gargantua", "Interstellar", Kind.BLACK_HOLE, 7, 0, NO, 0, -1, 58, -4, 0, 0, 0, null, 1,
                .55, 0, null, null);
        Body polyphemus = new Body("polyphemus", "Polyphemus", "Avatar", Kind.GAS, 6.5, 40, NO, 0, 3.2, 18, -3, .05, 8, 14, null, 1,
                .5, 0, null, null, false, false, false, List.of(new Satellite(0, .5, 0, 1, .13, c(.36, .62, .52), true)));
        Body halo = body("halo", "Halo", "Halo", Kind.RING_WORLD, 10, 30, NO, 0, -2.2, 40, -4, 0, 26, 30, null, 1, .3, 0, null, null);
        Body deathstar = new Body("deathstar", "Death Star", "Star Wars", Kind.ROCK, 2.6, 45, NO, 0, .8, 8, -5, .02, 10, 0, null, 1,
                .35, 0, null, c(.5, 1, .62), true, false, false, List.of());
        Satellite[] pieces = new Satellite[11];
        for (int i = 0; i < pieces.length; i++) {
            Mulberry32 r = new Mulberry32(900 + i);
            double a = r.next() * Math.PI * 2, d = 1.25 + r.next() * .6, w = .04 + r.next() * .05, rr = .04 + r.next() * .08;
            float[] col = c(.5 + r.next() * .15, .48 + r.next() * .12, .45 + r.next() * .1);
            pieces[i] = new Satellite(a, d, w, .3, rr, col, false);
        }
        Body shattered = new Body("shattered", "Shattered Moon", "Destiny", Kind.ROCK, 3.6, 50, NO, 0, 1.8, 32, -5, .03, 0, 0, null, 2,
                .4, 0, null, c(1, .5, .18), false, false, false, List.of(pieces));
        Body coruscant = new Body("coruscant", "Coruscant", "Star Wars", Kind.ROCK, 2.2, 70, NO, 0, -3.5, 22, -6, .05, 15, 0, null, 1,
                .45, 0, null, c(1, .8, .42), false, true, false, List.of());
        Body mustafar = body("mustafar", "Mustafar", "Star Wars", Kind.ROCK, 2, 55, NO, 0, 0, -12, -6, .06, 0, 0, null, 1, .5, 0, null,
                c(1, .45, .12));
        Body tatooine = body("tatooine", "Tatooine", "Star Wars", Kind.ROCK, 2.2, 40, NO, 0, -1.2, 2, -6, .04, 0, 0, null, 1, .4, 0,
                null, null);
        Body hoth = body("hoth", "Hoth", "Star Wars", Kind.ROCK, 1.8, 45, NO, 0, 4.5, 66, -6, .05, 0, 0, null, 1, .5, .3, c(.8, .9, 1),
                null);
        Body endor = body("endor", "Endor", "Star Wars", Kind.ICE, 1.6, 40, NO, 0, -5, 62, -6, .06, 0, 0, null, 1, .4, .35,
                c(.6, .85, 1), null);
        Body kamino = body("kamino", "Kamino", "Star Wars", Kind.ICE, 2, 45, NO, 0, 6.5, 57, -6, .05, 0, 0, null, 1, .4, .35,
                c(.6, .85, 1), null);
        Body arrakis = body("arrakis", "Arrakis", "Dune", Kind.ROCK, 2.1, 40, NO, 0, 2.8, -4, -6, .04, 0, 0, null, 1, .45, .2,
                c(1, .75, .5), null);
        PLANETS = List.of(mercury, venus, mars, jupiter, saturn, uranus, neptune, gargantua, polyphemus, halo, deathstar, shattered,
                coruscant, mustafar, tatooine, hoth, endor, kamino, arrakis);
    }

    /** The equatorial unit vector of a planet or wild world, for the sun's ecliptic longitude (degrees). */
    public static double[] place(Body b, double lamSun, double[] out) {
        if (b.real()) {
            return SkySphere.ecl(lamSun + b.rel(), b.bet(), out);
        }
        // The wild worlds: hours from the point opposite the sun, and a declination.
        double a = Math.toRadians(lamSun) + Math.PI + Math.toRadians(b.dra() * 15), d = Math.toRadians(b.dec());
        out[0] = Math.cos(d) * Math.cos(a);
        out[1] = Math.cos(d) * Math.sin(a);
        out[2] = Math.sin(d);
        return out;
    }

    public static Body find(String id) {
        if (MOON.id().equals(id)) {
            return MOON;
        }
        if (CARIAN.id().equals(id)) {
            return CARIAN;
        }
        if (MAJORA.id().equals(id)) {
            return MAJORA;
        }
        for (Body b : PLANETS) {
            if (b.id().equals(id)) {
                return b;
            }
        }
        return null;
    }
}
