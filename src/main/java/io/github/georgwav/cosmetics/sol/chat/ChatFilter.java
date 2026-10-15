package io.github.georgwav.cosmetics.sol.chat;

import java.util.Collection;
import java.util.Locale;

/**
 * Sol Chat's tabs and search while the chat is open: every message, only players' messages, or only the ones that
 * mention you, narrowed to those containing the search text (any case). Pure; unit tested.
 */
public final class ChatFilter {
    public enum Tab { ALL, PLAYERS, MENTIONS }

    private Tab tab = Tab.ALL;
    private String query = "";

    public Tab tab() {
        return tab;
    }

    public String query() {
        return query;
    }

    /** Sets the tab; returns whether it changed. */
    public boolean tab(Tab t) {
        boolean changed = t != tab;
        tab = t;
        return changed;
    }

    /** Sets the search text; returns whether it changed. */
    public boolean query(String q) {
        String clean = q == null ? "" : q.strip().toLowerCase(Locale.ROOT);
        boolean changed = !clean.equals(query);
        query = clean;
        return changed;
    }

    /** Back to every message; returns whether that changed anything. */
    public boolean reset() {
        boolean was = active();
        tab = Tab.ALL;
        query = "";
        return was;
    }

    public boolean active() {
        return tab != Tab.ALL || !query.isEmpty();
    }

    /**
     * Whether a message shows: {@code text} is it as shown, {@code signed} whether a player signed it (the game's
     * own player chat), {@code mention} whether Sol Chat marked it, {@code online} the tab list's names (a player's
     * message sent as server text still counts as a player's).
     */
    public boolean shows(String text, boolean signed, boolean mention, Collection<String> online) {
        switch (tab) {
            case PLAYERS -> {
                if (!signed && ChatSender.of(text, online) == null) {
                    return false;
                }
            }
            case MENTIONS -> {
                if (!mention) {
                    return false;
                }
            }
            default -> {
            }
        }
        return query.isEmpty() || text.toLowerCase(Locale.ROOT).contains(query);
    }
}
