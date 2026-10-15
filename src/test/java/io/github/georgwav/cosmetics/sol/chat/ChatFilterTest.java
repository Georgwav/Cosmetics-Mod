package io.github.georgwav.cosmetics.sol.chat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChatFilterTest {
    private static final List<String> ONLINE = List.of("Georg", "Sam");

    @Test
    void everythingByDefault() {
        ChatFilter f = new ChatFilter();
        assertFalse(f.active());
        assertTrue(f.shows("[Server] Welcome", false, false, ONLINE));
        assertTrue(f.shows("<Sam> gg", true, false, ONLINE));
    }

    @Test
    void playersTab() {
        ChatFilter f = new ChatFilter();
        assertTrue(f.tab(ChatFilter.Tab.PLAYERS));
        assertFalse(f.tab(ChatFilter.Tab.PLAYERS), "no change the second time");
        assertTrue(f.shows("anything", true, false, ONLINE), "signed chat is a player's");
        assertTrue(f.shows("12:30 [MVP+] Sam: gg", false, false, ONLINE), "server text from a player");
        assertFalse(f.shows("[Server] Welcome", false, false, ONLINE));
    }

    @Test
    void mentionsTab() {
        ChatFilter f = new ChatFilter();
        f.tab(ChatFilter.Tab.MENTIONS);
        assertTrue(f.shows("<Sam> hi Georg", true, true, ONLINE));
        assertFalse(f.shows("<Sam> hi", true, false, ONLINE));
    }

    @Test
    void searchIsAnyCaseAndNarrowsTheTab() {
        ChatFilter f = new ChatFilter();
        assertTrue(f.query("  SKY "));
        assertTrue(f.active());
        assertTrue(f.shows("<Sam> nice sky", true, false, ONLINE));
        assertFalse(f.shows("<Sam> gg", true, false, ONLINE));
        f.tab(ChatFilter.Tab.PLAYERS);
        assertFalse(f.shows("[Server] the sky is clear", false, false, ONLINE));
        assertTrue(f.reset());
        assertFalse(f.reset(), "already reset");
        assertTrue(f.shows("[Server] the sky is clear", false, false, ONLINE));
    }
}
