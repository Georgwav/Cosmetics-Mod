package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.sol.ui.screens.VanillaLook;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.gui.screens.DeathScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The death screen's red wash becomes the theme's dusk with an ember glow below (Sol UI on). */
@Mixin(DeathScreen.class)
public abstract class DeathLookMixin {
    //? if >=26.1 {
    /*@Inject(method = "extractDeathBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private static void cosmeticsmod$dusk(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int w, int h, CallbackInfo ci) {
        if (VanillaLook.on() && VanillaLook.deathVeil(new Canvas(graphics), w, h)) {
            ci.cancel();
        }
    }
    *///?} else {
    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void cosmeticsmod$dusk(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        net.minecraft.client.gui.screens.Screen self = (net.minecraft.client.gui.screens.Screen) (Object) this;
        if (VanillaLook.on() && VanillaLook.deathVeil(new Canvas(graphics), self.width, self.height)) {
            ci.cancel();
        }
    }
    //?}
}
