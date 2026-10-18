package io.github.georgwav.cosmetics.sol.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReleaseNotesTest {
    @Test
    void markdownBecomesPlainLines() {
        List<ReleaseNotes.Line> l = ReleaseNotes.lines("## What's new\r\n\n- **Streamer mode** hides your name\n* See [the PR](https://x/1)\n"
                + "1) Faster `sky`\n\n\n---\nThanks!\n\n");
        assertEquals(new ReleaseNotes.Line("What's new", true), l.get(0));
        assertEquals("", l.get(1).text());
        assertEquals("\u2022 Streamer mode hides your name", l.get(2).text());
        assertEquals("\u2022 See the PR", l.get(3).text());
        assertEquals("1. Faster sky", l.get(4).text());
        // Blank lines and the rule fold into one gap; none at the end.
        assertEquals("", l.get(5).text());
        assertEquals("Thanks!", l.get(6).text());
        assertEquals(7, l.size());
        assertTrue(ReleaseNotes.lines(null).isEmpty());
    }
}
