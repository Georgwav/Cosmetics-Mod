package io.github.georgwav.cosmetics.sol.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatTextTest {
    @Test
    void shortcodesBecomeSymbols() {
        assertEquals("gg ❤", Emoji.replace("gg :heart:"));
        assertEquals("★★", Emoji.replace(":star::STAR:"));
        assertEquals("time 12:30 :nope: :", Emoji.replace("time 12:30 :nope: :"), "unknown codes and times stay");
        assertEquals("no colons", Emoji.replace("no colons"));
        assertEquals("::", Emoji.replace("::"));
    }
}
