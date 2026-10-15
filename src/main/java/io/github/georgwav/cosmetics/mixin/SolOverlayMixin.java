package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.sol.ui.kit.SolOverlay;
import io.github.georgwav.cosmetics.ui.Canvas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.ToastManager;
*///?} elif >=1.21.2 {
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.ToastManager;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.ToastComponent;
*///?}

/**
 * Sol's top layer: drawn right after the game's toasts, over the HUD and any screen, whether or not the HUD is
 * hidden (Sol's notifications, and the test scenario's lone crosshair).
 */
//? if >=1.21.2 {
@Mixin(ToastManager.class)
//?} else {
/*@Mixin(ToastComponent.class)
*///?}
abstract class SolOverlayMixin {
    //? if >=26.1 {
    /*@Inject(method = "extractRenderState", at = @At("TAIL"))
    private void cosmeticsmod$overlay(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        SolOverlay.render(new Canvas(graphics));
    }
    *///?} else {
    @Inject(method = "render", at = @At("TAIL"))
    private void cosmeticsmod$overlay(GuiGraphics graphics, CallbackInfo ci) {
        SolOverlay.render(new Canvas(graphics));
    }
    //?}
}
