package io.github.georgwav.cosmetics.client;

import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SettingColorTest {
    @Test
    void oldColourNamesCarryOver() {
        Setting.Color c = new Setting.Color("color", 0xFFFFFFFF).themed().names(Map.of("red", 0xFFFF4040, "accent", Setting.Color.THEME));
        c.load(new JsonPrimitive("red"));
        assertEquals(0xFFFF4040, c.get());
        c.load(new JsonPrimitive("accent"));
        assertTrue(c.followsTheme(), "the old \"accent\" follows the theme");
        c.load(new JsonPrimitive("purple"));
        assertTrue(c.followsTheme(), "an unknown name keeps what it had");
    }

    @Test
    void aThemedColourSavesAsThemeAndReadsBack() {
        Setting.Color c = new Setting.Color("pressed", Setting.Color.THEME);
        assertTrue(c.themed, "a default of THEME makes it themed");
        assertEquals("theme", c.save().getAsString());
        c.load(new JsonPrimitive("#3A5BFF"));
        assertEquals(0xFF3A5BFF, c.get());
        assertEquals("#3A5BFF", c.save().getAsString());
        c.load(new JsonPrimitive("theme"));
        assertTrue(c.followsTheme());
    }

    @Test
    void anUnthemedColourIgnoresTheme() {
        Setting.Color c = new Setting.Color("low", 0xFFFF2A2A);
        c.load(new JsonPrimitive("theme"));
        assertEquals(0xFFFF2A2A, c.get());
        assertFalse(c.followsTheme());
        c.load(new JsonPrimitive("80112233"));
        assertEquals(0xFF112233, c.get(), "no opacity of its own: opaque");
    }
}
