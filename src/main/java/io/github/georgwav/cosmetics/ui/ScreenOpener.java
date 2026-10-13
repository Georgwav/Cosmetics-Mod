package io.github.georgwav.cosmetics.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Opens screens; the one place to adapt if a Minecraft version moves screen handling. */
public final class ScreenOpener {
    private static boolean openRequested;

    private ScreenOpener() {
    }

    public static void open(Screen screen) {
        //? if >=26.2 {
        /*Minecraft.getInstance().gui.setScreen(screen); // 26.2 moved screen handling into Gui
        *///?} else {
        Minecraft.getInstance().setScreen(screen);
        //?}
    }

    /**
     * Opens the settings screen on the next client tick. Needed when triggered from chat: the chat
     * screen closes right after a command runs and would close ours with it.
     */
    public static void requestOpen() {
        openRequested = true;
    }

    /** Called every client tick. */
    public static void tick() {
        if (openRequested) {
            openRequested = false;
            open(new CosmeticsScreen(null));
        }
    }
}
