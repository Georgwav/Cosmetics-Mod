package io.github.georgwav.cosmetics.sol.atmosphere;

import net.minecraft.client.Minecraft;

/**
 * The Night Sky lab's "Look around": the view turns slowly round the sky, 7° a second, until it's switched off
 * again (its key, or the button on the Night sky page). Paused while a screen is open; freelook turns the camera
 * instead of the player. Render thread only.
 */
public final class LookAround {
    private static boolean on;
    private static long last;

    private LookAround() {
    }

    public static void toggle() {
        on = !on;
        last = 0L;
    }

    public static boolean on() {
        return on;
    }

    /** Once a frame. */
    static void frame() {
        Minecraft mc = Minecraft.getInstance();
        if (!on || mc.player == null) {
            last = 0L;
            return;
        }
        long now = System.nanoTime();
        if (last != 0L && io.github.georgwav.cosmetics.ui.ScreenOpener.current() == null) {
            float dt = Math.min(0.1f, (now - last) / 1e9f);
            // The game's turn takes mouse units (0.15° each).
            mc.player.turn(7f * dt / 0.15f, 0.0);
        }
        last = now;
    }
}
