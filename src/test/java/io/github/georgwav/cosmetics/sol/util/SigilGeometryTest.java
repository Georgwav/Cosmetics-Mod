package io.github.georgwav.cosmetics.sol.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Appendix B: the Grace rune's geometry. */
class SigilGeometryTest {
    static final double S3 = Math.sqrt(3);

    @Test
    void upperAndLowerCirclesPassThroughEachOthersCentres() {
        double[][] c = Sprites.SIGIL_CIRCLES;
        int[] trio = {0, 2, 3};
        for (int i : trio) {
            for (int j : trio) {
                if (i != j) {
                    double d = Math.hypot(c[i][0] - c[j][0], c[i][1] - c[j][1]);
                    assertEquals(c[i][2], d, 1e-9, "circle " + i + " passes through centre " + j);
                }
            }
        }
    }

    @Test
    void middleCircleTopIsAtMinusHalf() {
        double[] m = Sprites.SIGIL_CIRCLES[1];
        assertEquals(1 / S3, m[1], 1e-12);
        assertEquals(-0.5, m[1] - m[2], 1e-9);
    }

    @Test
    void lowerPairCrossesTheSpineAtZeroAndRootThree() {
        double[] l = Sprites.SIGIL_CIRCLES[2];
        // x = 0 on the circle centred (-.5, √3/2), r 1: (0.5)^2 + (y - √3/2)^2 = 1.
        double dy = Math.sqrt(1 - 0.25);
        assertEquals(0, l[1] - dy, 1e-9);
        assertEquals(S3, l[1] + dy, 1e-9);
    }

    @Test
    void bowlFadesAndCuts() {
        assertEquals(1.0, Sprites.bowlWeight(0), 1e-12);
        assertEquals(Math.exp(-Math.pow(0.5 / 0.8, 2.2)), Sprites.bowlWeight(0.5), 1e-12);
        assertEquals(0, Sprites.bowlWeight(1.3), "cut below 0.07 near the tips");
        assertEquals(-4.47, Sprites.BOWL_CY, 1e-12);
        assertEquals(2.75, Sprites.BOWL_R, 1e-12);
    }

    @Test
    void rootThreeNodeIsLargest() {
        double[][] n = Sprites.SIGIL_SPINE_NODES;
        double best = -1;
        double bestY = 0;
        for (double[] node : n) {
            if (node[1] > best) {
                best = node[1];
                bestY = node[0];
            }
        }
        assertEquals(S3, bestY, 1e-12);
        assertEquals(6, n.length);
    }

    @Test
    void spriteBakesDeterministically() {
        Sprites.Sigil a = Sprites.sigil(0xFFFFD27A, 0xFFE47A26);
        assertEquals(30, a.unit());
        assertEquals(62, a.cx());
        assertEquals(89, a.cy());
        assertEquals(124, a.width());
        assertEquals(175, a.height());
        assertTrue(a.count() > 500, "line pixels");
        assertEquals(3, a.crackle().length);
    }
}
