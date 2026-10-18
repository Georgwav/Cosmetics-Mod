package io.github.georgwav.cosmetics.ui;

import org.junit.jupiter.api.Test;

import java.util.BitSet;

import static org.junit.jupiter.api.Assertions.*;

class GeistGlyphsTest {
    @Test
    void readsEachGeistFontsCharacters() {
        for (String ttf : new String[]{"geist-medium.ttf", "geist-semibold.ttf", "geist-mono-medium.ttf"}) {
            BitSet has = GeistGlyphs.coverage(ttf);
            for (char c = 0x20; c < 0x7F; c++) {
                assertTrue(has.get(c), ttf + " has '" + c + "'");
            }
            assertTrue(has.get('é') && has.get('ß'), ttf + " has accented Latin");
            assertFalse(has.get('中'), ttf + " has no Chinese: the game's font draws it");
            assertFalse(has.get(0x1F600), ttf + " has no emoji");
            assertTrue(has.cardinality() > 300, ttf + ": " + has.cardinality() + " characters");
        }
    }
}
