package io.github.georgwav.cosmetics.sol.ui.screens;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The HUD editor's snapping, on a 480 x 270 GUI screen. */
class HudSnapTest {
    private static final float W = 480f, H = 270f;

    @Test
    void snapsToTheScreensEdgesWithTheMargin() {
        HudSnap.Result r = HudSnap.place(5f, 266f - 20f - 2f, 40f, 20f, W, H, List.of(), false);
        assertEquals(HudSnap.MARGIN, r.x());
        assertEquals(HudSnap.MARGIN, r.guideX());
        // The bottom edge 2 px above the screen's (266 - 2 = 244 + 20 = 264; 268 is 4 away).
        assertEquals(H - HudSnap.MARGIN - 20f, r.y());
    }

    @Test
    void snapsItsMiddleToTheScreensMiddle() {
        HudSnap.Result r = HudSnap.place(W / 2f - 20f + 3f, 100f, 40f, 20f, W, H, List.of(), false);
        assertEquals(W / 2f - 20f, r.x());
        assertEquals(W / 2f, r.guideX());
        assertTrue(Float.isNaN(r.guideY()));
    }

    @Test
    void linesUpWithOtherBoxesAndSitsNextToThemWithTheGap() {
        HudSnap.Rect other = new HudSnap.Rect(100f, 100f, 50f, 30f);
        // Its left edge 3 px from the other's right edge plus the gap: it sits 2 px right of it.
        HudSnap.Result beside = HudSnap.place(150f + 2f + 3f, 140f, 20f, 10f, W, H, List.of(other), false);
        assertEquals(152f, beside.x());
        // Its middle near the other's middle.
        HudSnap.Result centred = HudSnap.place(115f + 2f, 60f, 20f, 10f, W, H, List.of(other), false);
        assertEquals(115f, centred.x());
        assertEquals(125f, centred.guideX());
    }

    @Test
    void theSmallestShiftWins() {
        // Left edge 3 from 100 (the other's left), right edge 1 from 152 (the other's right plus the gap... and 150).
        HudSnap.Rect other = new HudSnap.Rect(100f, 100f, 50f, 30f);
        HudSnap.Result r = HudSnap.place(103f, 200f, 46f, 10f, W, H, List.of(other), false);
        // Points: 103 (to 100: -3), 126 (to 125: -1), 149 (to 150: +1). -1 comes first among the equals.
        assertEquals(1f, Math.abs(r.x() - 103f));
    }

    @Test
    void shiftMovesFreelyButStaysOnTheScreen() {
        HudSnap.Result r = HudSnap.place(3f, 100f, 40f, 20f, W, H, List.of(), true);
        assertEquals(3f, r.x());
        assertTrue(Float.isNaN(r.guideX()));
        HudSnap.Result off = HudSnap.place(-30f, 300f, 40f, 20f, W, H, List.of(), true);
        assertEquals(0f, off.x());
        assertEquals(H - 20f, off.y());
    }

    @Test
    void arrowsNudgeOneOrTenAndClamp() {
        HudSnap.Rect box = new HudSnap.Rect(10f, 10f, 40f, 20f);
        assertEquals(11f, HudSnap.nudge(box, 1, 0, false, W, H).x());
        assertEquals(0f, HudSnap.nudge(box, 0, -1, true, W, H).y());
        assertEquals(W - 40f, HudSnap.nudge(new HudSnap.Rect(W - 45f, 0f, 40f, 20f), 1, 0, true, W, H).x());
    }
}
