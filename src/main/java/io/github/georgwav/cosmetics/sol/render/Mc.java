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

    /** The world's day time in ticks (the overworld clock from 26.1). */
    public static long dayTime(net.minecraft.world.level.Level level) {
        //? if >=26.1 {
        /*return level.getDefaultClockTime();
        *///?} else {
        return level.getDayTime();
        //?}
    }

    /** Clears the game's toasts (the test scenario's HUD shots). */
    public static void clearToasts() {
        Minecraft mc = Minecraft.getInstance();
        //? if >=26.2 {
        /*mc.gui.toastManager().clear();
        *///?} elif >=1.21.2 {
        mc.getToastManager().clear();
        //?} else {
        /*mc.getToasts().clear();
        *///?}
    }

    /** The game's main framebuffer: what the world is drawn into. */
    public static com.mojang.blaze3d.pipeline.RenderTarget mainTarget() {
        //? if >=26.2 {
        /*return Minecraft.getInstance().gameRenderer.mainRenderTarget();
        *///?} else {
        return Minecraft.getInstance().getMainRenderTarget();
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
