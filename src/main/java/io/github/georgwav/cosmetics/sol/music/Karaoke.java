package io.github.georgwav.cosmetics.sol.music;

import java.util.List;

/**
 * Where the song is in its lyrics: the line sung at a position (the last one started, background lines aside) and
 * the word within it, found by binary search; a per-song offset nudges the timing. Pure; unit tested.
 */
public final class Karaoke {
    private Karaoke() {
    }

    /** The main line at {@code positionMs} (with {@code offsetMs} added), or -1 before the first. */
    public static int line(Lyrics lyrics, long positionMs, long offsetMs) {
        if (!lyrics.synced()) {
            return -1;
        }
        List<Lyrics.Line> lines = lyrics.lines();
        long t = positionMs + offsetMs;
        int lo = 0, hi = lines.size() - 1, found = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (lines.get(mid).startMs() <= t) {
                found = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        while (found >= 0 && lines.get(found).background()) {
            found--;
        }
        return found;
    }

    /** The word sung in line {@code index} at the position, or -1 (no word timing, or before its first word). */
    public static int word(Lyrics lyrics, int index, long positionMs, long offsetMs) {
        if (index < 0 || index >= lyrics.lines().size()) {
            return -1;
        }
        List<Lyrics.Word> words = lyrics.lines().get(index).words();
        long t = positionMs + offsetMs;
        int found = -1;
        for (int i = 0; i < words.size(); i++) {
            if (words.get(i).startMs() <= t) {
                found = i;
            } else {
                break;
            }
        }
        return found;
    }

    /**
     * How much of line {@code index} has been sung, 0..1 by its letters (the words before the one being sung, and that
     * one as far as it's got), for a sweep across the whole line; -1 when the lyrics don't time its words.
     */
    public static float sung(Lyrics lyrics, int index, long positionMs, long offsetMs) {
        if (index < 0 || index >= lyrics.lines().size() || lyrics.lines().get(index).words().isEmpty()) {
            return -1f;
        }
        List<Lyrics.Word> words = lyrics.lines().get(index).words();
        int w = word(lyrics, index, positionMs, offsetMs);
        int total = 0, before = 0;
        for (int i = 0; i < words.size(); i++) {
            int n = words.get(i).text().length();
            total += n;
            if (i < w) {
                before += n;
            }
        }
        if (total == 0) {
            return -1f;
        }
        float in = w < 0 ? 0f : words.get(w).text().length() * wordProgress(lyrics, index, w, positionMs, offsetMs);
        return Math.min(1f, (before + in) / total);
    }

    /** How far through word {@code w} of line {@code index} the song is (0..1), for the lit sweep. */
    public static float wordProgress(Lyrics lyrics, int index, int w, long positionMs, long offsetMs) {
        if (index < 0 || w < 0) {
            return 0f;
        }
        Lyrics.Word word = lyrics.lines().get(index).words().get(w);
        long len = Math.max(1, word.endMs() - word.startMs());
        return Math.max(0f, Math.min(1f, (positionMs + offsetMs - word.startMs()) / (float) len));
    }
}
