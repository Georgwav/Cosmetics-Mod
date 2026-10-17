package io.github.georgwav.cosmetics.sol.client;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A GitHub release's notes (Markdown) as plain lines for the what's-new screen: headings marked, list items as
 * bullets, links as their text, emphasis and code marks dropped, runs of blank lines as one. Pure; unit tested.
 */
public final class ReleaseNotes {
    /** One line: its text, and whether it's a heading. */
    public record Line(String text, boolean heading) {
    }

    private static final Pattern LINK = Pattern.compile("!?\\[([^\\]]*)]\\([^)]*\\)");
    private static final Pattern MARKS = Pattern.compile("(\\*\\*|__|`|~~)");

    private ReleaseNotes() {
    }

    public static List<Line> lines(String markdown) {
        List<Line> out = new ArrayList<>();
        if (markdown == null) {
            return out;
        }
        boolean blank = true;
        for (String raw : markdown.replace("\r", "").split("\n")) {
            String s = raw.strip();
            if (s.isEmpty() || s.matches("^(-{3,}|\\*{3,}|_{3,})$") || s.startsWith("<!--")) {
                if (!blank) {
                    out.add(new Line("", false));
                    blank = true;
                }
                continue;
            }
            boolean heading = s.startsWith("#");
            if (heading) {
                s = s.replaceFirst("^#+\\s*", "");
            } else if (s.matches("^[-*+]\\s+.*")) {
                s = "\u2022 " + s.substring(1).strip();
            } else if (s.matches("^\\d+[.)]\\s+.*")) {
                s = s.replaceFirst("^(\\d+)[.)]\\s+", "$1. ");
            }
            s = MARKS.matcher(LINK.matcher(s).replaceAll("$1")).replaceAll("");
            out.add(new Line(s, heading));
            blank = false;
        }
        while (!out.isEmpty() && out.get(out.size() - 1).text().isEmpty()) {
            out.remove(out.size() - 1);
        }
        return out;
    }
}
