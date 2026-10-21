package io.github.georgwav.cosmetics.sol.hud;

import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.sol.ui.kit.SolScreen;
import io.github.georgwav.cosmetics.ui.Canvas;

/**
 * Sol UI's tab list as it opens: the players slide in from the left one after another, each row in three whole steps
 * of 4 pixels, 30 ms after the row above (rows still waiting are drawn out of sight). SolTabListMixin calls it at each
 * of the game's fills: a panel's fill (header, players, footer) ends the row before; a row's fill moves its row (its
 * fill, head, name and ping) until the next.
 */
public final class TabSlide {
    private static final int[] OFFSET = {-12, -8, -4, 0};
    private static final float STEP = 0.04f, STAGGER = 0.03f;
    private static boolean visible, pushed;
    private static long openedAt;
    private static int panelTop;
    /** For the in-game test: rows drawn while sliding; a moment held for the shot (seconds since opening; NaN: live). */
    public static int slidRows;
    public static float hold = Float.NaN;

    private TabSlide() {
    }

    /** The game shows or hides the tab list (every tick). */
    public static void visible(boolean on) {
        if (on && !visible) {
            openedAt = System.nanoTime();
        }
        visible = on;
    }

    private static boolean on() {
        var ui = Modules.SOL_UI;
        return ui.enabled() && ui.tabSlide.get() && !SolScreen.calm() && SolHud.draws(SolHud.Part.TAB);
    }

    /** At one of the tab list's fills (y1 its top, {@code color} the game's). */
    public static void fill(Canvas c, int y1, int color) {
        done(c);
        if (color == Integer.MIN_VALUE) {
            panelTop = y1;
            return;
        }
        if (!on()) {
            return;
        }
        int row = Math.max(0, (y1 - panelTop - 1) / 9);
        float t = (Float.isNaN(hold) ? (System.nanoTime() - openedAt) / 1e9f : hold) - row * STAGGER;
        if (t >= STEP * 3) {
            return;
        }
        float dx = t < 0f ? -100000f : OFFSET[Math.min(3, (int) (t / STEP))];
        c.push();
        c.translate(dx, 0f);
        pushed = true;
        slidRows++;
    }

    /** After the last row (the footer's fill, or the end of the list). */
    public static void done(Canvas c) {
        if (pushed) {
            c.pop();
            pushed = false;
        }
    }
}
