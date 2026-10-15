package io.github.georgwav.cosmetics.sol.render;

import io.github.georgwav.cosmetics.sol.util.Colors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GradeTest {
    @Test
    void zeroStrengthIsTheIdentity() {
        int[] lut = Grade.lut(0xFFFFD79C, 0xFF3A0E1C, 1.3f, 1.2f, 0f, null);
        for (int c : new int[]{0xFF000000, 0xFFFFFFFF, 0xFF808080, 0xFFF08A3A, 0xFF5E0F06}) {
            int g = Grade.lookup(lut, c);
            assertEquals(Colors.r(c), Colors.r(g), 5, "red of " + Colors.hex(c));
            assertEquals(Colors.g(c), Colors.g(g), 5, "green of " + Colors.hex(c));
            assertEquals(Colors.b(c), Colors.b(g), 5, "blue of " + Colors.hex(c));
        }
    }

    @Test
    void layoutIsSlicesOfRedAcrossGreenDownBlueBySlice() {
        int[] lut = Grade.lut(0xFFFFFFFF, 0xFF000000, 1f, 1f, 0f, null);
        assertEquals(Grade.W * Grade.H, lut.length);
        // Pure red at the end of the first slice's first row, pure green at the bottom of the first slice, blue in
        // the last slice.
        assertEquals(0xFFFF0000, lut[31]);
        assertEquals(0xFF00FF00, lut[31 * Grade.W]);
        assertEquals(0xFF0000FF, lut[31 * 32]);
    }

    @Test
    void warmHighlightsWarmWhiteAndLeaveBlackAlone() {
        int[] lut = Grade.lut(0xFFFFE8BC, 0xFF46201A, 1f, 1f, 1f, null);
        int white = Grade.lookup(lut, 0xFFF0F0F0);
        assertTrue(Colors.r(white) > Colors.b(white) + 10, "highlights take the warm colour: " + Colors.hex(white));
        int black = Grade.lookup(lut, 0xFF000000);
        assertTrue(Colors.r(black) < 60 && Colors.g(black) < 40, "shadows lift a little toward the shadow colour, no more");
        assertTrue(Colors.r(black) >= Colors.b(black), "and toward its warm hue");
    }

    @Test
    void everyChannelStaysOrderedAlongTheGreyRamp() {
        int[] lut = Grade.lut(0xFFFFE8BC, 0xFF46201A, 0.97f, 1.0f, 1f, null);
        int last = -1;
        for (int i = 0; i < 32; i++) {
            int v = Math.round(i / 31f * 255f);
            int g = Grade.lookup(lut, 0xFF000000 | (v << 16) | (v << 8) | v);
            float l = Colors.luma(g);
            assertTrue(l * 255f >= last - 1, "the grade never darkens a brighter grey below a darker one");
            last = Math.round(l * 255f);
        }
    }

    @Test
    void saturationBelowOneMovesColoursTowardGrey() {
        int[] lut = Grade.lut(0xFFFFFFFF, 0xFF000000, 0.5f, 1f, 1f, null);
        int c = Grade.lookup(lut, 0xFFE07020);
        assertTrue(Colors.r(c) - Colors.b(c) < 0xE0 - 0x20, "less spread between channels");
    }
}
