package io.github.georgwav.cosmetics.sol.render;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;

/** Small game calls whose names changed between versions. */
public final class Mc {
    private Mc() {
    }

    public static Camera camera() {
        //? if >=26.2 {
        /*return Minecraft.getInstance().gameRenderer.mainCamera();
        *///?} else {
        return Minecraft.getInstance().gameRenderer.getMainCamera();
        //?}
    }

    /** Hides or shows the HUD (F1). */
    public static void hideHud(boolean hide) {
        Minecraft mc = Minecraft.getInstance();
        //? if >=26.2 {
        /*if (mc.gui.hud.isHidden() != hide) {
            mc.gui.hud.toggle();
        }
        *///?} else {
        mc.options.hideGui = hide;
        //?}
    }

    public static boolean hudHidden() {
        Minecraft mc = Minecraft.getInstance();
        //? if >=26.2 {
        /*return mc.gui.hud.isHidden();
        *///?} else {
        return mc.options.hideGui;
        //?}
    }
}
