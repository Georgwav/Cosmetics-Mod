package io.github.georgwav.cosmetics.sol.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NebulaBakeTest {
    private static int[] bake() {
        return NebulaBake.bake(96, 48, -0.4f, 1.4f, -0.3f, 0.7f, 16f / 9f, new float[]{0.28f, 0.72f}, new float[]{0.22f, 0.32f},
                new float[]{0.36f, 0.3f}, new int[]{0xFF7B2CFF, 0xFFFF4FA3}, 1729);
    }

    @Test
    void sameCloudEveryTime() {
        assertArrayEquals(bake(), bake());
    }

    @Test
    void glowsRoundItsCloudsInTheirColours() {
        int[] px = bake();
        // Near the violet cloud's centre: bright, and more blue than green.
        int x = Math.round((0.28f + 0.4f) / 1.8f * 96), y = Math.round((0.22f + 0.3f) / 1.0f * 48);
        int c = px[y * 96 + x];
        assertTrue((c >>> 24) > 40, "the cloud glows");
        assertTrue(Colors.b(c) > Colors.g(c), "violet");
    }

    @Test
    void noBorderAtTheEdges() {
        int[] px = bake();
        for (int i = 0; i < 96; i++) {
            assertEquals(0, px[i] >>> 24 & 0xF0, "top row fades out");
            assertEquals(0, px[47 * 96 + i] >>> 24 & 0xF0, "bottom row fades out");
        }
        for (int j = 0; j < 48; j++) {
            assertEquals(0, px[j * 96] >>> 24 & 0xF0, "left column fades out");
            assertEquals(0, px[j * 96 + 95] >>> 24 & 0xF0, "right column fades out");
        }
    }

    @Test
    void noiseStaysInRange() {
        for (int i = 0; i < 2000; i++) {
            float x = i * 0.37f - 300f, y = i * 0.11f + 40f;
            float n = NebulaBake.fbm(x, y, 5);
            assertTrue(n >= 0f && n <= 1f);
        }
    }
}
