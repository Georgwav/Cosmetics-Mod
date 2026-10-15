package io.github.georgwav.cosmetics.sol.chat;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sol Chat's {@code :name:} shortcodes, turned into symbols the game's own font draws (so everyone sees them, with
 * or without Sol). Pure; unit tested.
 */
public final class Emoji {
    /** Shortcode (without the colons) → what it becomes. */
    public static final Map<String, String> CODES = new LinkedHashMap<>();

    static {
        String[][] pairs = {
                {"heart", "❤"}, {"hearts", "♥"}, {"star", "★"}, {"sparkle", "✦"}, {"sun", "☀"},
                {"moon", "☽"}, {"cloud", "☁"}, {"umbrella", "☂"}, {"snowman", "☃"}, {"bolt", "⚡"},
                {"smile", "☺"}, {"sad", "☹"}, {"skull", "☠"}, {"peace", "✌"}, {"check", "✔"},
                {"x", "✖"}, {"warning", "⚠"}, {"note", "♪"}, {"music", "♫"}, {"swords", "⚔"},
                {"crown", "♛"}, {"flower", "✿"}, {"diamond", "♦"}, {"spade", "♠"}, {"club", "♣"},
                {"arrow", "➜"}, {"phone", "☎"}, {"mail", "✉"}, {"pencil", "✎"}, {"scissors", "✂"},
                {"plane", "✈"}, {"yinyang", "☯"}, {"shrug", "¯\\_(ツ)_/¯"},
                {"tableflip", "(╯°□°)╯︵ ┻━┻"}, {"gg", "GG"},
        };
        for (String[] p : pairs) {
            CODES.put(p[0], p[1]);
        }
    }

    private Emoji() {
    }

    /** {@code text} with every known {@code :name:} replaced; unknown ones and lone colons stay as they are. */
    public static String replace(String text) {
        if (text.indexOf(':') < 0) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == ':') {
                int end = text.indexOf(':', i + 1);
                if (end > i + 1) {
                    String code = CODES.get(text.substring(i + 1, end).toLowerCase(java.util.Locale.ROOT));
                    if (code != null) {
                        out.append(code);
                        i = end + 1;
                        continue;
                    }
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }
}
