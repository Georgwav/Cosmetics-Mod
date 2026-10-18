package io.github.georgwav.cosmetics.sol.sky;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkySphereTest {
    private static final double[] V = new double[3];

    /** Altitude (degrees) of a star at RA (hours), Dec, on the sphere's current turn. */
    private static float alt(SkySphere s, double ra, double dec) {
        SkySphere.eq(ra, dec, V);
        float[] w = new float[3];
        s.toWorld(V[0], V[1], V[2], w);
        return SkySphere.altitude(w);
    }

    private static float todOf(double clock) {
        return (float) ((clock - 12.0) / 24.0);
    }

    @Test
    void sunKeepsTheGamesSunriseAndSunset() {
        SkySphere s = new SkySphere();
        s.update(10.3, 0f, 0, 34, 96);
        assertEquals(56f, s.sunAlt, 0.01f, "noon: 90° less the latitude");
        assertTrue(s.sun[2] > 0.5f, "due south at noon");
        assertEquals(0f, s.sun[0], 1e-4f);
        s.update(10.3, 0.25f, 0, 34, 96);
        assertEquals(0f, s.sunAlt, 0.01f, "on the horizon at the game's sunset");
        assertEquals(-1f, s.sun[0], 1e-4f, "due west");
        s.update(10.3, -0.25f, 0, 34, 96);
        assertEquals(0f, s.sunAlt, 0.01f, "on the horizon at the game's sunrise");
        assertEquals(1f, s.sun[0], 1e-4f, "due east");
        s.update(10.3, 0.5f, 0, 34, 96);
        assertEquals(-56f, s.sunAlt, 0.01f);
    }

    @Test
    void latitudeZeroIsTheGamesPath() {
        SkySphere s = new SkySphere();
        for (float tod = -0.5f; tod <= 0.5f; tod += 0.05f) {
            s.update(3, tod, 0, 0, 96);
            assertEquals(-(float) Math.sin(tod * Math.PI * 2), s.sun[0], 1e-4f);
            assertEquals((float) Math.cos(tod * Math.PI * 2), s.sun[1], 1e-4f);
            assertEquals(0f, s.sun[2], 1e-4f);
        }
    }

    @Test
    void poleStandsAtTheLatitudeInTheNorth() {
        SkySphere s = new SkySphere();
        for (float tod : new float[]{0f, 0.2f, 0.45f}) {
            s.update(40, tod, 3, 34, 96);
            assertEquals(34f, SkySphere.altitude(s.pole), 0.01f);
            assertEquals(0f, s.pole[0], 1e-4f);
            assertTrue(s.pole[2] < 0f, "north is −z");
            assertEquals(34f, alt(s, 2.53, 89.26), 0.8f, "Polaris stays put");
        }
    }

    @Test
    void theSeasonsTurnTheNightSky() {
        SkySphere s = new SkySphere();
        // Winter (the sun at 270°, day 24 of 96), 21:00: Orion is up in the east.
        s.update(24, todOf(21), 0, 34, 96);
        assertEquals(270.0, s.lamSun, 1e-6);
        assertTrue(alt(s, 5.919, 7.41) > 30f, "Betelgeuse up on a winter evening");
        // Summer (day 72), the same hour: Orion is gone and Scorpius is up in the south.
        s.update(72, todOf(21), 0, 34, 96);
        assertTrue(alt(s, 5.919, 7.41) < 0f, "Betelgeuse below on a summer evening");
        assertTrue(alt(s, 16.49, -26.43) > 15f, "Antares up on a summer evening");
        // A year later it's all the same.
        s.update(72 + 96, todOf(21), 0, 34, 96);
        assertTrue(alt(s, 16.49, -26.43) > 15f);
    }

    @Test
    void theMoonFollowsTheGamesPhase() {
        SkySphere s = new SkySphere();
        // Full moon (phase 0) at midnight: opposite the sun, fully lit, high in the south.
        s.update(5, 0.5f, 0, 34, 96);
        assertEquals(180f, s.elong, 0.01f);
        assertEquals(1f, s.illum, 0.01f);
        assertTrue(s.moonAlt > 40f);
        // New moon (phase 4) at midnight: with the sun, below the horizon, dark.
        s.update(5, 0.5f, 4, 34, 96);
        assertEquals(0f, s.elong, 0.01f);
        assertTrue(s.illum < 0.01f);
        assertTrue(s.moonAlt < -40f);
        // First quarter (phase 6) at sunset: high in the south.
        s.update(5, 0.25f, 6, 34, 96);
        assertEquals(78.75f, s.elong, 0.01f);
        assertTrue(s.moonAlt > 30f);
    }

    @Test
    void theMoonMovesOnSmoothlyAcrossTheGamesDayBoundary() {
        SkySphere s = new SkySphere();
        // Just before sunrise on phase 2, and just after it on phase 3: the same place.
        s.update(5, -0.2501f, 2, 34, 96);
        float e0 = s.elong;
        s.update(5, -0.2499f, 3, 34, 96);
        assertEquals(e0, s.elong, 0.05f);
        // And round from phase 7 to 0.
        s.update(5, -0.2501f, 7, 34, 96);
        e0 = s.elong;
        s.update(5, -0.2499f, 0, 34, 96);
        assertEquals(e0, s.elong, 0.05f);
    }

    @Test
    void galacticCentreIsInSagittarius() {
        SkySphere.gal(0, 0, V);
        double ra = Math.toDegrees(Math.atan2(V[1], V[0])) / 15.0, dec = Math.toDegrees(Math.asin(V[2]));
        assertEquals(17.76, (ra + 24) % 24, 0.05);
        assertEquals(-28.94, dec, 0.1);
    }
}
