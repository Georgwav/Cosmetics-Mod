package io.github.georgwav.cosmetics.sol.ui.kit;

import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.mixin.ContainerScreenAccessor;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * Sol UI's screen slide: an inventory, a container or the chat comes up 8 pixels into place in three whole steps as it
 * opens (ScreenSlideMixin moves the game's own drawing; nothing is scaled, so its pixels stay its pixels), and fades
 * in from the theme's panel colour, smoothly (drawn on Sol's top layer).
 */
public final class ScreenSlide {
    /** How far below its place the screen is at each step, in GUI pixels: in place at the last. */
    private static final int[] OFFSET = {8, 5, 3, 0};
    /** How long each step lasts. */
    private static final float STEP = 0.045f;
    /** How long the fade lasts, seconds: as long as the slide's steps. */
    private static final float FADE = 3 * STEP;

    private static Screen screen;
    private static long openedAt;
    private static boolean pushed;
    /** For the in-game test: screens that slid in, and frames the fade drew; a step held for its shot (-1: none). */
    public static int slides, fadeFrames;
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

    /** Over the opening screen's panel (or the chat's line), on Sol's top layer: its panel colour, fading out smoothly. */
    public static void fade(Gfx g) {
        Screen s = io.github.georgwav.cosmetics.ui.ScreenOpener.current();
        if (s == null || s != screen) {
            return;
        }
        int k = step(s);
        float t = hold >= 0 ? Math.min(1f, hold / 3f) : (System.nanoTime() - openedAt) / 1e9f / FADE;
        if (k >= 3 || t >= 1f) {
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
        float alpha = (1f - t) * (1f - t) * 0.6f;
        g.rect(x, y + OFFSET[k], w, h, Math.round(alpha * 255f) << 24 | (ThemeState.tokens().panel & 0xFFFFFF));
        fadeFrames++;
    }
}
