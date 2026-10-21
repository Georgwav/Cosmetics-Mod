package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.sol.ui.kit.ScreenSlide;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}

/**
 * Sol UI's screen slide (ScreenSlide): an inventory, a container or the chat drawn a few pixels lower while it opens,
 * round the game's own drawing of the screen and its tooltip.
 */
@Mixin(Screen.class)
abstract class ScreenSlideMixin {
    //? if >=26.1 {
    /*@Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("HEAD"), require = 0)
    private void cosmeticsmod$slide(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ScreenSlide.begin((Screen) (Object) this, new Canvas(graphics));
    }

    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("RETURN"), require = 0)
    private void cosmeticsmod$slid(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ScreenSlide.end(new Canvas(graphics));
    }
    *///?} elif >=1.21.10 {
    @Inject(method = "renderWithTooltipAndSubtitles", at = @At("HEAD"), require = 0)
    private void cosmeticsmod$slide(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ScreenSlide.begin((Screen) (Object) this, new Canvas(graphics));
    }

    @Inject(method = "renderWithTooltipAndSubtitles", at = @At("RETURN"), require = 0)
    private void cosmeticsmod$slid(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ScreenSlide.end(new Canvas(graphics));
    }
    //?} else {
    /*@Inject(method = "renderWithTooltip", at = @At("HEAD"), require = 0)
    private void cosmeticsmod$slide(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ScreenSlide.begin((Screen) (Object) this, new Canvas(graphics));
    }

    @Inject(method = "renderWithTooltip", at = @At("RETURN"), require = 0)
    private void cosmeticsmod$slid(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ScreenSlide.end(new Canvas(graphics));
    }
    *///?}
}
