package io.github.georgwav.cosmetics.sol.ui.kit;

import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.sol.fx.SolSounds;

/**
 * Sol UI's sounds (its UI sounds setting: one of Sol's sound packs, yours, or off): a click for buttons, tabs and
 * chips, the pack's toggle for switches, a lower click as a menu opens, and its pop as a toast comes in. Quiet, and
 * never two in the same instant.
 */
public final class UiSounds {
    private static long last;
    /** For the in-game test: sounds played. */
    public static int played;

    private UiSounds() {
    }

    public static void click() {
        play("click", 1f, 0.5f);
    }

    public static void toggle(boolean on) {
        play("toggle", on ? 1.08f : 0.92f, 0.55f);
    }

    public static void open() {
        play("click", 0.8f, 0.45f);
    }

    public static void toast() {
        play("pop", 1.15f, 0.4f);
    }

    private static void play(String event, float pitch, float loud) {
        var ui = Modules.SOL_UI;
        String pack = ui.uiSounds.get();
        if (pack.equals("off")) {
            return;
        }
        long now = System.nanoTime();
        if (now - last < 40_000_000L) {
            return;
        }
        last = now;
        played++;
        SolSounds.play(pack, event, loud * (float) (ui.uiVolume.get() / 100.0), pitch);
    }
}
