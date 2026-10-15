package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.sol.ui.screens.VanillaLook;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The pause screen's background with Sol's veil over the game's blur (Sol UI on). */
@Mixin(PauseScreen.class)
public abstract class PauseLookMixin {
    //? if >=26.1 {
    /*@Inject(method = "extractBackground", at = @At("TAIL"), require = 0)
    private void cosmeticsmod$veil(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick,
                                   CallbackInfo ci) {
        if (VanillaLook.on()) {
            Screen self = (Screen) (Object) this;
            VanillaLook.pauseVeil(new Canvas(graphics), self.width, self.height);
        }
    }
    *///?} else {
    @Inject(method = "renderBackground", at = @At("TAIL"), require = 0)
    private void cosmeticsmod$veil(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (VanillaLook.on()) {
            Screen self = (Screen) (Object) this;
            VanillaLook.pauseVeil(new Canvas(graphics), self.width, self.height);
        }
    }
    //?}
}
