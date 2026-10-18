package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.sol.hud.HudTweaks;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}

/** Sol UI's boss bar tweaks (HudTweaks): hidden, or drawn at their size from the top middle of the screen. */
@Mixin(BossHealthOverlay.class)
abstract class BossBarTweaksMixin {
    @Unique
    private boolean cosmeticsmod$scaled;

    //? if >=26.1 {
    /*@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void cosmeticsmod$in(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (HudTweaks.hidesBossBars()) {
            ci.cancel();
            return;
        }
        cosmeticsmod$scaled = HudTweaks.bossIn(new Canvas(graphics), graphics.guiWidth());
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V", at = @At("RETURN"), require = 0)
    private void cosmeticsmod$out(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (cosmeticsmod$scaled) {
            cosmeticsmod$scaled = false;
            new Canvas(graphics).pop();
        }
    }
    *///?} else {
    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void cosmeticsmod$in(GuiGraphics graphics, CallbackInfo ci) {
        if (HudTweaks.hidesBossBars()) {
            ci.cancel();
            return;
        }
        cosmeticsmod$scaled = HudTweaks.bossIn(new Canvas(graphics), graphics.guiWidth());
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;)V", at = @At("RETURN"), require = 0)
    private void cosmeticsmod$out(GuiGraphics graphics, CallbackInfo ci) {
        if (cosmeticsmod$scaled) {
            cosmeticsmod$scaled = false;
            new Canvas(graphics).pop();
        }
    }
    //?}
}
