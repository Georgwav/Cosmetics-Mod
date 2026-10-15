package io.github.georgwav.cosmetics.sol.music;

import java.util.List;

/**
 * A song's lyrics, timed by line (and by word when the source has it: Apple's syllable lyrics). Immutable.
 *
 * @param synced whether the lines have times (plain lyrics only show, they don't follow the song)
 */
public record Lyrics(List<Line> lines, boolean synced, String source) {
    public static final Lyrics NONE = new Lyrics(List.of(), false, "none");

    /**
     * @param agent      who sings it (Apple's {@code ttm:agent}: v1, v2 ... for duets), or null
     * @param background background vocals ({@code ttm:role="x-bg"})
     */
    public record Line(long startMs, long endMs, String text, List<Word> words, String agent, boolean background) {
    }

    public record Word(long startMs, long endMs, String text) {
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }
}
