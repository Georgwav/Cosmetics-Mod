package io.github.georgwav.cosmetics.sol.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Whether a chat line mentions you: your name or one of your own words, as a whole word, in any case; never in a
 * message you sent yourself ({@code <You> ...}). Pure; unit tested.
 */
public final class Mentions {
    private String key = "";
    private List<Pattern> patterns = List.of();
    private String self = "";

    /** Sets who you are and your extra words (comma-separated); kept until they change. */
    public void set(String name, String extra) {
        String k = name + "\u0000" + extra;
        if (k.equals(key)) {
            return;
        }
        key = k;
        self = name;
        List<Pattern> out = new ArrayList<>();
        List<String> words = new ArrayList<>();
        words.add(name);
        for (String w : extra.split(",")) {
            words.add(w.trim());
        }
        for (String w : words) {
            if (w.length() >= 2) {
                out.add(Pattern.compile("(?<![\\p{L}\\p{N}_])" + Pattern.quote(w) + "(?![\\p{L}\\p{N}_])",
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
            }
        }
        patterns = out;
    }

    public boolean mentions(String line) {
        if (!self.isEmpty() && line.toLowerCase(Locale.ROOT).startsWith("<" + self.toLowerCase(Locale.ROOT) + ">")) {
            return false;
        }
        for (Pattern p : patterns) {
            if (p.matcher(line).find()) {
                return true;
            }
        }
        return false;
    }
}
