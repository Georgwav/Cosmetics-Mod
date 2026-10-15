package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.sol.Feature;
import io.github.georgwav.cosmetics.sol.Guard;
import io.github.georgwav.cosmetics.sol.hud.SolHud;
import io.github.georgwav.cosmetics.sol.hud.SolHudCards;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.world.BossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}

/** Sol UI's boss bars: each bar drawn by Sol in place of the game's sprites; the game still writes the names. */
@Mixin(BossHealthOverlay.class)
abstract class SolBossBarMixin {
    //? if >=26.1 {
    /*@Inject(method = "extractBar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IILnet/minecraft/world/BossEvent;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void cosmeticsmod$bar(GuiGraphicsExtractor graphics, int x, int y, BossEvent event, CallbackInfo ci) {
        bar(new Canvas(graphics), x, y, event, ci);
    }
    *///?} else {
    @Inject(method = "drawBar(Lnet/minecraft/client/gui/GuiGraphics;IILnet/minecraft/world/BossEvent;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void cosmeticsmod$bar(GuiGraphics graphics, int x, int y, BossEvent event, CallbackInfo ci) {
        bar(new Canvas(graphics), x, y, event, ci);
    }
    //?}

    private static void bar(Canvas c, int x, int y, BossEvent event, CallbackInfo ci) {
        if (SolHud.draws(SolHud.Part.BOSS)) {
            SolHudCards.bossBar(c, x, y, event);
            if (Guard.on(Feature.SOL_UI)) {
                ci.cancel();
            }
        }
    }
}
