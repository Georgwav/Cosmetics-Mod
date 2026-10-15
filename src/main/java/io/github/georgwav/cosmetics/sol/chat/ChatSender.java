package io.github.georgwav.cosmetics.sol.chat;

import java.util.Collection;
import java.util.Locale;

/**
 * Who sent a chat line, from its text: the game's {@code <name>}, a server's {@code [Rank] name: } or
 * {@code name » } (a player in the tab list among the words before the colon or arrow), or with neither the first
 * word of the line's start that's a player ("Sam was slain by Georg"). Never a name further on in the message
 * itself. Servers often send chat as plain text, so this is how chat heads find the face. Pure; unit tested.
 */
public final class ChatSender {
    /** How far into a line the sender may be (ranks, clan tags and channel names come first). */
    private static final int REACH = 48;

    private ChatSender() {
    }

    /** The sender's name as the tab list has it, or null when no player in {@code online} starts the line. */
    public static String of(String line, Collection<String> online) {
        if (line == null || line.isEmpty() || online.isEmpty()) {
            return null;
        }
        String text = line.strip();
        // The game's own chat: "<name> message": that name, or nobody in the tab list.
        if (text.startsWith("<")) {
            int end = text.indexOf('>');
            if (end > 1 && end <= 17) {
                return known(text.substring(1, end), online);
            }
        }
        // The sender part ends at the first colon (or arrow) within reach; without one, the line's start.
        int limit = Math.min(text.length(), REACH);
        for (String delimiter : new String[]{": ", "\u00bb ", "> "}) {
            int at = text.indexOf(delimiter);
            if (at > 0 && at < limit) {
                limit = at;
                break;
            }
        }
        // The first word there that is a player; a word is letters, digits and underscores.
        int i = 0;
        while (i < limit) {
            char c = text.charAt(i);
            if (!word(c)) {
                i++;
                continue;
            }
            int start = i;
            while (i < text.length() && word(text.charAt(i))) {
                i++;
            }
            String found = known(text.substring(start, i), online);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static boolean word(char c) {
        return c == '_' || c < 128 && Character.isLetterOrDigit(c);
    }

    private static String known(String word, Collection<String> online) {
        if (word.isEmpty() || word.length() > 16) {
            return null;
        }
        for (String name : online) {
            if (name.equalsIgnoreCase(word)) {
                return name;
            }
        }
        return null;
    }

    /** Lower case, for callers keeping names in a set. */
    public static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
