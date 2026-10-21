package io.github.georgwav.cosmetics.sol.styles;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The texture editor's tools on small pictures (B.4): fill, replace, mirror, resize, undo and redo, opaque blocks. */
class EditorImageTest {
    private static final int RED = 0xFFFF0000, BLUE = 0xFF0000FF, GREY = 0xFF808080, CLEAR = 0;

    private static Pixels.Image solid(int w, int h, int c) {
        int[] px = new int[w * h];
        Arrays.fill(px, c);
        return new Pixels.Image(w, h, px);
    }

    @Test
    void fillStopsAtOtherColoursAndHonoursTolerance() {
        EditorImage e = EditorImage.of(solid(8, 8, GREY), false, null);
        e.begin();
        for (int y = 0; y < 8; y++) {
            e.pencil(4, y, 1, BLUE, false, false); // a wall down the middle
        }
        e.end();
        e.begin();
        e.fill(0, 0, RED, 0);
        e.end();
        assertEquals(RED, e.get(3, 7));
        assertEquals(BLUE, e.get(4, 3));
        assertEquals(GREY, e.get(5, 0), "the fill stops at the wall");
        // A near colour is inside the tolerance.
        EditorImage t = EditorImage.of(solid(4, 1, GREY), false, null);
        t.begin();
        t.pencil(2, 0, 1, 0xFF838383, false, false);
        t.end();
        t.begin();
        t.fill(0, 0, RED, 4);
        t.end();
        assertEquals(RED, t.get(3, 0));
    }

    @Test
    void replaceTurnsOneColourEverywhere() {
        EditorImage e = EditorImage.of(solid(4, 4, GREY), false, null);
        e.begin();
        e.pencil(0, 0, 1, BLUE, false, false);
        e.pencil(3, 3, 1, BLUE, false, false);
        e.end();
        e.begin();
        e.replace(BLUE, RED, 0);
        e.end();
        assertEquals(RED, e.get(0, 0));
        assertEquals(RED, e.get(3, 3));
        assertEquals(GREY, e.get(1, 1));
    }

    @Test
    void mirrorPaintsBothSides() {
        EditorImage e = EditorImage.of(solid(8, 8, CLEAR), false, null);
        e.begin();
        e.pencil(1, 2, 1, RED, true, true);
        e.end();
        assertEquals(RED, e.get(1, 2));
        assertEquals(RED, e.get(6, 2));
        assertEquals(RED, e.get(1, 5));
        assertEquals(RED, e.get(6, 5));
    }

    @Test
    void undoAndRedoKeepOnlyTheChangedPixels() {
        EditorImage e = EditorImage.of(solid(16, 16, GREY), false, null);
        e.begin();
        e.line(0, 0, 15, 15, 1, RED, false, false);
        assertTrue(e.end());
        e.begin();
        e.pencil(0, 15, 2, BLUE, false, false);
        e.end();
        assertEquals(2, e.undoSteps());
        assertTrue(e.undo());
        assertEquals(GREY, e.get(0, 15));
        assertEquals(RED, e.get(7, 7));
        assertTrue(e.undo());
        assertEquals(GREY, e.get(7, 7));
        assertFalse(e.undo());
        assertTrue(e.redo());
        assertEquals(RED, e.get(7, 7));
        // A new stroke after an undo drops the redo.
        e.begin();
        e.pencil(5, 5, 1, BLUE, false, false);
        e.end();
        assertFalse(e.canRedo());
        // At most 200 steps are kept.
        for (int i = 0; i < 250; i++) {
            e.begin();
            e.pencil(i % 16, 0, 1, (i / 16) % 2 == 0 ? RED : BLUE, false, false);
            e.end();
        }
        assertEquals(EditorImage.UNDO, e.undoSteps());
        // A stroke that changes nothing is no step.
        e.begin();
        assertFalse(e.end());
    }

    @Test
    void resizeIsNearestAndUndoable() {
        int[] px = new int[16 * 16];
        Arrays.fill(px, GREY);
        px[0] = RED;
        EditorImage e = EditorImage.of(new Pixels.Image(16, 16, px), false, null);
        e.resize(32);
        assertEquals(32, e.width());
        assertEquals(RED, e.get(1, 1), "each pixel becomes a 2 x 2 block");
        assertEquals(GREY, e.get(2, 2));
        e.resize(16);
        assertEquals(RED, e.get(0, 0));
        assertEquals(GREY, e.get(1, 0));
        e.undo();
        e.undo();
        assertEquals(16, e.width());
        assertEquals(RED, e.get(0, 0));
    }

    @Test
    void blocksStayOpaqueAndTheEraserPutsTheOriginalBack() {
        EditorImage e = EditorImage.of(solid(4, 4, GREY), true, null);
        e.begin();
        e.pencil(1, 1, 1, 0x40FF0000, false, false);
        e.end();
        assertEquals(RED, e.get(1, 1), "a block's pixels are painted opaque");
        e.begin();
        e.erase(1, 1, 1, false, false);
        e.end();
        assertEquals(GREY, e.get(1, 1), "the eraser paints the original back");
        EditorImage item = EditorImage.of(solid(4, 4, GREY), false, null);
        item.begin();
        item.erase(1, 1, 1, false, false);
        item.end();
        assertEquals(CLEAR, item.get(1, 1));
    }

    @Test
    void framesSelectionAndTheirMcmeta() {
        int[] strip = new int[4 * 8];
        Arrays.fill(strip, 0, 16, RED);
        Arrays.fill(strip, 16, 32, BLUE);
        EditorImage e = EditorImage.of(new Pixels.Image(4, 8, strip), false, "{\"animation\":{\"frametime\":3}}");
        assertEquals(2, e.frames());
        assertEquals(4, e.height());
        assertEquals(3, e.frameTime(1));
        e.showFrame(1);
        assertEquals(BLUE, e.get(0, 0));
        e.setFrameTime(1, 5);
        assertNotNull(e.mcmeta());
        assertTrue(e.mcmeta().contains("\"time\": 5"));
        assertEquals(8, e.texture().h());
        // Moving a selection cuts and puts down.
        EditorImage m = EditorImage.of(solid(6, 6, CLEAR), false, null);
        m.begin();
        m.pencil(0, 0, 2, RED, false, false);
        m.end();
        m.select(0, 0, 1, 1);
        m.move(3, 3);
        assertEquals(CLEAR, m.get(0, 0));
        assertEquals(RED, m.get(4, 4));
        m.selectNone();
        assertNull(m.selection());
        // The outline goes round what's there.
        m.begin();
        m.outline(BLUE);
        m.end();
        assertEquals(BLUE, m.get(2, 3));
        assertEquals(RED, m.get(3, 3));
        // The palette lists each colour once.
        assertEquals(2, m.palette(64).size());
    }

    @Test
    void gradientKeepsTheShape() {
        int[] px = new int[8];
        px[1] = GREY;
        px[6] = GREY;
        EditorImage e = EditorImage.of(new Pixels.Image(8, 1, px), false, null);
        e.begin();
        e.gradient(0, 0, 7, 0, RED, BLUE);
        e.end();
        assertEquals(CLEAR, e.get(3, 0), "clear pixels stay clear");
        assertTrue((e.get(1, 0) >> 16 & 255) > (e.get(1, 0) & 255));
        assertTrue((e.get(6, 0) & 255) > (e.get(6, 0) >> 16 & 255));
    }
}
