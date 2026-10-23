package io.github.georgwav.cosmetics.sol.atmosphere;

import io.github.georgwav.cosmetics.sol.sky.SkyFont;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SkyLyricsTest {
    private static SkyFont.Face face(int px) throws IOException {
        try (InputStream in = SkyLyricsTest.class.getResourceAsStream("/assets/cosmeticsmod/sky/lyrics_text.dat")) {
            assertNotNull(in);
            return SkyFont.read(in).face(SkyFont.LYRICS, px);
        }
    }

    @Test
    void theLetteringHasWhatLyricsUse() throws IOException {
        SkyFont.Face f = face(22);
        assertEquals(22, f.size);
        for (char c : "Don't you (ever) say \"no\"? ¿Qué? Ça va! Über, naïve; 100% & more…".toCharArray()) {
            assertTrue(f.has(c), "has " + c);
        }
        assertTrue(f.glyph(',').w() > 0);
        assertTrue(f.glyph('g').dy() + f.glyph('g').h() > 0, "descenders go below the baseline");
    }

    @Test
    void foldKeepsWhatItCanWrite() throws IOException {
        SkyFont.Face f = face(22);
        assertEquals("I’m “here” — now…", SkyLyrics.fold("I’m “here” — now…", f),
                "curly quotes, dashes and the ellipsis it has");
        assertEquals("\"so“ - 2", SkyLyrics.fold("„so“ − 2", f), "the ones it hasn't got as plain ones");
        assertEquals("Ça va, naïve", SkyLyrics.fold("  Ça   va,\tnaïve ", f), "accents it has stay; spaces collapse");
        assertEquals("Ama", SkyLyrics.fold("Ąmą", f), "accents it hasn't got come off");
        assertEquals("hi", SkyLyrics.fold("hi 🎵", f), "what it can't write is left out");
    }

    @Test
    void wrapsIntoTwoRowsAtMost() throws IOException {
        SkyFont.Face f = face(22);
        assertEquals(List.of("Short line"), SkyLyrics.wrap("Short line", f, 1000f));
        String longLine = "and I would walk five hundred miles and I would walk five hundred more just to be the man";
        List<String> rows = SkyLyrics.wrap(longLine, f, 300f);
        assertEquals(2, rows.size());
        for (String r : rows) {
            assertTrue(f.width(r, 0) <= 300f, r);
        }
        assertTrue(rows.get(1).endsWith("…"), "what didn't fit is cut: " + rows.get(1));
        assertTrue(longLine.startsWith(rows.get(0)), "the first row is the line's start");
        List<String> fits = SkyLyrics.wrap("one two three four", f, f.width("three four", 0) + 1f);
        assertEquals(List.of("one two", "three four"), fits, "two rows that fit aren't cut");
        List<String> wide = SkyLyrics.wrap("Supercalifragilisticexpialidocious", f, 120f);
        assertEquals(1, wide.size());
        assertTrue(f.width(wide.get(0), 0) <= 120f && wide.get(0).endsWith("…"), "a word wider than a row is cut");
    }
}
