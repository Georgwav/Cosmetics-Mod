package io.github.georgwav.cosmetics.sol.sky;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class SkyFontTest {
    private static SkyFont read() throws IOException {
        try (InputStream in = SkyFontTest.class.getResourceAsStream("/assets/cosmeticsmod/sky/sky_text.dat")) {
            assertNotNull(in);
            return SkyFont.read(in);
        }
    }

    @Test
    void everySizeTheNamesAreWrittenAt() throws IOException {
        SkyFont f = read();
        for (int family : new int[]{SkyFont.MONO, SkyFont.SANS}) {
            for (int px = 6; px <= 28; px++) {
                SkyFont.Face face = f.face(family, px);
                assertEquals(px, face.size);
                SkyFont.Glyph a = face.glyph('A');
                assertTrue(a.w() > 0 && a.h() > 0 && a.advance() > 0);
                assertTrue(a.dy() < 0, "capitals stand on the baseline");
            }
        }
        assertEquals(28, f.face(SkyFont.MONO, 60f).size, "the nearest there is");
    }

    @Test
    void theNamesLetters() throws IOException {
        SkyFont.Face mono = read().face(SkyFont.MONO, 8), sans = read().face(SkyFont.SANS, 9);
        for (char c : "BOÖTES CANES VENATICI".toCharArray()) {
            assertNotNull(mono.glyph(c));
        }
        assertTrue(mono.glyph('Ö').h() > mono.glyph('O').h(), "the dots");
        assertTrue(sans.glyph('ö').w() > 0);
        assertTrue(mono.width("ORION", 1) > mono.width("ORION", 0));
        // A monospaced face: every capital the same advance.
        assertEquals(mono.glyph('I').advance(), mono.glyph('W').advance());
    }
}
