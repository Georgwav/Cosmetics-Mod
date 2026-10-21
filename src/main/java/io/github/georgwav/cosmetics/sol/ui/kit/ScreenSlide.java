package io.github.georgwav.cosmetics.sol.ui.kit;

import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.mixin.ContainerScreenAccessor;
import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * Sol UI's screen slide: an inventory, a container or the chat comes up 8 pixels into place in three whole steps as it
 * opens (ScreenSlideMixin moves the game's own drawing; nothing is scaled, so its pixels stay its pixels), and fades
 * in through an ordered dither in the theme's panel colour that thins out a step at a time (drawn on Sol's top layer).
 */
public final class ScreenSlide {
    /** How far below its place the screen is at each step, in GUI pixels: in place at the last. */
    private static final int[] OFFSET = {8, 5, 3, 0};
    /** How long each step lasts. */
    private static final float STEP = 0.045f;
    /** The dither's tiles: three levels side by side, each {@link #TILE} GUI pixels square. */
    private static final int TILE = 64;
    private static SolTexture dither;

    private static Screen screen;
    private static long openedAt;
    private static boolean pushed;
    /** For the in-game test: screens that slid in, and frames the dither drew; a step held for its shot (-1: none). */
    public static int slides, ditherFrames;
    public static int hold = -1;

    private ScreenSlide() {
    }

    private static boolean wanted(Screen s) {
        var ui = Modules.SOL_UI;
        return ui.enabled() && ui.screenSlide.get() && !SolScreen.calm()
                && (s instanceof AbstractContainerScreen<?> || s instanceof ChatScreen);
    }

    /** The step (0 to 3, 3 in place) {@code s} is at; a new screen starts at 0. */
    public static int step(Screen s) {
        if (s != screen) {
            screen = s;
            openedAt = System.nanoTime();
            if (s != null && wanted(s)) {
                slides++;
            }
        }
        if (s == null || !wanted(s)) {
            return 3;
        }
        if (hold >= 0) {
            return Math.min(3, hold);
        }
        return Math.min(3, (int) ((System.nanoTime() - openedAt) / 1e9 / STEP));
    }

    /** Before the game draws {@code s}: lower it by its step's offset. */
    public static void begin(Screen s, Canvas c) {
        int k = step(s);
        pushed = k < 3;
        if (pushed) {
            c.push();
            c.translate(0f, OFFSET[k]);
        }
    }

    public static void end(Canvas c) {
        if (pushed) {
            c.pop();
            pushed = false;
        }
    }

    /** The dither over the opening screen's panel (or the chat's line), on Sol's top layer, in GUI units. */
    public static void dither(Gfx g) {
        Screen s = io.github.georgwav.cosmetics.ui.ScreenOpener.current();
        if (s == null || s != screen) {
            return;
        }
        int k = step(s);
        if (k >= 3) {
            return;
        }
        float x, y, w, h;
        if (s instanceof AbstractContainerScreen<?> cs) {
            ContainerScreenAccessor a = (ContainerScreenAccessor) cs;
            x = a.cosmeticsmod$left();
            y = a.cosmeticsmod$top();
            w = a.cosmeticsmod$width();
            h = a.cosmeticsmod$height();
        } else {
            x = 2f;
            y = s.height - 14f;
            w = s.width - 4f;
            h = 12f;
        }
        y += OFFSET[k];
        SolTexture t = texture();
        int c = 0xFF000000 | ThemeState.tokens().panel;
        float u0 = k * TILE / (3f * TILE);
        for (float ty = y; ty < y + h; ty += TILE) {
            float th = Math.min(TILE, y + h - ty);
            for (float tx = x; tx < x + w; tx += TILE) {
                float tw = Math.min(TILE, x + w - tx);
                g.image(t, tx, ty, tw, th, u0, 0f, u0 + tw / (3f * TILE), th / TILE, c, false);
            }
        }
        ditherFrames++;
    }

    /** Three levels of a 2 by 2 ordered dither, white: three of four pixels, two (a checker), one. */
    private static SolTexture texture() {
        if (dither == null || dither.released()) {
            int[] px = new int[3 * TILE * TILE];
            int[][] bayer = {{0, 2}, {3, 1}};
            for (int level = 0; level < 3; level++) {
                for (int y = 0; y < TILE; y++) {
                    for (int x = 0; x < TILE; x++) {
                        boolean on = bayer[y & 1][x & 1] < 3 - level;
                        px[y * 3 * TILE + level * TILE + x] = on ? 0xFFFFFFFF : 0;
                    }
                }
            }
            dither = SolTexture.of("screen_dither", 3 * TILE, TILE, false, px);
        }
        return dither;
    }
}
