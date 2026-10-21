package io.github.georgwav.cosmetics.sol.ui;

import io.github.georgwav.cosmetics.sol.ui.kit.ModuleIcons;
import io.github.georgwav.cosmetics.sol.ui.kit.PixelIcons;
import io.github.georgwav.cosmetics.sol.ui.kit.PixelTokens;
import io.github.georgwav.cosmetics.sol.util.TtfPixels;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The pixel kit's data: Silkscreen read as the pixels it was drawn on, the 12 x 12 icons, and the palette (A.4). */
class PixelKitTest {
    private static TtfPixels font(String file) throws Exception {
        try (InputStream in = PixelKitTest.class.getResourceAsStream("/assets/cosmeticsmod/font/" + file)) {
            assertNotNull(in, file);
            return TtfPixels.parse(in.readAllBytes(), 8);
        }
    }

    @Test
    void silkscreenIsReadOnItsPixelGrid() throws Exception {
        for (String file : new String[]{"silkscreen-regular.ttf", "silkscreen-bold.ttf"}) {
            TtfPixels f = font(file);
            TtfPixels.Glyph h = f.glyph('H');
            assertNotNull(h, file);
            assertEquals(5, h.h(), "capitals are 5 pixels tall");
            assertEquals(5, h.top(), "and sit on the baseline");
            // The letters every pixel label uses are there, and each glyph fits the em.
            for (char c : "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789·…+-/%:!?&'".toCharArray()) {
                TtfPixels.Glyph g = f.glyph(c);
                assertNotNull(g, file + " has " + c);
                assertTrue(g.advance() >= 1 && g.advance() <= 8, c + " advance " + g.advance());
                assertTrue(g.top() <= 7 && g.top() - g.h() >= -2, c + " fits the em");
            }
        }
    }

    @Test
    void boldIsWiderThanRegular() throws Exception {
        assertTrue(font("silkscreen-bold.ttf").glyph('M').w() >= font("silkscreen-regular.ttf").glyph('M').w());
    }

    @Test
    void iconsAreTwelveByTwelveAndEveryNameIsThere() {
        Map<String, boolean[]> all = PixelIcons.all();
        assertTrue(all.size() >= 75, "the prototype's icons and Sol's own: " + all.size());
        for (String name : new String[]{"sun", "solsq", "shirt", "run", "grid", "search", "x", "left", "person", "people", "hud",
                "mount", "note", "gear", "box", "check", "play", "stop", "plus", "dice", "star", "cape", "wing", "crown", "shield",
                "flame", "heart", "ring", "eye", "cross", "spark", "sword", "hitbox", "bulb", "dots", "fist", "keys", "bars", "pin",
                "armour", "potion", "lines", "chat", "chunk", "chest", "gauge", "cam", "people2", "server", "card", "crystal",
                "clock", "cloud", "globe", "bolt"}) {
            assertTrue(all.containsKey(name), "the appendix's " + name);
        }
        for (boolean[] cells : all.values()) {
            assertEquals(144, cells.length);
        }
        for (String icon : ModuleIcons.MODULES.values()) {
            assertTrue(PixelIcons.has(icon), "module icon " + icon);
        }
        for (String icon : ModuleIcons.EARLIER.values()) {
            assertTrue(PixelIcons.has(icon), "earlier module icon " + icon);
        }
        for (String icon : ModuleIcons.KEPT.values()) {
            assertTrue(PixelIcons.has(icon), "kept mod icon " + icon);
        }
        for (String icon : ModuleIcons.SECTIONS.values()) {
            assertTrue(PixelIcons.has(icon), "tab icon " + icon);
        }
        // The older line icons' names still work.
        assertTrue(PixelIcons.has("grid4") && PixelIcons.has("wings") && PixelIcons.has("puzzle") && PixelIcons.has("close"));
    }

    @Test
    void anIconSmallerThanTheCellIsCentred() {
        boolean[] play = PixelIcons.parse("play ..#.. .###.").get("play");
        // 5 x 2 in 12 x 12: columns 3..7, rows 5..6.
        assertTrue(play[5 * 12 + 3 + 2]);
        assertTrue(play[6 * 12 + 3 + 1] && play[6 * 12 + 3 + 3]);
        assertFalse(play[0]);
    }

    @Test
    void thePaletteFollowsThePreset() {
        PixelTokens p = new PixelTokens().set(0xFFFFB347, 0xFF5E0F06, 0xFFFE8A2C, false);
        assertEquals(0xFFFFB347, p.acc);
        assertEquals(0xFFFFCE87, p.accHi, "accent mixed .35 toward white");
        assertEquals(p.acc, p.en, "Theme: on glows in the accent");
        PixelTokens green = new PixelTokens().set(0xFFFFB347, 0xFF5E0F06, 0xFFFE8A2C, true);
        assertEquals(0xFF5FD13A, green.en);
        assertEquals(0xFF14300C, green.enDark);
        assertEquals(0xFFB4FA8C, green.enText);
        assertEquals(0x575FD13A, green.enA1, "en at 34%");
        assertEquals(0x245FD13A, green.enA2, "en at 14%");
        assertNotEquals(p.c1, new PixelTokens().set(0xFFFFB347, 0xFF04070F, 0xFFFE8A2C, false).c1, "slabs lean toward the sky");
    }

    @Test
    void theOnTextureHasItsSpecks() {
        int[] px = PixelTokens.onPixels(0xFF5FD13A, 0xFF14300C);
        assertEquals(32 * 16, px.length);
        int specks = 0;
        for (int c : px) {
            if (c != 0xFF14300C) {
                specks++;
            }
        }
        assertTrue(specks > 46 && specks < 46 * 6, "46 specks, 1 to 3 by 1 to 2 pixels: " + specks);
        assertArrayEquals(px, PixelTokens.onPixels(0xFF5FD13A, 0xFF14300C), "seeded: the same every time");
    }
}
