package io.github.georgwav.cosmetics.sol.music;

import io.github.georgwav.cosmetics.sol.util.Colors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AlbumThemeTest {
    /** A cover: mostly dark navy, a big hot-pink block, a smaller teal block and a little grey. */
    private static int[] cover() {
        int[] px = new int[48 * 48];
        for (int i = 0; i < px.length; i++) {
            int x = i % 48, y = i / 48;
            px[i] = y < 16 ? 0xFFFF2E88 : y < 24 && x < 24 ? 0xFF1FC8B4 : x > 44 ? 0xFF808080 : 0xFF0B1030;
        }
        return px;
    }

    @Test
    void picksTheVividColoursAndADarkPanel() {
        AlbumTheme.Palette p = AlbumTheme.palette(cover());
        assertTrue(Colors.r(p.accent()) > 200 && Colors.g(p.accent()) < 120, "pink is the accent: " + Colors.hex(p.accent()));
        assertTrue(Colors.g(p.glow()) > 150 && Colors.r(p.glow()) < 120, "teal is the glow: " + Colors.hex(p.glow()));
        assertTrue(Colors.luminance(p.panel()) < 0.03, "the panel is dark");
        assertTrue(Colors.contrast(p.accent(), p.panel()) >= 3.0);
    }

    @Test
    void deterministicAndSafeOnEdgeCases() {
        assertEquals(AlbumTheme.palette(cover()), AlbumTheme.palette(cover()));
        int[] grey = new int[100];
        java.util.Arrays.fill(grey, 0xFF777777);
        assertNotNull(AlbumTheme.palette(grey));
        assertNotNull(AlbumTheme.palette(new int[0]));
    }
}
