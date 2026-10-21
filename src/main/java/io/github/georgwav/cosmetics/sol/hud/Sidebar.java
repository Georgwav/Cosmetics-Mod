package io.github.georgwav.cosmetics.sol.hud;

import io.github.georgwav.cosmetics.client.hud.VanillaHud;

/**
 * Where the game last drew the scoreboard sidebar, on the screen in GUI pixels (its move in the HUD editor and its
 * size from Sol UI taken in), so a HUD module in its way can step aside while it shows.
 */
public final class Sidebar {
    private static float x0, y0, x1, y1;
    private static long seen;

    private Sidebar() {
    }

    /**
     * The sidebar drew from ({@code left}, {@code top}) to ({@code right}, {@code bottom}) where the game lays it out,
     * before the editor's move and Sol UI's size (which grows it from the screen's right edge, at its middle).
     */
    public static void drawn(int left, int top, int right, int bottom, int screenW, int screenH) {
        float s = HudTweaks.scoreboardScale();
        float dx = VanillaHud.dx(VanillaHud.Part.SCOREBOARD), dy = VanillaHud.dy(VanillaHud.Part.SCOREBOARD);
        float mid = screenH / 2f;
        x0 = dx + screenW + (left - screenW) * s;
        x1 = dx + screenW + (right - screenW) * s;
        y0 = dy + mid + (top - mid) * s;
        y1 = dy + mid + (bottom - mid) * s;
        seen = System.nanoTime();
    }

    /** Whether it's showing (it drew within the last quarter second). */
    public static boolean shown() {
        return seen != 0L && System.nanoTime() - seen < 250_000_000L;
    }

    /** Whether it's showing and covers any of the box from ({@code ax}, {@code ay}) to ({@code bx}, {@code by}). */
    public static boolean covers(float ax, float ay, float bx, float by) {
        return shown() && ax < x1 && bx > x0 && ay < y1 && by > y0;
    }

    public static float left() {
        return x0;
    }
}
