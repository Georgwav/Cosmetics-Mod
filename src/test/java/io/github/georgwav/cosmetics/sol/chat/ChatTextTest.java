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

    @Test
    void mentionsAreWholeWordsAndNeverYourOwnMessages() {
        Mentions m = new Mentions();
        m.set("Georg", "sol, builder");
        assertTrue(m.mentions("<Alex> hey georg, look"));
        assertTrue(m.mentions("[Party] Alex: GEORG!"));
        assertTrue(m.mentions("<Alex> the Sol sky"));
        assertTrue(m.mentions("<Alex> ask the builder"));
        assertFalse(m.mentions("<Alex> georgia is nice"), "a word that only starts with the name");
        assertFalse(m.mentions("<Georg> I said georg"), "your own message");
        assertFalse(m.mentions("<Alex> solar panels"));
        m.set("Georg", "");
        assertFalse(m.mentions("<Alex> the Sol sky"), "extra words follow the setting");
    }
}
