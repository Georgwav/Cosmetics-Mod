package io.github.georgwav.cosmetics.sol.sky;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BodyRenderTest {
    private static int[] render(String id, double phaseDeg, int n) {
        Bodies.Body b = Bodies.find(id);
        assertNotNull(b, id);
        BodyRender.Light l = new BodyRender.Light();
        double ps = Math.toRadians(phaseDeg);
        // The sun to the right on the screen.
        l.lx = Math.sin(ps);
        l.ly = 0;
        l.lz = Math.cos(ps);
        l.tilt = Math.toRadians(b.tilt());
        int s = BodyRender.size(b, n);
        int[] px = new int[s * s];
        BodyRender.render(b, BodyMaps.make(id), n, l, px);
        return px;
    }

    private static float bright(int c) {
        return ((c >> 16 & 255) + (c >> 8 & 255) + (c & 255)) / 765f * (c >>> 24) / 255f;
    }

    @Test
    void aFullMoonIsARoundLitDisc() {
        int n = 40;
        int[] px = render("moon", 0, n);
        assertEquals(0, px[0] >>> 24, "the corner is empty");
        assertEquals(255, px[(n / 2) * n + n / 2] >>> 24, "the middle is the moon");
        assertTrue(bright(px[(n / 2) * n + n / 2]) > 0.3f, "lit");
    }

    @Test
    void theLitSideFacesTheSun() {
        int n = 40;
        int[] px = render("moon", 90, n);
        float right = 0, left = 0;
        for (int j = n / 4; j < 3 * n / 4; j++) {
            right += bright(px[j * n + 3 * n / 4]);
            left += bright(px[j * n + n / 4]);
        }
        assertTrue(right > left * 4, "a half moon lit on the sun's side: " + right + " vs " + left);
    }

    @Test
    void theMoonsSeasAreDarkerThanItsHighlands() {
        float[] map = BodyMaps.make("moon");
        // Mare Imbrium (lon −16°, lat 33°) against the southern highlands (lon 0°, lat −60°).
        int seaI = (int) ((-16 + 180) / 360.0 * BodyMaps.W), seaJ = (int) ((90 - 33) / 180.0 * BodyMaps.H);
        int hiI = (int) ((10 + 180) / 360.0 * BodyMaps.W), hiJ = (int) ((90 + 60) / 180.0 * BodyMaps.H);
        float sea = map[(seaJ * BodyMaps.W + seaI) * 4], high = map[(hiJ * BodyMaps.W + hiI) * 4];
        assertTrue(sea < high * 0.85f, "sea " + sea + " highland " + high);
    }

    @Test
    void saturnsRingsReachPastTheBall() {
        int n = 30;
        Bodies.Body saturn = Bodies.find("saturn");
        int s = BodyRender.size(saturn, n);
        int[] px = render("saturn", 30, n);
        int ringPixels = 0;
        for (int j = 0; j < s; j++) {
            for (int i = 0; i < s; i++) {
                double x = (i + 0.5 - s / 2.0) / (n / 2.0), y = (j + 0.5 - s / 2.0) / (n / 2.0);
                if (x * x + y * y > 1.3 && (px[j * s + i] >>> 24) > 40) {
                    ringPixels++;
                }
            }
        }
        assertTrue(ringPixels > 20, "rings outside the ball: " + ringPixels);
    }

    @Test
    void gargantuaHasABlackShadowAndABrightDisk() {
        int n = 48;
        int[] px = render("gargantua", 0, n);
        int mid = px[(n / 2) * n + n / 2];
        assertEquals(255, mid >>> 24);
        assertEquals(0, mid & 0xFFFFFF, "the shadow is black");
        float best = 0;
        for (int c : px) {
            best = Math.max(best, bright(c));
        }
        assertTrue(best > 0.6f, "the disk glows");
    }

    @Test
    void haloIsARibbonNotABall() {
        int n = 60;
        int[] px = render("halo", 30, n);
        assertEquals(0, px[(n / 2) * n + n / 2] >>> 24, "you see through the middle");
        int filled = 0;
        for (int c : px) {
            filled += (c >>> 24) > 0 ? 1 : 0;
        }
        assertTrue(filled > 50 && filled < n * n / 2, "a thin ring: " + filled);
    }

    @Test
    void everyPlanetIsUpEveryNightAtTheSameHours() {
        SkySphere s = new SkySphere();
        double[] v = new double[3];
        float[] w = new float[3];
        for (double days : new double[]{0, 13.5, 40, 77}) {
            // Midnight: Jupiter, Mars and Saturn sit a fixed way from the sun, so the same ones are up.
            s.update(days, 0.5f, 0, 34, 96);
            for (String id : new String[]{"mars", "jupiter"}) {
                Bodies.place(Bodies.find(id), s.lamSun, v);
                s.toWorld(v[0], v[1], v[2], w);
                assertTrue(SkySphere.altitude(w) > 10f, id + " up at midnight on day " + days);
            }
            // Venus is the evening star: up in the west just after sunset.
            s.update(days, 0.28f, 0, 34, 96);
            Bodies.place(Bodies.find("venus"), s.lamSun, v);
            s.toWorld(v[0], v[1], v[2], w);
            assertTrue(SkySphere.altitude(w) > 5f && w[0] < 0f, "Venus low in the west after sunset");
        }
    }
}
