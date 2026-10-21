package io.github.georgwav.cosmetics.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraGlideTest {
    private static double len(double[] o) {
        return Math.sqrt(o[0] * o[0] + o[1] * o[1] + o[2] * o[2]);
    }

    @Test
    void outStartsBehindYourHeadAndEndsAtTheGamesPlace() {
        double[] o = new double[3];
        assertTrue(CameraGlide.out(0, 0, 0, -4, o));
        assertEquals(CameraGlide.NEAR, len(o), 1e-9);
        assertTrue(CameraGlide.out(1, 0, 0, -4, o));
        assertEquals(-4, o[2], 1e-9);
        // Slowing into place: more than half way at half time, and always outward.
        double last = 0;
        for (int i = 0; i <= 10; i++) {
            CameraGlide.out(i / 10.0, 0, 0, -4, o);
            assertTrue(len(o) >= last);
            last = len(o);
        }
        CameraGlide.out(0.5, 0, 0, -4, o);
        assertTrue(len(o) > CameraGlide.NEAR + (4 - CameraGlide.NEAR) / 2);
        // A wall right behind you: no glide.
        assertFalse(CameraGlide.out(0.5, 0, 0, -0.3, o));
    }

    @Test
    void inSpeedsFromWhereItWasToBehindYourHead() {
        double[] o = new double[3];
        assertTrue(CameraGlide.in(0, 3, 0, 0, o));
        assertEquals(3, o[0], 1e-9);
        CameraGlide.in(1, 3, 0, 0, o);
        assertEquals(CameraGlide.NEAR, o[0], 1e-9);
        // Speeding in: less than half way at half time.
        CameraGlide.in(0.5, 3, 0, 0, o);
        assertTrue(o[0] > 3 - (3 - CameraGlide.NEAR) / 2);
        assertFalse(CameraGlide.in(0.5, 0.2, 0, 0, o));
    }

    @Test
    void homeEasesFromTheFreecamToTheGamesPlace() {
        double[] o = new double[3];
        CameraGlide.home(0, 10, 5, 0, 0, 0, -4, false, o);
        assertEquals(10, o[0], 1e-9);
        assertEquals(5, o[1], 1e-9);
        CameraGlide.home(1, 10, 5, 0, 0, 0, -4, false, o);
        assertEquals(0, o[0], 1e-9);
        assertEquals(-4, o[2], 1e-9);
        // Into first person it ends just behind your head, on the freecam's side.
        CameraGlide.home(1, 10, 0, 0, 0, 0, 0, true, o);
        assertEquals(CameraGlide.NEAR, o[0], 1e-9);
        CameraGlide.home(0.5, 10, 0, 0, 0, 0, 0, true, o);
        assertEquals((10 + CameraGlide.NEAR) / 2, o[0], 1e-9);
    }
}
