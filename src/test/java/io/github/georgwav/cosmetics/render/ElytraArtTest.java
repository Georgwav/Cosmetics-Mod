package io.github.georgwav.cosmetics.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElytraArtTest {
    /** A Badlion cape texture at a pixel a block: 22 x 23 blocks, each pixel its own colour (its x and y). */
    private static int[] badlionCape() {
        int[] argb = new int[22 * 23];
        for (int y = 0; y < 23; y++) {
            for (int x = 0; x < 22; x++) {
                argb[y * 22 + x] = 0xFF000000 | x << 16 | y << 8 | 0x42;
            }
        }
        return argb;
    }

    private static int at(int[] layout, int scale, int x, int y) {
        return layout[y * ElytraArt.WIDTH * scale + x];
    }

    /** The cape pixel (x, y) a colour of {@link #badlionCape} came from. */
    private static String from(int argb) {
        return (argb >> 16 & 0xFF) + "," + (argb >> 8 & 0xFF);
    }

    @Test
    void capesWithoutAModelHaveTheirDesignWhereTheirLayoutPutsIt() {
        ElytraArt.Rect[] vanilla = ElytraArt.capeLayout(64, 32);
        assertEquals(new ElytraArt.Rect(1, 1, 11, 17), vanilla[0]);
        assertEquals(new ElytraArt.Rect(12, 1, 22, 17), vanilla[1]);
        // Badlion's: 22 blocks wide, 23 high, 16 px a block.
        ElytraArt.Rect[] badlion = ElytraArt.capeLayout(352, 368);
        assertEquals(new ElytraArt.Rect(16, 16, 176, 272), badlion[0]);
        assertEquals(new ElytraArt.Rect(192, 16, 352, 272), badlion[1]);
        assertEquals(16, ElytraArt.scaleFor(badlion[0], 32));
        assertEquals(8, ElytraArt.scaleFor(badlion[0], 8));
    }

    @Test
    void theCapesOutsideCoversTheWingsOutsideStretchedToItsLength() {
        ElytraArt.Rect[] rects = ElytraArt.capeLayout(22, 23);
        int[] elytra = ElytraArt.fromCape(badlionCape(), 22, 23, rects[0], rects[1], 1);
        // The wing's outside (36, 2 to 46, 22): all of the cape's 10 x 16, the same way round.
        assertEquals("1,1", from(at(elytra, 1, 36, 2)));
        assertEquals("10,1", from(at(elytra, 1, 45, 2)));
        assertEquals("1,16", from(at(elytra, 1, 36, 21)));
        assertEquals("10,16", from(at(elytra, 1, 45, 21)));
        // Every row of the cape shows, none left out by the stretch.
        java.util.Set<Integer> rows = new java.util.HashSet<>();
        for (int y = 2; y < 22; y++) {
            rows.add(at(elytra, 1, 40, y) >> 8 & 0xFF);
        }
        assertEquals(16, rows.size());
        // The inside (24, 2 to 34, 22): the cape's inside.
        assertEquals("12,1", from(at(elytra, 1, 24, 2)));
        assertEquals("21,16", from(at(elytra, 1, 33, 21)));
    }

    @Test
    void theWingsEdgesContinueItsSides() {
        ElytraArt.Rect[] rects = ElytraArt.capeLayout(22, 23);
        int[] elytra = ElytraArt.fromCape(badlionCape(), 22, 23, rects[0], rects[1], 1);
        // Outer edge (22, 24): next to the outside its last column, next to the inside the inside's first.
        assertEquals("10,1", from(at(elytra, 1, 22, 2)));
        assertEquals("12,1", from(at(elytra, 1, 23, 2)));
        // Inner edge (34, 36): the inside's last column, then the outside's first.
        assertEquals("21,4", from(at(elytra, 1, 34, 6)));
        assertEquals("1,4", from(at(elytra, 1, 35, 6)));
        // Top (24, 0): the outside's top row read the other way round, and the inside's.
        assertEquals("10,1", from(at(elytra, 1, 24, 0)));
        assertEquals("1,1", from(at(elytra, 1, 33, 0)));
        assertEquals("12,1", from(at(elytra, 1, 24, 1)));
        // Bottom (34, 0): their bottom rows.
        assertEquals("10,16", from(at(elytra, 1, 34, 0)));
        assertEquals("12,16", from(at(elytra, 1, 34, 1)));
        // Nothing outside the wings' part.
        assertEquals(0, at(elytra, 1, 5, 5));
        assertEquals(0, at(elytra, 1, 50, 10));
        assertEquals(0, at(elytra, 1, 30, 25));
    }

    @Test
    void aBigCapeDrawnSmallerAveragesItsPixels() {
        // 4 px a block, drawn at 2: each elytra pixel is 2 x 2 of the cape's.
        int[] argb = new int[88 * 92];
        for (int y = 0; y < 92; y++) {
            for (int x = 0; x < 88; x++) {
                argb[y * 88 + x] = (x & 1) == 0 ? 0xFFFF0000 : 0xFF0000FF;
            }
        }
        ElytraArt.Rect[] rects = ElytraArt.capeLayout(88, 92);
        int[] elytra = ElytraArt.fromCape(argb, 88, 92, rects[0], rects[1], 2);
        int c = at(elytra, 2, 36 * 2 + 3, 2 * 2 + 3);
        assertEquals(0xFF, c >>> 24);
        assertEquals(0x7F, c >> 16 & 0xFF);
        assertEquals(0x7F, c & 0xFF);
    }

    @Test
    void seeThroughPartsStaySeeThroughAndDontDarkenTheirNeighbours() {
        int[] argb = new int[2];
        argb[0] = 0x00000000;
        argb[1] = 0xFFFFFFFF;
        ElytraArt.Rect rect = new ElytraArt.Rect(0, 0, 2, 1);
        int c = ElytraArt.sample(argb, 2, 1, rect, 0f, 1f, 0f, 1f);
        assertEquals(0x80FFFFFF, c);
        assertEquals(0, ElytraArt.sample(new int[4], 2, 2, rect, 0f, 1f, 0f, 1f));
    }

    @Test
    void aCapeTexturesOwnElytraIsFound() {
        int[] empty = new int[64 * 32];
        assertFalse(ElytraArt.hasOwnElytra(empty, 64, 32));
        int[] drawn = new int[64 * 32];
        for (int y = 2; y < 22; y++) {
            for (int x = 36; x < 46; x++) {
                drawn[y * 64 + x] = 0xFF336699;
            }
        }
        assertTrue(ElytraArt.hasOwnElytra(drawn, 64, 32));
        // Not the 64 x 32 layout: no elytra part.
        assertFalse(ElytraArt.hasOwnElytra(new int[22 * 23], 22, 23));
    }

    @Test
    void glowingPartsAreDrawnOverTheCape() {
        int[] base = {0xFF0000FF, 0xFF0000FF, 0x00000000};
        int[] top = {0x00FFFFFF, 0x80FF0000, 0xFFFFFF00};
        ElytraArt.over(base, top);
        assertEquals(0xFF0000FF, base[0]);
        assertEquals(0xFF, base[1] >>> 24);
        assertTrue((base[1] >> 16 & 0xFF) > 0x70 && (base[1] & 0xFF) > 0x70);
        assertEquals(0xFFFFFF00, base[2]);
    }

    @Test
    void theElytraTakesTheGameElytrasShape() {
        // The game's elytra texture at its own size (2 x 1 here): seen on the left, see-through on the right, half
        // seen below.
        int[] mask = {0xFF000000, 0x00000000, 0x80000000, 0x00FFFFFF};
        int[] elytra = new int[4 * 4];
        java.util.Arrays.fill(elytra, 0xFF336699);
        ElytraArt.shape(elytra, 4, 4, mask, 2, 2);
        assertEquals(0xFF336699, elytra[0]);
        assertEquals(0xFF336699, elytra[1]);
        assertEquals(0x00336699, elytra[2]);
        assertEquals(0x80, elytra[2 * 4] >>> 24);
        assertEquals(0, elytra[3 * 4 + 3] >>> 24);
    }
}
