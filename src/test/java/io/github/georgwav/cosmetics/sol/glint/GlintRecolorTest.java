package io.github.georgwav.cosmetics.sol.glint;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GlintRecolorTest {
    // A tiny glint: transparent, dim purple, the brightest purple.
    private static final int[] SRC = {0x00000000, 0x80402060, 0xFF8040C0, 0xFF8040C0};

    @Test
    void solidKeepsBrightnessAndAlpha() {
        int[] out = new int[4];
        GlintRecolor.apply(SRC, out, 4, GlintRecolor.Mode.SOLID, 0xFF00FF00, 0, 1f, 0f);
        assertEquals(0x00000000, out[0], "transparent stays transparent");
        assertEquals(0xFF00FF00, out[2], "the brightest pixel takes the full colour");
        assertEquals(0x80, out[1] >>> 24, "alpha kept");
        int g = (out[1] >> 8) & 255;
        assertTrue(g > 100 && g < 140, "half as bright as the brightest: " + g);
        assertEquals(0, (out[1] >> 16) & 255);
    }

    @Test
    void strengthBrightensUpToFull() {
        int[] out = new int[4];
        GlintRecolor.apply(SRC, out, 4, GlintRecolor.Mode.SOLID, 0xFFFFFFFF, 0, 2f, 0f);
        assertEquals(0xFFFFFFFF, out[2] | 0xFF000000);
        assertEquals(255, (out[1] >> 16) & 255, "twice the strength fills the half-bright pixel");
    }

    @Test
    void twoColoursAndRainbowMoveWithThePhase() {
        int[] a = new int[4], b = new int[4];
        GlintRecolor.apply(SRC, a, 4, GlintRecolor.Mode.TWO, 0xFFFF0000, 0xFF0000FF, 1f, 0f);
        GlintRecolor.apply(SRC, b, 4, GlintRecolor.Mode.TWO, 0xFFFF0000, 0xFF0000FF, 1f, 0.5f);
        assertNotEquals(a[2], b[2]);
        GlintRecolor.apply(SRC, a, 4, GlintRecolor.Mode.RAINBOW, 0, 0, 1f, 0f);
        GlintRecolor.apply(SRC, b, 4, GlintRecolor.Mode.RAINBOW, 0, 0, 1f, 0.33f);
        assertNotEquals(a[3], b[3]);
        assertEquals(0xFFFF0000, GlintRecolor.hsv(0f, 1f, 1f));
        assertEquals(0xFF00FF00, GlintRecolor.hsv(1f / 3f, 1f, 1f));
    }
}
