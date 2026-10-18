package io.github.georgwav.cosmetics.sol.sky;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MilkyWayBakeTest {
    private static final double[] V = new double[3];

    /** Brightness (alpha times the brightest channel) averaged over a small patch round an equatorial direction. */
    private static float patch(int[] px, double[] dir) {
        int[] at = new int[3];
        MilkyWayBake.toFace(dir[0], dir[1], dir[2], at);
        int ox = (at[0] % 3) * MilkyWayBake.FACE, oy = (at[0] / 3) * MilkyWayBake.FACE;
        float sum = 0f;
        int n = 0;
        for (int dy = -6; dy <= 6; dy++) {
            for (int dx = -6; dx <= 6; dx++) {
                int x = Math.max(0, Math.min(MilkyWayBake.FACE - 1, at[1] + dx)), y = Math.max(0, Math.min(MilkyWayBake.FACE - 1, at[2] + dy));
                sum += (px[(oy + y) * MilkyWayBake.W + ox + x] >>> 24) / 255f;
                n++;
            }
        }
        return sum / n;
    }

    @Test
    void theBandIsBrightestAtTheCoreAndDarkAtThePoles() {
        int[] px = MilkyWayBake.bake(new MilkyWayBake.Parts(true, false));
        float core = patch(px, SkySphere.gal(0, -3, V).clone());
        float cygnus = patch(px, SkySphere.gal(78, 3, V).clone());
        float pole = patch(px, SkySphere.gal(0, 85, V).clone());
        float anti = patch(px, SkySphere.gal(180, 0, V).clone());
        assertTrue(core > 0.12f, "a bright core: " + core);
        assertTrue(cygnus > anti, "the Cygnus star cloud");
        assertTrue(core > anti * 2f, "fainter away from the core");
        assertTrue(pole < 0.01f, "nothing at the galactic pole: " + pole);
    }

    @Test
    void theNebulaeAtTheirRealPlaces() {
        int[] px = MilkyWayBake.bake(new MilkyWayBake.Parts(false, true));
        float orion = patch(px, SkySphere.eq(5.588, -5.39, V).clone());
        float andromeda = patch(px, SkySphere.eq(0.712, 41.27, V).clone());
        float empty = patch(px, SkySphere.eq(12.0, 60.0, V).clone());
        assertTrue(orion > 0.1f, "the Orion Nebula: " + orion);
        assertTrue(andromeda > 0.05f, "the Andromeda Galaxy: " + andromeda);
        assertEquals(0f, empty, 1e-6f, "nothing where there's none");
    }

    @Test
    void cubeFacesRoundTrip() {
        int[] at = new int[3];
        float[] d = new float[3];
        for (int f = 0; f < 6; f++) {
            for (int i = 0; i < MilkyWayBake.FACE; i += 37) {
                for (int j = 0; j < MilkyWayBake.FACE; j += 41) {
                    MilkyWayBake.texelDir(f, i, j, d);
                    MilkyWayBake.toFace(d[0], d[1], d[2], at);
                    assertArrayEquals(new int[]{f, i, j}, at);
                }
            }
        }
    }

    @Test
    void theLabsSoftGlow() {
        assertEquals(1f, MilkyWayBake.soft(0), 1e-6f);
        assertEquals(0.45f, MilkyWayBake.soft(0.45), 1e-6f);
        assertEquals(0f, MilkyWayBake.soft(1), 1e-6f);
    }
}
