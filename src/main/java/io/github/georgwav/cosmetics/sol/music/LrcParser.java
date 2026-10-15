package io.github.georgwav.cosmetics.sol.music;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LRC lyrics (LRCLIB's synced lyrics): {@code [mm:ss.xx]} tags, several on one line for a repeated line, an
 * {@code [offset:±ms]} tag, metadata tags and blank lines skipped, broken lines ignored. Pure; unit tested.
 */
public final class LrcParser {
    private static final Pattern TAG = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");
    private static final Pattern OFFSET = Pattern.compile("\\[offset:\\s*([+-]?\\d+)\\s*]", Pattern.CASE_INSENSITIVE);

    private LrcParser() {
    }

    public static Lyrics parse(String text, String source) {
        long offset = 0;
        record Timed(long at, String text) {
        }
        List<Timed> timed = new ArrayList<>();
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.strip();
            Matcher off = OFFSET.matcher(line);
            if (off.matches()) {
                offset = Long.parseLong(off.group(1));
                continue;
            }
            Matcher m = TAG.matcher(line);
            List<Long> times = new ArrayList<>();
            int end = 0;
            while (m.find() && m.start() == end) {
                long min = Long.parseLong(m.group(1)), sec = Long.parseLong(m.group(2));
                String frac = m.group(3);
                long ms = frac == null ? 0 : frac.length() == 1 ? Long.parseLong(frac) * 100
                        : frac.length() == 2 ? Long.parseLong(frac) * 10 : Long.parseLong(frac);
                times.add(min * 60_000 + sec * 1000 + ms);
                end = m.end();
            }
            if (times.isEmpty()) {
                continue;
            }
            String words = line.substring(end).strip();
            for (long t : times) {
                timed.add(new Timed(t, words));
            }
        }
        timed.sort((a, b) -> Long.compare(a.at, b.at));
        // An [offset] makes lyrics earlier when positive (the LRC convention).
        List<Lyrics.Line> lines = new ArrayList<>();
        for (int i = 0; i < timed.size(); i++) {
            long start = Math.max(0, timed.get(i).at - offset);
            long stop = i + 1 < timed.size() ? Math.max(start, timed.get(i + 1).at - offset) : start + 5000;
            lines.add(new Lyrics.Line(start, stop, timed.get(i).text, List.of(), null, false));
        }
        return new Lyrics(List.copyOf(lines), !lines.isEmpty(), source);
    }

    /** Plain lyrics (no times): one line each, not following the song. */
    public static Lyrics plain(String text, String source) {
        List<Lyrics.Line> lines = new ArrayList<>();
        for (String raw : text.split("\\r?\\n")) {
            lines.add(new Lyrics.Line(0, 0, raw.strip(), List.of(), null, false));
        }
        return new Lyrics(List.copyOf(lines), false, source);
    }
}
