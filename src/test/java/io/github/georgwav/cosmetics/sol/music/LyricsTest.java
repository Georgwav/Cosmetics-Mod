package io.github.georgwav.cosmetics.sol.music;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LyricsTest {
    @Test
    void lrcTagsOffsetsAndJunk() {
        Lyrics l = LrcParser.parse("""
                [ar:Someone]
                [ti:Song]
                [offset:+500]
                [00:12.30]First line
                [00:05.00][00:20.5]Chorus
                not a line
                [01:02.345]Last

                """, "lrclib");
        assertTrue(l.synced());
        assertEquals(4, l.lines().size());
        assertEquals("Chorus", l.lines().get(0).text());
        assertEquals(4500, l.lines().get(0).startMs(), "offset +500 makes it earlier");
        assertEquals(11800, l.lines().get(1).startMs());
        assertEquals(20000, l.lines().get(2).startMs(), "[00:20.5] is 20.5 s");
        assertEquals(61845, l.lines().get(3).startMs());
        assertEquals(l.lines().get(1).startMs(), l.lines().get(0).endMs(), "a line ends where the next starts");
        assertFalse(LrcParser.parse("no tags at all", "x").synced());
    }

    static final String TTML = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata"><body><div>
            <p begin="10.0" end="12.5" ttm:agent="v1"><span begin="10.0" end="10.6">Hel</span><span begin="10.6" end="11.0">lo</span> <span begin="11.2" end="12.5">world</span><span ttm:role="x-bg"><span begin="11.5" end="12.4">(ooh)</span></span></p>
            <p begin="1:02.000" end="1:05.000" ttm:agent="v2">Second &amp; line</p>
            </div></body></tt>""";

    @Test
    void ttmlWordsAgentsAndBackground() {
        Lyrics l = TtmlParser.parse(TTML, "apple");
        assertTrue(l.synced());
        assertEquals(3, l.lines().size());
        Lyrics.Line first = l.lines().get(0);
        assertEquals("Hello world", first.text());
        assertEquals("v1", first.agent());
        assertEquals(3, first.words().size());
        assertEquals("lo ", first.words().get(1).text(), "the space after a word stays with it");
        Lyrics.Line bg = l.lines().get(1);
        assertTrue(bg.background());
        assertEquals("(ooh)", bg.text());
        Lyrics.Line second = l.lines().get(2);
        assertEquals("Second & line", second.text());
        assertEquals(62000, second.startMs());
        assertEquals("v2", second.agent());
        assertTrue(second.words().isEmpty(), "line-level timing");
        assertSame(Lyrics.NONE, TtmlParser.parse("<broken", "x"));
        assertEquals(3_723_456, TtmlParser.time("1:02:03.456"));
    }

    @Test
    void karaokeFollowsTheSong() {
        Lyrics l = TtmlParser.parse(TTML, "apple");
        assertEquals(-1, Karaoke.line(l, 5_000, 0));
        assertEquals(0, Karaoke.line(l, 11_600, 0), "the background line doesn't take the main line's place");
        assertEquals(2, Karaoke.line(l, 70_000, 0));
        assertEquals(0, Karaoke.line(l, 61_000, 0));
        assertEquals(2, Karaoke.line(l, 61_000, 1_500), "the per-song offset nudges it");
        assertEquals(1, Karaoke.word(l, 0, 10_700, 0));
        assertEquals(-1, Karaoke.word(l, 0, 9_000, 0));
        assertEquals(0.5f, Karaoke.wordProgress(l, 0, 0, 10_300, 0), 0.01f);
        assertEquals(-1, Karaoke.line(LrcParser.plain("a\nb", "x"), 1000, 0), "plain lyrics don't follow");
    }
}
