package io.github.georgwav.cosmetics.sol.chat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChatSenderTest {
    private static final List<String> ONLINE = List.of("Georg", "Alex_99", "Sam");

    @Test
    void theGamesOwnChat() {
        assertEquals("Georg", ChatSender.of("<Georg> hello", ONLINE));
        assertEquals("Alex_99", ChatSender.of("<alex_99> gg", ONLINE), "names match whatever the case");
    }

    @Test
    void serverFormats() {
        assertEquals("Sam", ChatSender.of("[MVP+] Sam: gg", ONLINE));
        assertEquals("Georg", ChatSender.of("Party > [VIP] Georg: ready?", ONLINE));
        assertEquals("Alex_99", ChatSender.of("Alex_99 » hi", ONLINE));
    }

    @Test
    void neverANameInTheMessage() {
        assertNull(ChatSender.of("<Alex> nice sky, Georg!", ONLINE), "Alex isn't online; Georg is only mentioned");
        assertEquals("Sam", ChatSender.of("[MVP+] Sam: hi Georg", ONLINE));
        assertNull(ChatSender.of("[Server]: welcome Georg", ONLINE));
        assertEquals("Georg", ChatSender.of("12:30 <Georg> hi", ONLINE), "a time before the game's own format");
    }

    @Test
    void theFirstPlayerNamedWins() {
        assertEquals("Sam", ChatSender.of("Sam was slain by Georg", ONLINE));
    }

    @Test
    void noSender() {
        assertNull(ChatSender.of("[Server] Welcome!", ONLINE));
        assertNull(ChatSender.of("Samantha joined", ONLINE), "only whole words");
        assertNull(ChatSender.of("", ONLINE));
        assertNull(ChatSender.of("<Georg> hi", List.of()));
        assertNull(ChatSender.of("x".repeat(60) + " Georg: late", ONLINE), "too far into the line");
    }
}
