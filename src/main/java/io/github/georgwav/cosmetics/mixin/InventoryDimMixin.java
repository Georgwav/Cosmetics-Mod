package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.client.modules.ScreenTweaks;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Screen: no darkening behind inventories. A container screen darkens the world with the transparent background up
 * to 1.21.8, and with the blur and the in-world menu background from 1.21.9; each is left out for container
 * screens while Screen asks.
 */
@Mixin(Screen.class)
abstract class InventoryDimMixin {
    //? if >=26.1 {
    /*@Inject(method = {"extractTransparentBackground", "extractBlurredBackground",
            "extractMenuBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V"}, at = @At("HEAD"), cancellable = true, require = 0)
    *///?} else {
    @Inject(method = {"renderTransparentBackground", "renderBlurredBackground",
            "renderMenuBackground(Lnet/minecraft/client/gui/GuiGraphics;)V"}, at = @At("HEAD"), cancellable = true, require = 0)
    //?}
    private void cosmeticsmod$noDim(CallbackInfo ci) {
        if ((Object) this instanceof AbstractContainerScreen<?> && ScreenTweaks.hidesInventoryDim()) {
            ci.cancel();
        }
    }
}
