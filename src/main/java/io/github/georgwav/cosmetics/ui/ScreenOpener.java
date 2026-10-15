package io.github.georgwav.cosmetics.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Opens screens; the one place to adapt if a Minecraft version moves screen handling. */
public final class ScreenOpener {
    private static boolean openRequested;
    private static java.util.function.Supplier<Screen> requested;

    private ScreenOpener() {
    }

    public static void open(Screen screen) {
        //? if >=26.2 {
        /*Minecraft.getInstance().gui.setScreen(screen); // 26.2 moved screen handling into Gui
        *///?} else {
        Minecraft.getInstance().setScreen(screen);
        //?}
    }

    /** The screen open now, or null. */
    public static Screen current() {
        //? if >=26.2 {
        /*return Minecraft.getInstance().gui.screen();
        *///?} else {
        return Minecraft.getInstance().screen;
        //?}
    }

    /**
     * Opens the Wardrobe on the next client tick ({@code /cosmetics}). Needed when triggered from chat: the chat
     * screen closes right after a command runs and would close ours with it.
     */
    public static void requestOpen() {
        openRequested = true;
    }

    /** Opens a screen on the next client tick, if no other screen is open by then (a key press). */
    public static void request(java.util.function.Supplier<Screen> screen) {
        requested = screen;
    }

    /** Called every client tick. */
    public static void tick() {
        if (requested != null) {
            java.util.function.Supplier<Screen> s = requested;
            requested = null;
            if (current() == null) {
                open(s.get());
            }
        }
        if (openRequested) {
            openRequested = false;
            open(new io.github.georgwav.cosmetics.sol.ui.wardrobe.WardrobeScreen(null));
        }
    }
}
