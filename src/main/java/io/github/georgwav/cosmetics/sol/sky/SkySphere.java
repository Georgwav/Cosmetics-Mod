package io.github.georgwav.cosmetics.sol.sky;

/**
 * The one celestial sphere the Sol sky hangs on (the Night Sky lab's {@code envOf}): the sun, the moon, the planets
 * and the stars all turn round the celestial pole, which stands {@code latitude} degrees above the northern horizon.
 * The game's time of day is the hour angle, so the sun (declination 0) still rises and sets on the game's ticks;
 * over a {@code yearDays}-day year the sun moves once round the ecliptic, so the same hour of night shows a
 * different sky each season. The moon sits at the angle from the sun its phase gives.
 *
 * <p>Directions come out in the game's world axes: x east, y up, z south. Pure Java (no game classes); one
 * instance per frame on the render thread, reused.
 */
public final class SkySphere {
    public static final float DEFAULT_LATITUDE = 34f;
    public static final int DEFAULT_YEAR = 96;
    /** The tilt of the ecliptic to the equator. */
    static final double EPS = Math.toRadians(23.44);
    /** Where the sun is on day 0: the autumn equinox, the lab's default season (the summer Milky Way at dusk). */
    static final double LAM0 = 180.0;
    /** The galactic to equatorial rotation (J2000), as the lab has it. */
    private static final double[][] GT = {
            {-0.0548755604, -0.8734370902, -0.4838350155},
            {0.4941094279, -0.4448296300, 0.7469822445},
            {-0.8676661490, -0.1980763734, 0.4559837762}};

    /** Days (with the fraction) the sky has turned through, the hour (12 noon, 24 midnight), latitude in degrees. */
    public double days, clock, latitude;
    /** The sun's ecliptic longitude and right ascension (degrees), and the local sidereal angle (radians). */
    public double lamSun, lst;
    /**
     * Equatorial to world: row 0 gives east (world x), row 1 up (y), row 2 south (z). Each star's direction is
     * this times its equatorial unit vector.
     */
    public final float[] m = new float[9];
    /** The moon's equatorial unit vector. */
    public final double[] moonEq = new double[3];
    /** The sun, the moon and the celestial north pole, as world unit directions. */
    public final float[] sun = new float[3], moon = new float[3], pole = new float[3];
    /** The moon's angle from the sun (elongation, degrees), the sun-moon angle seen from the moon, lit fraction. */
    public float elong, phaseAngle, illum;
    /** Altitudes (degrees). */
    public float sunAlt, moonAlt;
    /** Which night it is, counted from the world's start: it changes at sunrise. */
    public long night;


    /**
     * Turns the sphere to a moment.
     *
     * @param days      days the world has run, with the fraction (the season)
     * @param timeOfDay the game's time of day, 0 at noon, 0.25 at sunset
     * @param moonPhase the game's moon phase, 0 full to 7, and {@code dayFraction} how far through that day it is
     *                  (0 at sunrise), so the moon moves on smoothly from night to night
     */
    public void update(double days, float timeOfDay, int moonPhase, double latitudeDeg, int yearDays) {
        update(days, timeOfDay, moonPhase, latitudeDeg, yearDays, Double.NaN);
    }

    /** As {@link #update}, with the moon held at an angle from the sun (degrees) instead, unless NaN. */
    public void update(double days, float timeOfDay, int moonPhase, double latitudeDeg, int yearDays, double heldElong) {
        this.days = days;
        this.latitude = latitudeDeg;
        double tod = timeOfDay - Math.floor(timeOfDay);
        clock = 12.0 + tod * 24.0;
        int year = Math.max(1, yearDays);
        lamSun = wrap360(LAM0 + 360.0 * (days / year - Math.floor(days / year)));
        double raS = Math.toRadians(lamSun);
        lst = raS + tod * Math.PI * 2.0;
        double cL = Math.cos(lst), sL = Math.sin(lst);
        double phi = Math.toRadians(latitudeDeg), cP = Math.cos(phi), sP = Math.sin(phi);
        // The lab's M: east, up (zenith) and north in equatorial axes; world z is south, so north flips.
        m[0] = (float) -sL;
        m[1] = (float) cL;
        m[2] = 0f;
        m[3] = (float) (cP * cL);
        m[4] = (float) (cP * sL);
        m[5] = (float) sP;
        m[6] = (float) (sP * cL);
        m[7] = (float) (sP * sL);
        m[8] = (float) -cP;
        // The sun stays on the equator (declination 0): the same sunrise and sunset ticks as the game.
        toWorld(Math.cos(raS), Math.sin(raS), 0.0, sun);
        sunAlt = altitude(sun);
        // The moon: its phase from the game's, centred on midnight and moving on through the day.
        double dayFraction = tod + 0.25 - Math.floor(tod + 0.25);
        elong = (float) (Double.isNaN(heldElong) ? wrap360(180.0 + 45.0 * (moonPhase + dayFraction - 0.75)) : wrap360(heldElong));
        night = (long) Math.floor(days - dayFraction + 1e-6);
        ecl(lamSun + elong, elong > 170f && elong < 190f ? 0.0 : 1.5, moonEq);
        toWorld(moonEq[0], moonEq[1], moonEq[2], moon);
        moonAlt = altitude(moon);
        double cosPsi = -(sun[0] * moon[0] + sun[1] * moon[1] + sun[2] * moon[2]);
        phaseAngle = (float) Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, cosPsi))));
        illum = (float) ((1.0 + cosPsi) / 2.0);
        toWorld(0.0, 0.0, 1.0, pole);
    }

    /** An equatorial unit vector to a world direction, into {@code out}. */
    public void toWorld(double x, double y, double z, float[] out) {
        out[0] = (float) (m[0] * x + m[1] * y + m[2] * z);
        out[1] = (float) (m[3] * x + m[4] * y + m[5] * z);
        out[2] = (float) (m[6] * x + m[7] * y + m[8] * z);
    }

    /** Altitude in degrees of a world unit direction. */
    public static float altitude(float[] v) {
        return (float) Math.toDegrees(Math.asin(Math.max(-1f, Math.min(1f, v[1]))));
    }

    /** The equatorial unit vector of right ascension (hours) and declination (degrees). */
    public static double[] eq(double raHours, double decDeg, double[] out) {
        double a = Math.toRadians(raHours * 15.0), d = Math.toRadians(decDeg);
        out[0] = Math.cos(d) * Math.cos(a);
        out[1] = Math.cos(d) * Math.sin(a);
        out[2] = Math.sin(d);
        return out;
    }

    /** The equatorial unit vector of ecliptic longitude and latitude (degrees). */
    public static double[] ecl(double lamDeg, double betDeg, double[] out) {
        double l = Math.toRadians(lamDeg), b = Math.toRadians(betDeg), cb = Math.cos(b);
        double x = Math.cos(l) * cb, y = Math.sin(l) * cb, z = Math.sin(b);
        out[0] = x;
        out[1] = y * Math.cos(EPS) - z * Math.sin(EPS);
        out[2] = y * Math.sin(EPS) + z * Math.cos(EPS);
        return out;
    }

    /** The equatorial unit vector of galactic longitude and latitude (degrees). */
    public static double[] gal(double lDeg, double bDeg, double[] out) {
        double l = Math.toRadians(lDeg), b = Math.toRadians(bDeg);
        double gx = Math.cos(b) * Math.cos(l), gy = Math.cos(b) * Math.sin(l), gz = Math.sin(b);
        out[0] = GT[0][0] * gx + GT[1][0] * gy + GT[2][0] * gz;
        out[1] = GT[0][1] * gx + GT[1][1] * gy + GT[2][1] * gz;
        out[2] = GT[0][2] * gx + GT[1][2] * gy + GT[2][2] * gz;
        return out;
    }

    static double wrap360(double a) {
        a %= 360.0;
        return a < 0 ? a + 360.0 : a;
    }
}
