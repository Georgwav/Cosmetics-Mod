package io.github.georgwav.cosmetics.sol.hud;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Sol HUD's pixel icons: every cell drawn, in its own place, in the theme's colours. */
class HudIconAtlasTest {
    private static final int ACCENT = 0xFFFF5E8A, GLOW = 0xFFFA9588;

    private static int at(int[] px, HudIconAtlas.Icon icon, int x, int y) {
        return px[(icon.v() + y) * HudIconAtlas.SIZE + icon.u() + x];
    }

    private static long drawn(int[] px, HudIconAtlas.Icon icon) {
        long n = 0;
        for (int y = 0; y < HudIconAtlas.CELL; y++) {
            for (int x = 0; x < HudIconAtlas.CELL; x++) {
                if (at(px, icon, x, y) != 0) {
                    n++;
                }
            }
        }
        return n;
    }

    @Test
    void everyIconFitsAndIsDrawn() {
        int[] px = HudIconAtlas.bake(ACCENT, GLOW);
        assertTrue(HudIconAtlas.Icon.values().length <= 49, "more icons than cells");
        for (HudIconAtlas.Icon icon : HudIconAtlas.Icon.values()) {
            assertTrue(icon.u() + HudIconAtlas.CELL <= HudIconAtlas.SIZE && icon.v() + HudIconAtlas.CELL <= HudIconAtlas.SIZE);
            assertTrue(drawn(px, icon) > 0, icon + " is empty");
        }
    }

    @Test
    void heartsTakeTheAccentAndHalfHeartsEmptyOnTheRight() {
        int[] px = HudIconAtlas.bake(ACCENT, GLOW);
        // Row 2 of the heart (cell row 4): "o#####o" from x = 1.
        assertEquals(ACCENT, at(px, HudIconAtlas.Icon.HEART_FULL, 2, 4));
        assertEquals(ACCENT, at(px, HudIconAtlas.Icon.HEART_HALF, 2, 4));
        assertEquals(HudIconAtlas.EMPTY, at(px, HudIconAtlas.Icon.HEART_HALF, 6, 4));
        assertEquals(HudIconAtlas.EMPTY, at(px, HudIconAtlas.Icon.HEART_CONTAINER, 3, 4));
        // Shanks in the glow; hunger keeps its green.
        assertEquals(GLOW, at(px, HudIconAtlas.Icon.FOOD_FULL, 3, 6));
        assertEquals(HudIconAtlas.HUNGER, at(px, HudIconAtlas.Icon.HUNGER_FULL, 3, 6));
    }

    @Test
    void sameThemeSamePictureOtherThemeOtherHearts() {
        assertArrayEquals(HudIconAtlas.bake(ACCENT, GLOW), HudIconAtlas.bake(ACCENT, GLOW));
        int[] other = HudIconAtlas.bake(0xFF4DFFB0, GLOW);
        assertTrue(!Arrays.equals(HudIconAtlas.bake(ACCENT, GLOW), other));
        assertEquals(0xFF4DFFB0, at(other, HudIconAtlas.Icon.HEART_FULL, 2, 4));
    }
}
